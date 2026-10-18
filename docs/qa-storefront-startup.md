# Storefront startup under stalled font services

Verified 2026-10-18 as part of SF-76 / SF-100 QA.

## Reproducer and diagnosis

`frontend/index.html` previously loaded a Google Fonts stylesheet in the head.
Holding that request unresolved reproduced an empty `#app` and no storefront
trigger; releasing it immediately allowed Vue and the storefront to initialize.
`font-display: swap` inside that remote stylesheet cannot help while the
stylesheet itself is still pending.

The regression test `startup.spec.ts` keeps Google font services unresolved and
opens the document with `waitUntil: commit`, then expects the actual storefront
heading and chat trigger. It failed before the change and passed after it.

This establishes a real startup-blocking path. Historical intermittent E2E
failures did not retain traces, so they cannot all be attributed to this path.
During diagnosis, 80 direct opens and 120 opens/reloads had no blank-page failure.
A 48-case focused repetition had one navigation reset while temporary diagnostic
code was being edited; a trace run had one delivery timing failure under concurrent
browser load. Neither is counted as reproduction of the historical blank page.
A fresh-cache Vite run also passed all 65 pre-fix tests. No speculative router or
DevTools changes were made from those observations.

## Change

Serve the existing Be Vietnam Pro family locally using the same five weights
(400, 500, 600, 700, 900), Latin/Vietnamese/Latin-ext subsets and `font-display: swap`.
Remove remote stylesheet/preconnect tags. No runtime font CDN requests, dependency
upgrade, routing change, retry or timeout increase is required.

15 WOFF2 subsets total 237080 bytes (about 232 KiB); browsers fetch only the
weight/ranges they use. Vite fingerprints the imported assets in production.
Upstream source: [Google Fonts stylesheet](https://fonts.googleapis.com/css2?family=Be+Vietnam+Pro:wght@400;500;600;700;900&display=swap).
Redistribution license: [included OFL](../frontend/src/assets/fonts/OFL.txt),
from [Google Fonts repository](https://github.com/google/fonts/blob/main/ofl/bevietnampro/OFL.txt).

## Verification

- Pre-fix held-font regression: failed, storefront unavailable until request released.
- Post-fix `npm --prefix frontend run test:e2e -- e2e/startup.spec.ts`: 2 passed.
- The second test loads Vietnamese text at all five weights and verifies loaded
  FontFace entries plus font request origins matching the application.
- `npm --prefix frontend run lint:ci`: passed.
- `npm --prefix frontend run build`: passed, including TypeScript check; existing
  VueUse PURE-annotation warnings remain non-blocking.
- `npm --prefix frontend run test:e2e`: **67 passed (1.2m)** on Vite dev-server,
  default two workers, no retries.
- Production preview: storefront booted while Google Fonts remained unresolved;
  all five weights loaded successfully from the production assets.

The change removes this external stylesheet dependency at startup. It does not
claim that every possible blank page, route import failure or network outage is
eliminated. No backend/API changes or production deployment were made by this fix.
