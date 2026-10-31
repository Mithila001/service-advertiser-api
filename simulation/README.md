# Service Advertiser simulation

This optional demo runs the existing API against its own PostgreSQL database and
uses Locust to create synthetic activity. It does not write directly to the
database. You need Docker Desktop with Linux containers and Docker Compose v2;
you do not need Java, Maven or Python installed on your computer.

## Quick start

Run these commands in PowerShell from the repository root.

### First time: start the API and create sample data

```powershell
docker compose -f simulation/compose.yaml up -d --build --wait
docker compose -f simulation/compose.yaml run --rm bootstrap
```

The first command builds and starts the simulation API, its private database, and its own monitoring services.
The second creates synthetic accounts and businesses. Bootstrap is a one-time
step for this database. If you already ran it, skip it; it deliberately refuses
to populate an existing or partly initialized database.

### Start or restart the activity dashboard

```powershell
docker compose -f simulation/compose.yaml up -d locust
```

Open [Locust](http://localhost:8089). Start with **10 users** and a spawn rate
of **2 users per second**, then click **Start**. The host field should already
be `http://api:8080`; leave it as is. Users means concurrent simulated visitors,
not the number of accounts in the database. You can later try 25, 50 or 100 users.

The API and its Swagger page are available at
[localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html).
All published ports bind to your own computer only.

### Stop when you are done

To stop the whole simulation but keep its database and generated accounts:

```powershell
docker compose -f simulation/compose.yaml --profile activity --profile tools down
```

The database and `simulation/runtime/manifest.json` are paired. Keep both if you
want to continue later. On a later run, start the API and Locust again, but do
not run bootstrap again:

```powershell
docker compose -f simulation/compose.yaml up -d --wait api
docker compose -f simulation/compose.yaml up -d locust
```

## Important safety notes

- The sample accounts and credentials are synthetic and predictable. Never use
  them for real accounts.
- The simulation has its own PostgreSQL volume. It does not use your development
  database or root `.env` configuration.
- Bootstrap refuses to overwrite an existing or interrupted setup. If it reports
  that data already exists, do not keep retrying it; see **Reset the simulation**
  below only if you intend to erase this simulation's data.
- The workload supports up to 100 concurrent users. It creates and changes data
  through the API, so higher load makes more requests against the local API.

## Optional: view logs in Grafana

Monitoring starts with the normal full-stack command. Open Grafana at
[localhost:3001](http://localhost:3001), username `admin`, password
`simulation_only_grafana` (synthetic, local-only). Loki and Prometheus have independent
simulation storage. Development Grafana can run simultaneously at localhost:3000.

The API itself remains independent of monitoring: starting only `api` is useful for
matched monitoring-overhead comparisons. Start monitoring before a measured workload
when you need its history. See [monitoring operations](../observability/README.md).

## Advanced options

<details>
<summary>Change token lifetimes for a short test</summary>

Default access tokens last 15 minutes and refresh tokens last seven days. To
test expiry faster, stop Locust and recreate the API with shorter lifetimes:

```powershell
$env:SIM_ACCESS_LIFETIME = '20s'
$env:SIM_REFRESH_LIFETIME = '90s'
docker compose -f simulation/compose.yaml up -d --wait api
```

Remove the overrides and recreate the API to restore defaults:

```powershell
Remove-Item Env:SIM_ACCESS_LIFETIME, Env:SIM_REFRESH_LIFETIME
docker compose -f simulation/compose.yaml up -d --wait api
```

</details>

<details>
<summary>Run a headless load test and save a report</summary>

Stop the dashboard first, then run a two-minute test with 10 users and save CSV
and HTML reports under `simulation/results/`:

```powershell
docker compose -f simulation/compose.yaml stop locust
docker compose -f simulation/compose.yaml run --rm locust -f locustfile.py --host http://api:8080 --headless -u 10 -r 2 -t 2m --csv results/10-users --html results/10-users.html
```

Change `-u 10` and the report prefix to try 25, 50 or 100 users. Request labels
group IDs and unique names; reports do not contain request bodies or tokens.
There are no performance thresholds or automatic optimization.

</details>

<details>
<summary>Run simulator checks</summary>

Run deterministic checks in the same container as the workload:

```powershell
docker compose -f simulation/compose.yaml run --rm --no-deps --entrypoint python bootstrap check.py
```

To exercise a real HTTP lifecycle, first complete bootstrap and stop Locust,
then run:

```powershell
docker compose -f simulation/compose.yaml run --rm --entrypoint python bootstrap check.py --live
```

This creates one additional customer and verifies account and business flows. It
removes that account's temporary businesses but retains the customer. The same
deterministic checks can optionally run on a host with Python:
`python simulation/check.py`.

</details>

<details>
<summary>Save simulation application logs to a persistent volume</summary>

By default, the API writes JSON to Docker output. To additionally save application
log files, include this extra Compose file when starting the API:

```powershell
docker compose -f simulation/compose.yaml -f simulation/compose.logging-files.yaml up -d api
```

The files live in the `simulation_logs` volume. To inspect the latest entries:

```powershell
docker compose -f simulation/compose.yaml -f simulation/compose.logging-files.yaml exec api sh -c 'tail -n 5 /var/log/service-advertiser/service-advertiser.json'
```

Include the extra file in later simulation commands to keep file output enabled.
Alloy still collects Docker output, not these files.

</details>

<details>
<summary>Reset and erase the simulation</summary>

**Destructive:** this deletes the simulation database and its generated accounts.
Only reset if you intentionally want a fresh database. Do not use the root
development Compose file for this.

```powershell
docker compose -f simulation/compose.yaml --profile activity --profile tools down
$volume = @(docker volume ls -q --filter label=com.docker.compose.project=service-advertiser-simulation --filter label=com.docker.compose.volume=simulation_data)
if ($volume.Count -ne 1) { throw 'Expected exactly one simulation database volume' }
docker volume inspect $volume[0] --format '{{json .Labels}}'
# Verify ownership above before this intentional database-only deletion.
docker volume rm $volume[0]
if ($LASTEXITCODE -ne 0) { throw 'Volume deletion failed; keep the manifest' }
Remove-Item -LiteralPath ./simulation/runtime/manifest.json -ErrorAction SilentlyContinue
Remove-Item -LiteralPath ./simulation/runtime/manifest.tmp -ErrorAction SilentlyContinue
```

Monitoring volumes are retained. Erase their history separately only if intended;
never use `down --volumes` as the database-only reset. Then repeat the first-time
setup. Reports remain under `simulation/results/`
until you remove them yourself. Keep the lock file; a stale lock filename is
harmless and will be reused.

</details>

## How the simulation works

The simulation is isolated in the `service-advertiser-simulation` Compose
project, private network and PostgreSQL volume. Only the API on port 8081 and
Locust on port 8089 are published, and both bind to loopback. The simulator uses
only the Compose API hostname, does not follow redirects and does not use host
proxies. It cannot be pointed at an arbitrary remote target.

Bootstrap creates 100 synthetic accounts (80 customers and 20 providers), 20
businesses and 60–80 items. Catalog categories and city/district pairs are
resolved through the API; missing catalog entries stop bootstrap. See the
[data rules](docs/data-rules.md) and [user flows](docs/simulation-flows.md).

`simulation/runtime/manifest.json` stores synthetic credentials, generated IDs,
ownership and identity counters. Keep this manifest with its database; deleting
one without the other is unsupported. A process lock prevents simultaneous
bootstrap, checks and Locust runs against the same runtime. Stop the dashboard
container before starting a separate simulator process.

Continuous activity stops registering accounts at 500 stored or reserved users
and reuses available accounts. It caps live businesses at 100 and items at 10 per
business. Bootstrap businesses cannot be deleted and keep at least two active
items. Reads dominate the workload. New-account growth is finite because the API
has no account-deletion endpoint.

Java changes require rebuilding the API image with `--build`; Python and data
changes are bind-mounted and need a simulator restart. Limits, task weights and
wait times are documented constants, not a separate configuration system.

On Linux the Locust image runs as UID/GID 1000. Ensure only `simulation/runtime`
and `simulation/results` are writable by that identity, or use a local Compose
override setting `user` on both `bootstrap` and `locust` to your host UID/GID.
Do not make the checkout world-writable. The disposable verification runners
automatically select the invoking Linux user's UID/GID for their own mounts.

The isolated API explicitly disables rate limiting with RATE_LIMIT_ENABLED=false. Body and database limits remain active. Container termination allows 45 seconds. Verify throttling separately with protection enabled; keep Locust focused on performance.

## Environment differences

Simulation reuses the shared application image and PostgreSQL/Flyway schema, real
JWT/refresh flows, and application business rules. It intentionally uses dummy
secrets, disables rate limiting, has no public reverse proxy, and does not inherit
the deployment resource caps. JSON/request/database protections remain enabled.
Metrics scrape every 15 seconds; monitoring overhead affects benchmark results.
Resettable synthetic datasets and repeatability take priority over production parity.
Do not load real data or external credentials or expose the loopback ports publicly.
See [verification and Phase 2](../infrastructure/verification.md) for measured runs.

Account registration now requires a private phone; first-party synthetic clients
supply a formatted test phone explicitly. Business creation/replacement requires
`addressLine1`. Public business phone remains optional and independent.
V8 requires real phone/address values for existing data; use only the documented
disposable database reset procedures when discarding development/simulation data.
Simulation database and manifest must be reset together. Never reset deployment
volumes to apply this schema change. Event timestamps now include UTC `Z`.
