# Authorization QA Checklist

**Ngày kiểm tra:** 26/07/2026

**Scope:** ma trận quyền trong [`api-authorization-spec.md`](./api-authorization-spec.md),
phiên đăng nhập của SF-72, CSRF và guard điều hướng phía frontend.

## Backend scenarios

| # | Scenario | Evidence | Kết quả |
| --- | --- | --- | --- |
| 1 | Cả 10 endpoint vận hành trả `401` khi gọi ẩn danh | `AuthorizationTests.refusesEveryOperationalEndpointToAnonymousCallers` (parameterized ×10) | Pass |
| 2 | Cả 10 endpoint vận hành trả `403` với vai trò `CUSTOMER` | `refusesEveryOperationalEndpointToCustomers` (×10) | Pass |
| 3 | `GET /returns/orders` và `POST /returns` trả `403` với vai trò kho | `keepsCommercialReturnDecisionsAwayFromTheWarehouse` (×2) | Pass |
| 4 | Kho chuyển return sang `RESTOCKED` thành công nhưng `APPROVED`/`REJECTED` trả `403`; chủ shop làm được cả ba; trạng thái return không đổi sau mỗi lần bị chặn | `letsTheWarehouseRestockButNotApproveOrRejectAReturn` | Pass |
| 5 | Request bị chặn không đổi tồn kho và không sinh `stock_movements` | `leavesDataUntouchedWhenAuthorizationRejectsTheRequest` | Pass |
| 6 | Request thay đổi trạng thái thiếu CSRF token bị từ chối dù đúng vai trò | `rejectsStateChangingRequestsThatCarryNoCsrfToken` | Pass |
| 7 | Catalog, `/actuator/health` và `/auth/session` mở cho ẩn danh | `keepsGuestShoppingAndHealthChecksOpen` | Pass |
| 8 | Kho và chủ shop cùng vào được tồn kho, giao hàng, danh sách đổi trả | `letsBothOperationalRolesReachSharedWarehouseScreens` | Pass |
| 9 | Tám luồng nghiệp vụ MVP vẫn chạy đúng sau khi bật phân quyền | Toàn bộ controller test cũ, chạy kèm ngữ cảnh vai trò tương ứng | Pass |

Phân biệt `401` và `403` là điểm dễ trượt: mặc định của Spring khi không khai
báo cơ chế đăng nhập nào là trả `403` cho cả người chưa đăng nhập. Scenario 1
đã bắt đúng sai lệch này trước khi merge; `HttpStatusEntryPoint` được thêm để
sửa.

## Browser scenarios

Playwright mock toàn bộ response API; không test nào đọc hay sửa dữ liệu thật.

| Scenario | Evidence | Kết quả |
| --- | --- | --- |
| Khách ẩn danh mở đường dẫn vận hành bị đưa tới trang đăng nhập, đăng nhập xong quay lại đúng nơi định đến | `login.spec.ts` — anonymous visitor | Pass |
| Nhân viên kho mở đường dẫn của chủ shop bị đưa về trang của mình, và không thấy link tới đó trong sidebar | `login.spec.ts` — warehouse staff | Pass |
| Chủ shop vào được màn hình kho vì vai trò bao trùm | `login.spec.ts` — shop owner | Pass |
| Storefront vẫn mở cho khách vãng lai, không bị chuyển hướng | `login.spec.ts` — storefront | Pass |
| Phiên hết hạn giữa chừng đưa người dùng về trang đăng nhập thay vì kẹt ở màn hình lỗi | `login.spec.ts` — expired session | Pass |

## Commands và kết quả

```bash
cd backend && bash ./mvnw -B spotless:check clean verify
# Tests run: 107, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS

cd frontend && npm run type-check && npm run lint:ci && npm run build
npx playwright test
# 28 passed
```

## Giới hạn đã biết

- `GET /deliveries` vẫn trả `totalAmount` và thông tin người nhận cho nhân viên
  kho. Ghi nhận ở mục 8 của contract; cách xử lý đúng là cắt bớt projection chứ
  không chặn endpoint, chưa làm trong phạm vi này.
- Kiểm thử phân quyền chạy trên H2 qua MockMvc. Quy tắc phân quyền không phụ
  thuộc DBMS nên không cần chạy lại trên PostgreSQL.
