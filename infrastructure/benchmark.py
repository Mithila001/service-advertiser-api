"""One reproducible Phase 2 run, using a new synthetic simulation and runtime pair."""
import argparse
import csv
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import tempfile
import time
from urllib.parse import urlencode
from acceptance import ROOT, command


METRIC_NAMES = ('up', 'http_server_requests_seconds_count', 'jvm_memory_used_bytes',
                'jvm_gc_pause_seconds_count', 'process_cpu_usage', 'process_uptime_seconds',
                'hikaricp_connections_active', 'hikaricp_connections_idle',
                'hikaricp_connections_pending', 'hikaricp_connections_max')
SAFE_LABELS = {'__name__', 'application', 'environment', 'job', 'instance', 'uri', 'method',
               'status', 'outcome', 'exception', 'area', 'pool', 'le', 'statistic'}


def prometheus_query(base, query):
    url = 'http://localhost:9090/api/v1/query?' + urlencode({'query': query})
    response = json.loads(command(*base, 'exec', '-T', 'prometheus', 'wget', '-qO-', url))
    if response.get('status') != 'success':
        raise AssertionError('Prometheus query failed: ' + str(response.get('error', 'unknown error')))
    return response['data']['result']


def telemetry(base):
    targets = prometheus_query(base, 'up')
    application_targets = [series for series in targets
                           if series['metric'].get('job') == 'service-advertiser']
    if not application_targets or any(series['value'][1] != '1' for series in application_targets):
        raise AssertionError('Prometheus reports a failed or missing API scrape')
    names = '|'.join(METRIC_NAMES)
    metrics = prometheus_query(base, '{__name__=~"' + names + '"}')
    available = {series['metric'].get('__name__') for series in metrics}
    required = {'up', 'http_server_requests_seconds_count', 'jvm_memory_used_bytes',
                'process_cpu_usage', 'hikaricp_connections_active', 'hikaricp_connections_max'}
    missing = required - available
    if missing:
        raise AssertionError('Prometheus is missing expected application metrics: ' + ', '.join(sorted(missing)))
    counts = prometheus_query(base, 'sum(rate(http_server_requests_seconds_count[1m]))')
    p95 = prometheus_query(base,
        'histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[1m])))')
    return {
        'scrape_up': len(application_targets),
        'requests_per_second': counts[0]['value'][1] if counts else None,
        'http_p95_seconds': p95[0]['value'][1] if p95 else None,
        'metrics': [{'labels': {key: value for key, value in series['metric'].items() if key in SAFE_LABELS},
                     'value': series['value'][1]} for series in metrics],
    }


