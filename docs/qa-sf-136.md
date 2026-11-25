# Delivery and return batch reads — SF-136

The delivery list previously executed one parent SELECT plus two child SELECTs per
order. The two return lists each executed one parent SELECT plus one per parent.
Batching now uses PostgreSQL bigint arrays, separately groups items and history,
and retains parent and child ordering. Empty parent lists execute no child queries.
The update/locking paths retain their existing single-parent queries.

`BatchReadTests` uses a disposable PostgreSQL 17 container and a JDBC counting
proxy around the real repositories. Setup is excluded from counts. For one and
20 parent records it checks 3 SELECTs for delivery and 2 for each return list;
empty lists use 1 each. On an isolated checkout of the original code, the
20-parent cases failed the new budgets: delivery used 41 SELECTs instead of 3;
returns and returnable orders each used 21 instead of 2. The baseline audit
changed only earlier expectations in its temporary test copy to reach each case. Tests cover duplicate product snapshots,
separate histories, empty children and return status aggregation. The same count is also checked for each HTTP endpoint through standalone MockMvc
with the same PostgreSQL-backed services. This standalone check excludes security;
the existing controller and concurrency suites separately cover authorization and
inventory transitions.

This change does not add pagination: response size and memory use still grow
with the number of records. It does not claim a latency benchmark. Production
data and schema are unchanged.

Verification on 2026-11-25:

- `bash backend/mvnw -f backend/pom.xml -B spotless:check clean verify`: 263
  tests passed, zero failures/errors/skips. This full run used the initial
  three PostgreSQL batch tests before the standalone HTTP assertions were added.
- `bash backend/mvnw -f backend/pom.xml -B spotless:apply
  -Dtest=BatchReadTests,DeliveryControllerTests,ReturnControllerTests,DeliveryConcurrencyTests,ReturnConcurrencyTests test`:
  18 tests passed.
- `bash backend/mvnw -f backend/pom.xml -B spotless:apply -Dtest=BatchReadTests test`:
  three tests passed after adding HTTP query-count checks on PostgreSQL.
- Frontend was not changed; its suites were not rerun locally.
