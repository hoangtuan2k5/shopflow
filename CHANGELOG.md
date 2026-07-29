# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Access**:
  - Rate-limit repeated failed sign-ins for the same username and return `429` with `Retry-After`.

## [0.9.2] - 2026-07-27

### Fixed
- **Access**:
  - The browser can complete state-changing requests again. Since 0.9.0 the CSRF configuration named the token store but not the token reader, so Spring Security's default handler demanded a masked header while the cookie carried the raw token. Guest checkout answered 401 and every staff write answered 403, with the whole test suite green throughout (SF-101).
  - A read request now issues the CSRF cookie, so a visitor's first write no longer has to fail once to obtain a token (SF-101).
- **Warehouse screens**:
  - Realigned the stock table headings with their data and stopped the card clipping its third action, which was unreachable in Vietnamese (SF-88).
  - The delivery stepper no longer draws its connecting line across the neighbouring circle and through its tick (SF-88).

### Changed
- **Testing**:
  - Added a regression test that completes the CSRF cookie-to-header round trip the way a browser does. The existing authorization tests use a helper that bypasses the configured token handler, so they pass against a configuration that rejects every real request (SF-101).

## [0.9.1] - 2026-07-26

### Fixed
- **Storefront**:
  - Added a way into the workspace from the storefront header. The sign-in control only rendered on routes that require a session, so a visitor landing on the storefront had no link to the login page at all (SF-87).

## [0.9.0] - 2026-07-26

### Added
- **Access**:
  - Added user accounts with BCrypt-hashed passwords, three seeded demo accounts, and login, logout and current-session endpoints backed by an HttpOnly session cookie (SF-72).
  - Added the login screen, session restore on start-up, and a sign-out control that clears cached data (SF-72).
  - Added role-based authorization across every endpoint, with catalog, order creation and guest payment left open, and route guards that send anonymous visitors to sign in and bounce wrong-role visitors to their own workspace (SF-73).
  - Added a content-dependent check so warehouse staff can restock a return but cannot approve or reject one (SF-73).

### Changed
- **Security**:
  - Unknown, wrong-password and deactivated accounts now fail identically in status, message and response time (SF-72).
  - CSRF protection is enabled for state-changing requests, and the API documentation endpoints are switched off in the staging profile (SF-73).

### Fixed
- **Payment**:
  - Orders are now addressed by an unguessable reference when paying. The sequential order id in the payment path let anyone mark a stranger's order paid, or fail it and release the stock it was holding (SF-85).

### Breaking
- Warehouse and shop owner areas now require signing in. Demo credentials are listed in the README.
- `POST /orders/{orderId}/payments` is replaced by `POST /orders/{orderRef}/payments`; the reference comes from the order creation response.

## [0.8.0] - 2026-07-26

### Added
- **Localization**:
  - Added English and Vietnamese message catalogs covering the storefront, checkout, inventory, delivery, return and shell screens, with a compact language selector in both the storefront and operations headers (SF-69).
  - Added locale resolution that follows a stored manual choice, then the browser language, falling back to English, and keeps the document language attribute in sync (SF-69).

### Changed
- **Frontend**:
  - Client-side validation messages and date formatting now follow the active locale; prices stay in VND and backend error messages are shown as returned (SF-69).

## [0.7.0] - 2026-07-26

### Added
- **Customer Returns**:
  - Added the customer return API contract covering the `REQUESTED` → `APPROVED`/`REJECTED` → `RESTOCKED` lifecycle (SF-18).
  - Added return creation for delivered orders with partial quantities, a purchased-quantity cap across non-rejected returns, and transactional restock that raises on-hand stock and writes a `RETURN_RESTOCK` movement per item (SF-17).
  - Added the shared Warehouse and Shop Owner return management UI with review, restock decision and restock confirmation (SF-63).
  - Added controller, PostgreSQL concurrency and browser scenario coverage with QA evidence (SF-19).
- **Low Stock Alerts**:
  - Added the low stock alert contract deriving `lowStock` from the per-product threshold at read time (SF-64).
  - Added `lowStockThreshold` and `lowStock` to the inventory read model, a threshold update endpoint, and the alert banner, badge and threshold dialog in the Inventory UI (SF-65).
  - Added controller and browser scenario coverage with QA evidence (SF-66).

### Fixed
- **Customer Returns**:
  - Lock the product row before restocking so the lazy inventory insert is serialised and the lock order matches every other stock path, removing a PostgreSQL deadlock against the threshold endpoint (SF-67).
  - Scope the returnable-items aggregate by order so return lookups stop summing every non-rejected return item row per delivered order (SF-67).
- **Inventory**:
  - Always report a low stock threshold failure, including network errors that carry no error body (SF-67).

## [0.6.1] - 2026-07-22

