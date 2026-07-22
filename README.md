# ShopFlow

Hệ thống bán hàng và quản lý kho cho shop online quy mô nhỏ. Mô phỏng các luồng tạo đơn hàng, thanh toán, cập nhật trạng thái giao hàng, quản lý tồn kho, nhập hàng, hoàn hàng và cảnh báo sắp hết hàng.

Currency baseline của MVP: **VND (đồng Việt Nam)**; giá và amount dùng đơn vị đồng, không có phần lẻ.

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

| Sprint | Thời gian | Trạng thái | Phạm vi / kết quả |
|---|---|---|---|
| **Sprint 1** | 18/05/2026 → 13/06/2026 | Closed | Foundation: monorepo + CI, bootstrap backend/frontend, process docs. Phạm vi nghiệp vụ (catalog, order, payment, inventory baseline) một phần chuyển sang sprint sau. |
| **Sprint 2** | 15/06/2026 → 29/06/2026 | Closed | Goal ban đầu: operational flows. Thực tế hoàn thành domain model (SF-10), Product Catalog end-to-end (SF-2, SF-36–39), SRS baseline, release **v0.3.0** / **v0.4.0**. Issue chưa xong chuyển sang Sprint 3. |
| **Sprint 3** | 17/07/2026 → 31/07/2026 | **Active** | Carry-over unfinished MVP: Create Customer Order (SF-3), payment simulation (SF-4), inventory (SF-6), delivery (SF-5), supplier receiving (SF-7), customer return (SF-8), low-stock alert (SF-9). |

Chi tiết backlog và board trên Jira project [`SF`](https://tuanwork.atlassian.net/browse/SF) (Shopflow).

## Tiến độ hiện tại

Cập nhật theo Jira board và repo (2026-07-22). Release mới nhất: **v0.5.0**.

| Hạng mục | Jira | Trạng thái | Ghi chú |
|---|---|---|---|
| Monorepo / CI / BE+FE bootstrap | SF-20…SF-28 | Done | Foundation sẵn sàng phát triển feature |
| Core domain model | SF-10 | Done | Truy vết trong SRS |
| Browse Product Catalog | SF-2 (SF-36…39) | Done | API contract, backend, frontend, verify |
| Create Customer Order | SF-3 | In Progress | API contract **SF-44 Done** (`docs/api-customer-order-spec.md`); còn SF-11, SF-12, SF-35, SF-43, SF-13 |
| Simulate Order Payment | SF-4 (SF-40…42) | In Progress | Jira parent đang In Progress; chưa có implementation trong repo |
| Manage Inventory Stock | SF-6 (SF-14…16) | Done | API contract, backend, warehouse UI và verification |
| Update Delivery Status | SF-5 (SF-49…52) | Done | Contract, backend, shared Warehouse/Shop Owner UI và verification |
| Receive Supplier Stock | SF-7 (SF-55…58) | Done | API contract, backend, Warehouse UI và verification |
| Process Customer Return | SF-8 (SF-17…19) | Chưa implement | Trong Sprint 3 |
| Send Low Stock Alert | SF-9 | Chưa implement | Trong Sprint 3 |

Thứ tự triển khai khuyến nghị trong Sprint 3: **SF-3** (order) → **SF-4** (payment) → inventory/operational stories.

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

Frontend API client nằm tại `frontend/src/api`. Product Catalog đã có REST endpoint
thật (`GET /products`, `GET /products/{id}`). Client vẫn dùng Axios + TypeScript
interfaces; có thể thay bằng generated client từ OpenAPI (`/v3/api-docs`) khi cần.

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
