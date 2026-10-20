# Synthetic data rules

## Curated inputs

`data/people.json` supplies personal names. `data/businesses.json` contains 20
business clusters, each with a display name, lowercase unique-name base,
description, address, business category names, suitable city/district pairs and
four item templates. Item templates contain a title, description, PRODUCT/SERVICE
type, relevant item categories and a minimum/maximum LKR price.

Clusters cover IT, repairs, education, automotive, cleaning, retail, food,
agriculture, beauty, construction, clothing, furniture, gardening, hospitality,
bookkeeping, photography, pets, printing, fitness and logistics. Both products
and services appear in the resulting dataset.

Public catalog APIs are authoritative. Business/item category names are mapped to
IDs and every city page is retrieved. Locations use both city and district names
to avoid ambiguous city-only matches. Missing mappings or invalid templates fail
before bootstrap writes. No generated database IDs are hardcoded.

## Identity and relationships

Account emails follow `sim_user_0001@example.test`; passwords are deliberately
predictable simulation-only values derived from the same counter. This permits
detection of known accounts after accidental manifest loss. Credentials are not
appropriate for real accounts and are never printed.

Business unique names follow `sim_<cluster-base>_<counter>`, within the API's
50-character limit. The persisted business counter continues across deletion,
preventing accidental name reuse. Display names can repeat as allowed by the API.
Each user owns at most one business. Each business's templates stay within its
curated industry; category IDs are obtained from catalogs, never inferred from
business IDs or item IDs. Locations are selected only from that cluster's list.

Contacts use synthetic account email addresses and a simulation phone placeholder.
Street addresses are synthetic. Item prices are whole LKR values selected within
template ranges; valid real-workflow checks also exercise fractional prices.
Prices are nonnegative; titles and generated names respect API length limits.
Business categories stay within three and item categories within two.

## Population and persistence

Bootstrap creates exactly 100 accounts. The first 20 become providers, one per
cluster. Each receives 3–4 distinct templates, yielding 60–80 initial items. New
items start ACTIVE through the application's normal default. Remaining accounts
stay customers until continuous activity promotes them.

The runtime manifest records a version, initialization flag, identity counters and
accounts with credentials, returned IDs, cluster index, protected flag, current
business reference and pending operation. It does not store access/refresh tokens.
The manifest is atomically replaced after progress and paired with the persistent
simulation PostgreSQL volume. An exclusive process lock guards the pair's simulator
writer; in-process locks serialize leases and capacity reservations.

An identity is reserved and saved before registration. Failed/uncertain registration
therefore cannot lose a committed account or silently allocate another identity.
Pending registrations count toward the 500-account limit. After uncertain mutations,
HTTP ownership/item reads reconcile the state. No mutation is blindly retried.
If a response containing a new account ID is lost, its reserved identity remains
usable through login; the manifest does not fabricate an ID from its counter.

## Lifecycle limits

| Record | Limit or lifecycle |
| --- | --- |
| Accounts | 500 across runs, including reserved identities; retained until reset |
| Businesses | 100 live/reserved; one per account |
| Items | 10 per business, ACTIVE and INACTIVE combined |
| Bootstrap businesses | Cannot be deleted by the simulator |
| Bootstrap ACTIVE items | At least two per protected business |
| Other businesses/items | Can be created, updated and deleted repeatedly |

Concurrent users cannot lease the same account. Mutations must satisfy current
ownership and item counts. Item deletion frees a slot. Business deletion cascades
its items and returns its owner to CUSTOMER; the user account and session survive.
Refresh obtains the current role before continuing.

Stopping retains all records and local credentials. Reset requires stopping the
simulation Compose project, removing only its volume, and removing its manifest;
see the [README](../README.md). Deleting just the manifest or just the database is
unsupported and must not trigger automatic population over unknown state.
