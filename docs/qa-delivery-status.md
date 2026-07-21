# Delivery Status QA Evidence

This document records the repeatable verification used to close
[SF-5](https://tuanwork.atlassian.net/browse/SF-5). Browser tests use mocked
delivery APIs so they are deterministic and do not require seeded local data.

## Automated coverage

| Requirement                                                                                                 | Evidence                                                                                                                                                        |
| ----------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| AC-06: valid PAID delivery transitions and history                                                          | `DeliveryControllerTests.advancesLifecycleAndCompletesInventoryAtomically`; `delivery.spec.ts` Warehouse happy path                                             |
| AC-06: unpaid, skipped, repeated, malformed, or missing-order transitions are rejected without side effects | `DeliveryControllerTests.rejectsUnpaidSkippedRepeatedAndMalformedTransitionsWithoutSideEffects`; `DeliveryControllerTests.returnsNotFoundAndValidationErrors`   |
| AC-07: DELIVERED updates on-hand/reserved stock and writes movements atomically                             | `DeliveryControllerTests.advancesLifecycleAndCompletesInventoryAtomically`; `DeliveryControllerTests.rollsBackDeliveredChangesWhenOneReservationIsInconsistent` |
| Concurrent completion cannot update or decrement stock twice                                                | `DeliveryConcurrencyTests.concurrentCompletionDecrementsInventoryOnlyOnce`                                                                                      |
| Warehouse and Shop Owner use the same workflow                                                              | `delivery.spec.ts` Warehouse and Shop Owner scenarios                                                                                                           |
| Loading, loaded, empty, failure/retry, success, rejected transition, and history states                     | `delivery.spec.ts` browser scenarios                                                                                                                            |
| Mobile layout has no horizontal overflow; dialog restores keyboard focus                                    | `delivery.spec.ts` Shop Owner mobile scenario                                                                                                                   |
| Browser runtime has no uncaught page errors in the primary workflow                                         | `delivery.spec.ts` Warehouse happy path                                                                                                                         |

## Commands

Run backend verification from `backend/`:

```bash
bash ./mvnw spotless:check verify
```

Run frontend verification from `frontend/`:

```bash
npm ci
npx playwright install chromium
npm run type-check
npm run lint:ci
npm run build
npm run test:e2e
```

Playwright starts the Vite development server automatically. Any failed browser
assertion makes `npm run test:e2e` exit with a non-zero status. CI installs
Chromium and runs the same browser command for every pull request targeting
`develop` or `main`.
