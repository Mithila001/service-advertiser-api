# Environment operations

The application has three independent environments. Development uses host Java;
Simulation and Deployment use the same root Dockerfile. PostgreSQL schema changes
remain owned by Flyway; do not edit applied migrations or enable Hibernate DDL.

Requires Java 21 and Maven for host development, Docker with Linux containers,
and Compose **2.24.4+**. Includes need 2.20+, optional dependencies need 2.20+, and
the local deployment port override needs 2.24.4+. Run commands from the repo root.

| Purpose | Compose project | Database / API | Grafana |
|---|---|---|---|
| Development | `service-advertiser` | loopback 5433 / host 8080 | loopback 3000, separate optional project |
| Simulation | `service-advertiser-simulation` | private / loopback 8081 | loopback 3001 |
| Deployment | `service-advertiser-deployment` | private / Caddy 80,443 | loopback 3002, SSH tunnel |

Development observability retains the existing
`service-advertiser-observability` project and volumes. Existing development and
simulation database volume identities are preserved. No data migration is needed
for the default identities. If you previously used `-p`, keep that project name.
Inspect `docker compose ls` and volume ownership before changing project names.
There are no global container names or shared data networks between environments.

## Authoritative command matrix

In PowerShell, define argument arrays once. They are command arguments, not a new
configuration mechanism. The deployment env file contains paths/settings, not secrets.

```powershell
$dev = @('-f', 'docker-compose.yaml')
$obs = @('--env-file', '.env', '-f', 'observability/compose.yaml', '-f', 'observability/compose.local.yaml')
$sim = @('-f', 'simulation/compose.yaml')
$deploy = @('--env-file', 'C:/private/service-advertiser/deployment.env', '-f', 'deployment/compose.yaml', '--profile', 'database')
```

| Action | Development DB | Development monitoring | Simulation | Deployment |
|---|---|---|---|---|
| Start | `docker compose @dev up -d --wait` | `docker compose @obs up -d` | `docker compose @sim up -d --build --wait` | `docker compose @deploy up -d --wait` |
| Status | `docker compose @dev ps` | `docker compose @obs ps` | `docker compose @sim ps` | `docker compose @deploy ps` |
| Logs | `docker compose @dev logs --tail 100` | `docker compose @obs logs --tail 100` | `docker compose @sim logs --tail 100 api` | `docker compose @deploy logs --tail 100 api` |
| Stop, retain data | `docker compose @dev down` | `docker compose @obs down` | `docker compose @sim --profile activity --profile tools down` | `docker compose @deploy down` |
| Intentional reset | Procedure below | Explicit volume procedure below | [Simulation reset](../simulation/README.md#reset-and-erase-the-simulation) | No routine reset; restore runbook only |

For Linux deployment, use an array in Bash:

```bash
deploy=(--env-file /etc/service-advertiser/deployment.env -f deployment/compose.yaml --profile database)
docker compose "${deploy[@]}" up -d --wait
docker compose "${deploy[@]}" ps
docker compose "${deploy[@]}" logs --tail 100 api
docker compose "${deploy[@]}" down
```

Do not add `--volumes` to normal maintenance. Stop/start and image recreation keep
data. Restart policies restart exited processes; Docker health status alone does
not restart unhealthy processes. API startup waits for PostgreSQL when included,
not for monitoring. Readiness includes the database; liveness does not.

## Local application

Copy `.env.example` to `.env` and generate a private JWT key as described in the
root README. Use unquoted `KEY=value` lines: Spring parses this file as Java
properties. Run from the root directory, including your IDE working directory.

```powershell
docker compose @dev up -d --wait
mvn spring-boot:run
```

The default `development` profile alone imports `.env`; there is no dotenv
dependency. OS environment variables override file properties. Containers use
explicit profiles and do not import this file. Tests explicitly use `test`.
The API listens on `127.0.0.1:8080` and default health is `/actuator/health`.

Optional monitoring changes health location: Actuator runs on management port
9090, and `/livez` and `/readyz` remain on the API listener. Only health and
Prometheus are exposed on management; `/actuator/env` and config properties are
not exposed. See [monitoring instructions](../observability/README.md) before
binding management beyond loopback.

## Intentional development reset

This erases only the development database. Back it up first if needed. Stop the
host application. Resolve the volume by labels; never guess a global volume name.

```powershell
docker compose @dev down
$volume = @(docker volume ls -q --filter label=com.docker.compose.project=service-advertiser --filter label=com.docker.compose.volume=postgres_data)
if ($volume.Count -ne 1) { throw 'Expected exactly one development database volume' }
docker volume inspect $volume[0] --format '{{json .Labels}}'
# After verifying this is the intended disposable development database:
docker volume rm $volume[0]
docker compose @dev up -d --wait
```

Monitoring reset is separate: stop that project's monitoring, resolve each of
`loki_data`, `grafana_data`, `alloy_data`, `prometheus_data` by project and volume
labels, inspect ownership, then explicitly remove only the intended volumes.
Resetting Grafana also removes its saved accounts/settings; resetting Alloy loses
positions. Do not use Docker prune or another environment's project arguments.

## Checks

```powershell
mvn '-Dtest=U_*' test
mvn test
python infrastructure/check.py
python infrastructure/check.py --containers
docker compose @sim run --rm --no-deps --entrypoint python bootstrap check.py
```

The infrastructure checker renders all Compose variants with temporary dummy
configuration, asserts exposure/isolation/resource rules, and optionally runs
image-native validators. It does not start application environments or delete
volumes. The live acceptance runner is documented in [verification](verification.md).

## CI and release verification

Main pushes run Maven tests, version/helper checks and plain infrastructure
configuration checks. Tag pushes run container-native checks, one temporary image
build and disposable acceptance before creating a release draft. This keeps heavy
verification off ordinary merges; standalone simulation and benchmarking remain
manual. See the [release guide](../docs/for-dev/release-management.md) for commands,
permissions and seven-day report retention. The linked verification document
preserves historical results; it is not proof of a new remote workflow run.

## Architecture and limits

Shared application configuration owns business behavior and reliability defaults;
profiles own environment behavior; Compose owns topology, storage and resources.
Simulation keeps real API/auth/business flows but disables throttling, uses dummy
credentials, and keeps independently adjustable resources. It is not a production
capacity certificate. Never point Locust at Deployment or real customer data.

Deployment starts with 2,656 MiB total container limits on a 4 GB host. API 768 MiB
(384 MiB heap), PostgreSQL 512, Loki 512, Prometheus 384, Grafana 256, Alloy 128,
Caddy 96. There are no blanket CPU quotas. The budget needs measurement under your
workload; a Linux VM memory guarantee cannot be inferred from Docker Desktop.

Prometheus retention is 7d/1GB locally and in Simulation, 3d/512MB in Deployment.
Loki retains seven days locally/in Simulation and three days in Deployment. These
are not hard disk quotas: account for WAL, active files and delayed compaction.
Docker logs rotate at roughly three 10 MiB files; application JSON rotation keeps
the existing bounded policy. Monitor disk with `docker system df -v` and free space.

Use standard datasource settings for managed PostgreSQL; keep TLS verification
enabled for external databases. Application code contains no cloud provider API.
There is no Kubernetes, Vault, tracing stack, automatic deployment or load balancer.
