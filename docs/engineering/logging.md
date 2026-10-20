# Logging and observability

Spring Boot's existing SLF4J/Logback integration writes application events and one
HTTP completion summary per request. Boot's native Logstash encoder produces JSON
Lines, including MDC and SLF4J key/value fields. No logging backend is required for
the application to run. See [operations](../../observability/README.md) for commands.

## Events and correlation

Use INFO for selected successful lifecycle changes, WARN for authentication or
authorization rejection and suspicious refresh activity, and ERROR for unexpected
failures. Ordinary reads and successful refreshes need only their request summary.
Prefer stable `event` names, fixed reasons, and internal identifiers. Successful
business/API events are emitted after transactional service calls return, so a
failed commit is not reported as success. A refresh replay event says revocation
was *requested* inside the transaction; it is not an independent audit record.
Database-backed moderation history remains the authoritative business audit.

The servlet filter runs before Spring Security and supplies `X-Request-ID` on
responses. It accepts exactly one header containing 1–64 ASCII letters, digits,
underscores or hyphens; otherwise it generates a UUID. The ID is correlation data,
never proof of identity or uniqueness. MDC `request_id` connects application events,
error diagnostics, and the completion summary. Previous MDC is restored on exit.

Summaries contain method, route template, status and elapsed milliseconds measured
with a monotonic clock. Unrouted/security-rejected requests use `<unmapped>` rather
than exposing arbitrary paths. Summaries have no stack traces. The existing
exception advice owns handled unexpected-error traces; the filter owns failures
that escape the chain. Error redispatch is skipped. Current endpoints are
synchronous; add async completion/context handling before introducing async APIs.
Successful `GET /actuator/health` requests are quiet. Failed checks remain visible;
the endpoint is public and returns status only.

## Sensitive data and diagnostics

Never add bodies, query strings, arbitrary path values, sensitive headers,
passwords, JWTs, refresh tokens/hashes, secrets, email addresses, moderation text,
or DTOs to logs. Do not attach exceptions to expected authentication/validation
events. Use fixed reasons rather than arbitrary exception messages. Review new
unexpected exception messages and causes: stack traces can contain external data.
Known Hibernate JDBC diagnostic loggers are disabled to avoid duplicate,
value-bearing SQL diagnostics; unexpected errors remain owned by the advice.

Use targeted logger overrides, for example
`--logging.level.mithila001.service_advertiser=DEBUG`, when launching the application for troubleshooting. Use YAML or
`SPRING_APPLICATION_JSON` for logger names containing underscores, which ordinary
environment-variable binding cannot preserve. DEBUG and
TRACE in Spring MVC, Security, Hibernate SQL/bind logging or other dependencies can
expose rejected values or credentials. Inspect the enabled loggers before sharing
diagnostic files. Application log retention is not a substitute for access control.

## Outputs and retention

| Mode | Console | Application files | Central collection |
| --- | --- | --- | --- |
| Local | Readable, with request ID and event fields | JSON Lines, enabled by default | Optional Alloy file reader |
| Simulation | Logstash JSON | Disabled by default | Optional Alloy Docker reader |
| Automated tests | Console | Disabled; file checks use temporary directories | None |

Local output defaults to `logs/service-advertiser.json`, relative to the working
directory. Set `LOGGING_FILE_NAME` to change the path or to an empty value to disable
files. `LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash` enables JSON console output.
Normal output is synchronous. No application process sends logs directly to Loki.

Local Logback files roll daily or at 10 MB, retaining seven days and at most 100 MB
of archives, with cleanup on startup. The active file is additional; cleanup and
compression are asynchronous and limits are not instantaneous disk quotas. Standard
`logging.logback.rollingpolicy.*` properties override these defaults. Use one
application instance per log path. Rotation is rename-based; Alloy follows the
active file rather than separately rereading compressed archives.

Docker `json-file` logs retain three 10 MB files per simulation/observability
container. Optional simulation files use a separate named volume and the same
Logback policy. They are never a second ingestion path. Loki independently retains
seven days using its compactor and persistent filesystem storage. Deletion is
asynchronous (including a two-hour delete delay); age retention is not a capacity
quota. Check Docker's allocated disk usage and available host storage during load.

Alloy positions persist across ordinary restarts and source switches. Collection is
best effort, not an exactly-once durable audit transport. Starting late can recover
some retained data, but offline rotation, file truncation, container removal, old
timestamps, checkpoint timing and outages can cause gaps or replay. Start Alloy
before planned simulations. Docker container removal can erase its remaining logs;
logs already ingested into Loki have their own lifetime. Stopping observability
does not affect the API.

## Verification

Run `mvn test` with Java 21 and Docker for PostgreSQL integration tests, or
`mvn "-Dtest=U_*" test` for isolated tests. Request tests cover IDs, MDC, security,
validation, errors, health exclusions and event privacy. A child-JVM configuration
test loads the real application YAML and checks native JSON output, size rotation
and file disabling without changing the parent test logging system.

Use the operational smoke checks for collection, restart persistence and source
switching. Compare identical Locust user counts, spawn rates, durations and seeded
datasets before changing logging to asynchronous delivery. Record request rate,
latency percentiles, errors, output bytes and disk growth. Keep baseline and enabled
runs separate from developer data; do not reset an existing simulation to obtain
benchmark numbers. Metrics monitoring and distributed tracing are separate work.
