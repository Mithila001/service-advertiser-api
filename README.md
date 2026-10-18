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
changes also take effect for general bearer access when tokens expire; business endpoints
check current account state and ownership in the database on every protected request. Login and refresh
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

## Business management

| Method | Endpoint | Behavior |
| --- | --- | --- |
| POST | `/api/v1/businesses` | Register a business; returns 201 |
| GET | `/api/v1/businesses/me` | Retrieve your business |
| PUT | `/api/v1/businesses/me` | Replace editable business information |
| DELETE | `/api/v1/businesses/me` | Permanently delete your business; returns 204 |
| GET | `/api/v1/businesses/{uniqueName}` | Public profile; no authentication required |

Registration example (requires a customer access JWT):

```json
{
  "uniqueName": "colombo_services",
  "information": {
    "name": "Colombo Services",
    "city": "Colombo",
    "description": "Local services",
    "phone": "+94112345678"
  }
}
```

`uniqueName` accepts 3?50 lowercase ASCII letters, numbers, or underscores and
cannot be changed. Display names may repeat. `information` also accepts `email`,
`websiteUrl`, `addressLine1`, `addressLine2`, `district`, `province`, `postalCode`,
`latitude`, `longitude`, and `logoUrl` (an existing URL field; no upload endpoint).
Coordinates are optional and limited to latitude ?90 and longitude ?180, with
up to seven fractional digits. Responses contain `id`, `uniqueName`, `information`,
`status`, `createdAt`, and `updatedAt`.

PUT takes the `information` object directly; name and city are required and omitted
optional fields are cleared. Unique name, status, business ID, and timestamps are not editable.
`categoryIds` can select up to three business categories and is cleared when omitted.
Registration creates an ACTIVE business and switches CUSTOMER to PROVIDER atomically.
Deletion clears ownership and switches back to CUSTOMER atomically, preserving the
account and authentication sessions. Refresh or log in to obtain the current JWT role;
existing JWTs can still access your business based on current database ownership.
Only active accounts can register or manage businesses. Missing own/public profiles
return 404; duplicate unique names or a second registration return 409; disallowed
account states/registration roles return 403.

Migration V3 preserves existing businesses, assigns `legacy_business_<id>` unique
names, and fills missing/blank cities with `Unknown`. Applied migrations are unchanged.
Migration V4 adds items with a business foreign key using `ON DELETE CASCADE`.

## Categories

Business and item categories are predefined in the database. The catalog endpoints
are public and return categories ordered by name:

| Method | Endpoint | Behavior |
| --- | --- | --- |
| GET | `/api/v1/categories/business` | List business categories |
| GET | `/api/v1/categories/item` | List item categories |

Each entry contains `id`, `name`, and `description`. Supply category IDs when
registering a business inside `information`, or when creating an item:

```json
{"uniqueName":"colombo_services","information":{"name":"Colombo Services","city":"Colombo","categoryIds":[20,23]}}
```

```json
{"title":"Home cleaning","type":"SERVICE","categoryIds":[13]}
```

Categories are optional. A business may have up to three business categories;
an item may have up to two item categories. Duplicate, nonpositive, and unknown
IDs are rejected. Business PUT replaces its categories and omission clears them.
Item PATCH preserves categories when omitted, replaces them when supplied, and
clears them with `"categoryIds":[]`.

`app.categories.max-per-business` (`BUSINESS_MAX_CATEGORIES`) defaults to 3, and
`app.categories.max-per-item` (`ITEM_MAX_CATEGORIES`) defaults to 2. Both limits
must be positive. The category catalogs and their seed data are installed by
Flyway migration V5; existing businesses and items remain uncategorized.

## Item management

| Method | Endpoint | Behavior |
| --- | --- | --- |
| POST | `/api/v1/businesses/me/items` | Create an ACTIVE item; returns 201 |
| GET | `/api/v1/businesses/me/items` | Own items, including inactive, ordered by ID |
| PATCH | `/api/v1/items/{id}` | Partially update your item |
| DELETE | `/api/v1/items/{id}` | Permanently delete your item; returns 204 |
| GET | `/api/v1/items/{id}` | Public active item detail |
| GET | `/api/v1/businesses/{uniqueName}/items?page=0&size=10` | Public active items |

Create example:

```json
{"title":"Home cleaning","type":"SERVICE","description":"Two hours","price":2500.00,"categoryIds":[13]}
```

Title (up to 150 characters) and type (`PRODUCT` or `SERVICE`) are required.
Description and price are optional. Price uses a nonnegative decimal with up to
17 integer digits and two fractional digits. Null price means unspecified; zero
means free. Client-supplied business IDs do not control ownership. New items
always start ACTIVE.

PATCH accepts `title`, `description`, `type`, `price`, `status` (`ACTIVE` or
`INACTIVE`), and `categoryIds`. Omitted fields are preserved. Explicit null clears
description or price; null title, type, status, or categoryIds is invalid. Example:

```json
{"status":"INACTIVE","price":null,"categoryIds":[]}
```

Protected item endpoints check the current account, PROVIDER role, and business
ownership in the database. Creation, updates, and deletion require an ACTIVE
business. Suspended or disabled businesses may still list their own items.
Other businesses' item IDs return 404 for updates/deletion. Public detail returns
404 unless both item and business are ACTIVE.

Public lists return `content`, `page`, `size`, `totalElements`, and `totalPages`.
Page numbering starts at zero; size must be 1?100. Results are ordered by item ID.
Unknown, suspended, or disabled businesses produce an empty public list.

`app.items.max-per-business` (environment variable `ITEMS_MAX_PER_BUSINESS`)
defaults to 10 and must be positive. Both ACTIVE and INACTIVE items count. Requests
at the limit return 409. The owner-row database lock serializes creation and
business deletion; deleting an item frees a slot. Deleting a business permanently
removes all its items while retaining the user and authentication sessions.
