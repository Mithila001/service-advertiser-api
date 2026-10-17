# Test naming rule

Prefix each test class with:

- `U_` for an isolated unit or MVC slice test. Collaborators outside the slice are mocked; it does not exercise the full application with the real database.
- `I_` for an integration test that loads the full application or connects multiple layers to infrastructure such as PostgreSQL.

Keep the existing `Test` or `Tests` suffix so Maven continues to discover the test class. For example: `U_RegistrationServiceTests` and `I_ItemIntegrationTests`.

Test support classes such as `PostgresTestSupport` are not test classes and do not get a prefix.
