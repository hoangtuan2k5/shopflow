# ShopFlow — Customer Order API Specification

**Phiên bản:** 1.0
**Ngày lập:** 13/07/2026
**Liên quan:**
- [SF-3: Create Customer Order](https://tuanwork.atlassian.net/browse/SF-3)
- [SF-44: Define Customer Order API Contract](https://tuanwork.atlassian.net/browse/SF-44)
- [SF-12: Define Order Data Model](https://tuanwork.atlassian.net/browse/SF-12)
- [SF-11: Implement Stock Validation and Reservation](https://tuanwork.atlassian.net/browse/SF-11)
- [SF-35: Implement Order Creation Flow](https://tuanwork.atlassian.net/browse/SF-35)
- [SF-43: Implement Order Creation Frontend](https://tuanwork.atlassian.net/browse/SF-43)
- [SF-13: Verify Customer Order Scenarios](https://tuanwork.atlassian.net/browse/SF-13)
- [Confluence: Customer Order API Specification](https://tuanwork.atlassian.net/wiki/spaces/SF/pages/9109505)
- [`database-schema.md`](./database-schema.md) — Database schema reference
- [`database-schema.sql`](./database-schema.sql) — DDL PostgreSQL
- [`SRS.md`](./SRS.md) — FR-02, BR-01 đến BR-04, BR-15
- [`api-product-catalog-spec.md`](./api-product-catalog-spec.md) — Product Catalog (read model trước khi đặt hàng)

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [POST /orders](#2-post-orders)
3. [Stock validation và reservation](#3-stock-validation-và-reservation)
4. [Concurrency](#4-concurrency)
5. [Error responses](#5-error-responses)
6. [Database Reference](#6-database-reference)
7. [Implementation Notes](#7-implementation-notes)
8. [Test Cases](#8-test-cases)
9. [Version History](#9-version-history)

---

## 1. Tổng quan

Cung cấp API tạo customer order — khách hàng gửi danh sách sản phẩm và thông tin
giao hàng; hệ thống kiểm tra available stock, tạo order `PENDING_PAYMENT`, snapshot
giá/tên, và reserve inventory theo quantity (all-or-nothing).

| Thuộc tính | Giá trị |
|---|---|
| Base URL | `http://localhost:8080` |
| Content-Type | `application/json` |
| Phạm vi | Create order only (`POST /orders`). Payment, delivery, cancel ngoài scope SF-3/SF-44. |

**Quy tắc nghiệp vụ cốt lõi (FR-02):**

- Request phải có customer name, receiver name/phone, address line, city, và ít nhất một item `quantity > 0`.
- Email, customer phone, district là optional.
- Trước khi persist: kiểm tra available stock cho **mọi** item.
- Thiếu stock ở bất kỳ item nào → **không** tạo order, order item, reservation, hay stock movement.
- Thành công → order `PENDING_PAYMENT`, `delivery_status = NONE`, reserve đúng quantity trong cùng transaction.
- Snapshot `product_name`, `unit_price`, customer và shipping fields tại thời điểm tạo.
- `totalAmount` = `SUM(unitPrice × quantity)` của các order item.
- Response tối thiểu có order identifier và status để tiếp tục payment (SF-4).

---

## 2. POST /orders

Tạo order mới từ danh sách product và thông tin giao hàng.

### Request

**Body:**

```json
{
  "customer": {
    "fullName": "Nguyen Van A",
    "email": "a@example.com",
    "phone": "0901234567"
  },
  "shippingAddress": {
    "receiverName": "Nguyen Van A",
    "phone": "0901234567",
    "addressLine": "123 Nguyen Hue",
    "district": "District 1",
    "city": "Ho Chi Minh"
  },
  "paymentMethod": "CARD",
  "items": [
    {
      "productId": 1,
      "quantity": 2
    },
    {
      "productId": 3,
      "quantity": 1
    }
  ]
}
```

**Request fields:**

| Field | Type | Required | Notes |
|---|---|---|---|
| `customer` | object | yes | Snapshot khách hàng lúc đặt |
| `customer.fullName` | string | yes | → `orders.customer_name`; non-blank |
| `customer.email` | string or omit/null | no | → `orders.customer_email` |
| `customer.phone` | string or omit/null | no | → `orders.customer_phone` |
| `shippingAddress` | object | yes | Địa chỉ giao |
| `shippingAddress.receiverName` | string | yes | → `orders.receiver_name`; non-blank |
| `shippingAddress.phone` | string | yes | → `orders.receiver_phone`; non-blank |
| `shippingAddress.addressLine` | string | yes | → `orders.address_line`; non-blank |
| `shippingAddress.district` | string or omit/null | no | → `orders.district` |
| `shippingAddress.city` | string | yes | → `orders.city`; non-blank |
| `paymentMethod` | string enum | yes | `CARD` hoặc `COD` → `orders.payment_method`. MVP payment simulation (SF-4) dùng `CARD`. `COD` được lưu trữ theo schema; quy tắc COD success không thuộc baseline SF-3. |
| `items` | array | yes | Ít nhất 1 phần tử |
| `items[].productId` | number (long) | yes | `products.id`; mỗi `productId` chỉ xuất hiện **một lần** trong `items` |
| `items[].quantity` | number (int) | yes | Integer `> 0` |

**Không chấp nhận từ client (server tự tính / snapshot):**

- `unitPrice`, `productName`, `totalAmount`
- `status`, `deliveryStatus`, order `id`
- `customerId` (MVP: guest checkout; `orders.customer_id` = null)

### Response: `201 Created`

```json
{
  "id": 42,
  "status": "PENDING_PAYMENT",
  "deliveryStatus": "NONE",
  "paymentMethod": "CARD",
  "totalAmount": 3098.98,
  "customer": {
    "fullName": "Nguyen Van A",
    "email": "a@example.com",
    "phone": "0901234567"
  },
  "shippingAddress": {
    "receiverName": "Nguyen Van A",
    "phone": "0901234567",
    "addressLine": "123 Nguyen Hue",
    "district": "District 1",
    "city": "Ho Chi Minh"
  },
  "items": [
    {
      "productId": 1,
      "productName": "iPhone 15",
      "unitPrice": 999.99,
      "quantity": 2,
      "lineTotal": 1999.98
    },
    {
      "productId": 3,
      "productName": "AirPods Pro",
      "unitPrice": 1099.00,
      "quantity": 1,
      "lineTotal": 1099.00
    }
  ],
  "createdAt": "2026-07-13T10:15:30.000Z"
}
```

**Response fields:**

| Field | Type | Source | Notes |
|---|---|---|---|
| `id` | number (long) | `orders.id` | BIGSERIAL PK; dùng làm order identifier cho payment |
| `status` | string enum | `orders.status` | Luôn `PENDING_PAYMENT` khi create thành công |
| `deliveryStatus` | string enum | `orders.delivery_status` | Luôn `NONE` khi create |
| `paymentMethod` | string enum | `orders.payment_method` | `CARD` hoặc `COD` |
| `totalAmount` | number | `orders.total_amount` | NUMERIC(12,2); = sum line totals |
| `customer.*` | object | snapshot columns | Echo thông tin đã lưu |
| `shippingAddress.*` | object | snapshot columns | Echo thông tin đã lưu; `district` có thể `null` |
| `items[].productId` | number (long) | `order_items.product_id` | |
| `items[].productName` | string | `order_items.product_name` | Snapshot từ `products.name` |
| `items[].unitPrice` | number | `order_items.unit_price` | Snapshot từ `products.price` |
| `items[].quantity` | number (int) | `order_items.quantity` | |
| `items[].lineTotal` | number | computed | `unitPrice * quantity` (không lưu cột riêng) |
| `createdAt` | string (ISO-8601) | `orders.created_at` | UTC |

**Side effects khi 201 (cùng một DB transaction):**

1. Insert `orders` (`status = PENDING_PAYMENT`, `delivery_status = NONE`).
2. Insert `order_items` với snapshot name/price.
3. Với mỗi item: `inventory_items.reserved_stock += quantity`.
4. Với mỗi item: insert `stock_movements` (`type = ORDER_RESERVED`, `reference_type = ORDER`, `reference_id = order.id`).
5. **Không** giảm `on_hand_stock` (BR-04).

---

## 3. Stock validation và reservation

### Available stock

Tính runtime (không lưu DB), giống catalog:

```text
available_stock = on_hand_stock - reserved_stock
```

| Điều kiện | Ý nghĩa |
|---|---|
| Product `active = true` và có `inventory_items` | Dùng công thức trên |
| Product không tồn tại | Invalid product → reject request |
| Product `active = false` | Coi như không bán → reject request |
| Không có row `inventory_items` | `available_stock = 0` → insufficient nếu `quantity > 0` |

### Validation order (trước khi ghi)

1. Validate schema/body (required fields, `quantity > 0`, `paymentMethod` enum, không duplicate `productId`, `items` non-empty).
2. Load products + inventory cho mọi `productId` trong request.
3. Reject nếu bất kỳ product missing/inactive.
4. Reject nếu bất kỳ item có `quantity > available_stock` (liệt kê **tất cả** item thiếu trong một response — FR-02.8).
5. Chỉ khi tất cả pass → persist order + reserve trong **một transaction**.

### Atomicity (BR-03)

Order nhiều item: fail một item → fail toàn bộ request. Không partial order, không partial reserve.

---

## 4. Concurrency

**Expectation (chống over-reservation):**

Hai (hoặc nhiều) `POST /orders` đồng thời cùng tranh available stock của một product **không** được làm `reserved_stock` vượt quá `on_hand_stock`, và không được tạo order khi sau khi khóa dữ liệu stock không còn đủ.

**Cách đáp ứng expectation (bắt buộc cho implementer SF-11 / SF-35):**

1. Toàn bộ validate-final + insert order/items + tăng `reserved_stock` + insert `ORDER_RESERVED` movements chạy trong **một database transaction**.
2. Khóa các row `inventory_items` liên quan trước khi đọc available stock lần cuối, ví dụ `SELECT … FOR UPDATE` theo `product_id` (hoặc `UPDATE … WHERE … AND (on_hand_stock - reserved_stock) >= :qty` với kiểm tra row count).
3. Nếu sau khi khóa, stock không đủ → rollback transaction, trả error insufficient stock (giống case tuần tự).
4. Commit chỉ khi mọi item đã reserve thành công.

**Không chấp nhận:** đọc stock không khóa, rồi insert order, rồi update reserved — khoảng trống giữa read và write cho phép over-reservation.

> Ghi chú: SF-11 từng nêu limitation MVP về race. Contract này **chốt expectation** phải chống over-reservation; implementation SF-11/SF-35 bám section này.

---

## 5. Error responses

Error shape thống nhất với catalog (`message` + `status`). Một số case bổ sung field chi tiết.

```json
{
  "message": "…",
  "status": 400
}
```

### 5.1 Validation / bad request — `400 Bad Request`

| Case | `message` (ví dụ) | Chi tiết thêm |
|---|---|---|
| Missing/blank required field | `Invalid order request` | Có thể kèm mô tả field trong `message` |
| `quantity` ≤ 0 hoặc không phải integer dương | `Invalid item quantity` | |
| `items` rỗng | `Order must contain at least one item` | |
| Duplicate `productId` trong `items` | `Duplicate productId in items` | |
| `paymentMethod` không thuộc `CARD`/`COD` | `Invalid payment method` | |
| Product không tồn tại hoặc inactive | `Product not available` | `unavailableProductIds: number[]` |
| Insufficient available stock | `Insufficient stock` | `insufficientItems` (xem dưới) |

**Insufficient stock example:**

```json
{
  "message": "Insufficient stock",
  "status": 400,
  "insufficientItems": [
    {
      "productId": 2,
      "requestedQuantity": 5,
      "availableStock": 1
    }
  ]
}
```

| Field | Notes |
|---|---|
| `insufficientItems[].productId` | Product không đủ |
| `insufficientItems[].requestedQuantity` | Quantity client yêu cầu |
| `insufficientItems[].availableStock` | Available tại thời điểm validate (sau lock nếu concurrent) |

HTTP **400** cho insufficient stock (khớp sequence diagram trong `database-schema.md` §14.1). Không dùng 409/422 trong MVP contract này.

### 5.2 Malformed JSON — `400 Bad Request`

Body không parse được JSON: framework default hoặc:

```json
{
  "message": "Malformed request body",
  "status": 400
}
```

### 5.3 Không có `404` cho create flow

`POST /orders` không trả 404. Product missing/inactive gộp vào **400** + `unavailableProductIds` để multi-item request có một semantics thống nhất.

---

## 6. Database Reference

### Schema: `shopflow`

**`orders` (create path):**

| Column | Type | Value khi create |
|---|---|---|
| `id` | BIGSERIAL | Generated |
| `customer_id` | BIGINT nullable | `NULL` (guest MVP) |
| `customer_name` | VARCHAR(255) | `customer.fullName` |
| `customer_phone` | VARCHAR(20) | `customer.phone` hoặc null |
| `customer_email` | VARCHAR(255) | `customer.email` hoặc null |
| `receiver_name` | VARCHAR(255) | `shippingAddress.receiverName` |
| `receiver_phone` | VARCHAR(20) | `shippingAddress.phone` |
| `address_line` | VARCHAR(500) | `shippingAddress.addressLine` |
| `district` | VARCHAR(100) | `shippingAddress.district` hoặc null |
| `city` | VARCHAR(100) | `shippingAddress.city` |
| `status` | VARCHAR(30) | `PENDING_PAYMENT` |
| `delivery_status` | VARCHAR(30) | `NONE` |
| `payment_method` | VARCHAR(20) | `CARD` hoặc `COD` |
| `total_amount` | NUMERIC(12,2) | Computed sum |
| `created_at` / `updated_at` | TIMESTAMPTZ | Now |

**`order_items`:**

| Column | Type | Value |
|---|---|---|
| `order_id` | BIGINT | FK → new order |
| `product_id` | BIGINT | Request `productId` |
| `product_name` | VARCHAR(255) | Snapshot `products.name` |
| `unit_price` | NUMERIC(12,2) | Snapshot `products.price` |
| `quantity` | INT | Request `quantity` (> 0) |

**`inventory_items`:**

| Column | Change |
|---|---|
| `reserved_stock` | `+= quantity` per product |
| `on_hand_stock` | unchanged |

Constraint: `reserved_stock <= on_hand_stock` vẫn phải giữ sau update.

**`stock_movements`:**

| Column | Value |
|---|---|
| `product_id` | Item product |
| `type` | `ORDER_RESERVED` |
| `quantity` | Reserved qty (positive) |
| `reference_type` | `ORDER` |
| `reference_id` | `orders.id` |

### Lệch model đã chốt

| Nguồn | Ghi chú | Quyết định contract |
|---|---|---|
| SF-12: Order id UUID | Schema dùng BIGSERIAL | **API dùng `number` (long)** theo schema |
| AC text "Pending Payment" | Enum DB/SRS: `PENDING_PAYMENT` | **API string `PENDING_PAYMENT`** |
| SF-12 field names (embedded objects) | JSON camelCase | Map như bảng request/response ở trên |

---

## 7. Implementation Notes

### Backend (SF-11, SF-12, SF-35)

Package: `dev.hoangtuan.shopflow.order`

Expected structure:

```text
order/
├── OrderController.java          # @RestController, POST /orders
├── CreateOrderRequest.java       # Request DTO (+ nested records)
├── OrderResponse.java            # Response DTO
├── OrderService.java             # Orchestration: validate → reserve → persist
├── StockReservationService.java  # Optional extract: lock + available check + reserve (SF-11)
├── OrderRepository.java
├── OrderItemRepository.java
├── OrderErrorHandler.java        # 400 shape
└── package-info.java             # Đã có
```

**Key conventions (khớp catalog):**

- Google Java Format (Spotless, indent 2 spaces)
- Constructor injection
- `@Transactional` trên use-case create
- `@Tag(name = "Order")` cho Swagger
- MapStruct/Lombok theo pattern module catalog nếu đã có entity mapping
- Không tin client `unitPrice` / `productName`

**Pseudo-flow:**

```text
@Transactional
create(request):
  validateRequest(request)
  lockInventoryRows(productIds)          // FOR UPDATE
  products = loadActiveProducts(productIds)
  fail if any missing/inactive
  fail if any insufficient available
  order = insert order + items (snapshots)
  for each item: reserved += qty; movement ORDER_RESERVED
  return 201 OrderResponse
```

### Frontend (SF-43)

Base: `import { request } from '@/api/httpClient'`

Expected:

```typescript
// src/api/order.ts
export type PaymentMethod = 'CARD' | 'COD'

export interface CreateOrderRequest {
  customer: {
    fullName: string
    email?: string | null
    phone?: string | null
  }
  shippingAddress: {
    receiverName: string
    phone: string
    addressLine: string
    district?: string | null
    city: string
  }
  paymentMethod: PaymentMethod
  items: Array<{ productId: number; quantity: number }>
}

export interface OrderResponse {
  id: number
  status: 'PENDING_PAYMENT'
  deliveryStatus: 'NONE'
  paymentMethod: PaymentMethod
  totalAmount: number
  customer: {
    fullName: string
    email: string | null
    phone: string | null
  }
  shippingAddress: {
    receiverName: string
    phone: string
    addressLine: string
    district: string | null
    city: string
  }
  items: Array<{
    productId: number
    productName: string
    unitPrice: number
    quantity: number
    lineTotal: number
  }>
  createdAt: string
}

export function createOrder(body: CreateOrderRequest) {
  return request<OrderResponse>({ method: 'POST', url: '/orders', data: body })
}
```

**UI notes:**

- Checkout form: customer + shipping + line items (từ catalog selection)
- Disable submit khi có item `OUT_OF_STOCK` ở catalog (UX); server vẫn là source of truth
- Map `ApiClientError` / 400 `insufficientItems` thành thông báo per product
- Sau 201: giữ `order.id` + `status` để sang payment simulation (SF-4)

### Out of scope

- `GET /orders/{id}`, list orders, cancel order
- Payment endpoints (SF-4)
- Authenticated customer profile / `customer_id` linking
- Admin order management

---

## 8. Test Cases (SF-13)

| # | Scenario | Expected |
|---|---|---|
| 1 | POST hợp lệ, mọi item đủ stock | 201; `status=PENDING_PAYMENT`; `deliveryStatus=NONE`; `totalAmount` đúng; items snapshot name/price |
| 2 | Sau create: `reserved_stock` tăng đúng qty; `on_hand_stock` không đổi | DB assert |
| 3 | Sau create: có `stock_movements` type `ORDER_RESERVED` per item, `reference_id` = order id | DB assert |
| 4 | Một item insufficient stock | 400; `insufficientItems` chứa product đó; **không** có order/items/reserve/movement mới |
| 5 | Nhiều item, chỉ một thiếu stock | 400; liệt kê item thiếu; không partial order |
| 6 | Product id không tồn tại | 400; `unavailableProductIds` |
| 7 | Product `active=false` | 400; `unavailableProductIds` |
| 8 | Product không có inventory row | 400 insufficient (`availableStock=0`) |
| 9 | `quantity` = 0 hoặc âm | 400 |
| 10 | `items` = `[]` | 400 |
| 11 | Duplicate `productId` trong items | 400 |
| 12 | Missing `customer.fullName` hoặc shipping required field | 400 |
| 13 | `paymentMethod` invalid | 400 |
| 14 | Client gửi `unitPrice` (nếu có field lạ) | Ignored hoặc 400 theo binder; server price luôn từ DB |
| 15 | Concurrent two orders exhaust same last unit | Chỉ một 201; order kia 400 insufficient; `reserved_stock <= on_hand_stock` |

---

## 9. Version History

| Version | Date | Changes |
|---|---|---|
| 1.0 | 2026-07-13 | Initial API spec: POST /orders, stock validation/reservation, concurrency expectation, error payloads |
