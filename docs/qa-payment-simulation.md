# Payment Simulation QA Checklist

- Jira: SF-42
- Parent: SF-4
- Verified: 2026-07-21
- Scope: `POST /orders/{orderId}/payments`, inventory release, concurrency, and customer payment UI

## API and data scenarios

| # | Scenario | Automated evidence | Result |
| --- | --- | --- | --- |
| 1 | SUCCESS creates one payment and changes the order to `PAID` | `PaymentControllerTests.completesSuccessfulPaymentAndKeepsReservation` | Pass |
| 2 | SUCCESS keeps reserved and on-hand stock unchanged | Same test, inventory assertions | Pass |
| 3 | FAILED changes the order to `PAYMENT_FAILED` and releases reservation | `PaymentControllerTests.completesFailedPaymentAndReleasesReservation` | Pass |
| 4 | EXPIRED releases every order product but leaves on-hand stock unchanged | `PaymentControllerTests.expiresPaymentAndReleasesEachProductWithoutChangingOnHand` | Pass |
| 5 | Each release writes a negative `PAYMENT_FAILED_RELEASE` movement with the order reference | FAILED and EXPIRED controller tests | Pass |
| 6 | Missing order returns 404 without creating payment data | `PaymentControllerTests.rejectsMissingOrderInvalidBodyAndRepeatedPayment` | Pass |
| 7 | Invalid state and repeated payment return 409 with at most one attempt | Controller validation and repeated-payment tests | Pass |
| 8 | Invalid result or failure reason returns 400 without side effects | Controller malformed-body and failure-reason tests | Pass |
| 9 | An inconsistent reservation rolls back the payment and every earlier release | `PaymentControllerTests.rollsBackPaymentAndEarlierReleasesWhenOneReservationIsInconsistent` | Pass |
| 10 | Two concurrent requests for one order create at most one attempt | `PaymentConcurrencyTests.onlyOneConcurrentPaymentAttemptCanComplete` with PostgreSQL Testcontainers | Pass |

The concurrency check uses two independent service calls against a real
PostgreSQL container. It asserts one success, one conflict, one payment row, a
`PAID` order, and an unchanged reservation.

## Customer payment UI checks

Headless Chromium ran against the Vite dev server with product, order, and
payment API responses intercepted. The smoke check did not read or modify
development data.

| Scenario | Expected | Result |
| --- | --- | --- |
| SUCCESS | Shows `Payment SUCCESS · Order PAID` and hides result actions | Pass |
| FAILED | Shows `Payment FAILED · Order PAYMENT_FAILED` and the failure reason | Pass |
| EXPIRED | Shows `Payment EXPIRED · Order PAYMENT_FAILED` and the expiry reason | Pass |
| Pending request | Disables payment actions and announces processing | Pass; SSR regression |
| 409 API response | Keeps order `#501`, displays the server error, and keeps actions available | Pass |
| Responsive layout | 390 px mobile and 1440 px desktop have no horizontal overflow | Pass |
| Browser console | No unexpected errors; the expected mocked 409 resource response is excluded | Pass |

The CheckoutForm Node regression covers all three terminal states, loading,
API error, the original order handoff, and field-specific order errors.

## Verification commands

```sh
cd backend
bash mvnw -q -Dtest=PaymentControllerTests test
bash mvnw -q -Dtest=PaymentConcurrencyTests test
bash mvnw -q spotless:check verify

cd ../frontend
node --test src/components/forms/__tests__/CheckoutForm.test.ts
npm run type-check
npm run lint:ci
npm run build-only
git diff --check
```

Results on 2026-07-21:

- Payment controller scenarios: passed.
- PostgreSQL payment concurrency scenario: passed.
- Full backend tests and Spotless: passed.
- CheckoutForm Node regression: passed.
- Frontend type-check, lint, and production build: passed.
- Headless Chromium payment matrix: passed at desktop and mobile widths.

## Scope limits

- No real gateway, retry, refund, webhook, polling, or COD workflow was tested
  because those behaviors are outside SF-4.
- SF-42 changes test coverage and QA evidence only; production behavior remains
  the implementation merged under SF-41.
