# Simulation and application guide

This is a quick map for changing the simulation and the Service Advertiser API
without having to rediscover the whole repository. The simulation is a client
of the real application: Python creates traffic through HTTP, while Spring Boot
handles validation, authorization, business rules and database writes.

For the copy-paste setup and reset instructions, see the
[simulation operator README](../../simulation/README.md). This guide focuses on
how the pieces fit together and where to make a change.

## The short mental model

```text
Locust visitor or bootstrap script
        | HTTP + optional bearer token
        v
Spring controller -> service -> repository -> PostgreSQL
        | response JSON
        v
Simulator updates its local session and manifest state
```

The `simulation` Compose project runs its own API container and PostgreSQL
volume. The simulator does not connect to PostgreSQL directly. That matters:
simulation writes exercise the same API rules as a real client, and its data is
separate from your local development database.

## Where things live

| Path | Responsibility | Start here when… |
| --- | --- | --- |
| `simulation/compose.yaml` | Isolated API, database, bootstrap and Locust services | You need to understand containers, ports or persistence |
| `Dockerfile` | Builds the Java API image | A Java/API change needs to appear in the simulation container |
| `simulation/common.py` | HTTP transport, response checks, login/refresh, catalog loading, manifest, locks, account leases and API-backed mutations | Shared simulator behavior or state handling is wrong |
| `simulation/bootstrap.py` | Creates the initial synthetic accounts, businesses and items | Initial seed behavior needs to change |
| `simulation/locustfile.py` | Virtual user lifecycle, weighted reads/writes and request labels | Continuous workload behavior needs to change |
| `simulation/check.py` | Deterministic checks with a fake transport, plus an optional live API lifecycle | You changed shared simulator logic and need its smallest existing check |
| `simulation/data/` | Synthetic people and business/item templates | Seed content or category/location mappings need to change |
| `simulation/docs/` | Detailed user flows and data invariants | You need the fuller rules behind a behavior |
| `src/main/java/...` | Spring controllers, services, repositories, entities and DTOs | API behavior or business rules need to change |
| `src/main/resources/db/migration/` | Flyway schema/data migrations | Database schema or seeded catalog data needs to change |

## How the simulator works

### One-time bootstrap

`bootstrap.py` obtains a process lock, checks that the database and manifest are
in a supported fresh state, then loads categories and locations from public API
endpoints. It validates the curated templates before writing. It creates 100
synthetic accounts; 20 become providers, each with a business and three or four
items. All writes go through API endpoints. If bootstrap is interrupted, it
stops safely; do not rerun it over partially created data. Use the reset steps
in the operator README only when you intend to erase the simulation database.

### Continuous activity

Locust starts visitor, customer and provider virtual users. They share the same
`Visitor` behavior with different starting profiles. A visitor browses and
searches public businesses/items. Signed-in users can register businesses and
manage their own items. Reads are much more common than writes. Account and
business caps keep this workload finite.

Each virtual user owns its API session, tokens and current business/item IDs.
Accounts are leased so two simulator users do not mutate the same account at
once. `common.py` writes important identity and pending-operation state to
`simulation/runtime/manifest.json`, paired with the Compose database volume.
Keep those together. Never invent an ID locally or blindly retry an uncertain
write: reconcile by reading ownership/items through the API.

### Session and write safety

Registration does not log in automatically. Login returns an access token and
a one-use refresh token. The simulator refreshes before protected calls when
needed and replaces both tokens after a successful refresh. A lost/used refresh
token means login again.

Before a mutation, the simulator persists a pending intent. It changes confirmed
local state only after a successful response. If the result is uncertain, it
reads server state to reconcile instead of resending the write. Protected seed
businesses and their minimum active-item count are safeguards; respect these
when changing write tasks or data limits.

## How an API request moves through the app

For an API behavior change, trace the request in this order:

1. **Controller** (`*Controller.java`) maps the HTTP method/path, gets the
   authenticated user ID when needed, and returns the response/status.
2. **Request/response DTO** (`dto/`) defines the JSON shape and usually request
   validation. Keep API input rules at this boundary.
3. **Service** (`*Service.java`) owns the workflow, authorization/domain checks
   and transaction boundary.
4. **Repository** (`*Repository.java`) performs persistence queries.
5. **Entity** (`Business`, `Item`, `User`, etc.) represents persisted state;
   schema changes belong in a new Flyway migration.

