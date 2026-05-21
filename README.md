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
├── backend/    # shopflow-backend (Spring Boot)
├── frontend/   # shopflow-frontend (Vue 3 + Vite)
├── README.md
└── LICENSE
```

- `backend/`: Spring Boot service, Java 21, Maven, PostgreSQL, Flyway, OpenAPI.
- `frontend/`: Vue 3 + Vite + TypeScript, Tailwind CSS, shadcn-vue, Vue Router, Pinia, TanStack Query.

## Tech stack

- **Backend:** Spring Boot & Spring
- **Frontend:** Vue.js
- **Database:** PostgreSQL, H2 Database
- **Infrastructure & Cloud:** Cloudflare, Amazon S3, Oracle Cloud
- **Tooling:** Git/GitHub, Postman, Jira

### Backend

- Java 21 LTS
- Spring Boot 3.3.x
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

### Backend

```bash
cd backend
./mvnw spring-boot:run
```

Mặc định ứng dụng chạy ở `http://localhost:8080`. Swagger UI tại `http://localhost:8080/swagger-ui.html`.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Mặc định FE chạy ở `http://localhost:5173` và gọi tới backend qua biến môi trường `VITE_API_BASE_URL` trong `frontend/.env.local`.

## License

MIT License. Xem chi tiết tại [LICENSE](LICENSE).

Copyright (c) 2026 ShopFlow Team.
