# Inventory Management QA Checklist

- Jira: SF-16
- Parent: SF-6
- Verified: 2026-07-21
- Scope: `GET /inventory`, manual adjustment, concurrency, and warehouse UI

## API and data scenarios

| # | Scenario | Automated evidence | Result |
| --- | --- | --- | --- |
| 1 | List active, inactive, and missing-inventory products | `InventoryControllerTests.listsEveryProductWithComputedStock` | Pass |
| 2 | Compute available as on-hand minus reserved | Same list test | Pass |
| 3 | Apply a valid negative delta and trim the reason | `adjustsOnHandAndWritesMovementNote` | Pass |
| 4 | Write the matching `MANUAL_ADJUSTMENT` quantity and note | Same adjustment test | Pass |
| 5 | Create inventory for a positive adjustment when none exists | `createsMissingInventoryForPositiveAdjustment` | Pass |
| 6 | Reject a result below reserved stock without side effects | `rejectsAdjustmentThatWouldDropBelowReserved` | Pass |
| 7 | Reject zero, blank reason, and fractional delta | `rejectsInvalidAdjustmentBodies` | Pass |
| 8 | Reject a missing product without creating inventory | `returnsNotFoundWithoutCreatingInventory` | Pass |
| 9 | Reject a negative initial adjustment and integer overflow | `rejectsNegativeMissingInventoryAndIntegerOverflow` | Pass |
| 10 | Reject missing delta and reason longer than 500 characters | `rejectsMissingDeltaAndOverlongReason` | Pass |
| 11 | Serialize concurrent decrements at the reserved boundary | `InventoryConcurrencyTests.concurrentAdjustmentsPreserveReservedStockInvariant` | Pass |

The concurrency check runs two independent transactional service calls against
PostgreSQL Testcontainers. Starting from on-hand `9` and reserved `8`, exactly
one decrement succeeds, one conflicts, final stock is `8/8/0`, and exactly one
movement is written.

## Warehouse UI checks

Headless Chromium ran against the Vite dev server with inventory responses
intercepted. The checks did not read or modify development inventory.

| Scenario | Expected | Result |
| --- | --- | --- |
| Loading | Four stable inventory placeholders remain visible until the response arrives | Pass |
| Loaded list | Product, on-hand, reserved, available, and adjustment action are visible | Pass |
| Empty list | A literal no-products state is shown | Pass |
| API load failure | Automatic retries end in a user-facing error; `Try again` reloads the list | Pass |
| Client validation | Zero delta and blank reason show field errors without sending a request | Pass |
| Successful adjustment | Sends delta/reason, updates the row, and announces success | Pass |
| `409` adjustment conflict | Shows the business error and retains delta/reason in the open dialog | Pass |
| Responsive layout | 390 × 844 mobile and 1440 × 900 desktop have no horizontal overflow | Pass |
| Dialog accessibility | Escape closes the dialog and returns focus to the triggering action | Pass |
| Browser runtime | No unexpected page errors | Pass |

## Verification commands

```sh
cd backend
bash mvnw -q -Dtest=InventoryControllerTests test
bash mvnw -q -Dtest=InventoryConcurrencyTests test
bash mvnw -q spotless:check verify

cd ../frontend
npm run type-check
npm run lint:ci
npm run build
git diff --check
```

Results on 2026-07-21:

- Eight Inventory controller tests passed.
- One PostgreSQL concurrency test passed.
- Full backend verification and Spotless passed.
- Frontend type-check, lint, and production build passed.
- Desktop and mobile browser QA passed.

## Scope limits

- Pagination, search, movement history, inventory import, and authentication are
  outside SF-6.
- SF-16 adds coverage and QA evidence only; no production behavior changed.
