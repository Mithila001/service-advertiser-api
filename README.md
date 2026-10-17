# Service Advertiser API

## Authentication configuration

Requires Java 21, Maven, and PostgreSQL. Flyway applies migrations on startup.
The application automatically loads `.env` from its working directory using
Spring Boot's built-in properties importer. For local development, use the project
root as the working directory, including in your IDE.

Set up `.env` once with a stable Base64-encoded secret of at least 32 random bytes:

```properties
JWT_SECRET_BASE64=paste-your-generated-key-here
```

To generate a key in PowerShell:

```powershell
$jwtBytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($jwtBytes)
[Convert]::ToBase64String($jwtBytes)
```

Generate the key once and save the output as `JWT_SECRET_BASE64` in `.env`.
Use plain `KEY=value` lines without quotes or `export`; this file is parsed as
Java properties. `.env` is ignored by Git and must stay private.
The saved key is reused on every startup; no terminal environment setup is needed.
All application instances must use the same key. Changing it invalidates existing
access JWTs. Missing or invalid secrets prevent startup even though the file
import itself is optional.

Production deployments can supply environment variables through their secret
manager instead of providing `.env`. Environment variables override values from
the file. Use HTTPS in production.

| Configuration key (`.env` or environment variable) | Default |
| --- | --- |
| `JWT_SECRET_BASE64` | Required; no default |
| `JWT_ISSUER` | `service-advertiser` |
| `JWT_AUDIENCE` | `service-advertiser-api` |
| `JWT_ACCESS_LIFETIME` | `15m` |
| `JWT_REFRESH_LIFETIME` | `7d` |

Database settings can also be supplied in `.env` through `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`.
Start PostgreSQL, then run `mvn spring-boot:run` from the project root.

## Login and token lifecycle

`POST /api/v1/auth/register` retains its existing registration contract and does
not automatically log in. The following JSON endpoints require no access JWT;
omit `Authorization` when calling them.

```http
POST /api/v1/auth/login
Content-Type: application/json

{"email":"user@example.com","password":"your-password"}
```

Login returns `200` with `Cache-Control: no-store`:

```json
{
  "accessToken": "<signed HS256 JWT>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "refreshToken": "<opaque refresh token>",
  "refreshTokenExpiresAt": "2026-10-22T10:00:00Z"
}
```

Send `Authorization: Bearer <accessToken>` on protected API requests. The JWT
contains the user ID in `sub`, plus `role`, `iss`, `aud`, `iat`, and `exp`.
Spring Security exposes the JWT as the principal and the role as `ROLE_CUSTOMER`,
`ROLE_PROVIDER`, or `ROLE_ADMIN`. No database lookup occurs during bearer validation.

```http
POST /api/v1/auth/refresh
Content-Type: application/json

{"refreshToken":"<current refresh token>"}
```

Refresh returns a new access JWT and a new refresh token with the original session
expiry using the response fields shown above. Replace both stored tokens after each
successful refresh. A session expires seven
days after login by default; refreshing does not extend that deadline. Once the
refresh token expires, log in again. Only SHA-256 hashes of refresh tokens are
persisted. Every login creates a separate session.

Each refresh token can be used once. A successful refresh marks it as consumed and
stores the replacement token's hash in the same session. Reusing a consumed token
returns `401` and revokes the entire session's refresh tokens, including its newest
replacement. Other login sessions remain active. Serialize refresh requests on the
client: concurrent requests with the same token trigger reuse detection and require
logging in again. If a refresh response is lost, retrying its consumed token also
requires logging in again.

```http
POST /api/v1/auth/logout
Content-Type: application/json

{"refreshToken":"<refresh token from this session>"}
```

Logout returns `204` even for unknown or previously revoked well-formed tokens.
It revokes that session's refresh tokens and leaves other devices logged in.
Delete the tokens from client storage after logout. Existing access JWTs remain
valid until expiry, at most 15 minutes by default. Account suspension and role
changes also take effect for bearer access when tokens expire; login and refresh
always check the current account state and issue the current role.

Invalid request data returns `400`; invalid credentials or refresh tokens return
`401`; insufficient permissions return `403`. Errors use
`{"status":401,"error":"Unauthorized","message":"...","fieldErrors":{}}`.

Expired refresh records are deleted daily at 03:00 UTC. Auth responses and application
logs must not be used to persist tokens or credentials. Browser cookies and a
cross-origin frontend policy are outside this implementation; no authentication
cookies or permissive CORS rules are configured.

## Tests

Run `mvn test`. Integration tests require Docker and create an isolated PostgreSQL
18 container. Tests supply a test-only signing key and do not require a production
JWT secret or your development database. Coverage includes JWT validation, API
errors, registration, refresh token rotation and reuse detection, signing failures,
concurrent refresh, independent sessions, legacy-token rejection, and expiry cleanup.
