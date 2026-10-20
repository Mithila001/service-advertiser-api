# Service Advertiser simulation

An API-driven demonstration environment using Python, Locust and the existing
Spring Boot application. Bootstrap creates 100 synthetic accounts (80 customers,
20 providers), 20 businesses and 60–80 items. Continuous activity browses, registers,
creates, updates and deletes through HTTP. No simulator writes domain data directly
to PostgreSQL.

Requires Docker Desktop with Linux containers and Docker Compose v2. Host Python,
Java and Maven are unnecessary. All commands below run from the repository root.

## Start and populate

```powershell
docker compose -f simulation/compose.yaml up -d --build --wait api
docker compose -f simulation/compose.yaml run --rm bootstrap
docker compose -f simulation/compose.yaml up -d locust
```

Open [Locust](http://localhost:8089), choose 10 users initially and a spawn rate of
2 users/second, then start. Progress to 25, 50 and 100 users. The maximum is 100;
this count describes concurrent virtual users, not registered database accounts.
Leave the host field at `http://api:8080`. Other targets are rejected. The API and
Swagger UI are available at [localhost:8081](http://localhost:8081/swagger-ui/index.html).

Bootstrap runs once and exits. It refuses an existing or interrupted manifest,
including a completed initialization. A missing manifest with surviving known
simulation identities also causes refusal. Recovery from interrupted bootstrap
requires an explicit reset; there is no automatic wipe or bootstrap resume.
Continuous activity requires completed bootstrap.

## Isolation and configuration

`compose.yaml` creates the `service-advertiser-simulation` project, a private internal
network, an unexposed PostgreSQL 18 service and its own persistent volume. The API
is built using Java 21 and existing Flyway migrations. Simulation datasource,
JWT issuer, audience and synthetic signing key are supplied directly to the API
container. Development Compose, `.env`, database and business rules are unchanged.
The Docker build context excludes local secrets and generated runtime files.

API and Locust also join a second bridge network so Docker can publish their ports;
containers attached only to an internal network do not get published port bindings.
PostgreSQL joins only the internal network. Only API port 8081 and dashboard port
8089 are published, both on loopback.
Simulators allow only the Compose API hostname and do not follow redirects or use
host proxies. This version intentionally has no arbitrary remote-target option.

Locust and bootstrap share the pinned `locustio/locust:2.43.3` image. Python uses
Locust, its installed dependencies and the standard library; there are no additional
pip installs. Source is bind-mounted, so Python/data changes need only a simulator
restart. Java changes require rebuilding the API image.

Default access lifetime is 15 minutes; fixed refresh lifetime is seven days.
For a quicker expiry check, stop Locust and recreate the API with shorter lifetimes:

```powershell
$env:SIM_ACCESS_LIFETIME = '20s'
$env:SIM_REFRESH_LIFETIME = '90s'
docker compose -f simulation/compose.yaml up -d --wait api
```

Remove those two environment variables and recreate the API to restore defaults.
In PowerShell, use `Remove-Item Env:SIM_ACCESS_LIFETIME, Env:SIM_REFRESH_LIFETIME`
before running `docker compose -f simulation/compose.yaml up -d --wait api` again.
Business/item/category limits are fixed at the application's defaults of 10/3/2.
Account and business limits, task weights and waiting periods are documented
constants in the Python files, rather than a separate configuration framework.

## State, data and reruns

Curated JSON provides 32 personal names and 20 business clusters with four relevant
item templates each. Catalog category names and city/district pairs are resolved
through HTTP on startup. Missing catalog entries fail before population. Prices
vary within each template's LKR range; item types and category relationships stay
consistent. See [data rules](docs/data-rules.md).

`runtime/manifest.json` stores synthetic credentials, generated account IDs,
business ownership, cluster assignments and monotonic identity counters. Writes
use atomic replacement. Tokens remain in memory and are excluded from reports and
logs. Credentials and the signing key are deliberately synthetic and predictable;
never use them for real accounts. Runtime files and reports are Git-ignored.

A process lock prevents overlapping bootstrap, live-check and Locust processes
against the same runtime. Stop the dashboard container before running another
simulator, even if its current test has stopped. On a later Locust run, ownership
and items are reconciled through authenticated HTTP reads before activity starts.
Keep the manifest with its database: deleting one without the other is unsupported.

Continuous activity stops registration at 500 stored/reserved accounts and reuses
available accounts instead. It caps live businesses at 100 and items at 10 per
business. Bootstrap businesses cannot be deleted and retain at least two ACTIVE
items. Other businesses can be deleted and recreated. Read operations dominate;
new-account growth is finite because this API has no account-deletion endpoint.
See [user flows](docs/simulation-flows.md) for action eligibility and transitions.

## Reports and headless runs

The dashboard shows grouped endpoint statistics and failures and can download
reports. Default dashboard CSV files are written under `simulation/results/`.
Stop the dashboard container before a separate headless run:

```powershell
docker compose -f simulation/compose.yaml stop locust
docker compose -f simulation/compose.yaml run --rm locust -f locustfile.py --host http://api:8080 --headless -u 10 -r 2 -t 2m --csv results/10-users --html results/10-users.html
```

Repeat with `-u 25`, `-u 50`, and `-u 100`, using a distinct report prefix. Request
labels group IDs and unique names; reports do not contain request bodies or tokens.
Empty searches and records legitimately removed between discovery and detail reads
are handled as normal client behavior. Other HTTP failures remain visible even
when the simulator reconciles afterward. Untrustworthy state stops the run with a
nonzero exit code. No performance thresholds or automatic optimization are included.

## Checks

Run deterministic checks in the same container used by the workload:

```powershell
docker compose -f simulation/compose.yaml run --rm --no-deps --entrypoint python bootstrap check.py
```

The same checks can optionally run on a host with Python: `python simulation/check.py`.
They cover catalog pagination and mapping, tokens, transitions, failed writes,
leases, capacity limits, process locking, dashboard guards, protected-item floors,
target restrictions and bootstrap refusal. The two Locust-specific checks are
skipped when optional host Python does not have Locust installed.

After bootstrap and while Locust is stopped, exercise a real HTTP lifecycle:

```powershell
docker compose -f simulation/compose.yaml run --rm --entrypoint python bootstrap check.py --live
```

This creates one additional customer and verifies business promotion, token
rotation, item updates/deletion, business deletion/demotion and re-registration.
It deletes only that account's temporary businesses and retains the customer.
Use a headless run longer than 15 minutes to exercise normal access-token expiry,
or use the shorter lifetimes above and run for two minutes to exercise both
access expiry and fixed refresh expiry. Also check that `-u 101` and a dashboard
request above 100 are rejected.

## Stop and explicitly reset

Stopping preserves the database, manifest and reports:

```powershell
docker compose -f simulation/compose.yaml --profile activity --profile tools down
```

**Reset deletes the simulation database and its account manifest.** First run the
following project-scoped command, then remove only the named manifest. Keep the
lock file in place: the next process will reuse it, and a stale filename is harmless.

```powershell
docker compose -f simulation/compose.yaml --profile activity --profile tools down --volumes
Remove-Item -LiteralPath ./simulation/runtime/manifest.json -ErrorAction SilentlyContinue
Remove-Item -LiteralPath ./simulation/runtime/manifest.tmp -ErrorAction SilentlyContinue
```

Run the startup/bootstrap commands again. Do not use the root development Compose
file for reset. Reports remain available until you remove them deliberately.

## Limits

One process, up to 100 virtual users, at most 500 accounts and 100 businesses.
Startup reconciliation logs into stored accounts and therefore takes longer as the
dataset grows. A single small manifest is rewritten on mutations; use a transactional
state store only if future scale requires it. Initial profile weights describe
starting behavior; actual roles change during journeys. This is a valid-client
demonstration workload, with no deliberate invalid requests, moderation, distributed
workers, monitoring stack, CI pipeline or general scenario engine.
