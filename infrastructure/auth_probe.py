"""Diagnose fresh-token rejection in a disposable simulation; never prints tokens."""
import argparse
import base64
import hashlib
import hmac
import json
import sys
import subprocess
import time
from acceptance import ROOT, command


def probe():
    sys.path.insert(0, '/mnt/locust')
    from common import Api, HttpTransport, RequestFailure, BASE_URL
    from locust.env import Environment
    from locustfile import transport_for
    api = Api(HttpTransport())
    account = {'email': 'clock_probe@example.test', 'password': 'Synthetic_clock_probe_1!'}
    api.call('POST', '/api/v1/auth/register', {'name': 'Clock Probe', **account}, expected=(201,))
    api = Api(transport_for(Environment(host=BASE_URL)))
    for attempt in range(2000):
        api.login(account)
        try:
            api.ownership()
        except RequestFailure as error:
            if error.status != 401:
                raise
            parts = api.access.split('.')
            claims = json.loads(base64.urlsafe_b64decode(parts[1] + '=' * (-len(parts[1]) % 4)))
            now = time.time()
            key = base64.b64decode('c2ltdWxhdGlvbl9vbmx5X3NpZ25pbmdfa2V5XzMyX2J5dGVz')
            signature = base64.urlsafe_b64encode(hmac.new(key, '.'.join(parts[:2]).encode(), hashlib.sha256).digest()).decode().rstrip('=')
            result = {'attempt': attempt, 'issued_ahead_ms': (claims['iat'] - now) * 1000,
                      'expires_in_seconds': claims['exp'] - now, 'signature_valid': hmac.compare_digest(signature, parts[2])}
            time.sleep(0.25)
            try:
                api.ownership()
                result['same_token_valid_after_250ms'] = True
            except RequestFailure:
                result['same_token_valid_after_250ms'] = False
            print(json.dumps(result), flush=True)
            raise AssertionError('Fresh token rejected; diagnostics above contain no token values')
    print('2000 Locust fresh token login/read pairs passed', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--image', default='service-advertiser:infrastructure-check')
    parser.add_argument('--inside', action='store_true', help=argparse.SUPPRESS)
    options = parser.parse_args()
    if options.inside:
        probe()
    else:
        import secrets
        import tempfile
        from pathlib import Path
        project = 'sa-auth-probe-' + secrets.token_hex(4)
        with tempfile.TemporaryDirectory(dir=ROOT / 'target') as temporary:
            override = Path(temporary) / 'compose.yaml'
            override.write_text(f'''services:
  api:
    image: {options.image}
    ports: !override []
    command:
      - --logging.level.mithila001.service_advertiser.auth.AuthConfiguration=DEBUG
  bootstrap:
    volumes:
      - {(ROOT / 'infrastructure').as_posix()}:/mnt/probe:ro
''')
            args = ['docker', 'compose', '-p', project, '-f', 'simulation/compose.yaml', '-f', str(override)]
            try:
                command(*args, 'up', '-d', '--no-build', '--wait', 'api')
                try:
                    print(command(*args, 'run', '--rm', '--no-deps', '--entrypoint', 'python', 'bootstrap',
                                  '/mnt/probe/auth_probe.py', '--inside'))
                except subprocess.CalledProcessError as error:
                    print(error.stdout.decode(errors='replace'), flush=True)
                    raise
            finally:
                try:
                    (ROOT / 'target/auth-probe-api.log').write_text(command(*args, 'logs', '--no-color', 'api'))
                finally:
                    command(*args, 'down', '--volumes', '--remove-orphans')
