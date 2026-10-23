# Service Advertiser API

## API user simulation

The [Locust simulation module](simulation/README.md) provides an isolated Docker
environment, realistic API-driven population, and continuous activity for up to
100 virtual users.

## Interactive API documentation

Run the application, then open [Swagger UI](http://localhost:8080/swagger-ui/index.html).
The generated OpenAPI specification is available as [JSON](http://localhost:8080/v3/api-docs)
or [YAML](http://localhost:8080/v3/api-docs.yaml). Use your application's host and port
if different. These documentation endpoints are public.

To try protected operations:

1. Register an account, then call `POST /api/v1/auth/login` in Swagger UI.
2. Copy `accessToken` from the response.
3. Click **Authorize**, paste only the token (without `Bearer`), and authorize.
4. Use **Try it out** on an operation with a lock icon. Admin operations require an ADMIN account.

Use real catalog IDs from the Categories and Locations endpoints when adapting
request examples. After refreshing or logging in again, replace the token in
**Authorize**. Refresh and logout take the opaque refresh token in their JSON body.
**Try it out** sends real requests, including writes and deletions.

Endpoint descriptions and request examples live alongside the controllers and DTOs;
shared API information, bearer authentication, and error responses live in `OpenApiConfig`.

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
| `PAGINATION_DEFAULT_SIZE` | `10` |
| `PAGINATION_MAX_SIZE` | `100` |
| `PAGINATION_MAX_OFFSET` | `10000` |

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
    "cityId": 1,
    "description": "Local services",
    "phone": "+94112345678"
  }
}
```

`uniqueName` accepts 3 to 50 lowercase ASCII letters, numbers, or underscores and
cannot be changed. Display names may repeat. `information` requires `cityId` from
the public location catalog and also accepts `email`, `websiteUrl`, `addressLine1`,
`addressLine2`, `postalCode`,
`latitude`, `longitude`, and `logoUrl` (an existing URL field; no upload endpoint).
Coordinates are optional and limited to latitude from -90 to 90 and longitude from -180 to 180, with
up to seven fractional digits. Responses contain `id`, `uniqueName`, `information`,
`status`, `createdAt`, and `updatedAt`.

Responses include the canonical `city`, `district`, and `province` names plus
`cityId`, `districtId`, and `provinceId`. PUT takes the information object directly;
name and `cityId` are required and omitted optional fields are cleared. Location
names are derived from the selected city. Unique name, status, business ID, and timestamps are not editable.
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
Migration V6 seeds all nine provinces, 25 districts, and five selected localities
per district. This starter city catalog is intentionally curated rather than
exhaustive; locality names are a project-curated starter list. Its administrative
hierarchy follows the [Department of Census and Statistics 2024 preliminary report](https://www.statistics.gov.lk/Resource/en/Population/CPH_2024/Preliminary_Report.pdf).
Existing business location text is retained; only unambiguous matches
are linked. Unmatched legacy businesses remain readable and become searchable by
catalog location after their owner updates the business with a city ID.

## Locations

The public catalog supports a hierarchical city picker:

| Method | Endpoint | Behavior |
| --- | --- | --- |
| GET | `/api/v1/locations/provinces` | List provinces alphabetically |
| GET | `/api/v1/locations/districts?provinceId=1` | List districts, optionally within a province |
| GET | `/api/v1/locations/cities?q=colombo&districtId=1` | Search and page through cities |

City results include their district and province IDs and names. City search is a
case-insensitive substring match. The catalog endpoints are public.

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
{"uniqueName":"colombo_services","information":{"name":"Colombo Services","cityId":1,"categoryIds":[20,23]}}
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
Page numbering starts at zero. The client can choose a size up to the configured
maximum. Results are ordered by item ID.
Unknown, suspended, or disabled businesses produce an empty public list.

`app.items.max-per-business` (environment variable `ITEMS_MAX_PER_BUSINESS`)
defaults to 10 and must be positive. Both ACTIVE and INACTIVE items count. Requests
at the limit return 409. The owner-row database lock serializes creation and
business deletion; deleting an item frees a slot. Deleting a business permanently
removes all its items while retaining the user and authentication sessions.

## Public discovery

Business search and item search are separate public endpoints. Search matches the
business name or item title, respectively, using trimmed, case-insensitive literal
substring matching. Search text is limited to 150 characters. Category IDs use
ANY matching; other filters combine with AND.

| Method | Endpoint | Filters and sorting |
| --- | --- | --- |
| GET | `/api/v1/businesses?q=repair&categoryIds=1,2&cityId=1&sort=name,asc` | Active businesses; sort by name |
| GET | `/api/v1/items?q=cleaning&type=SERVICE&categoryIds=13,25&provinceId=1` | Active items from active businesses; filter by type, location, and price |

Both return `content`, `page`, `size`, `totalElements`, and `totalPages`. Use
zero-based `page` and `size`; defaults are 0 and 10, maximum size is 100, and
offsets over 10,000 are rejected. Configure these limits with
`PAGINATION_DEFAULT_SIZE`, `PAGINATION_MAX_SIZE`, and `PAGINATION_MAX_OFFSET`.
Name sorting is case-insensitive and uses ID as a stable tie-breaker. Item sorting
also accepts `sort=price,asc` or `sort=price,desc`; unpriced items sort below LKR 0
ascending and after priced items descending.

Item filters also accept `type=PRODUCT|SERVICE`, `minPrice`, and `maxPrice`. Prices
are in Sri Lankan rupees (LKR), with at most two fractional digits. Negative values
and ranges where minimum exceeds maximum return `400`. Unpriced items remain
included when no minimum is supplied, including max-only searches. Supplying any
minimum, including zero, excludes unpriced items. Zero is a known free price.

Examples:

```http
GET /api/v1/businesses?q=repair&categoryIds=1,2&provinceId=1&page=0&size=10&sort=name,asc
GET /api/v1/items?q=cleaning&cityId=1&type=SERVICE&minPrice=500&maxPrice=5000&sort=price,asc
```

Unsupported sort fields, unknown or duplicate category IDs, invalid location IDs,
and contradictory city/district/province filters return `400`. Businesses and
items with legacy locations but no catalog city remain in unfiltered results and
are omitted when a catalog location filter is used.
## Logging

Local development writes readable console output and rotated JSON files under
`logs/`. See [logging conventions](docs/engineering/logging.md) and
[optional Grafana/Loki operations](observability/README.md).

## API protection and reliability

Rate limiting is enabled by default for POST login, registration, and refresh.
Login permits 120 requests per IP per minute and 5 per normalized email/IP per
minute. Registration permits 20 per IP per ten minutes; refresh permits 120 per
IP per minute. Buckets start full and refill continuously. HTTP 429 includes
Retry-After in seconds; wait before trying again. A rejected refresh has not
consumed the refresh token. Never automatically retry an uncertain refresh outcome.

Limits use the direct remote address and ignore forwarded headers. Configure a
trusted proxy separately before deploying behind one. Shared-IP users have separate
account budgets but share the IP budget. Account/IP limits do not stop distributed
guessing, and an attacker sharing an IP can affect users there. State is local to
one JVM and resets on restart. At 10,000 live bucket entries, new identities receive
503 with Retry-After: 60; active entries are not evicted. Idle entries expire after
at least 15 minutes or a full refill period, whichever is longer.

JSON bodies under /api/v1 are limited to 65,536 bytes, including chunked bodies and
trailing content. Oversized bodies return 413 before mutations. Accepted bodies
are buffered within that bound. Business/item descriptions accept 10,000 characters;
registration passwords retain the minimum length and accept at most 72 UTF-8 bytes.
Field violations return 400. Existing login password matching remains unchanged.

Hikari waits up to 3 seconds for a connection and validates within 1 second.
Runtime PostgreSQL statements time out after 5 seconds. Flyway uses a separate
connection without the runtime initialization SQL. Recognized database timeouts
return a generic 503; writes are not retried. These are not universal HTTP deadlines.
Graceful shutdown allows requests 30 seconds per shutdown phase; simulation Docker
allows 45 seconds before force termination.

See .env.example for RATE_LIMIT_* capacity/period settings, RATE_LIMIT_ENABLED,
JSON_MAX_REQUEST_SIZE, DB_CONNECTION_TIMEOUT_MS, DB_VALIDATION_TIMEOUT_MS,
DB_STATEMENT_TIMEOUT_MS, and SHUTDOWN_GRACE_PERIOD. Keep validation timeout below
connection timeout. Disabling rate limits does not disable body or database limits.