def volume_usage(project):
    report = json.loads(command('docker', 'system', 'df', '-v', '--format', '{{json .}}'))
    rows = [volume for volume in report.get('Volumes', []) if volume['Name'].startswith(project + '_')]
    sizes = {}
    for volume in rows:
        match = re.fullmatch(r'([0-9.]+)\s*(B|kB|MB|GB|TB)', volume.get('Size', ''))
        sizes[volume['Name']] = {
            'reported_size': volume.get('Size'),
            'bytes': int(float(match.group(1)) * {'B': 1, 'kB': 1_000, 'MB': 1_000_000,
                                                  'GB': 1_000_000_000, 'TB': 1_000_000_000_000}[match.group(2)])
            if match else None,
        }
    return sizes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--users', type=int, choices=(10, 25, 50, 100), required=True)
    parser.add_argument('--monitoring', choices=('on', 'off'), default='on')
    parser.add_argument('--image', default='service-advertiser:infrastructure-check')
    parser.add_argument('--warmup', default='60s')
    parser.add_argument('--duration', default='5m')
    args = parser.parse_args()
    project = 'sa-benchmark-' + secrets.token_hex(4)
    output = ROOT / 'target' / (project + f'-{args.users}-{args.monitoring}')
    output.mkdir(parents=True)
    metadata = {'users': args.users, 'spawn_rate': 2, 'monitoring': args.monitoring,
                'warmup': args.warmup, 'duration': args.duration, 'project': project,
                'started_utc': datetime.now(timezone.utc).isoformat(),
                'image': command('docker', 'image', 'inspect', args.image, '--format', '{{.Id}}').strip(),
                'samples': []}
    with tempfile.TemporaryDirectory(prefix='runtime-', dir=output) as temporary:
        temporary = Path(temporary)
        runtime = temporary / 'runtime'
        runtime.mkdir()
        simulator_user = f'{os.getuid()}:{os.getgid()}' if os.name == 'posix' else '1000:1000'
        override = temporary / 'compose.yaml'
        override.write_text(f'''services:
  api:
    image: {args.image}
    ports: !override []
  grafana:
    ports: !override []
  bootstrap:
    user: '{simulator_user}'
    volumes:
      - {runtime.as_posix()}:/mnt/locust/runtime
  locust:
    user: '{simulator_user}'
    volumes:
      - {runtime.as_posix()}:/mnt/locust/runtime
      - {output.as_posix()}:/mnt/locust/results
''')
        base = ['docker', 'compose', '-p', project, '-f', 'simulation/compose.yaml', '-f', str(override)]
        workload = ['run', '--rm', 'locust', '-f', 'locustfile.py', '--host', 'http://api:8080',
                    '--headless', '-u', str(args.users), '-r', '2']
        try:
            command(*base, 'up', '-d', '--no-build', '--wait', *([] if args.monitoring == 'on' else ['api']))
            command(*base, 'run', '--rm', 'bootstrap')
            command(*base, 'run', '--rm', '--entrypoint', 'python', 'bootstrap', 'check.py', '--live')
            command(*base, *workload, '-t', args.warmup)
            containers = command(*base, 'ps', '-q').split()
            api_container = command(*base, 'ps', '-q', 'api').strip()
            metadata['volume_sizes_before_measured_window'] = volume_usage(project)
            with (output / 'locust.log').open('wb') as logfile:
                measured = subprocess.Popen([*base, *workload, '-t', args.duration, '--csv', 'results/measured',
                                             '--html', 'results/measured.html'], cwd=ROOT, stdout=logfile, stderr=subprocess.STDOUT)
                next_diagnostics = 0
                try:
                    while measured.poll() is None:
                        sample = {'utc': datetime.now(timezone.utc).isoformat(),
                                  'stats': command('docker', 'stats', '--no-stream', '--format', '{{json .}}', *containers).splitlines()}
                        if time.monotonic() >= next_diagnostics:
                            sample['api_log_bytes'] = len(command('docker', 'logs', api_container, binary=True))
                            if args.monitoring == 'on':
                                sample['telemetry'] = telemetry(base)
                            next_diagnostics = time.monotonic() + 30
                        metadata['samples'].append(sample)
                        (output / 'metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')
                        time.sleep(5)
                    if measured.returncode:
                        raise RuntimeError('Locust exited unsuccessfully; inspect locust.log')
                finally:
                    if measured.poll() is None:
                        measured.terminate()
                        measured.wait(timeout=30)
            with (output / 'measured_stats.csv').open(newline='') as stats:
                aggregate = next(row for row in csv.DictReader(stats) if row['Name'] == 'Aggregated')
            metadata['aggregate'] = aggregate
            metadata['volume_sizes_after_measured_window'] = volume_usage(project)
            before = metadata['volume_sizes_before_measured_window']
            after = metadata['volume_sizes_after_measured_window']
            metadata['volume_growth_bytes'] = {
                name: after.get(name, {}).get('bytes') - before.get(name, {}).get('bytes')
                if after.get(name, {}).get('bytes') is not None and before.get(name, {}).get('bytes') is not None
                else None for name in before.keys() | after.keys()}
            assert int(aggregate['Request Count']) > 0
            assert int(aggregate['Failure Count']) == 0, 'Unexpected workload failures; inspect the saved reports'
            if args.monitoring == 'on':
                assert any(sample.get('telemetry', {}).get('metrics') for sample in metadata['samples']), \
                    'Prometheus returned no application telemetry during the measured window'
            states = {}
            for container in containers:
                oom_killed = command('docker', 'inspect', container, '--format', '{{.State.OOMKilled}}').strip() == 'true'
                restart_count = int(command('docker', 'inspect', container, '--format', '{{.RestartCount}}').strip())
                states[container] = {'oom_killed': oom_killed, 'restart_count': restart_count}
                assert not oom_killed and restart_count == 0, (container, states[container])
            metadata['container_states'] = states
            metadata['status'] = 'passed'
        except Exception:
            metadata['status'] = 'failed'
            raise
        finally:
            metadata['finished_utc'] = datetime.now(timezone.utc).isoformat()
            (output / 'metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')
            try:
                (output / 'containers.log').write_text(command(*base, '--profile', 'activity', '--profile', 'tools', 'logs', '--no-color', '--tail', '100'))
            finally:
                command(*base, 'down', '--volumes', '--remove-orphans')
    print('Reports retained at ' + str(output))


if __name__ == '__main__':
    main()