For example, item creation enters `ItemController`, validates
`CreateItemRequest`, calls `ItemService.create`, checks provider ownership and
item capacity, then saves through `ItemRepository`. A simulator request does
not skip any of those steps.

Useful app areas:

- Auth: `auth/` and `config/SecurityConfig.java`.
- Business management/moderation/discovery: `business/`.
- Items and discovery: `item/`.
- Catalogs: `category/` and `location/`.
- Shared paging/search/errors: `common/`.
- App defaults and environment overrides: `src/main/resources/application.yaml`.
- API contract while running: `http://localhost:8081/swagger-ui/index.html` in
  simulation mode.

## Fast local workflow

Run these from the repository root in PowerShell. Docker Desktop with Linux
containers is required.

### Run the API without the simulation

For ordinary Java/API work, the root Compose file starts only the development
database. Create the local `.env` and JWT secret as described in the root
[README](../../README.md#authentication-configuration), then run:

```powershell
docker compose up -d postgres
./mvnw.cmd spring-boot:run
```

Open [local Swagger UI](http://localhost:8080/swagger-ui/index.html). Use this
mode when iterating on the API alone; use the isolated simulation mode below
when you want seeded data and Locust traffic.

### First run

```powershell
docker compose -f simulation/compose.yaml up -d --build --wait api
docker compose -f simulation/compose.yaml run --rm bootstrap
docker compose -f simulation/compose.yaml up -d locust
```

Open [Locust](http://localhost:8089), start with 10 users and a spawn rate of 2,
and keep its host as `http://api:8080`. The host name is for containers on the
Compose network. The API is available on your computer at
[Swagger UI](http://localhost:8081/swagger-ui/index.html).

### After code changes

- **Java/API change:** rebuild/recreate the API with
  `docker compose -f simulation/compose.yaml up -d --build --wait api`.
- **Python or synthetic data change:** restart the simulator container, for
  example `docker compose -f simulation/compose.yaml restart locust`. Python
  files are mounted into the container.
- **Schema or bootstrap behavior change:** use a fresh simulation database when
  appropriate; Flyway migrations run at API startup, while bootstrap only
  supports a fresh database. Follow the reset section in the operator README
  before erasing any data.

To run the existing deterministic simulator checks in Docker:

```powershell
docker compose -f simulation/compose.yaml run --rm --no-deps --entrypoint python bootstrap check.py
```

For the real HTTP lifecycle check, finish bootstrap and stop Locust first, then
run `docker compose -f simulation/compose.yaml run --rm --entrypoint python bootstrap check.py --live`.
The live check creates one customer and removes its temporary businesses; it
retains that customer. Do not run it while Locust is active.

To stop the simulation and keep its database, use:

```powershell
docker compose -f simulation/compose.yaml --profile activity --profile tools down
```

## A good change loop

1. Reproduce the behavior through Swagger or Locust and identify whether it is
   simulator behavior or application behavior.
2. Read the shared helper/caller flow before editing. For API issues, follow
   controller → DTO → service → repository/entity. For simulator issues, find
   the behavior in `locustfile.py` and shared HTTP/state logic in `common.py`.
3. Change the smallest responsible layer. Keep simulator data compliant with
   the public catalogs and API validation; do not add database shortcuts.
4. Run the simulator check above for changes to shared simulator behavior, or
   the relevant application check for Java changes. Check the actual endpoint
   or Locust statistics after the change.
5. If results look stale, confirm which container/image you changed and
   rebuild the API when Java code changed. Stop the stack when done; database
   contents persist until an intentional reset.

## Common gotchas

- Locust's target is `http://api:8080` inside Compose. Do not change it to
  `localhost`; inside the Locust container, localhost means Locust itself.
- Bootstrap is a one-time action for a fresh database. Existing or partially
  initialized data is a stop condition, not a reason to retry it.
- `simulation/runtime/manifest.json` and the simulation volume are a pair.
  Removing only one can make simulator state inconsistent.
- The default simulation load is bounded, and write task eligibility depends
  on current account/business/item state. A task doing nothing can be correct
  when its preconditions are not met.
- A public detail lookup can return 404 if the target was removed after search.
  The simulator allows that race for those reads; other unexpected responses
  should be investigated.
- Synthetic credentials and the simulation JWT key are for this local stack
  only.
