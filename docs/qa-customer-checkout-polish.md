# Customer checkout polish (SF-158/SF-159)

## SF-158 scope

Customer-facing EN/VI copy is shortened without app/demo/backend explanations. Checkout keeps one explicit payment action, removes result-selection controls and redundant hints, and preserves validation, loading/error feedback and account restrictions. The existing payment API remains simulated; no payment provider, backend/schema/policy change is included.

The two earlier uncommitted checkout-copy removals are included. Checkout SSR coverage passed after updating the old debug-control expectations. Full expanded browser/build/lint results are pending; local APIs are mocked. Never use mocked payment success as live banking evidence.

Release plan: prepare v0.14.2 changelog/notes in develop, then promote through a separate two-parent develop-to-main PR after approved CI and fresh backup/restore. Validate both public domains and exact popup after deployment without submitting production orders/payments. Keep v0.14.1 unchanged; owner signs the final verified main SHA. All Git/deployment/publication approval gates remain in effect.
