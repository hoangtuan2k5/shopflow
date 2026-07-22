# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
