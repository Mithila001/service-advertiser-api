# Verification record and Phase 2 handoff

Current status (2026-10-26): Phase 2 workstation checks were executed with the
repaired image. See the final execution record below for results and evidence.
Remote CI, Linux VM/file-permission, LAN firewall and public TLS checks still need
their real external environments. Earlier partial reports remain as investigation
history and are superseded by the final record.

## Reproduce Phase 1

Run with Java 21, Maven and Docker access from the repository root:

```powershell
mvn test
python infrastructure/check.py --containers
docker build -t service-advertiser:infrastructure-check .
python infrastructure/acceptance.py --image service-advertiser:infrastructure-check
```

The acceptance runner creates fresh `sa-check-*` projects, random published
loopback ports, dummy secrets and a disposable simulation database/manifest pair.
It verifies host development startup, simultaneous stacks, real proxy client-IP
budgets, scrape/log collection, persistence, a custom-format restore followed by
authenticated application access/write, a short Locust workload and a simulation
database-only reset with other environments' sentinels intact. Cleanup removes
only its own generated projects/volumes. It never selects an existing project.
Read the runner before invoking it on a shared Docker host.

Evidence is retained under `target/`: `infrastructure-acceptance.json`, Locust
`phase1_*.csv`, and failure logs if applicable. Generated secrets/runtime files are
temporary and excluded from Git and Docker builds. Run checks without Python `-O`
because acceptance assertions must remain enabled.

## Verification limits

Record actual execution results in the implementation record below. Local Docker
Desktop can verify configuration and container resource limits but is not proof
of a dedicated 2-vCPU Linux VM's performance, Linux host-file ownership semantics,
Windows firewall isolation from a second LAN host, or public TLS issuance.
Those gates remain explicitly pending until tested on the corresponding hardware.
Never report a GitHub Actions run as successful without an actual remote run.

## Phase 2 instructions for a smaller model

Use the repaired source and image from the post-repair record below. Do not change
security, proxy trust, secrets, dependencies, schema, business behavior or resource
budget. Escalate a reproducible failure needing any of those changes. Do not
delete unfamiliar volumes or reset a developer's existing simulation.

1. Repeat the documented development quick start and optional monitoring start/stop.
2. Repeat simulation population, stop/resume and intentional reset using disposable
   data only. Verify all documented ports/URLs and project names.
3. Repeat local deployment validation, persistence and backup/restore checks.
4. Run the workload matrix below sequentially on the same otherwise idle host.
5. Repeat Alloy/Grafana/Prometheus restart checks on a disposable stack: log a new
   request ID after Alloy restart, verify dashboard provisioning and historical
   queries after backend restart, stop monitoring and confirm the API still serves.
6. For retention tests use disposable volumes and a shorter test configuration;
   do not change deployment retention or wait three/seven days unnecessarily.

### Measured workload matrix

```powershell
python infrastructure/benchmark.py --users 10
python infrastructure/benchmark.py --users 25
python infrastructure/benchmark.py --users 50
python infrastructure/benchmark.py --users 100
# Matched monitoring-overhead comparison; use at least three matched pairs.
python infrastructure/benchmark.py --users 25 --monitoring off
python infrastructure/benchmark.py --users 25 --monitoring on
```

Each invocation creates and populates a fresh independent database/runtime pair,
checks its HTTP lifecycle, warms up for 60 seconds, and then measures for 5 minutes
at 2 users/second spawn rate. Reports exclude the warm-up process. The simulation
uses a separate measured Locust process, so its measured window includes the new
spawn ramp; server/database caches remain warmed. This is not a steady-state-only
latency measurement. The simulation remains stochastic; equally bootstrapped starting datasets do not guarantee
identical task sequences. Record repeated samples rather than claiming exact
production equivalence. The driver preserves reports, metadata, periodic container
stats and diagnostic logs under `target/sa-benchmark-*`, then removes its data.

Inspect `measured_stats.csv`, `measured_failures.csv`, `measured.html`, metadata and
logs. The driver records periodic Docker stats, allowlisted Prometheus metrics,
API Docker-log byte counts, and per-project volume sizes before and after the
measurement. Capture throughput, median/p95/p99, error type, unexpected 429s,
CPU/memory, scrape gaps, JVM/pool pressure and storage growth. Never treat missing
measurements as zero.

