# ShopFlow — Product Catalog API Specification

**Phiên bản:** 1.2
**Ngày cập nhật:** 17/07/2026
**Liên quan:**
- [SF-2: Browse Product Catalog](https://tuanwork.atlassian.net/browse/SF-2)
- [SF-36: Define Product Catalog API Contract](https://tuanwork.atlassian.net/browse/SF-36)
- [SF-37: Implement Backend Product Catalog](https://tuanwork.atlassian.net/browse/SF-37)
- [SF-38: Implement Frontend Product Catalog](https://tuanwork.atlassian.net/browse/SF-38)
- [SF-39: Verify Product Catalog](https://tuanwork.atlassian.net/browse/SF-39)
- [Confluence: Product Catalog API Specification](https://tuanwork.atlassian.net/wiki/spaces/SF/pages/2097414)
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

Giá trả về là số nguyên theo đơn vị VND; API không nhận hoặc phát sinh phần lẻ.

| Thuộc tính | Giá trị |
|---|---|
| Base URL | `http://localhost:8080` |
| Content-Type | `application/json` |
| Currency | `VND` |

---

## 2. GET /products

Trả về danh sách product đang active, kèm trạng thái còn hàng.

**Response:** `200 OK`

```json
[
  {
    "id": 1,
    "name": "iPhone 15",
    "price": 19990000,
    "stockStatus": "IN_STOCK"
  },
  {
    "id": 2,
    "name": "MacBook Air M4",
    "price": 29990000,
    "stockStatus": "OUT_OF_STOCK"
  }
]
```

**Response fields:**

| Field | Type | Source | Notes |
|---|---|---|---|
| `id` | `number` (long) | `products.id` | BIGSERIAL PK |
| `name` | `string` | `products.name` | VARCHAR(255), NOT NULL |
| `price` | `number` | `products.price` | NUMERIC(12,2), ≥ 0, số nguyên VND |
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
  "stockStatus": "IN_STOCK"
}
```

**Response fields:**

| Field | Type | Source | Notes |
|---|---|---|---|
| `id` | `number` (long) | `products.id` | |
| `name` | `string` | `products.name` | |
| `description` | `string` or `null` | `products.description` | TEXT, nullable |
| `price` | `number` | `products.price` | NUMERIC(12,2), số nguyên VND |
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

Package: `dev.hoangtuan.shopflow.catalog`

Expected structure:

```
catalog/
├── CatalogController.java   # @RestController, GET /products, GET /products/{id}
├── ProductDto.java          # Response DTO (record or class)
├── ProductService.java      # Business logic + stockStatus computation
├── ProductRepository.java   # JPA Repository querying active products
└── package-info.java        # Đã có: "Catalog feature: hiển thị product cho khách hàng..."
```

**Key conventions:**
- Google Java Format (Spotless, indent 2 spaces)
- Lombok for getters/setters/builder
- MapStruct for entity → DTO mapping
- Constructor injection for dependencies
- Return `Optional<Product>` → map to 200 or throw 404
- `@Tag(name = "Catalog")` cho Swagger grouping

**Projection query (JPA):**

```java
@Query("SELECT p FROM Product p WHERE p.active = true")
List<Product> findAllActive();

@Query("SELECT p FROM Product p WHERE p.id = :id AND p.active = true")
Optional<Product> findActiveById(@Param("id") Long id);
```

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
}

interface ProductDetail extends ProductListItem {
  description: string | null
}

export function getProducts() {
  return request<ProductListItem[]>({ method: 'GET', url: '/products' })
}

export function getProductById(id: number) {
  return request<ProductDetail>({ method: 'GET', url: `/products/${id}` })
}
```

**UI components:**
- Catalog list: shadcn-vue `Card` components, `OUT_OF_STOCK` marked with red badge
- Product detail: dialog/modal or dedicated view showing full description
- TanStack Query `useQuery` for data fetching
- Loading & error states handled via `ApiClientError`

---

## 7. Test Cases (SF-39)

| # | Scenario | Expected |
|---|---|---|
| 1 | GET /products khi có product active | 200, danh sách chỉ gồm product active |
| 2 | GET /products khi không có product nào active | 200, mảng rỗng `[]` |
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
| 1.2 | 2026-07-17 | Chuyển currency baseline sang VND và quy định price là số nguyên |
| 1.1 | 2026-07-17 | Clarify USD as the single MVP currency |
| 1.0 | 2026-06-15 | Initial API spec: GET /products, GET /products/{id}, stockStatus spec |
