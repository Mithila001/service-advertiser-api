# Test naming rule

Prefix each test class with:

- `U_` for an isolated unit or MVC slice test. Collaborators outside the slice are mocked; it does not exercise the full application with the real database.
- `I_` for an integration test that loads the full application or connects multiple layers to infrastructure such as PostgreSQL.

Keep the existing `Test` or `Tests` suffix so Maven continues to discover the test class. For example: `U_RegistrationServiceTests` and `I_ItemIntegrationTests`.

Test support classes such as `PostgresTestSupport` are not test classes and do not get a prefix.

U_ProtectionTests checks account/IP isolation, normalization, disabled behavior, configuration rejection, and UTF-8 password boundaries. Full runtime database, chunked HTTP, and shutdown verification requires Docker and an available Maven dependency cache.

`I_DatabaseProtectionTests` verifies runtime statement settings, cancellation,
rollback recovery, direct/JPA pool acquisition failure, and Flyway isolation.
`I_GracefulShutdownTests` uses real HTTP for chunked byte boundaries and request
draining. Its delay endpoint is test-only. Both reuse isolated PostgreSQL support.
Run `mvn "-Dtest=U_*" test` for isolated checks and `mvn test` with Docker running.

Container acceptance: start a fresh isolated simulation with the documented
bootstrap command, run `python check.py --live` through the bootstrap container,
and compare headless Locust runs at 10 and 25 users with identical durations and
spawn rates. Confirm no 429 responses. Stop the API while traffic is active and
inspect graceful-shutdown logs and container exit status. Keep the paired manifest
and volume; do not reset an existing simulation to obtain these results.