For monitoring overhead, compare matched pairs with the same image/user count,
warm-up/duration/host conditions and fresh initial data. Report percentage changes
in throughput, latency and resources, with sample medians. No performance SLO is
invented. Functional errors, OOM, missing expected telemetry and throttling with
limits disabled are failures. Do not optimize based on a single result.

### Report format

For each check record command, expected result, actual result, exit code, evidence
path and pass/fail/pending. Include source revision/image identity, host CPU/RAM,
Docker/Compose versions and run UTC timestamps. List reproducible failures and
external prerequisites separately. A suggested fix is not a verified pass.

## Implementation record

Verified on 2026-10-25 with Java 21, Docker Engine 28.1.1 and Compose
2.36.0-desktop.1 (the sandbox CLI also rendered configurations with 2.35.1).
The Docker Desktop VM has approximately 3.83 GiB available; it is not the target
single Linux VM. Existing development/simulation/monitoring volumes were inventoried
and left untouched. All live tests used generated disposable projects.

New infrastructure dependencies use [Prometheus 3.13 LTS](https://prometheus.io/docs/introduction/release-cycle/)
(patch 3.13.5) and [Caddy 2.11.7](https://github.com/caddyserver/caddy/releases/tag/v2.11.7).
Review supported releases when updating images, retain the verified digest, and
rerun native validation plus live acceptance after version changes.

| Check | Result | Evidence |
|---|---|---|
| Full Java suite, including real monitoring sockets | PASS: 93 tests | `target/infrastructure-tests-final.log` |
| Six Compose variants and image-native validators | PASS | `target/infrastructure-config-final.log` |
| Shared image build, non-root runtime | PASS | `target/infrastructure-image-final.log` |
| Host development + simultaneous Simulation/Deployment | PASS | `target/infrastructure-live-final.log` |
| Required secret tree and malformed JWT rejection | PASS | same live log |
| Real proxy IP/account budgets and spoof rejection | PASS | same live log |
| Readiness outage/recovery, independent liveness | PASS | same live log |
| Correlated file/stdout logs and Prometheus scraping | PASS | same live log |
| Restart/recreation persistence; custom dump/restore + auth/write | PASS | same live log |
| Populated simulation reset preserves other sentinels | PASS | same live log |
| Short 10-user Locust acceptance | PASS: 909 requests, 0 failures (3-minute run) | `target/before-auth-clock-fix/phase1_stats.csv` |
| Deployment resource sample and no OOM/automatic restart | PASS for local smoke only | `target/before-auth-clock-fix/infrastructure-acceptance.json` |
| Backup script shell syntax | PASS in PostgreSQL image | `sh -n` |
| Actual GitHub Actions execution | PENDING: workflow added, not pushed/run | `.github/workflows/verify.yaml` |
| Linux protected-file ownership, 2-vCPU/4-GB capacity | PENDING: target VM required | deployment runbook |
| Windows firewall/Docker-host scrape and second-LAN denial | PENDING: host-specific firewall and LAN client required | monitoring guide |
| Public DNS/TLS issuance | PENDING: real VM/domain required | deployment runbook |
| Full Phase 2 execution on the repaired image | PASS for workstation-run checks; real VM, LAN/firewall, public TLS and remote CI remain pending | final Phase 2 record below |

The original 3-minute Locust run measured approximately 5.42 requests/s, p95 68 ms, p99 74 ms, with 909 requests and zero failures. This is a smoke result, not a performance SLO or capacity assessment. The original deployment resource sample totaled approximately 836 MiB; its per-service memory and image digests are retained in `target/before-auth-clock-fix/infrastructure-acceptance.json`.

The working tree contains the implementation; the base Git revision alone does
not identify these uncommitted changes. Commit the reviewed result before release.

Verified base revision: `5ba94d3aeb384d0af1759da6e34d4def9c407931`.

Original application image identity (before repair): `sha256:2de565c88b534329162fb7e568d1083992d321ed7c5363dfe893688b4a7ebef7`.

Phase 2 driver smoke (not the full measurement matrix): `--users 10 --warmup 10s --duration 30s` passed. Reports: `target/sa-benchmark-14bb355b-10-on/`.

Monitoring-disabled Phase 2 driver smoke (`--users 10 --monitoring off --warmup 5s --duration 15s`) also passed. Reports: `target/sa-benchmark-058cb5c5-10-off/`. These short runs verify the driver branches; they are not matched performance comparisons.

## Phase 1 diagnosis - backward clock correction and fresh-token 401

One earlier acceptance run failed during Locust's initial ownership reconciliation:
`GET /api/v1/businesses/me` returned 401 immediately following successful login.
Phase 2 reproduced it during the warm-up of a 25-user monitoring-disabled run.

Subsequent investigation passed 1,000 fresh login/read pairs through urllib and
2,000 through Locust's HTTP client. The millisecond issuance-boundary unit check
also passes. The final extended 3-minute full acceptance run passed with 909
requests and no failures. These passes do not erase the earlier failure.

The saved API stdout establishes a backward wall-clock step within the same
request thread (`http-nio-8080-exec-1`):

| Event, in output order | UTC timestamp |
|---|---|
| Login succeeded after signing the token | `2026-10-25T18:34:40.508749583Z` |
| That login's request completed, HTTP 200 | `2026-10-25T18:34:39.770535795Z` |
| Next ownership read rejected, HTTP 401 | `2026-10-25T18:34:39.776875192Z` |

The clock stepped backward approximately 738 ms. JWT serialization stores issuance
at whole-second precision, so the token's `iat` was `18:34:40Z`, ahead of the
decoder's clock at the next request. The custom validator allowed no future
issuance time and consequently rejected a fresh token. The OS/VM mechanism causing
the clock adjustment was not established; the application failure is reproduced
deterministically using these timestamps without changing any host clock.

The custom claim validator samples its clock once and permits `iat` up to 60 seconds
ahead. This is a bounded allowance for issuance only; expiration and `nbf` remain
strict, and signature, issuer, audience, subject, role and claim-order validation
remain enforced. Spring Security documents a usual
[60-second clock-skew allowance](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html#oauth2resourceserver-jwt-validation).
No request retry was added. Keep host clocks synchronized; investigate larger
corrections instead of increasing the allowance.

The new clock-step regression failed on the old code and passed after the fix.
Boundary checks accept 60-second future issuance, reject 61 seconds, and reject
expired/not-yet-valid tokens. The PostgreSQL auth suite checks both login and
refresh tokens through the security filter after a small rollback. All 96 Java
tests passed (`target/auth-clock-full-tests.log`); the before-fix failure is
retained at `target/auth-clock-before.log`.

Evidence: `target/infrastructure-intermittent-401-api.log` and
`target/infrastructure-intermittent-401.json`; probes:
`target/infrastructure-auth-probe.log` and `target/infrastructure-auth-probe-locust.log`.
`target/auth-probe-api.log` is replaced on each probe run; the post-repair copy is
preserved separately below. Reproduce the bounded synthetic diagnostic with
`python infrastructure/auth_probe.py --image service-advertiser:auth-clock-fix`.
It uses a fresh `sa-auth-probe-*` project, never prints tokens, and preserves its API
diagnostic log before cleanup. Only the token-free application DEBUG diagnostics
are enabled there. `--help` parses arguments and exits without starting containers.

Post-repair live evidence is recorded in the follow-up below. Phase 2 must use the
repaired image; the historical measurements below used the old one.

## Phase 2 execution record - partial; halted on 2026-10-26

The earlier claim that a new 2,000-pair probe completed before this matrix relied
on a log from the preceding Phase 1 run; it is withdrawn as Phase 2 evidence.
The matrix reproduced the Phase 1 failure,
so testing stopped at the handoff boundary. No authentication rules, retry behavior,
proxy trust, secrets, dependencies, schema or resource limits were changed.

Host: Windows Docker Desktop, Docker Engine 28.1.1, Compose 2.36.0-desktop.1,
3 Docker CPUs and approximately 3.83 GiB available to Docker. Application image:
`sha256:2de565c88b534329162fb7e568d1083992d321ed7c5363dfe893688b4a7ebef7`.
Each successful benchmark used 25 users or the listed user count, spawn rate 2/s,
60-second warm-up and five-minute measured window, with a fresh database/runtime pair.
The Locust measurement starts a new process after warm-up and includes the ramp.

| Run | Monitoring | Requests | Failures | Requests/s | Median / p95 / p99 (ms) | Result |
|---|---:|---:|---:|---:|---:|---|
| 10 users | on | 1,398 | 0 | 5.10 | 6 / 68 / 74 | PASS |
| 25 users | on | 3,036 | 0 | 11.03 | 5 / 29 / 75 | PASS |
| 50 users | on | 5,636 | 0 | 20.45 | 5 / 15 / 70 | PASS |
| 100 users | on | 10,767 | 0 | 39.11 | 5 / 14 / 69 | PASS |
| Matched pair 1, 25 users | off | 3,030 | 0 | 11.06 | 5 / 16 / 68 | PASS |
| Matched pair 1, 25 users | on | 3,029 | 0 | 11.01 | 6 / 21 / 69 | PASS |
| Matched pair 2, 25 users | on | 2,979 | 0 | 10.82 | 5 / 37 / 70 | PASS |
| Matched pair 2, 25 users | off | no measured window | n/a | n/a | n/a | HALTED: fresh-token 401 during warm-up |

These are measurements from this Windows Docker Desktop host, not performance
thresholds or a Linux VM capacity claim. The monitoring comparison is incomplete:
only one complete matched pair exists, so no overhead conclusion is drawn. Pair 3
was not started. The 100-user run's sampled peak container memory was API 469 MiB,
Grafana 462 MiB, Alloy 82 MiB, Loki 102 MiB, PostgreSQL 98 MiB and Prometheus
60 MiB. Container CPU and memory samples are retained in each run's `metadata.json`;
JVM/pool metric trends, scrape gaps and volume/log growth were not separately
queried or measured during these runs.

The halted command was `python infrastructure/benchmark.py --users 25 --monitoring off`.
Locust stopped during its initial warm-up while reconciling the signed-in account,
with `GET /api/v1/businesses/me: unexpected HTTP 401`.
The API log records the request as status 401 at
`2026-10-25T18:34:39.776875192Z`; it does not contain a token or the DEBUG
`jwt_claims_rejected` fields because that logger was not enabled for this ordinary
workload. The run metadata is marked `failed`; the five-minute measured process did
not start, so its empty metrics are not a pass. Evidence is in
`target/sa-benchmark-479391cf-25-off/metadata.json` and `containers.log`.

All completed/failed `sa-benchmark-*` projects were checked after their runs; no
benchmark containers or volumes remained. The earlier claim that profile selectors
caused a teardown defect was not established: observations were made before command
sessions completed. Those selectors were unnecessary, but removing them is not a
demonstrated root-cause fix. The diagnosis follow-up hardens the probe and benchmark
so log-collection failure cannot skip `compose down`.

The following Phase 2 work remained pending at the stop: repeat the development and deployment command
workflows; repeat simulation stop/resume/reset and deployment backup/restore; finish
matched comparison pairs 2 and 3; restart Alloy, Grafana and Prometheus and verify
history/log continuity; verify monitoring outages do not affect API requests; and
run bounded retention/rotation checks. Resume these with the repaired image after
the post-repair live checks pass. Previous performance rates may be affected by
the host clock steps; use a synchronized host for comparisons.

## Post-repair verification - 2026-10-26

Repaired application image identity:
`sha256:2dc314b315821f11cb5d1ce370e8dba6156d0705a75b2820e17783637ab153c9`.
Both local tags `service-advertiser:auth-clock-fix` and
`service-advertiser:infrastructure-check` point to this image. The original image
is retained as `service-advertiser:before-auth-clock-fix`. The source includes
uncommitted changes; record a reviewed source revision before release.

| Command / check | Expected and actual result | Exit | Evidence |
|---|---|---:|---|
| Targeted auth tests before repair | Expected failure reproduced the observed clock-step rejection; 2 failures | 1, expected | `target/auth-clock-before.log` |
| Targeted auth unit/integration tests after repair | 19 tests passed, including bounded issuance, strict expiry/nbf, login and refresh | 0 | `target/auth-clock-after.log` |
| `mvn test` | All 96 tests passed | 0 | `target/auth-clock-full-tests.log` |
| Shared image build | Fixed image built successfully | 0 | `target/auth-clock-image.log` |
| `python infrastructure/auth_probe.py --image service-advertiser:auth-clock-fix` | 2,000 fresh login/read pairs passed; 2,000 successful login events and zero HTTP 401s | 0 | `target/auth-clock-live-probe.log`, `target/auth-clock-probe-api.log` |
| `python infrastructure/benchmark.py --users 25 --monitoring off --image service-advertiser:auth-clock-fix` | Full 60-second warm-up and five-minute measurement passed: 3,020 requests, zero failures/exceptions | 0 | `target/auth-clock-benchmark.log`, `target/sa-benchmark-bfe5841e-25-off/` |
| `python infrastructure/acceptance.py --image service-advertiser:auth-clock-fix --duration 3m` | Full acceptance passed; 908 simulation requests, zero failures, proxy spoof protection, secrets, monitoring, restore/write, persistence and reset isolation passed | 0 | `target/auth-clock-acceptance.log`, `target/auth-clock-acceptance.json` |
| `python target/auth-clock-cleanup-check.py` | Injected workload and diagnostic-log failures propagated; both runners still removed their own generated containers/volumes | 0 | `target/auth-clock-cleanup-check.log` |
| `python infrastructure/auth_probe.py --help` and Python compilation | Help exited without Docker access; changed scripts compile | 0 | documented command |

The fresh probe succeeded despite backward timestamp observations on the same
Java threads: 90 observations over 100 ms, maximum observed reversal 2.732 seconds
(these are per-thread observations, not a count of distinct system corrections).
Token-free details are in `target/auth-clock-probe-clock-events.json`. No test
changed the host or VM clock. The acceptance run recorded no OOM or automatic
container restart, and approximately 827 MiB total deployment-container memory at
its final sample. This remains local functional evidence, not target-VM capacity
verification.

Checks after command completion confirmed no containers or volumes remained for
`sa-auth-probe-1832fbca`, `sa-benchmark-bfe5841e` or the three
`sa-check-1bdb8b4d-*` projects. The forced-failure check also asserted cleanup by
each generated project's exact labels. Existing application databases/projects
were not started, reset or renamed.

The read-only `w32tm /query /status` check reported Windows Time running but not
synchronized, source `Local CMOS Clock`, with no successful sync time
(`target/auth-clock-host-time.log`). This is a host condition to resolve before
performance comparisons; it does not establish which OS/VM component caused each
clock step. No Windows time-service configuration was changed. Keep production
VM time synchronized too; expiry remains strict and issuance more than 60 seconds
ahead is rejected.

The application authentication blocker is cleared. Resume remaining Phase 2
workflow, monitoring restart/outage and retention checks using the repaired image.
Repeat matched performance comparisons on one synchronized host with the same
repaired image for every sample; do not combine old-image and repaired-image
samples into an overhead conclusion. Public TLS, real Linux file permissions,
LAN/firewall isolation, target-VM capacity and remote CI execution remain the
external prerequisites listed above.

## Final Phase 2 execution report - 2026-10-26

The remaining local Phase 2 checklist was executed on the repaired image. No
existing Development, Simulation or observability data was reset. The ordinary
Development PostgreSQL volume was stopped and started without volume removal. All
load tests and destructive reset/restore checks used newly generated `sa-*`
projects and disposable data. The pre-existing unrelated FPG application and
database containers remained running; one Docker sample showed about 42 MiB and
0.13% CPU combined, so this was a mostly idle, not completely idle, workstation.

Host: Windows Docker Desktop, Docker Engine 28.1.1, Compose
2.36.0-desktop.1, 3 Docker CPUs and approximately 3.83 GiB available to Docker.
Windows Time reported synchronized to `time.windows.com` before the matrix
(`target/phase2-host-time.log`). The base source revision is
`5ba94d3aeb384d0af1759da6e34d4def9c407931`; the tested application image is
`sha256:2dc314b315821f11cb5d1ce370e8dba6156d0705a75b2820e17783637ab153c9`.
The source contains uncommitted changes, so this base revision alone is not a
reproducible source revision. Workload runs were recorded from 2026-10-25
20:22 UTC to 21:29 UTC.

| Check | Result | Evidence |
|---|---|---|
| Development quick start | PASS: `docker compose -f docker-compose.yaml up -d --wait` preserved the existing PostgreSQL volume; documented `mvn spring-boot:run` loaded Development configuration and returned `/actuator/health` HTTP 200; API stopped before the database | command output during run; preserved volume `service-advertiser_postgres_data` |
| Development optional monitoring | PASS: main readiness, management health and Prometheus returned HTTP 200; `/actuator/env` remained protected; Prometheus scraped loopback-bound host management via `host.docker.internal:9090`; stopping/restarting monitors did not interrupt the API; Prometheus history and dashboards survived restart | `target/phase2-dev-monitoring/` |
| Simulation full-stack workflow | PASS (runner commands exited 0): `python target/phase2_simulation_lifecycle.py` verified population/live checks; stop/resume retained 104 users, paired manifest and Locust report; one-minute 10-user run completed 447 requests with zero failures; database-only reset removed only its paired data/manifest and preserved monitoring volumes/report; rebootstrap returned 100 users | `target/sa-p2-simulation-26aa8ac5-lifecycle/result.json`, `compose.log` |
| Deployment local validation, persistence and restore | PASS (exit 0): `python infrastructure/acceptance.py --image service-advertiser:auth-clock-fix --duration 1m`; loopback Deployment restart/persistence, custom-format backup/restore into a fresh database, Flyway/application checks and authenticated read/write succeeded; source database remained unchanged | `target/infrastructure-acceptance.json` |
| Configuration variants | PASS (exit 0): `python infrastructure/check.py --containers`; all six Compose variants rendered and Loki, Alloy, Prometheus and Caddy native validators ran. Caddy printed non-fatal warnings about redundant `header_up` directives/formatting; proxy-trust configuration was not changed during Phase 2 | `target/phase2-config-validation.log` |
| Monitoring restart/outage checks | PASS: Alloy collected a post-restart request; Grafana dashboard UIDs persisted; Prometheus retained samples across restart; API stayed ready during a monitoring outage; logs/scrapes resumed and API recreation was observed | `target/sa-p2-simulation-26aa8ac5-lifecycle/result.json` |
| Log rotation | PASS: each of the six Simulation services had Docker JSON rotation configured at `10m`, three files; API output remained below 4.74 MiB in the 100-user measured run | lifecycle result and benchmark metadata |
| Bounded retention | PASS (exit 0): `python target/phase2_retention_check.py`; a 26-hour-old Loki marker in a previous daily table was queryable before retention and absent after about 60 seconds with a test-only 1-hour period; Prometheus's pinned sample expired after about 60 seconds with test-only 45-second retention/10-second blocks, and a new scrape was available after restart. Loki retention is compactor-based and asynchronous; caches were disabled only in this test configuration. Deployment retention was unchanged ([Loki retention behavior](https://grafana.com/docs/loki/latest/operations/storage/retention/)) | `target/phase2-retention/result.json` |
| Generated resource cleanup | PASS: benchmark and retention containers/volumes were checked and removed by their generated names; no matching generated resources remained. Existing databases and volumes were left untouched | `target/phase2-benchmark-commands.txt`, final `docker ps`/`docker volume ls` checks |
| Remote GitHub Actions | PENDING: workflow is present but no remote run was available in this session | `.github/workflows/verify.yaml` |
| External Deployment gates | PENDING: real Linux UID/GID/permissions, 2-vCPU/4-GB VM capacity, second-host LAN/firewall denial and public TLS issuance need those environments | deployment runbook and monitoring guide |

### Workload measurements

Each run used a fresh, equivalent bootstrap, the same application image,
2 users/second spawn rate, a 60-second warm-up, then a five-minute Locust window.
Runs were sequential. The measured Locust process starts after warm-up and
includes its new spawn ramp. Rates are evidence from this workstation, not pass
thresholds or a production capacity claim. All ten runs passed, each recorded
zero Locust failures/exceptions and zero sampled HTTP 429 counters. Monitoring-on
runs recorded ten diagnostic Prometheus snapshots each, with the API target up at
every snapshot. All container states reported zero OOM kills and zero restarts.
The 30-second monitoring snapshots showed 47–66 MiB JVM heap, 0–1 active
connections out of 10 and 0 pending connections; these are sampled values, not
continuous maxima.

| Run | Users | Monitoring | Requests | Requests/s | Median / p95 / p99 (ms) | Result directory |
|---|---:|---:|---:|---:|---:|---|
| Matrix | 10 | on | 1,344 | 4.51 | 6 / 62 / 67 | `target/sa-benchmark-e7c6c154-10-on/` |
| Matrix | 25 | on | 3,034 | 10.17 | 5 / 22 / 65 | `target/sa-benchmark-240d0571-25-on/` |
| Matrix | 50 | on | 5,685 | 19.06 | 4 / 11 / 63 | `target/sa-benchmark-3abc0326-50-on/` |
| Matrix | 100 | on | 10,590 | 35.54 | 4 / 11 / 63 | `target/sa-benchmark-bc72a641-100-on/` |
| Pair 1 | 25 | off | 3,115 | 10.45 | 4 / 17 / 63 | `target/sa-benchmark-db10c8f9-25-off/` |
| Pair 1 | 25 | on | 3,033 | 10.19 | 5 / 24 / 64 | `target/sa-benchmark-2f6d1477-25-on/` |
| Pair 2 | 25 | off | 2,943 | 9.88 | 5 / 26 / 62 | `target/sa-benchmark-18a6c1b2-25-off/` |
| Pair 2 | 25 | on | 2,981 | 10.01 | 5 / 19 / 63 | `target/sa-benchmark-a6b84706-25-on/` |
| Pair 3 | 25 | off | 2,956 | 9.92 | 4 / 18 / 63 | `target/sa-benchmark-11bba04b-25-off/` |
| Pair 3 | 25 | on | 3,072 | 10.30 | 5 / 21 / 65 | `target/sa-benchmark-10cc8a59-25-on/` |

Matched monitoring comparisons (`on` relative to its `off` run):

| Measure | Pair 1 | Pair 2 | Pair 3 | Mean change |
|---|---:|---:|---:|---:|
| Throughput | -2.5% | +1.3% | +3.8% | +0.9% |
| p95 latency | +41.2% | -26.9% | +16.7% | +10.3% |
| p99 latency | +1.6% | +1.6% | +3.2% | +2.1% |
| Average sampled API CPU | +25.5% | +4.3% | +8.6% | +12.8% |
| Average sampled CPU summed across containers | +51.4% | +38.2% | +30.2% | +39.9% |
| Peak combined API + PostgreSQL memory | -7.3% | +1.8% | +1.6% | -1.3% |
| Peak memory summed across the whole stack | +103.0% | +109.6% | +127.6% | +113.4% |

The whole-stack memory and CPU increases include the monitoring containers and
are not application-only overhead. They add about 556–648 MiB peak memory in
these pairs; API plus PostgreSQL peak memory stayed within roughly -7% to +2%.
The average throughput difference was small and inconsistent across pairs; p95
also varied in both directions. These three stochastic runs do not support a
performance guarantee or tuning decision. Docker CPU percentages are sampled
per-container figures summed together, not a normalized host saturation measure.

In the three matched pairs, simulation database volume growth was approximately
16.33–16.37 MiB per run. With monitoring on, Loki grew 1.29–1.36 MiB and
Prometheus 0.19–0.20 MiB during the measured window; Alloy and Grafana volume
growth rounded to zero after warm-up/provisioning. API Docker-log output reached
1.55–1.63 MiB in those pairs and 4.73 MiB in the 100-user run. `docker system df`
size reporting is an approximation, not a hard disk limit. At 100 users, peak
sampled whole-stack memory was about 1.18 GiB and no OOM/restart occurred on the
3-CPU/3.83-GiB Docker Desktop host. This does not verify the deployment resource
budget on the intended smaller VM.

The benchmark driver now records these telemetry and volume measurements in
addition to Locust reports and `docker stats`; its 10-user/30-second smoke also
passed. The script change is in `infrastructure/benchmark.py`. The CI workflow
still has no remote success result.