### Added
- **Catalog**:
  - Added 36 active, in-stock demo products with Vietnamese descriptions and whole-VND prices to populate the production storefront and Warehouse workflows (SF-61).

## [0.6.0] - 2026-07-22

### Added
- **Supplier Receiving**:
  - Added the receiving API contract, transactional `POST /receivings` backend, inventory update, and `STOCK_RECEIVED` audit movement (SF-7).
  - Added the Warehouse receiving workflow with validation, retryable errors, and refreshed inventory.
  - Added controller, PostgreSQL concurrency and rollback, and browser scenario coverage with QA evidence.

### Changed
- **Frontend**:
  - Use Be Vietnam Pro as the default UI typeface for Vietnamese text while preserving system-font fallbacks (SF-59).

## [0.5.0] - 2026-07-22

### Added
- **Customer Orders**:
  - Added the customer order API contract, order data model, stock validation and reservation, order creation, checkout UI, and end-to-end scenario coverage (SF-3).
- **Payments**:
  - Added simulated payment states, result handling, API contract, and scenario coverage (SF-4).
- **Inventory**:
  - Added stock list and adjustment APIs, owner and warehouse management UI, API contract, and scenario coverage (SF-6).
- **Delivery**:
  - Added delivery status transitions, completion handling, shared management UI, API contract, and scenario coverage (SF-5).
- **CI/CD & DevOps**:
  - Added production Docker images and a Docker Compose stack for the frontend, backend, and PostgreSQL.
  - Added Caddy HTTPS and API routing for both ShopFlow domains.
  - Added automatic VPS deployment after CI passes on `main` (SF-53).

### Changed
- Updated ShopFlow requirements, catalog prices, and currency formatting to use VND (SF-1, SF-45).
- Improved the responsive customer storefront and simplified its messaging (SF-47, SF-48).

### Fixed
- Hardened order validation and payment handoff behavior (SF-46).
- Preserved the VPS environment during deployment bootstrap and routed API requests before the SPA fallback (SF-53).

## [0.4.0] - 2026-07-12

### Added
- **Product Catalog**:
  - Added backend endpoints for listing active products and viewing product details with inventory-based stock status (SF-37).
  - Added development sample data for testing the catalog locally (SF-37).
  - Added a branded customer catalog with product list, stock indicators, detail view, and responsive states (SF-38).
  - Added integration coverage and QA evidence for Product Catalog API and UI scenarios (SF-39).

## [0.3.0] - 2026-07-05

### Added
- **Documentation**:
  - Added the ShopFlow MVP Software Requirements Specification with scope, business rules, lifecycles, acceptance criteria, and Jira traceability.
  - Added the Product Catalog API specification for backend, frontend, and verification work (SF-36).
  - Traced the core domain model requirement to the SRS glossary, lifecycle, and data model sections (SF-10).

### Changed
- **Documentation & Process**:
  - Aligned the README sprint plan with the Jira board.
  - Required agents to derive commit and PR conventions from recent repository history.
  - Required Jira keys in squash merge commit bodies.

## [0.2.0] - 2026-06-10

### Added
- **Frontend**:
  - Set up Tailwind CSS and shadcn-vue component library (SF-27).
  - Added role-based layout, routing, and Pinia state skeleton (SF-28).
  - Added a temporary frontend API client (SF-21).

### Changed
- **Documentation & Process**:
  - Enforced squash merge strategy, release tagging, and added `docs/*` and `chore/*` branch types (SF-31).
  - Added agent coding workflow and PR history review rules (SF-32).
  - Clarified `develop` to `main` release merge strategy uses a merge commit (SF-33).
  - Required PR number suffix in squash merge subject (SF-34).
- **Chore**:
  - Ignore `.omo` directory.

## [0.1.0] - 2026-06-05

### Added
- **Backend**:
  - Initialized Spring Boot backend application (SF-20).
  - Configured PostgreSQL datasource and automated schema migration via Flyway (SF-23).
  - Integrated OpenAPI using Springdoc-OpenAPI/Swagger UI available at `/swagger-ui.html` (SF-24).
  - Configured Spring Boot Actuator health and info endpoints.
- **Frontend**:
  - Initialized Vue 3 frontend project with TypeScript, Vite, Vue Router, and Pinia (SF-26).
- **CI/CD & DevOps**:
  - Set up GitHub Actions CI pipeline running Maven verification for backend and npm lint/build for frontend (SF-25).
  - Configured Spotless code formatting plugin for backend codebase.
  - Implemented pre-commit hooks for automatically applying code style checking.
- **Documentation**:
  - Added git branching strategy, commit convention (Conventional Commits), and PR guidelines in `CONTRIBUTING.md`.
  - Added project README.md and database schema description in `docs/database-schema.md`.

### Changed
- Aligned Spring Boot main class bootstrap setup as `dev.hoangtuan.shopflow.ShopflowApplication` (SF-20).
