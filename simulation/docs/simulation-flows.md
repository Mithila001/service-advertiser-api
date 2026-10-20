# Simulation flows

## Initialization

Bootstrap locks the runtime, validates all curated templates against public catalogs,
checks for existing simulation identities, then records initialization intent.
It creates 100 accounts through registration. The first 20 log in, register one
business each, refresh their role-bearing JWTs, and create 3–4 items. Each business
and its items are read back before initialization completes. Any interrupted
bootstrap requires explicit reset.

Continuous activity acquires the same process lock. Before spawning users it loads
catalogs, logs into stored accounts and reconciles business/item ownership through
HTTP. Missing protected data or unrecoverable identity state stops the run.

## Users and state

Locust starts Visitor, Customer and Provider classes with weights 60:25:15. These
are initial profile weights rather than fixed role quotas. All use 2–8 second waits.
Customer and Provider users exclusively lease a stored account, preferring their
initial role. If that pool is exhausted they use another available account and
follow its actual ownership. With no account available, they continue anonymous
activity. Accounts are returned when users stop.

The current API session, access/refresh tokens, owned business and item list belong
to one virtual user. There is no sharing of sessions or concurrent modification of
a business by simulator users.

| Current state | Eligible activity | Next state |
| --- | --- | --- |
| Anonymous | Browse/search/details | Anonymous |
| Anonymous | Register, then log in; reuse a lease at the account cap | Customer or leased account's actual state |
| Customer | Browse/search/details | Customer |
| Customer | Register a business below the global cap, then refresh | Provider |
| Provider | Browse and manage own business/items | Provider |
| Provider with an unprotected business | Delete own business and refresh | Customer |

## Action selection

Each user chooses a read task with weight 95 and a write task with weight 5.
Read tasks search businesses or items, optionally filtering by search text, real
catalog categories or cities. Item searches sometimes filter type and price.
They choose detail targets from returned records and also browse public business
item lists. Empty results end that action successfully.

For anonymous users a write task attempts registration/login. Customers attempt
business registration. A provider's write task selects:

| Choice | Probability within provider write tasks | Conditions |
| --- | --- | --- |
| Create item | 30% | Fewer than 10 owned items, including inactive |
| Patch item | 30% | Owned item exists; protected active-item floor respected |
| Update business | 20% | Owned business exists |
| Delete item | 19% | Eligible owned item exists |
| Delete business | 1% | Business is not protected bootstrap data |

Ineligible actions end without sending invalid HTTP requests. PATCH varies price,
description or ACTIVE/INACTIVE status. Business PUT copies all editable fields from
the current business before varying its description, preserving optional fields
and categories. Deleting a business clears item state because the API cascades
deletion. The user continues customer activity and can register a later business.

## Sessions and failure handling

Registration never authenticates implicitly. Login issues both tokens. Before
protected requests, access expiry triggers refresh; successful refresh replaces
both tokens. Refresh tokens are discarded before sending and never retried. A lost
refresh response or a 401 triggers a new login; fixed refresh expiry also logs in
again. Business promotion/demotion explicitly refreshes the session.

Every mutating operation records pending intent before HTTP. Confirmed local state
changes only after success. A failed/uncertain operation remains visible in request
statistics; the user rereads ownership/items instead of blindly resubmitting writes.
An uncertain registration is recovered by logging in with its reserved identity.
If reconciliation fails, the run stops rather than inventing state or another account.

Discovery/detail races can legitimately yield 404 for a removed business or an
inactive/deleted item. Those detail reads accept 404 and discard the stale target.
Other unexpected statuses and malformed response shapes are failures. Diagnostics
contain fixed endpoint labels and status information, never server response bodies,
passwords or tokens.

## Bounds and execution

At most 100 virtual users, 500 stored/reserved accounts and 100 live/reserved
businesses are supported. Account and business capacity reservations are serialized.
Bootstrap businesses are protected from deletion and cannot lose their last two
ACTIVE items. Distributed runners and foreign target hosts are rejected. A process
lock also prevents an idle dashboard and a second simulator from sharing state.
