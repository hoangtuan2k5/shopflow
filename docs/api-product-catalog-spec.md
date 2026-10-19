# ShopFlow — Product Catalog API Specification

**Phiên bản:** 1.5
**Ngày cập nhật:** 17/10/2026
**Liên quan:**
- [SF-2: Browse Product Catalog](https://tuanwork.atlassian.net/browse/SF-2)
- [SF-36: Define Product Catalog API Contract](https://tuanwork.atlassian.net/browse/SF-36)
- [SF-37: Implement Backend Product Catalog](https://tuanwork.atlassian.net/browse/SF-37)
- [SF-38: Implement Frontend Product Catalog](https://tuanwork.atlassian.net/browse/SF-38)
- [SF-39: Verify Product Catalog](https://tuanwork.atlassian.net/browse/SF-39)
- [`database-schema.md`](./database-schema.md) — Database schema reference
- [`database-schema.sql`](./database-schema.sql) — DDL PostgreSQL

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [GET /products](#2-get-products)
3. [GET /products/{id}](#3-get-productsid)
4. [Cách tính stockStatus](#4-cách-tính-stockstatus)
5. [Database Reference](#5-database-reference)
6. [Implementation Notes](#6-implementation-notes)
7. [Test Cases](#7-test-cases)
8. [Version History](#8-version-history)

---

## 1. Tổng quan

Cung cấp API read-only cho Product Catalog — khách hàng xem danh sách sản phẩm đang bán và chi tiết từng sản phẩm.

Tài liệu này là API contract được duy trì trong repository. Yêu cầu nghiệp vụ nằm
trong [SRS](./SRS.md) (FR-01); kết quả kiểm tra và lệnh chạy nằm trong
[QA Product Catalog](./qa-product-catalog.md).

Hai endpoint đều công khai, không yêu cầu đăng nhập hay CSRF token và không
thay đổi dữ liệu. Backend local dùng URL bên dưới; qua frontend production,
thêm prefix `/api` (ví dụ `GET /api/products`).

SF-90 triển khai `imageUrl: string | null` cho list/detail; xem [Product Image API](./api-product-image-spec.md). Thiếu ảnh
trả `null`, frontend dùng placeholder; các field hiện có giữ nguyên.

Giá trả về là số nguyên theo đơn vị VND; API không nhận hoặc phát sinh phần lẻ.

| Thuộc tính | Giá trị |
|---|---|
| Base URL | `http://localhost:8080` |
| Content-Type | `application/json` |
| Currency | `VND` |

---

## 2. GET /products

Trả về từng đợt product đang active, kèm trạng thái còn hàng (SF-127).

| Query parameter | Quy tắc |
|---|---|
| `limit` | Số nguyên từ 1 đến 100; mặc định 20 |
| `after` | ID cuối đợt trước, số nguyên long không âm; mặc định 0 |
| `q` | Tìm chuỗi con trong tên, tối đa 255 ký tự; bỏ khoảng trắng đầu/cuối và không phân biệt hoa/thường; không bỏ dấu. `%`, `_`, `!` là ký tự thường, không phải wildcard |

Ví dụ: `GET /products?limit=20&after=56&q=sharp`.

Response đổi từ mảng sang `{ items, nextCursor }`; backend và frontend phải triển khai cùng phiên bản. `nextCursor` là ID dạng chuỗi hoặc `null` khi hết dữ liệu. Mỗi đợt sắp xếp ID tăng dần, chỉ lấy ID lớn hơn `after`; truy vấn database giới hạn `limit + 1` dòng để xác định còn dữ liệu, không cần COUNT toàn catalog. Tham số sai kiểu, ngoài giới hạn hoặc `q` quá dài trả `400` với `{ "message": "Invalid catalog query parameters", "status": 400 }`.

Không có snapshot xuyên các đợt: đổi tên, trạng thái active hoặc thêm sản phẩm trong lúc cuộn có thể thay đổi kết quả; thứ tự ID không thay đổi. Khi đổi từ khóa, bắt đầu lại với `after=0`.

**Response:** `200 OK`

```json
{
  "items": [
    {
      "id": 1,
      "name": "iPhone 15",
      "price": 19990000,
      "stockStatus": "IN_STOCK",
      "imageUrl": null
    },
    {
      "id": 2,
      "name": "MacBook Air M4",
      "price": 29990000,
      "stockStatus": "OUT_OF_STOCK",
      "imageUrl": null
    }
  ],
  "nextCursor": null
}
```

**Response fields:**

| Field | Type | Source | Notes |
|---|---|---|---|
| `id` | `number` (long) | `products.id` | BIGSERIAL PK |
| `name` | `string` | `products.name` | VARCHAR(255), NOT NULL |
| `price` | `number` | `products.price` | NUMERIC(12,2), ≥ 0, số nguyên VND |
| `imageUrl` | `string` or `null` | `products.image_key` | URL đọc ảnh R2; null nếu chưa có |
| `stockStatus` | `string` enum | Computed | `IN_STOCK` hoặc `OUT_OF_STOCK` |

**Query behavior:**
- Chỉ trả về product có `active = true`
- Product bị soft-delete (`active = false`) không xuất hiện

---

## 3. GET /products/{id}

Trả về chi tiết 1 product.

**Path parameters:**

| Param | Type | Notes |
|---|---|---|
| `id` | `number` (long) | Product ID |

**Response:** `200 OK`

```json
{
  "id": 1,
  "name": "iPhone 15",
  "description": "Apple iPhone 15 128GB — phiên bản mới nhất với Dynamic Island, cổng USB-C và camera 48MP.",
  "price": 19990000,
  "stockStatus": "IN_STOCK",
  "imageUrl": null
}
```

**Response fields:**

| Field | Type | Source | Notes |
|---|---|---|---|
| `id` | `number` (long) | `products.id` | |
| `name` | `string` | `products.name` | |
| `description` | `string` or `null` | `products.description` | TEXT, nullable |
| `price` | `number` | `products.price` | NUMERIC(12,2), số nguyên VND |
| `imageUrl` | `string` or `null` | `products.image_key` | URL đọc ảnh R2; null nếu chưa có |
| `stockStatus` | `string` enum | Computed | `IN_STOCK` hoặc `OUT_OF_STOCK` |

**Error responses:**

| Status | Case |
|---|---|
| `404 Not Found` | Product không tồn tại hoặc `active = false` |

```json
{
  "message": "Product not found",
  "status": 404
}
```

---

## 4. Cách tính stockStatus

`stockStatus` được **tính runtime**, không lưu trong DB.

Query từ bảng `inventory_items`:

```sql
available_stock = on_hand_stock - reserved_stock
```

| available_stock | stockStatus |
|---|---|
| > 0 | `IN_STOCK` |
| 0 hoặc < 0 | `OUT_OF_STOCK` |

> **Lưu ý:** Nếu product chưa có record trong `inventory_items`, mặc định `available_stock = 0` → `OUT_OF_STOCK`.

---

## 5. Database Reference

### Schema: `shopflow`

**`products` table** (V1__init.sql):

| Column | Type | Constraints |
|---|---|---|
| `id` | BIGSERIAL | PK |
| `name` | VARCHAR(255) | NOT NULL |
| `description` | TEXT | |
| `price` | NUMERIC(12,2) | NOT NULL, CHECK (price >= 0), số nguyên VND |
| `active` | BOOLEAN | NOT NULL, DEFAULT TRUE |
| `low_stock_threshold` | INT | |
| `created_at` | TIMESTAMPTZ | NOT NULL |
| `updated_at` | TIMESTAMPTZ | NOT NULL |

**Index:** `idx_products_active` ON `products(active)` — tối ưu catalog query.

**`inventory_items` table:**

| Column | Type | Constraints |
|---|---|---|
| `product_id` | BIGINT | FK UNIQUE NOT NULL → 1-1 với products |
| `on_hand_stock` | INT | NOT NULL DEFAULT 0, CHECK (>= 0) |
| `reserved_stock` | INT | NOT NULL DEFAULT 0, CHECK (>= 0) |

**Constraint:** `CHECK (reserved_stock <= on_hand_stock)`

---

## 6. Implementation Notes

### Backend (SF-37)

Cấu trúc sau refactor SF-130, dưới `dev.hoangtuan.shopflow`:

```text
controller/CatalogController.java       # GET /products, GET /products/{id}
service/ProductService.java             # Orchestration và response mapping
repository/jpa/ProductRepository.java   # Truy vấn catalog và projection tồn kho
model/catalog/Product.java             # JPA entity
dto/catalog/ProductDto.java            # Response records
exception/handler/CatalogErrorHandler.java
```

**Key conventions:**
- Google Java Format (Spotless, indent 2 spaces).
- Constructor injection; controller gọi service, service gọi repository.
- DTO là Java records, mapping thuần; không gọi DB từ DTO/mapper.
- `Optional` từ repository được chuyển thành response hoặc 404 theo contract.
- `@Tag(name = "Catalog")` cho Swagger grouping.
- Xem [kiến trúc backend](architecture/backend.md) để biết trách nhiệm từng tầng.

**Truy vấn phân trang (SF-127):** `ProductRepository.findActivePage(after, pattern, pageable)` dùng native SQL với `p.active = TRUE`, `p.id > :after`, `LOWER(p.name) LIKE :pattern ESCAPE '!'`, join tồn kho và `ORDER BY p.id`. `PageRequest.of(0, limit + 1)` giới hạn kết quả ở database; service chỉ trả tối đa `limit` item và cursor từ item cuối nếu có dòng dư.

### Frontend (SF-38)

Base: `import { request } from '@/api/httpClient'`

Expected:

```typescript
// src/api/catalog.ts
interface ProductListItem {
  id: number
  name: string
  price: number
  stockStatus: 'IN_STOCK' | 'OUT_OF_STOCK'
  imageUrl: string | null
}

interface ProductDetail extends ProductListItem {
  description: string | null
}

interface ProductPage {
  items: ProductListItem[]
  nextCursor: string | null
}

// getProducts({ after, q, signal }) gọi qua httpClient, limit=20,
// và validateProductPrice cho từng item.


export function getProductById(id: number) {
  return request<ProductDetail>({ method: 'GET', url: `/products/${id}` })
}
```

**UI components:**
- Catalog list: shadcn-vue `Card` components, `OUT_OF_STOCK` marked with red badge
- Product detail: dialog/modal or dedicated view showing full description
- TanStack Query `useInfiniteQuery` cho danh sách; `useQuery` cho chi tiết. Cache danh sách dùng `['products', 'pages', q]`, khác cache chi tiết.
- Storefront tự tải khi gần cuối và có nút tải thêm; chủ shop dùng nút tải thêm. Lỗi tải tiếp giữ các đợt đã tải và chỉ thử lại khi người dùng yêu cầu.
- Tìm kiếm debounce 300 ms và bắt đầu đợt đầu khi đổi từ khóa; giỏ hàng giữ thông tin món độc lập với danh sách đang tìm.
- Số đếm chỉ thể hiện các sản phẩm đã tải, không giả định đây là tổng catalog.
- Loading & error states handled via `ApiClientError`

---

## 7. Test Cases (SF-39)

| # | Scenario | Expected |
|---|---|---|
| 1 | GET /products khi có product active | 200, danh sách chỉ gồm product active |
| 2 | GET /products khi không có product nào active | 200, `{ "items": [], "nextCursor": null }` |
| 3 | GET /products có product hết hàng | Item có `stockStatus: "OUT_OF_STOCK"` |
| 4 | GET /products/{id} với id tồn tại & active | 200, đầy đủ fields kể cả description |
| 5 | GET /products/{id} với id không tồn tại | 404 |
| 6 | GET /products/{id} với id của product `active=false` | 404 |
| 7 | stockStatus = IN_STOCK khi available > 0 | Trả về `IN_STOCK` |
| 8 | stockStatus = OUT_OF_STOCK khi available = 0 | Trả về `OUT_OF_STOCK` |

---

## 8. Version History

| Version | Date | Changes |
|---|---|---|
| 1.5 | 2026-10-17 | Phân trang cursor, tìm kiếm server, response items/nextCursor và giới hạn query (SF-127) |
| 1.4 | 2026-10-16 | Bổ sung contract imageUrl nullable cho list/detail (SF-89) |
| 1.3 | 2026-10-11 | Duy trì contract trong repository; bổ sung quyền truy cập và liên kết SRS/QA (SF-125) |
| 1.2 | 2026-07-17 | Chuyển currency baseline sang VND và quy định price là số nguyên |
| 1.1 | 2026-07-17 | Clarify USD as the single MVP currency |
| 1.0 | 2026-06-15 | Initial API spec: GET /products, GET /products/{id}, stockStatus spec |
