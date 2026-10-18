# Shop-owner dashboard verification (SF-129)

## Scope and contract

See [the API contract](./api-dashboard-spec.md) for payment deduplication,
calendar boundaries, exact-money transport and the distinction between period
metrics and current inventory/delivery snapshots.

The UI is at `/shop-owner/dashboard`. The existing `/shop-owner` delivery screen
is retained and links back through owner navigation. Only SHOP_OWNER can call
`GET /dashboard`; route guards also exclude anonymous, customer and warehouse
sessions. No migration or production data modification is included.

## Automated verification

| Check | Result |
| --- | --- |
| `bash backend/mvnw -f backend/pom.xml -B spotless:check` | Passed |
| `bash backend/mvnw -f backend/pom.xml -B clean verify` | 139 tests passed (6 dashboard PostgreSQL integration tests) |
| `npm --prefix frontend run lint:ci` | Passed |
| `npm --prefix frontend run build` | Passed, including type-check |
| `npm --prefix frontend run test:e2e` | 53 tests passed on full rerun |

Dashboard integration cases cover: owner/other-role access, first-successful
payment deduplication before filtering, multiple items and payments, order price
snapshots after catalog changes, returns without refund accounting, Vietnamese
midnight and month boundaries, zero-filled buckets, current stock/reservations,
missing inventory, top-five tie order, invalid/oversized ranges and defaults.
Browser cases cover exact amounts beyond Number.MAX_SAFE_INTEGER, tooltip,
keyboard data table, filter submission/presets, stale responses, loading,
errors/retry, empty periods, role navigation and 360/390/1440 px layouts.

The initial complete browser run had four blank/unrendered development-page
failures in existing catalog, session-reload, product-dialog and returns cases.
All four passed three focused repetitions (12 tests). No existing timeout,
retry or application behavior was changed to accommodate those failures.

## Production-build smoke with a disposable database

On 2026-10-18, restored a read-only snapshot of the demonstration database into
a dedicated PostgreSQL 17 container, ran the actual Java jar and served the
production frontend build through a local proxy. Only the copy was accessed
by the test backend. Production records and R2 objects were not changed;
temporary database, backend and proxy were removed afterwards.

For 2026-06-01 through 2026-10-18, grouped by month, an independent SQL query
against the copy matched the API exactly:

| Metric | Expected and actual |
| --- | --- |
| Gross paid amount | 920,450,000 VND |
| Orders created | 180 |
| Current paid orders in progress | 24 |
| Current active low-stock products | 8 |
| Monthly buckets | 5 |

Actual owner login, filtering, chart rendering and exact tooltip formatting
passed at 1440x1000, 390x844 and 360x800. No horizontal overflow, uncaught
JavaScript errors or Unovis warnings were observed. These are synthetic demo
sales, not actual shop revenue. Test fixtures do not depend on those records.

- [Desktop](./qa/sf-129/dashboard-1440.png)
- [Mobile 390 px](./qa/sf-129/dashboard-390.png)
- [Mobile 360 px](./qa/sf-129/dashboard-360.png)

## Dependencies and limits

Chart helpers are adapted from the MIT-licensed [shadcn-vue chart
registry](https://www.shadcn-vue.com/r/styles/new-york-v4/chart.json), using
Unovis Vue/TS 1.7.1. The upstream license is retained alongside those helpers.
Only the chart components needed here are included; no global tooltip cache
or unmounted Vue apps are retained. Dashboard code is loaded by its route.
Existing lockfile package versions are unchanged.

Production dependency audit reported the same existing advisory set as the
baseline lockfile: @vue/server-renderer, axios, form-data, nanoid, postcss,
source-map-js, vite and vue. No new production advisory package was introduced
by the chart dependencies. Dependency updates outside SF-129 were not attempted.
Existing VueUse PURE-annotation build warnings remain non-blocking.

No real-device testing, accounting/refund implementation, production deployment,
load testing or claim of historical inventory/delivery reconstruction is made.
Charts use approximate numeric coordinates; exact values remain in cards,
tooltips and the keyboard-accessible table. CI/PR review and release approval
remain separate from this local verification.
