# Optional log exploration

You only need this guide if you want to browse application logs in Grafana. The
observability stack is optional: it does not start the app or database, and the
app continues working if this stack is stopped.

## Quick start

1. Start Docker Desktop with Linux containers. Run the commands below from the
   repository root in PowerShell.
2. Put a private password in the repository-root `.env` file:

   ```dotenv
   GRAFANA_ADMIN_PASSWORD=choose-a-private-password
   ```

   Compose needs this password to create Grafana's first admin account. The
   root `.gitignore` excludes `.env`, but the file is plain text on your machine.
   If you prefer not to save it there, set `$env:GRAFANA_ADMIN_PASSWORD` in your
   current PowerShell window and remove `--env-file .env` from the command you
   run below.

3. Start the **one** source that matches where your app is running:

   **Local Spring Boot app** — use this when running the app directly on your
   computer. Alloy reads `logs/service-advertiser.json`:

   ```powershell
   docker compose --env-file .env -f observability/compose.yaml -f observability/compose.local.yaml up -d
   ```

   **Simulation in Docker** — use this when running the simulation Compose stack.
   Alloy reads only the simulation API container's Docker output:

   ```powershell
   docker compose --env-file .env -f observability/compose.yaml -f observability/compose.simulation.yaml up -d
   ```

4. Open [Grafana](http://localhost:3000), sign in with username `admin` and the
   password from `.env`, then select **Explore**, choose the **Loki** data source,
   and run this query:

   ```logql
   {service_name="service-advertiser"}
   ```

   Give it a recent time range. Logs appear only after the selected app source
   has made requests.

That is all you need for normal use. Choose exactly one source override at a
time. To stop the stack, run the matching `docker compose ...` command above
again, replacing `up -d` with `down`. This keeps collected data so it is there
the next time you start the stack.

## Useful details

- Grafana is available only on your computer at `localhost:3000`; Loki and Alloy
  are not exposed on host ports.
- The password is applied when Grafana first starts. Changing `.env` later does
  not change the account stored in the persistent Grafana volume.
- Local log files are under `logs/`. Simulation logs are normally collected from
  Docker output. Docker can remove those logs when the API container is removed.
- Loki keeps logs for seven days, but retention cleanup is delayed and does not
  impose a hard disk limit. Docker Desktop stores the named data volumes in its
  Linux VM.
- Simulation collection mounts the Docker socket read-only. A read-only mount
  still grants powerful Docker API access, so use it only with trusted images
  and configuration on a trusted local machine.
- Start collection before a workload if you need its logs. Local mode follows
  the active JSON file, not old compressed archives; recovery of logs written
  before Alloy starts is not guaranteed.

## Optional simulation log files

Simulation normally writes to Docker output, which Alloy collects. To also save
application JSON files in a persistent volume, start the API with this extra
Compose file:

```powershell
docker compose -f simulation/compose.yaml -f simulation/compose.logging-files.yaml up -d api
```

The files are in the `simulation_logs` volume. Alloy continues reading Docker
output. Include the same extra Compose file in later simulation commands if you
want to keep that file output enabled.

## Advanced operations

<details>
<summary>Switch between local and simulation sources</summary>

Run the `up -d` command for the source you want. The shared Compose project keeps
the same Loki and Grafana data volumes and Alloy's saved positions. Start
collection before workloads; duplicate-free recovery of missed history is not
guaranteed. Do not rename Alloy components or delete its state volume casually.

</details>

<details>
<summary>Inspect storage or check container status</summary>

Use the same Compose source command from Quick start, replacing `up -d` with
`ps` to see its containers. Other useful PowerShell commands:

```powershell
Get-Content ./logs/service-advertiser.json -Tail 10
docker volume ls --filter label=com.docker.compose.project=service-advertiser-observability
docker system df -v
```

Observability volumes end in `loki_data`, `grafana_data` and `alloy_data`.
Inside their containers they are mounted at `/loki`, `/var/lib/grafana` and
`/var/lib/alloy/data`.

</details>

<details>
<summary>Validate configuration or test log collection</summary>

Set `$obs` to the selected source's Compose arguments, then run:

```powershell
$obs = @('--env-file', '.env', '-f', 'observability/compose.yaml', '-f', 'observability/compose.local.yaml')
docker compose @obs config --quiet
docker compose @obs run --rm --no-deps alloy validate /etc/alloy/source.alloy
docker compose @obs run --rm --no-deps loki '-config.file=/etc/loki/config.yaml' '-verify-config=true'
```

For simulation source validation, change `compose.local.yaml` to
`compose.simulation.yaml`. To check collection, send a request with
`X-Request-ID: logging-smoke` and query it in Grafana:

```logql
{service_name="service-advertiser"} | json | request_id="logging-smoke"
```

</details>

<details>
<summary>Erase observability data (destructive)</summary>

This deletes Loki history, Grafana settings/accounts and Alloy read positions.
It does not delete simulation or application database volumes. Run only when you
intend to erase observability data:

```powershell
$obs = @('--env-file', '.env', '-f', 'observability/compose.yaml', '-f', 'observability/compose.local.yaml')
docker compose @obs down --volumes
```

</details>

The [logging conventions](../docs/engineering/logging.md) cover architecture,
privacy, rotation and retention. Loki retention is asynchronous; seven days is
an age limit, not a hard storage quota. For retention tests, use separate
disposable volumes and a test configuration. For performance comparisons, use
equal Locust parameters and disposable, equally seeded databases; report latency,
throughput, errors and storage growth before considering asynchronous logging.
