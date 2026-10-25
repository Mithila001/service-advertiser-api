"""Live, destructive ONLY within fresh sa-check-* projects; never uses existing data."""
import argparse
import base64
import csv
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import secrets
import shlex
import socket
import subprocess
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]


def command(*args, binary=False, input=None):
    result = subprocess.run(args, cwd=ROOT, input=input,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        print(result.stderr.decode(errors='replace')[-6000:], flush=True)
        result.check_returncode()
    return result.stdout if binary else result.stdout.decode()


def http(base, path, payload=None, headers=None):
    request = Request(base + path, data=None if payload is None else json.dumps(payload).encode(),
                      headers={'Content-Type': 'application/json', **(headers or {})})
    try:
        with urlopen(request, timeout=15) as response:
            return response.status, response.read().decode()
    except HTTPError as error:
        return error.code, error.read().decode()


def eventually(check, description, timeout=120):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        try:
            if check():
                print(description + ': OK', flush=True)
                return
        except (URLError, subprocess.CalledProcessError, json.JSONDecodeError):
            pass
        time.sleep(2)
    raise AssertionError(description + ': timed out')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--image', default='service-advertiser:infrastructure-check')
    parser.add_argument('--duration', default='30s')
    args = parser.parse_args()
    output = ROOT / 'target'
    output.mkdir(exist_ok=True)
    prefix = 'sa-check-' + secrets.token_hex(4)
    evidence = {'source': command('git', '-c', f'safe.directory={ROOT.as_posix()}', 'rev-parse', 'HEAD').strip(),
                'image': command('docker', 'image', 'inspect', args.image, '--format', '{{.Id}}').strip(),
                'started_utc': datetime.now(timezone.utc).isoformat(), 'project_prefix': prefix}
    projects = []
    clients = []
    host_process = None
    with tempfile.TemporaryDirectory(prefix='acceptance-', dir=output) as folder:
        folder = Path(folder)
        for filename, value in [('database_password', secrets.token_urlsafe(32)),
                                ('jwt_key', base64.b64encode(secrets.token_bytes(32)).decode()),
                                ('grafana_password', secrets.token_urlsafe(32))]:
            (folder / filename).write_text(value)
        env = folder / 'validation.env'
        env.write_text(f'APPLICATION_IMAGE={args.image}\nSITE_ADDRESS=http://localhost\n'
                       f'DEPLOYMENT_SECRETS_DIR={folder.as_posix()}\n'
                       'DEPLOYMENT_INGRESS_SUBNET=10.203.91.0/24\n'
                       'CADDY_INGRESS_ADDRESS=10.203.91.254\nTRUSTED_PROXY_REGEX=10\\.203\\.91\\.254\n')
        deploy_override = folder / 'deployment.yaml'
        deploy_override.write_text('''services:
  caddy:
    ports: !override ['127.0.0.1::80']
  grafana:
    ports: !override ['127.0.0.1::3000']
  api:
    environment:
      RATE_LIMIT_LOGIN_IP_CAPACITY: 4
      RATE_LIMIT_LOGIN_ACCOUNT_IP_CAPACITY: 2
''')
        runtime = folder / 'runtime'
        reports = folder / 'results'
        runtime.mkdir()
        reports.mkdir()
        simulator_user = f'{os.getuid()}:{os.getgid()}' if os.name == 'posix' else '1000:1000'
        sim_override = folder / 'simulation.yaml'
        sim_override.write_text(f'''services:
  api:
    image: {args.image}
    ports: !override ['127.0.0.1::8080']
    command:
      - --logging.level.mithila001.service_advertiser.auth.AuthConfiguration=DEBUG
  grafana:
    ports: !override ['127.0.0.1::3000']
  bootstrap:
    user: '{simulator_user}'
    volumes:
      - {runtime.as_posix()}:/mnt/locust/runtime
      - {reports.as_posix()}:/mnt/locust/results
  locust:
    user: '{simulator_user}'
    volumes:
      - {runtime.as_posix()}:/mnt/locust/runtime
      - {reports.as_posix()}:/mnt/locust/results
''')

        def compose(kind, *arguments, binary=False, input=None):
            return command('docker', 'compose', *projects_by_kind[kind], *arguments, binary=binary, input=input)

        projects_by_kind = {
            'dev': ['-p', prefix + '-dev', '--env-file', str(env), '-f', 'docker-compose.yaml'],
            'deploy': ['-p', prefix + '-deploy', '--env-file', str(env), '-f', 'deployment/compose.yaml',
                       '-f', 'deployment/compose.local.yaml', '-f', str(deploy_override), '--profile', 'database'],
            'sim': ['-p', prefix + '-sim', '--env-file', str(env), '-f', 'simulation/compose.yaml', '-f', str(sim_override)],
        }
        dev_override = folder / 'development.yaml'
        dev_override.write_text("services:\n  postgres:\n    ports: !override ['127.0.0.1::5432']\n")
        projects_by_kind['dev'] += ['-f', str(dev_override)]

        def container(kind, service):
            return compose(kind, 'ps', '-q', service).strip()

        def sql(kind, statement):
            return compose(kind, 'exec', '-T', 'postgres', 'sh', '-ec',
                           'psql -tA -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "$1"', '--', statement).strip()

        def internal(kind, url):
            return compose(kind, 'exec', '-T', 'prometheus', 'wget', '-qO-', url)

        try:
            missing_secrets = subprocess.run(['docker', 'run', '--rm', '--network', 'none',
                '-e', 'SPRING_PROFILES_ACTIVE=deployment', args.image], cwd=ROOT, capture_output=True)
            assert missing_secrets.returncode != 0
            assert b'/run/secrets' in missing_secrets.stdout + missing_secrets.stderr
            print('required deployment secret tree fails closed: OK', flush=True)
            projects.append('dev')
            compose('dev', 'up', '-d', '--wait')
            jar_container = command('docker', 'create', args.image).strip()
            try:
                command('docker', 'cp', jar_container + ':/app/app.jar', str(folder / 'application.jar'))
            finally:
                command('docker', 'rm', jar_container)
            with socket.socket() as listener:
                listener.bind(('127.0.0.1', 0))
                host_port = listener.getsockname()[1]
            host_environment = {**os.environ, 'SPRING_PROFILES_ACTIVE': 'development',
                                'SPRING_DATASOURCE_URL': 'jdbc:postgresql://' + compose('dev', 'port', 'postgres', '5432').strip() + '/service_advertiser',
                                'SPRING_DATASOURCE_USERNAME': 'dev_user123', 'SPRING_DATASOURCE_PASSWORD': 'dev_user123',
                                'JWT_SECRET_BASE64': base64.b64encode(secrets.token_bytes(32)).decode()}
            with (folder / 'host.log').open('w') as host_log:
                host_process = subprocess.Popen(['java', '-jar', str(folder / 'application.jar'), '--server.port=' + str(host_port)],
                                                cwd=folder, env=host_environment, stdout=host_log, stderr=subprocess.STDOUT)
            host_base = 'http://127.0.0.1:' + str(host_port)
            eventually(lambda: http(host_base, '/actuator/health')[0] == 200, 'host development startup')
            sql('dev', "CREATE TABLE environment_sentinel (value text); INSERT INTO environment_sentinel VALUES ('development')")
            for kind in ('deploy', 'sim'):
                projects.append(kind)
                compose(kind, 'up', '-d', '--no-build', '--wait', '--wait-timeout', '180')
                print(kind + ': full stack healthy', flush=True)
            malformed_key = subprocess.run(['docker', 'compose', *projects_by_kind['deploy'],
                'run', '--rm', '--no-deps', '-e', 'JWT_SECRET_BASE64=invalid', 'api',
                '--logging.file.name='], cwd=ROOT, capture_output=True)
            assert malformed_key.returncode != 0
            assert b'JWT_SECRET_BASE64' in malformed_key.stdout + malformed_key.stderr
            print('malformed deployment signing key fails closed: OK', flush=True)
            deploy_base = 'http://' + compose('deploy', 'port', 'caddy', '80').strip()
            sim_base = 'http://' + compose('sim', 'port', 'api', '8080').strip()
            assert http(deploy_base, '/readyz')[0] == 200
            assert http(deploy_base, '/actuator/prometheus')[0] == 404
            for kind in ('deploy', 'sim'):
                def scraped(kind=kind):
                    query = json.loads(internal(kind, 'http://localhost:9090/api/v1/query?' + urlencode({'query': 'up'})))
                    return any(item['value'][1] == '1' for item in query['data']['result'])
                eventually(scraped, kind + ' Prometheus scrape')
            payload = {'name': 'Infrastructure Test', 'email': 'infra@example.test', 'password': 'Infrastructure_only_1!'}
            assert http(deploy_base, '/api/v1/auth/register', payload)[0] == 201
            status, body = http(deploy_base, '/api/v1/auth/login', {k: payload[k] for k in ('email', 'password')})
            assert status == 200
            token = json.loads(body)['accessToken']
            assert http(deploy_base, '/api/v1/categories/business', headers={'Authorization': 'Bearer ' + token})[0] == 200
            for kind, base in [('deploy', deploy_base), ('sim', sim_base)]:
                request_id = prefix + '-' + kind
                assert http(base, '/api/v1/categories/business', headers={'X-Request-ID': request_id})[0] == 200
                query = '{service_name="service-advertiser"} |= "' + request_id + '"'
                url = 'http://loki:3100/loki/api/v1/query_range?' + urlencode({'query': query, 'limit': '10'})
                eventually(lambda: bool(json.loads(internal(kind, url))['data']['result']), kind + ' correlated logs')
            # Keep clients alive simultaneously so Docker cannot recycle a previous client IP.
            curl_image = 'curlimages/curl:8.16.0'
            network = prefix + '-deploy_egress'
            login_url = 'http://caddy/api/v1/auth/login'
            bad_login = json.dumps({'email': 'unknown@example.test', 'password': 'Wrong_password_1!'})
            curl = ['curl', '-s', '-o', '/dev/null', '-w', '%{http_code}', '-H', 'Content-Type: application/json']
            def client_script(script):
                identifier = command('docker', 'run', '-d', '--network', network, '--entrypoint', 'sh',
                                     curl_image, '-c', 'sleep 600').strip()
                clients.append(identifier)
                return command('docker', 'exec', identifier, 'sh', '-ec', script)
            # Same client/account must remain throttled even when forwarding headers change.
            lines = []
            for attempt in range(3):
                lines.append(shlex.join([*curl, '-H', 'X-Forwarded-For: 192.0.2.' + str(attempt),
                                         '-d', bad_login, login_url]) + '; echo')
            result = client_script('\n'.join(lines))
            assert result.split() == ['401', '401', '429'], result
            # Different fresh client has an independent account/IP budget.
            result = client_script(shlex.join([*curl, '-d', bad_login, login_url]))
            assert result == '401', result
            lines = [shlex.join([*curl, '-H', 'X-Forwarded-For: 198.51.100.' + str(attempt),
                                 '-d', json.dumps({'email': f'ip-{attempt}@example.test', 'password': 'Wrong_password_1!'}), login_url]) + '; echo'
                     for attempt in range(6)]
            result = client_script('\n'.join(lines))
            assert result.split() == ['401', '401', '401', '401', '429', '429'], result
            print('proxy IP/account isolation and spoof resistance: OK', flush=True)
            before = sql('deploy', 'SELECT count(*) FROM users')
            compose('deploy', 'stop', 'postgres')
            assert http(deploy_base, '/livez')[0] == 200
            assert http(deploy_base, '/readyz')[0] == 503
            compose('deploy', 'start', 'postgres')
            eventually(lambda: http(deploy_base, '/readyz')[0] == 200, 'readiness recovery with independent liveness')
            compose('deploy', 'restart', 'postgres')
            eventually(lambda: http(deploy_base, '/readyz')[0] == 200, 'database restart recovery')
            compose('deploy', 'up', '-d', '--no-deps', '--force-recreate', '--wait', 'api')
            assert sql('deploy', 'SELECT count(*) FROM users') == before
            assert sql('dev', 'SELECT value FROM environment_sentinel') == 'development'
            assert http(host_base, '/actuator/health')[0] == 200
            print('restart/recreation persistence: OK', flush=True)
            backup = compose('deploy', 'exec', '-T', 'postgres', 'sh', '-ec',
                             'pg_dump -Fc -U "$POSTGRES_USER" -d "$POSTGRES_DB"', binary=True)
            sql('deploy', 'CREATE DATABASE restore_check')
            compose('deploy', 'exec', '-T', 'postgres', 'sh', '-ec',
                    'pg_restore --exit-on-error -U "$POSTGRES_USER" -d restore_check', input=backup)
            restored = compose('deploy', 'exec', '-T', 'postgres', 'sh', '-ec',
                               'psql -tA -U "$POSTGRES_USER" -d restore_check -c "SELECT count(*) FROM users"').strip()
            assert restored == before
            # Point only the disposable API at the restored database; verify auth and write.
            restore_override = folder / 'restore.yaml'
            restore_override.write_text('services:\n  api:\n    environment:\n      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/restore_check\n')
            original_args = projects_by_kind['deploy'][:]
            projects_by_kind['deploy'] += ['-f', str(restore_override)]
            compose('deploy', 'up', '-d', '--no-deps', '--wait', 'api')
            status, restored_login = http(deploy_base, '/api/v1/auth/login', {k: payload[k] for k in ('email', 'password')})
            assert status == 200
            restored_auth = {'Authorization': 'Bearer ' + json.loads(restored_login)['accessToken']}
            status, cities = http(deploy_base, '/api/v1/locations/cities?size=1')
            assert status == 200
            city = json.loads(cities)['content'][0]['id']
            assert http(deploy_base, '/api/v1/businesses', {
                'uniqueName': 'restored_business', 'information': {'name': 'Restore verification', 'cityId': city}
            }, restored_auth)[0] == 201
            assert http(deploy_base, '/api/v1/businesses/me', headers=restored_auth)[0] == 200
            assert sql('deploy', 'SELECT count(*) FROM business') == '0'
            payload['email'] = 'restored-write@example.test'
            assert http(deploy_base, '/api/v1/auth/register', payload)[0] == 201
            assert sql('deploy', 'SELECT count(*) FROM users') == before
            projects_by_kind['deploy'] = original_args
            print('custom-format backup, restore and authenticated application write: OK', flush=True)
            compose('sim', 'run', '--rm', 'bootstrap')
            compose('sim', 'run', '--rm', '--entrypoint', 'python', 'bootstrap', 'check.py', '--live')
            compose('sim', 'run', '--rm', 'locust', '-f', 'locustfile.py', '--host', 'http://api:8080',
                    '--headless', '-u', '10', '-r', '2', '-t', args.duration, '--csv', 'results/phase1')
            evidence['locust_reports'] = [path.name for path in reports.iterdir()]
            for path in reports.iterdir():
                (output / path.name).write_bytes(path.read_bytes())
            with (reports / 'phase1_stats.csv').open(newline='') as stats:
                aggregate = next(row for row in csv.DictReader(stats) if row['Name'] == 'Aggregated')
                assert int(aggregate['Request Count']) > 0
                assert int(aggregate['Failure Count']) == 0, aggregate
                evidence['simulation_aggregate'] = aggregate
            # Database-only reset after populated activity; other environments retain sentinels.
            compose('sim', '--profile', 'activity', '--profile', 'tools', 'down')
            sim_volume = prefix + '-sim_simulation_data'
            labels = json.loads(command('docker', 'volume', 'inspect', sim_volume))[0]['Labels']
            assert labels['com.docker.compose.project'] == prefix + '-sim'
            assert labels['com.docker.compose.volume'] == 'simulation_data'
            command('docker', 'volume', 'rm', sim_volume)
            for name in ('manifest.json', 'manifest.tmp'):
                (runtime / name).unlink(missing_ok=True)
            compose('sim', 'up', '-d', '--no-build', '--wait', 'api')
            assert sql('sim', 'SELECT count(*) FROM users') == '0'
            assert sql('dev', 'SELECT value FROM environment_sentinel') == 'development'
            assert http(host_base, '/actuator/health')[0] == 200
            print('database-only simulation reset and cross-environment sentinels: OK', flush=True)
            assert sql('deploy', 'SELECT count(*) FROM users') == before
            evidence['container_stats'] = command('docker', 'stats', '--no-stream', '--format',
                                                  '{{.Name}} {{.MemUsage}} {{.CPUPerc}}',
                                                  *compose('deploy', 'ps', '-q').split()).splitlines()
            evidence['images'] = command('docker', 'image', 'inspect', args.image, 'postgres:18',
                'grafana/loki:3.7.8', 'grafana/grafana:13.2.4', 'grafana/alloy:v1.20.1',
                'prom/prometheus:v3.13.5', 'caddy:2.11.7-alpine', 'locustio/locust:2.43.3',
                '--format', '{{.Id}} {{json .RepoDigests}}').splitlines()
            for identifier in compose('deploy', 'ps', '-q').split():
                assert command('docker', 'inspect', identifier, '--format', '{{.State.OOMKilled}}').strip() == 'false'
                assert command('docker', 'inspect', identifier, '--format', '{{.RestartCount}}').strip() == '0'
            evidence['status'] = 'passed'
            print('synthetic simulation lifecycle and short workload: OK', flush=True)
        except Exception:
            evidence['status'] = 'failed'
            if (folder / 'host.log').exists():
                (output / 'acceptance-host.log').write_bytes((folder / 'host.log').read_bytes())
            for kind in projects:
                try:
                    (output / ('acceptance-' + kind + '.log')).write_text(compose(kind, 'logs', '--no-color', '--tail', '120'))
                except subprocess.CalledProcessError:
                    pass
            raise
        finally:
            for identifier in clients:
                command('docker', 'rm', '-f', identifier)
            if host_process is not None:
                host_process.terminate()
                try:
                    host_process.wait(timeout=45)
                except subprocess.TimeoutExpired:
                    host_process.kill()
                    host_process.wait()
            (output / 'infrastructure-acceptance.json').write_text(json.dumps(evidence, indent=2) + '\n')
            # All names are generated above. No existing project is ever selected.
            for kind in reversed(projects):
                compose(kind, 'down', '--volumes', '--remove-orphans')
    print('Live acceptance passed; evidence: target/infrastructure-acceptance.json', flush=True)


if __name__ == '__main__':
    main()
