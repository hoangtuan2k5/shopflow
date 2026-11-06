# Kiểm chứng giữ giỏ qua đăng nhập — SF-147

Ngày 2026-11-06, nhánh `feature/SF-147-cart-login-persistence`, nền develop
`677059a` sau PR #149. Chỉ thay frontend/tài liệu; không sửa backend, API,
SQL, secret hoặc deploy. Giỏ và phiên riêng từng origin theo contract.

## Phạm vi

Pinia giữ ID/số lượng và dữ liệu sản phẩm trong RAM. Chỉ version1 và các cặp
productId/quantity được ghi vào `shopflow.cart.v1`, không có giá, tên, địa chỉ,
account, password/OTP/Google proof/token hay orderRef. Write đồng bộ trước route
transition. ID phải positive safe integer, quantity positive integer ≤2147483647;
không ép chuỗi, cắt số lẻ hay wrap overflow. JSON tối đa64 KiB theo UTF-8.

Mỗi lần mount catalog lấy lại chi tiết đúng các ID đã chọn, không tải cả catalog
hoặc tin giá localStorage. Lỗi API/deleted/out-of-stock giữ ID/số lượng và hiện
vấn đề; checkout bị chặn tới khi xóa/cập nhật được. Request đang khôi phục không
hồi sinh item đã xóa. Server vẫn quyết định giá/tồn khi đặt đơn.

Storage hỏng/bị chặn/quota đầy không crash; cart RAM vẫn dùng được với cảnh báo
ở catalog/cart/login. Failed save thử xóa snapshot cũ để không phục hồi giỏ khác
một cách im lặng. Không hứa giữ qua reload/Google khi storage không khả dụng.

Intent checkout chỉ mở form sau restore, không auto-POST. Khi user ID đổi,
reset order/payment mutation và remount form để xóa PII. Giỏ giữ qua logout/401;
chỉ explicit start-over theo luồng cũ xóa giỏ. Không tự xóa khi order đang pending
hoặc lỗi, không retry POST khi chưa rõ kết quả.

## Kiểm tra

- `npm --prefix frontend run build`: qua; còn warning PURE annotation của dependency.
- `npm --prefix frontend run lint:ci`: qua, không warning/error.
- `npm --prefix frontend run test:e2e -- --trace on`:90 test qua, không retry,
  sau cập nhật intent checkout và các test mới.
- `git diff --check`: qua.
- Không chạy lại backend local vì không thay mã/config/API backend; CI phải chạy
  backend lẫn frontend trên commit mới trước merge, không dùng PASS của SF-146
  làm bằng chứng cho commit này.

Tám test browser mới phủ:

1. Add quantity2 → password login → reload → full-page Google cancel → còn đúng2,
   không nhân đôi và storage không chứa secret/PII.
2. Restore ID45 ngoài trang đầu, chỉ gọi detail45; không render giá/tên giả từ
   persisted JSON mà dùng API, subtotal tính trên giá API.
3. Return `checkout=1` mở form trống và không gửi order.
4. JSON hỏng, ID không safe, quantity overflow và quá64 KiB không fetch ID giả,
   không crash; hiện warning và giỏ rỗng.
5. Storage SecurityError/QuotaExceededError: cart RAM vẫn dùng qua SPA login,
   hiện warning trước Google navigation.
6. Deleted/out-of-stock: giữ issue, chặn checkout; remove/retry khôi phục đúng
   và mở intent form, không tự tạo đơn.
7. 401 khi submit: xóa shipping/name, giữ ID/quantity cho phiên sau; không có
   PII/orderRef trong storage.
8. Quantity2147483647 tăng tiếp bị từ chối, không wrap/truncate hoặc ghi sai.

Test UI dùng API mock; Google cancel là redirect toàn trang do route mock,
không phải consent Google thật. Backend/gating/owner checkout thuộc SF-148.
Không đồng bộ cart giữa domain, thiết bị hay tài khoản trên server.

Python Playwright qua helper `webapp-testing/with_server.py` đã kiểm thêm restore
ID45/quantity3 với desktop1280 tiếng Anh và mobile390 tiếng Việt. Đã xem ảnh
`/tmp/sf147-cart-1280.png`, `/tmp/sf147-cart-390.png`: không tràn ngang, số lượng
và subtotal dùng giá API đúng. Các API được mock; helper đã dừng Vite sau lượt
kiểm tra. Hai root domain không được truy cập và Google thật chưa được thử.
