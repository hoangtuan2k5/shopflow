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
├── frontend/            # shopflow-frontend (Vue 3 + Vite, planned)
├── docker-compose.yml   # local development services
├── README.md
└── LICENSE
```

- `backend/`: Spring Boot service, Java 21, Maven, PostgreSQL, Flyway, OpenAPI.
- `frontend/`: Vue 3 + Vite + TypeScript, Tailwind CSS, shadcn-vue, Vue Router, Pinia, TanStack Query. Folder này sẽ được khởi tạo trong phần frontend.
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
- Tailwind CSS + shadcn-vue
- Vue Router, Pinia
- TanStack Query
- VeeValidate + Zod
- ESLint + Prettier

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
./mvnw spring-boot:run
```

Mặc định ứng dụng chạy ở `http://localhost:8080`. Swagger UI tại `http://localhost:8080/swagger-ui.html`.

### Frontend

Frontend folder chưa được khởi tạo trong repository hiện tại. Khi frontend được tạo bằng Vue 3 + Vite, luồng chạy local dự kiến là:

```bash
cd frontend
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

Test profile dùng H2 in-memory và tự chạy Flyway migration trong schema `shopflow`.

### Frontend

| Biến | Mặc định | Mô tả |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080` | Base URL của backend API cho frontend local. |

## Build và test

### Backend

```bash
cd backend
./mvnw test
./mvnw package
```

### Frontend

Frontend chưa được khởi tạo. Khi có `frontend/package.json`, các lệnh CI/local dự kiến là:

```bash
cd frontend
npm install
npm run lint
npm run build
```

## CI pipeline

CI dự kiến chạy trên GitHub Actions với các job độc lập:

- `backend`: chạy Maven build và test trong `backend/`.
- `frontend`: chạy lint và build trong `frontend/` sau khi frontend được khởi tạo.

Branch `main` và `develop` nên yêu cầu Pull Request và CI pass trước khi merge.

## License

MIT License. Xem chi tiết tại [LICENSE](LICENSE).

Copyright (c) 2026 ShopFlow Team.
