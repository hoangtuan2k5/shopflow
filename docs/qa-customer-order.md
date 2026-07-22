# Customer Order QA Checklist

- Jira: SF-13, SF-46
- Parent: SF-3
- Verified: 2026-07-20
- Scope: `POST /orders`, stock reservation, and the customer checkout UI

## Contract scenarios

| # | Scenario | Automated evidence | Result |
| --- | --- | --- | --- |
| 1 | Valid order with every item in stock | `OrderControllerTests.createsOrderWithServerSnapshotsTotalsAndReservation` | Pass |
| 2 | `reserved_stock` increases by the requested quantity while `on_hand_stock` stays unchanged | Same test, database assertions | Pass |
| 3 | One `ORDER_RESERVED` movement is written per item with the order reference | Same test, movement count assertion | Pass |
| 4 | One item has insufficient stock | `OrderControllerTests.rejectsInsufficientStockWithoutPartialOrderOrReservation` | Pass |
| 5 | Multi-item order rejects atomically when one item is insufficient | Same test, no order/item/movement assertions | Pass |
| 6 | Product ID does not exist | `OrderControllerTests.rejectsMissingProductAndMissingInventory` | Pass |
| 7 | Product is inactive | `OrderControllerTests.rejectsUnavailableProductAndInvalidPaymentMethod` | Pass |
| 8 | Active product has no inventory row | `OrderControllerTests.rejectsMissingProductAndMissingInventory` | Pass |
| 9 | Quantity is zero or negative | `OrderControllerTests.rejectsEmptyItemsAndNonPositiveQuantities` | Pass |
| 10 | `items` is empty | `OrderControllerTests.rejectsEmptyItemsAndNonPositiveQuantities` | Pass |
| 11 | Duplicate product ID | `OrderControllerTests.rejectsDuplicateItemsAndFractionalStoredPrice` | Pass |
| 12 | Required customer or shipping field is missing/blank | `OrderControllerTests.rejectsMissingRequiredFields` | Pass |
| 13 | `paymentMethod=COD` or another invalid value | `OrderControllerTests.rejectsUnavailableProductAndInvalidPaymentMethod` | Pass |
| 14 | Client sends a `unitPrice` field | `OrderControllerTests.ignoresClientUnitPriceAndUsesServerSnapshot` | Pass; DB price wins |
| 15 | Two concurrent orders compete for the last unit | `OrderConcurrencyTests.onlyOneConcurrentOrderCanReserveTheLastUnit` using PostgreSQL Testcontainers | Pass; exactly one successful order |
| 16 | Fractional or quoted product ID/quantity | `OrderControllerTests.rejectsCoercedItemNumbersWithoutSideEffects` | Pass; 400 with no writes |
| 17 | Customer and shipping field errors identify their paths | `OrderControllerTests.rejectsMissingRequiredFields` | Pass |
| 18 | Order total exceeds `NUMERIC(12,2)` while the whole-VND boundary remains valid | `OrderControllerTests.rejectsOrderTotalOutsideDatabaseRangeWithoutSideEffects` and `acceptsLargestWholeVndOrderTotalSupportedByDatabase` | Pass |

The concurrency test uses a real PostgreSQL container and two independent
transactional service calls. It asserts one successful order, one insufficient
stock result, one reservation, one movement, and `reserved_stock <= on_hand_stock`.

## Customer checkout UI smoke checks

Executed locally with headless Chromium against the Vite dev server. Product and
order API responses were intercepted so the check did not modify development
inventory; the success and insufficient-stock payloads matched the API contract.

| Scenario | Expected | Result |
| --- | --- | --- |
| Catalog with available and unavailable products | Prices render as VND; `Unavailable` is disabled | Pass |
| Add an available product | Checkout rail appears with quantity and whole-VND total | Pass |
| Submit with blank required fields | Inline field messages are shown; no order request is accepted | Pass |
| Successful checkout | Success state preserves order ID `501` and `PENDING_PAYMENT` | Pass |
| Insufficient-stock response | Alert and per-product `Only 0 available right now.` message appear | Pass |
| Mobile layout at 390 × 844 | No horizontal overflow; fields and actions remain usable | Pass |
| Browser console | No unexpected errors; expected 400 resource response excluded | Pass |

The SF-46 component regression check uses Vite SSR and the existing Node test
runner. It verifies that the payment handoff renders the exact order ID, status,
and VND total, and that a selected item plus field-specific server error remain
visible. Browser focus behavior was not rerun because this environment has no
Playwright/Chromium dependency.

## Verification commands

```sh
cd backend
bash mvnw -q -Dtest=OrderControllerTests test
bash mvnw -q -Dtest=OrderConcurrencyTests test
bash mvnw -q spotless:check verify

cd ../frontend
node --experimental-strip-types --test src/api/__tests__/validateProductPrice.test.ts src/components/forms/__tests__/CheckoutForm.test.ts
npm run type-check
npm run lint:ci
npm run build
git diff --check
```

Results on 2026-07-20:

- Order controller tests: 11 passed.
- PostgreSQL concurrency test: 1 passed.
- Frontend native Node tests: 2 passed.
- Frontend type-check, lint, and production build: passed.
- Spotless and whitespace checks: passed.

## Notes and scope limits

- The UI smoke check verifies checkout behavior only; payment remains out of
  scope for SF-43/SF-13.
- Testcontainers requires a Docker daemon. The normal H2 test profile remains
  unchanged for the existing unit/API tests.
