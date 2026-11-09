# Customer checkout polish (SF-158/SF-159)

## SF-158 scope

Customer-facing EN/VI copy is shortened without app/demo/backend explanations. Checkout keeps one explicit payment action, removes result-selection controls and redundant hints, and preserves validation, loading/error feedback and account restrictions. The existing payment API remains simulated; no payment provider, backend/schema/policy change is included.

The two earlier uncommitted checkout-copy removals are included. Checkout SSR coverage passed after updating the old debug-control expectations. Final frontend build/lint passed, the SSR regression passed and the full browser suite passed141 cases without retries; local APIs are mocked. Never use mocked payment success as live banking evidence.

Release plan: prepare v0.14.2 changelog/notes in develop, then promote through a separate two-parent develop-to-main PR after approved CI and fresh backup/restore. Validate both public domains and exact popup after deployment without submitting production orders/payments. Keep v0.14.1 unchanged; owner signs the final verified main SHA. All Git/deployment/publication approval gates remain in effect.

## SF-159 scope and regression evidence

Checkout matches paymentResult.orderId to successOrder.id before rendering either the result or its order status. It reuses existing translated order-state labels and removes the hardcoded Payment/Order enum line. SUCCESS maps to PAID; FAILED and EXPIRED preserve the server's PAYMENT_FAILED order state. Request errors show a generic localized failure and do not claim success. The underlying request contract and backend transitions are unchanged.

The SSR reproducer failed against the old raw PENDING_PAYMENT rendering, then passed after the fix. SSR assertions cover both locales, success/failure/expiry and mismatched order IDs; the existing field-specific error coverage remains. Eight new payment browser cases cover both locales, no automatic payment, one disabled action during a pending request, attempted duplicate click, confirmed states, transport error/no retry and fresh-order reset. Two copy cases inspect checkout/account surfaces; existing ten long-title layout cases remain.

Initial focused run:23/26 passed; one old prefill-message expectation and two fixtures containing the display name Khách hàng demo failed. Updated the expected copy and used a normal Buyer session fixture; production logic was not relaxed. Initial full run:140/141 passed; the remaining chat test expected the replaced session-token wording. Corrected that expectation to the reload instruction. Final rerun:141/141 browser cases passed without retries; frontend build/lint and the final SSR regression passed.

Local screenshot artifacts:/tmp/shopflow-sf158/screenshots/ include EN/VI successful payment and mobile failed/network outcomes. These use mocked APIs; no live customer order, payment, email or password change was performed. Backend suites are not repeated locally because no backend or API/schema changes are included; CI must still pass before merge.

## Commands and review boundary

```bash
cd frontend
node --experimental-strip-types --test src/components/forms/__tests__/CheckoutForm.test.ts
cd ..
npm --prefix frontend run build
CI=1 npm --prefix frontend run test:e2e -- --retries=0 --reporter=list
npm --prefix frontend run lint:ci
git diff --cached --check
```

Visual inspection covered local payment success/failure/network states, checkout/delivery and EN/VI auth screenshots at desktop/mobile. Initial command attempts from the wrong SSR working directory were corrected before recording a pass. Commit/push/PR, release/main merge, bypass, deployment and owner tag/release publication have not been performed for this bundle.

## SF-158 follow-up: remove the whole introduction

Owner additionally requested removal of the entire storefront introduction and all three benefit tiles, not just revised wording. CatalogView now starts with the product section. Its heading becomes the page h1; removed section imports, scroll action and unused EN/VI strings are removed as well. Existing copy browser cases assert the first section is products and the heading remains accessible; frontend build/lint and21 focused browser cases passed. EN/VI desktop/mobile screenshots were captured and inspected. Release PR168 stays draft until this follow-up is reviewed and included in develop.
