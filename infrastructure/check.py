"""Non-destructive Compose checks; --containers also runs image-native validators."""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def run(*args, capture=False):
    return subprocess.run(args, cwd=ROOT, check=True, text=True,
                          stdout=subprocess.PIPE if capture else None).stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--containers', action='store_true')
    args = parser.parse_args()
    # Only configuration rendering uses these placeholders; no deployment is started.
    with tempfile.TemporaryDirectory(prefix='service-advertiser-config-') as directory:
        configuration = Path(directory) / 'validation.env'
        configuration.write_text('\n'.join([
            'APPLICATION_IMAGE=service-advertiser:validation', 'SITE_ADDRESS=http://localhost',
            'GRAFANA_ADMIN_PASSWORD=validation_only_password',
            'DEPLOYMENT_SECRETS_DIR=' + Path(directory).as_posix(),
        ]) + '\n')
        models = {}
        variants = {
            'development': ['-f', 'docker-compose.yaml'],
            'local-monitoring': ['-f', 'observability/compose.yaml', '-f', 'observability/compose.local.yaml'],
            'simulation': ['-f', 'simulation/compose.yaml', '--profile', 'activity', '--profile', 'tools'],
            'deployment': ['-f', 'deployment/compose.yaml', '--profile', 'database'],
            'deployment-local': ['-f', 'deployment/compose.yaml', '-f', 'deployment/compose.local.yaml', '--profile', 'database'],
            'managed-database': ['-f', 'deployment/compose.yaml'],
        }
        for name, flags in variants.items():
            model = json.loads(run('docker', 'compose', '--env-file', str(configuration), *flags,
                                   'config', '--format', 'json', capture=True))
            models[name] = model
            services = model['services']
            for service_name in ('postgres', 'loki', 'prometheus', 'alloy'):
                if service_name in services and name != 'development':
                    assert not services[service_name].get('ports'), (name, service_name)
            assert not any(service.get('container_name') for service in services.values())
            print(name + ': configuration OK')
        sim = models['simulation']
        deploy = models['deployment']
        assert sim['name'] == 'service-advertiser-simulation'
        assert deploy['name'] == 'service-advertiser-deployment'
        assert models['development']['name'] == 'service-advertiser'
        assert models['local-monitoring']['name'] == 'service-advertiser-observability'
        assert sim['services']['alloy']['environment']['ALLOY_COMPOSE_PROJECT'] == sim['name']
        assert sim['services']['api']['environment']['RATE_LIMIT_ENABLED'] == 'false'
        api = deploy['services']['api']
        assert api['environment']['RATE_LIMIT_ENABLED'] == 'true'
        assert 'PASSWORD' not in ' '.join(api['environment'])
        assert 'JWT_SECRET_BASE64' not in api['environment']
        assert not api.get('ports')
        assert len(api['secrets']) == 2
        assert not any('docker.sock' in str(volume) for volume in deploy['services']['alloy']['volumes'])
        for name in ('loki_data', 'grafana_data', 'alloy_data', 'prometheus_data'):
            assert sim['volumes'][name]['name'] != deploy['volumes'][name]['name']
        local_ports = models['deployment-local']['services']['caddy']['ports']
        assert len(local_ports) == 1 and local_ports[0]['host_ip'] == '127.0.0.1'
        assert str(local_ports[0]['published']) == '8082'
        assert sum(int(service.get('mem_limit', 0)) for service in deploy['services'].values()) == 2656 * 1024 * 1024
        assert not models['managed-database']['services'].get('postgres')
    for dashboard in (ROOT / 'observability/dashboards').glob('*.json'):
        assert json.loads(dashboard.read_text())['panels']
    if args.containers:
        validators = [
            ('grafana/alloy:v1.20.1', 'alloy-local.alloy', ['validate', '/config']),
            ('grafana/alloy:v1.20.1', 'alloy-deployment.alloy', ['validate', '/config']),
            ('grafana/alloy:v1.20.1', 'alloy-simulation.alloy', ['validate', '/config']),
            ('grafana/loki:3.7.8', 'loki.yaml', ['-config.file=/config', '-config.expand-env=true', '-verify-config=true']),
        ]
        for image, filename, command in validators:
            run('docker', 'run', '--rm', '--mount',
                f'type=bind,source={ROOT / "observability" / filename},target=/config,readonly',
                '-e', 'ALLOY_COMPOSE_PROJECT=service-advertiser-simulation', image, *command)
        run('docker', 'run', '--rm', '--mount',
            f'type=bind,source={ROOT / "observability/loki.yaml"},target=/config,readonly',
            '-e', 'LOKI_RETENTION_PERIOD=72h', 'grafana/loki:3.7.8',
            '-config.file=/config', '-config.expand-env=true', '-verify-config=true')
        for environment in ('local', 'simulation', 'deployment'):
            run('docker', 'run', '--rm', '--entrypoint', 'promtool', '--mount',
                f'type=bind,source={ROOT / "observability" / ("prometheus." + environment + ".yaml")},target=/etc/prometheus/prometheus.yaml,readonly',
                'prom/prometheus:v3.13.5', 'check', 'config', '/etc/prometheus/prometheus.yaml')
        run('docker', 'run', '--rm', '--mount',
            f'type=bind,source={ROOT / "deployment/Caddyfile"},target=/etc/caddy/Caddyfile,readonly',
            '-e', 'SITE_ADDRESS=http://localhost', 'caddy:2.11.7-alpine',
            'caddy', 'validate', '--config', '/etc/caddy/Caddyfile', '--adapter', 'caddyfile')
    print('Infrastructure checks passed')


if __name__ == '__main__':
    main()
