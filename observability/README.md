# Logs, metrics and dashboards

Each environment has its own Loki, Prometheus, Grafana and Alloy state. There is
no source-switching operation and no shared environment history. Use the
[command matrix](../infrastructure/README.md) for startup/status/stop.

Existing shared-project history is preserved in the development monitoring volumes;
older simulation entries collected there stay there until retention removes them.
They are not copied into the new simulation monitoring volumes. Older development
entries may still have `environment="local"`; new entries use `development`.

## Development (optional)

Normal local development needs only PostgreSQL and the host application. To add
monitoring, first set `GRAFANA_ADMIN_PASSWORD` in root `.env`. Never commit it.
Start Spring with `development,monitoring`, initially bound to loopback:

```powershell
mvn spring-boot:run '-Dspring-boot.run.profiles=development,monitoring'
# In another terminal:
curl.exe --fail http://127.0.0.1:9090/actuator/prometheus
```

Docker Prometheus scrapes `host.docker.internal:9090`. A loopback listener may not
be reachable through Docker Desktop on your installation. Before binding
management to `0.0.0.0`, restrict the listener with Windows Firewall:

1. Inspect `Get-NetConnectionProfile` and Windows Firewall's default inbound policy.
   Keep unsolicited inbound blocked on every active profile.
2. Inspect existing Java inbound allow rules using Windows Defender Firewall
   advanced security. Remove or narrow broad Java allow rules exposing 9090.
3. Determine the actual Docker Desktop/backend source address or interface from
   firewall logging and Docker Desktop networking. Do not assume a WSL subnet.
4. Create an elevated inbound allow rule scoped to TCP 9090, the current Java
   executable, and only the verified Docker source/interface. Template after
   assigning actual values:

```powershell
# New-NetFirewallRule -DisplayName 'Service Advertiser Docker metrics' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 9090 -Program $javaExecutable -RemoteAddress $dockerSource -Profile Any
```

5. Restart Spring with `$env:MANAGEMENT_BIND_ADDRESS='0.0.0.0'` in its terminal
   and the monitoring profiles. API binding remains loopback.
6. Start monitoring, verify the target is UP, and verify from another LAN machine
   that `Test-NetConnection <developer-host> -Port 9090` fails. If a safe source
   restriction cannot be established, keep management on loopback.

```powershell
docker compose --env-file .env -f observability/compose.yaml -f observability/compose.local.yaml up -d
```

Open localhost:3000, sign in as `admin` with the configured password. First boot
initializes the account; changing `.env` does not rotate an existing Grafana account.
Local Alloy reads the JSON file read-only; Spring's console stays readable.
Remove the firewall rule when no longer needed. Never disable the firewall.

## Simulation and Deployment

Normal Simulation startup includes monitoring at localhost:3001. Its synthetic
Grafana password is `simulation_only_grafana`. Alloy reads only that project's API
stdout. A read-only Docker socket still grants powerful Docker API access; use
this collector only for trusted local synthetic simulation.

Deployment monitoring is included at loopback 3002, accessed through SSH tunneling.
Alloy runs as the application's group and reads its JSON volume read-only, without
Docker socket access. See the [deployment runbook](../deployment/README.md).

## Explore and dashboards

Provisioned **Application overview** and **Simulation overview** use stable
`prometheus` and `loki` datasource UIDs. Select an available environment, generate
traffic, and allow two scrapes for rate panels. Panels show request/error rates,
p50/p95/p99, JVM heap/committed memory, GC, process CPU, Hikari active/idle/max/pending,
and logs. JVM committed memory is not container RSS; use `docker stats --no-stream`.
No host/database exporter is installed, so dashboards do not claim those metrics.

In Explore select Loki:

```logql
{service_name="service-advertiser",environment="simulation"}
```

Send a request with `X-Request-ID: infrastructure-smoke`, then query:

```logql
{service_name="service-advertiser",environment="simulation"} | json | request_id="infrastructure-smoke"
```

Development uses `environment="development"`; Deployment uses `deployment`.
Request IDs stay JSON fields, never Loki/Prometheus labels. Metrics use route
templates, not user IDs, raw URLs or submitted values. Successful probes/scrapes
are quiet; failures stay visible where the request logger applies.

## Validate and diagnose

Run `python infrastructure/check.py --containers` for Compose and image-native
validation. Inspect scraping from inside Prometheus:

```powershell
docker compose -f simulation/compose.yaml exec -T prometheus wget -qO- 'http://localhost:9090/api/v1/targets'
```

If data is missing, inspect API readiness, target errors, collector permissions,
datasource health and time ranges. Blank panels do not mean zero load. Stopping
monitoring must not interrupt API requests. Start collection before workloads:
rotation, container removal and outages can cause gaps/replay. This is not an
authoritative audit transport.

Loki retains seven days locally/in Simulation and three in Deployment. Prometheus
retains seven days/1GB or three days/512MB. Retention is asynchronous and excludes
some active/WAL overhead; inspect disk separately. Alloy positions and dashboard
state survive normal restart. Reset monitoring separately from databases with the
ownership checks in [operations](../infrastructure/README.md). Use disposable
projects for tests, preserving existing history. See [logging engineering notes](../docs/engineering/logging.md)
for privacy and file rotation.
