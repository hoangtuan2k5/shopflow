# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
