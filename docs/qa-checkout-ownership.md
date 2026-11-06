# Kiểm chứng checkout và chủ đơn — SF-148

Ngày 2026-11-06, nhánh `feature/SF-148-authenticated-order-ownership`, nền develop
`d10c27b` sau PR #150. Không thêm migration; dùng owner column nullable của V7.
Không sửa secret, DNS hoặc triển khai production.

## Tái hiện và thay đổi

Test mới trước sửa gửi POST order với cookie/header CSRF thật của guest:
expected401 nhưng thực tế201. Lệnh baseline:
`bash backend/mvnw -f backend/pom.xml -B spotless:apply -Dtest=CheckoutOwnershipTests#guestWithARealCsrfCookieCannotCreateAnOrderOrPayment test`.
Đây là lỗi đã tái hiện trên DB dùng một lần, không gọi API production.

Sau sửa, SecurityConfig yêu cầu phiên cho order/payment. Controller lấy stable
user ID từ AccountPrincipal qua AuthService; JSON root field ngoài schema
(accountUserId/userId/role gồm trong đó) bị400. Customer email là snapshot,
không quyết định owner và không ghi user ID vào customer_id.

OrderService khóa/check account active trước inventory, ghi account_user_id trong
cùng transaction snapshot/reserve. Row lock giữ account tồn tại/active tới commit;
request checkout/payment cùng một account được serialize trong transaction ngắn.
Giữ stock lock order theo product ID, saveAndFlush và MANDATORY reservation.
Concurrency order được chạy với hai account khác nhau để không vô tình kiểm
mỗi việc serialize account thay vì cạnh tranh stock thật.

PaymentOrderRepository SELECT FOR UPDATE lấy owner cùng status/method/amount.
Sau structural validation, owner check đứng trước status/duplicate và mọi write/
release. Wrong-owner, malformed/unknown ref và legacy owner null cùng404
`Order not found`; SHOP_OWNER không override. Đúng owner duplicate/status cũ409.
Không tự backfill/claim, hủy đơn hoặc release reservation lịch sử.

UI vẫn cho browse/add tự do, gate ở checkout và kiểm lại trước submit/payment.
Login quay lại form với giỏ cũ và XSRF mới, không auto-POST. 401 giữ intent/giỏ
nhưng xóa private checkout state theo SF-147. Timeout/lỗi không tự retry POST.

## Kiểm tra tự động

Java21, Maven wrapper, PostgreSQL18.4 Testcontainers và baseline H2 hiện có.
Không nạp mail/Google key thật. API contract test qua mà không sửa snapshot:
không thêm owner field vào request/response hoặc thay payload cũ.

- Bộ tập trung order/payment/controller, cookie CSRF, concurrency, data model,
  architecture và API contract:37 test qua; sau đó bổ sung test service inactive.
- CheckoutOwnershipTests cuối:8 qua với cookie/header CSRF thật, không dùng
  helper `.with(csrf())`. Context/database riêng tránh helper lớp khác thay
  cookie repository thật trên shared filter.
- `bash backend/mvnw -f backend/pom.xml -B spotless:check clean verify`:239 qua,
  0 failure/error/skip; JAR đóng gói thành công.
- `npm --prefix frontend run build`:qua; warning PURE annotation dependency còn.
- `npm --prefix frontend run lint:ci`:qua, 0 warning/error.
- `npm --prefix frontend run test:e2e -- --trace on`:93 qua, không retry.
- Loopback backend/PostgreSQL/browser thật:qua; chi tiết bên dưới.
- `git diff --check`:qua.

Backend tests phủ guest với CSRF hợp lệ401/no effect; ba role/demo tạo và pay
đơn riêng; snapshot email khác owner; root ownership injection400; wrong-owner,
legacy và unknown cùng404; state/reservation/movement không đổi; owned failure
release đúng một lần; duplicate409; revoked session và inactive/missing direct
service caller không ghi dữ liệu. Các suite cũ vẫn qua, fixture payment được
gắn demo owner có chủ đích thay vì mở lại guest payment để làm test xanh.

## Giới hạn rollout

Chưa migrate/deploy production hoặc thay dữ liệu đơn cũ. Trước release cần
thống kê PENDING_PAYMENT legacy owner null và reservation đang giữ, backup/restore
và rollout approval; không tự giải quyết chúng bằng cancel/release. Payment vẫn
mô phỏng, không bank/card gateway thật. Live Google và real-mail acceptance thuộc
SF-149, không lấy loopback/mock evidence làm PASS cho hai public domains.

## Browser với backend thật

JAR từ cùng implementation chạy profile dev trên127.0.0.1:8080, PostgreSQL17
dùng một lần trên127.0.0.1:15488, Vite127.0.0.1:5173. Flyway chỉ dùng migrations
chuẩn; Google/mail key để trống, storage endpoint giả loopback9. Không gọi API
auth/order/payment mock ở lượt này. Kịch bản Python ngoài repo:
`/tmp/sf148-live-local.py`; chạy qua helper `webapp-testing/with_server.py`.

- Guest lấy cookie CSRF thật, POST order401; so DB counts/reserved không đổi.
- Tìm SKU37 ngoài20 item đầu bằng search thật → add → checkout → login bằng
  demo customer → restore giỏ/detail API và form trống, không tự tạo order.
- Cookie session HTTP-only/Lax; Secure=false đúng môi trường HTTP loopback,
  không được dùng làm bằng chứng cookie Secure qua proxy public.
- POST order từ UI/Axios với CSRF thật201, SQL xác nhận owner1; response không
  có owner field. Customer snapshot dùng dữ liệu giả cho thử nghiệm.
- Browser context khác đăng nhập demo SHOP_OWNER, thử FAILED cho đơn customer:
  404 `Order not found`; DB/payment/reserved/movement counts giữ nguyên.
- Context chủ đơn chọn SUCCESS từ UI:payment1, order PAID, reserved của SKU vẫn1;
  logout204 và session anonymous. Payment là mô phỏng, không phải giao dịch thật.
- Đã xem `/tmp/sf148-real-backend-checkout.png`; helper dừng Vite, JVM/DB test
  được dừng có mục tiêu; không dừng dịch vụ/container workspace khác.

Hai lỗi harness đã xử lý, không tính PASS:profile test không chạy standalone vì
S3 bean giả do JUnit cấp (chuyển sang dev + endpoint giả); SKU nằm ngoài trang
đầu (dùng search thật). Không đổi code hoặc làm yếu assertions để qua harness.

Sau lượt này bổ sung test wrong-owner đối với đơn đã PAID, buộc lỗi giống unknown
thay vì lộ409 theo trạng thái. Lượt cuối `bash backend/mvnw -f backend/pom.xml -B spotless:apply clean verify`
đạt240 test, 0 failure/error/skip; JAR thành công. Chạy `spotless:check` riêng sau
đó để xác nhận formatter không còn thay đổi. Lượt239 phía trên thuộc trước test
bổ sung; không cộng hai lượt để báo số test.
