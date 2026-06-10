# ShopFlow

Hệ thống bán hàng và quản lý kho cho shop online quy mô nhỏ. Mô phỏng các luồng tạo đơn hàng, thanh toán, cập nhật trạng thái giao hàng, quản lý tồn kho, nhập hàng, hoàn hàng và cảnh báo sắp hết hàng.

## Team

ShopFlow Team gồm 3 thành viên:

| Họ tên | Vai trò |
|---|---|
| Hoàng Chiều Nguyễn Tuấn | Team Leader, Backend Developer & DevOps |
| Phạm Thị Thu Thủy | Business Analyst |
| Nguyễn Tiến Dũng | Frontend Developer |

## Cấu trúc monorepo

```
shopflow/
├── backend/             # shopflow-backend (Spring Boot)
├── docs/                # database schema and technical documentation
├── frontend/            # shopflow-frontend (Vue 3 + Vite)
├── docker-compose.yml   # local development services
├── README.md
└── LICENSE
```

- `backend/`: Spring Boot service, Java 21, Maven, PostgreSQL, Flyway, OpenAPI.
- `frontend/`: Vue 3 + Vite + TypeScript, Vue Router, Pinia, TanStack Query,
  VeeValidate, Zod, ESLint, Oxlint, Prettier.
- `docs/`: tài liệu database schema và các ghi chú kỹ thuật.

## Tech stack

- **Backend:** Spring Boot & Spring
- **Frontend:** Vue.js
- **Database:** PostgreSQL, H2 Database
- **Infrastructure & Cloud:** Cloudflare, Amazon S3, Oracle Cloud
- **Tooling:** Git/GitHub, Postman, Jira

### Backend

- Java 21 LTS
- Spring Boot 4.0.x
- Maven
- Spring Web, Spring Data JPA, Validation
- Lombok, MapStruct
- PostgreSQL (dev/staging), H2 (test)
- Flyway Migration
- Spring Boot Actuator
- Springdoc OpenAPI

### Frontend

- Vue 3 + Vite + TypeScript
- Vue Router, Pinia
- TanStack Query, VeeValidate, Zod
- Axios API client wired to the backend OpenAPI document
- ESLint, Oxlint, Prettier

## Stakeholder

- Chủ shop
- Nhân viên kho
- Khách hàng

## Sprint plan

- **Sprint 1 (18/05/2026 → 31/05/2026):** Foundation MVP. Domain model, product catalog, order creation, payment simulation, inventory baseline, bootstrap monorepo + CI.
- **Sprint 2 (01/06/2026 → 14/06/2026):** Operational flows. Delivery status, supplier receiving, customer return, low stock alert.
- **Sprint 3:** Shop operations and admin experience. Dashboard, inventory movement history, product management, role-based access (mocked), end-to-end demo.

Chi tiết backlog quản lý trên Jira project `SF` (Shopflow).

## Cách chạy local

### PostgreSQL local

Backend mặc định trỏ tới PostgreSQL tại `localhost:5432`. Nếu dùng container đi kèm repo, chạy:

```bash
docker-compose up -d
```

Container PostgreSQL expose ra port `5433` để tránh đụng PostgreSQL cài local. Khi chạy backend với container này, set `DB_URL`:

```bash
DB_URL=jdbc:postgresql://localhost:5433/shopflow
```

### Backend

```bash
cd backend
bash ./mvnw spring-boot:run
```

Mặc định backend dùng profile `dev`, kết nối PostgreSQL qua các biến `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`. Ứng dụng chạy ở `http://localhost:8080`. Swagger UI
tại `http://localhost:8080/swagger-ui.html`.

Chạy staging profile:

```bash
cd backend
SPRING_PROFILES_ACTIVE=staging DB_URL=jdbc:postgresql://<host>:5432/shopflow DB_USERNAME=<user> DB_PASSWORD=<password> bash ./mvnw spring-boot:run
```

### Frontend

Frontend dùng Vue 3 + Vite. Chạy local:

```bash
cd frontend
cp .env.example .env.local
npm install
npm run dev
```

Mặc định FE chạy ở `http://localhost:5173` và gọi tới backend qua biến môi trường `VITE_API_BASE_URL` trong `frontend/.env.local`.

## Biến môi trường

### Backend

| Biến | Mặc định | Mô tả |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/shopflow` | JDBC URL tới PostgreSQL. Dùng `jdbc:postgresql://localhost:5433/shopflow` nếu chạy PostgreSQL bằng `docker-compose.yml`. |
| `DB_USERNAME` | `postgres` | Username kết nối database. |
| `DB_PASSWORD` | `postgres` | Password kết nối database. |

Profile `staging` bắt buộc truyền đủ 3 biến database qua môi trường. Test profile
dùng H2 in-memory và tự chạy Flyway migration trong schema `shopflow`.

### Frontend

| Biến | Mặc định | Mô tả |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080` | Base URL của backend API cho frontend local. |

Frontend API client nằm tại `frontend/src/api`. Trong Sprint 1, backend chưa có
domain REST endpoint nên client tạm thời dùng Axios và TypeScript interfaces để
gọi OpenAPI document tại `/v3/api-docs`; khi backend có endpoint nghiệp vụ, có
thể thay bằng generated client từ OpenAPI spec.

## Build và test

Các lệnh dưới đây là cùng bộ lệnh CI dùng để kiểm tra monorepo.

### Backend

```bash
cd backend
bash ./mvnw spotless:apply
bash ./mvnw spotless:check
bash ./mvnw test
bash ./mvnw package
```

### Frontend

```bash
cd frontend
npm ci
npm run lint:ci
npm run build
```

### Full monorepo check

Chạy từ root repository:

```bash
cd backend
bash ./mvnw -B spotless:check
bash ./mvnw -B clean verify

cd ../frontend
npm ci
npm run lint:ci
npm run build
```

## CI pipeline

CI chạy trên GitHub Actions tại `.github/workflows/ci.yml` với 2 job độc lập, chạy song song:

- `backend`: chạy Java format check bằng Spotless, rồi Maven build và test trong `backend/`.
- `frontend`: chạy `npm ci`, `npm run lint:ci`, `npm run build` trong `frontend/`.

Backend dùng Spotless Maven Plugin để kiểm tra format Java. Spotless chạy
`google-java-format` với Google style và `removeUnusedImports`; khi cần tự format:

```bash
cd backend
bash ./mvnw spotless:apply
```

Repo có sẵn Git pre-commit hook tại `.githooks/pre-commit` để chạy `spotless:apply`
trước mỗi commit. Bật hook một lần sau khi clone:

```bash
git config core.hooksPath .githooks
```

Branch `main` và `develop` nên yêu cầu Pull Request và CI pass trước khi merge.

## License

MIT License. Xem chi tiết tại [LICENSE](LICENSE).

Copyright (c) 2026 ShopFlow Team.
