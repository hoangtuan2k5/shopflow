# Kiểm chứng quên mật khẩu bằng OTP — SF-145

Bản kiểm thử tích hợp ban đầu ngày 2026-11-05, branch `feature/SF-145-password-reset-email-otp`, worktree
`/home/ubuntu/workspace/shopflow-sf145`, base develop `b9413f9`.

## Phạm vi và nền

Worktree chứa bản sao thay đổi chưa commit của SF-142–144 để kiểm thử tích hợp.
Worktree SF-144 cùng các bản gốc SF-142/143 được giữ nguyên. Trước commit/PR,
phải tích hợp các prerequisite theo thứ tự rồi áp riêng diff SF-145. Không
coi nhánh này là rollout độc lập trên schema/API cũ. Patch riêng để review:
`/tmp/sf145-only.patch`, đã kiểm bằng reverse apply --check (không áp vào source).

Bổ sung POST `/auth/password/forgot`, POST `/auth/password/reset` và màn hình
`/forgot-password` Việt/Anh. Reset kết hợp challengeId/OTP/newPassword trong
một request, không có bước xác minh để cấp token/session reset riêng.
RegistrationService/RegistrationRepository được đổi thành CustomerOtpService/
EmailChallengeRepository vì hai purpose nay thực sự dùng chung quota, locking,
HMAC, resend, delivery state và cleanup; không sao chép một cơ chế OTP thứ hai.

## Môi trường và kết quả

Java21, Maven wrapper, PostgreSQL18.4 theo Testcontainers hiện có; PostgreSQL17
cho browser harness và migration test; H2 cho regression baseline. Không có
migration hoặc dependency mới. Npm dùng cùng lockfile/dependency đã cài của
SF-144; không thay package.json hoặc lockfile. Lệnh Maven chạy từ `backend/`,
npm từ `frontend/`.

| Kiểm tra | Lệnh/phạm vi | Kết quả |
| --- | --- | --- |
| Reset tập trung | `bash ./mvnw -B spotless:apply -Dtest=PasswordRecoveryTests test` | 9 test qua; sau đó tăng assertion cho outstanding challenges trong full suite |
| Hồi quy đăng ký và login | RegistrationTests, AuthControllerTests | 12 + 15 test qua trong lần chạy tập trung |
| Backend cuối | `bash ./mvnw -B spotless:check clean verify` | 218 test qua, 0 failure/error/skip; build JAR thành công |
| Frontend build/lint | `npm run build`; `npm run lint:ci` | Qua; còn warning PURE annotation từ dependency |
| Browser đầy đủ chạy sau backend | `npm run test:e2e -- --trace on` | 77 test qua, không retry |
| Diff/format cuối | `git diff --check`; `bash ./mvnw -B spotless:check` | Qua |

Các lần browser chạy trong lúc Testcontainers thay đổi container có lỗi tải
trang ở các test login/dashboard/chat cũ. Trace ghi nhận `ERR_NETWORK_CHANGED`
ở request tải module giao diện; đây là lỗi mạng môi trường, chưa chứng minh
lỗi nghiệp vụ các màn hình đó. Hai test login qua8 lần lặp khi kiểm tra riêng.
Không tăng timeout, thêm retry hoặc làm yếu assertion. Lượt nghiệm thu cuối
chạy backend xong mới chạy browser để tránh thay đổi mạng Docker cùng lúc.
Một lượt Maven trước đó kết thúc SIGTERM/143 giữa chừng, không được tính PASS.

## Hành vi được kiểm chứng

- Forgot dùng email canonical, giữ dấu chấm/plus-tag. Unknown, inactive và
  Google-only nhận cùng response202/floor4 giây, không gửi mã, không thêm local
  password. Demo không email vẫn đăng nhập theo username cũ.
- RESET_PASSWORD không thay slot/verifier REGISTER; resend tìm purpose từ DB
  và session, không nhận email/purpose/userId từ body để đổi đích. Invalid input,
  unknown fields, CSRF và Origin bị từ chối theo shape auth đã chốt.
- OTP10 phút, một lần, keyed HMAC và session binding giữ nguyên. Test hợp lệ
  ở599 giây, hết hạn tại600; wrong-session, wrong-purpose, superseded, replay,
  inactive giữa flow và vượt attempts không đổi credential.
- Tối đa5 lần sai được commit trước response lỗi; failure budget theo email/
  purpose không reset khi resend. Challenge gửi thất bại vẫn chịu failure
  budget nhưng không bao giờ trở thành proof hợp lệ. UUID rác chỉ tạo IP bucket.
- Sorted rate locks → email advisory lock → user → challenge. Mail gửi ngoài
  transaction; generation CAS không hồi sinh mã cũ. Registration regression
  tiếp tục kiểm quota atomicity, late delivery và concurrent signup/verify.
- Sau đúng proof mới BCrypt mật khẩu mới; cập nhật hash/auth_version và consume
  trong cùng transaction. Những reset challenge khác trỏ cùng account bị expire.
  Hai reset đồng thời chỉ một204; version tăng1, hash thuộc request thắng và
  chỉ có một notification. Không auto-login.
- Hai session A/B còn quyền sau forgot, rồi đều mất quyền sau reset. Mật khẩu
  cũ bị từ chối, mật khẩu mới login được. Forgot không clear login failure limit;
  successful reset mới clear canonical email để người dùng đăng nhập lại được.
- Notification gửi sau commit; fake mail xác nhận không còn transaction và đã
  thấy version mới. Lỗi notification không rollback hash, không retry tự động.
- Password mới dùng cùng validator signup:8 code point, Lu/Ll, ASCII digit,
  Unicode P/S, tối đa72 byte UTF-8, không trim/normalize. Test7/8, thiếu nhóm,
  Unicode và72/73 byte đã có và được chạy lại trong backend suite.
- Snapshot OpenAPI chỉ thêm hai path/schema reset; tất cả path và schema đã
  có trong bản nền SF-144 giữ nguyên. Response user/session không đổi.

## Browser với backend thật

Harness ngoài repo: Spring Boot loopback18185, PostgreSQL17 dùng một lần, fake
Resend loopback18186, Vite4195 với cache tách khỏi server mock E2E. Fake mail
chỉ giữ OTP trong memory, không gửi ra ngoài và không ghi OTP vào log/file.

Playwright Python đi qua UI thật ở desktop1280/mobile390: đăng ký/xác minh →
tạo hai phiên A/B → forgot giữ nguyên A/B → sai OTP → reset đúng → không auto
login → A/B mất quyền → mật khẩu cũ401 → login mới và reload giữ CUSTOMER.
Auth endpoints không được mock ở lượt này; cookie CSRF và JSESSIONID thực.
Ảnh form đã kiểm tra trực tiếp: `/tmp/sf145-desktop-reset.png`,
`/tmp/sf145-mobile-reset.png`. Mock E2E còn kiểm VN/EN, keyboard/focus,
validation trước request, Retry-After, clear input nhạy cảm và reload mất proof.

Harness Java, fake mail và các Vite con còn sót từ helper đã được dừng có mục
tiêu; không dừng container/dịch vụ của workspace khác.

## Cấu hình và giới hạn

Compose được render vào memory bằng `/opt/shopflow/.env`, chỉ in kết quả kiểm
tra: OTP_HMAC_SECRET hiện có, decode ít nhất32 byte, các biến DB/R2/Qdrant/mail
vẫn có. Không in giá trị secret, sửa env hay recreate container. CI/browser
harness dùng secret/key giả riêng, không dùng key Resend thật.

Chưa gửi thư thật/kiểm Inbox, Google login, giữ giỏ, owner checkout hoặc nghiệm
thu hai public domain. Quota IP vẫn dùng peer, có thể chia sẻ sau Caddy tới khi
pin proxy chain; session vẫn in-memory. Bản kiểm thử tích hợp này chưa triển khai production. Trạng thái publish được
ghi riêng bên dưới; tính năng mới chưa chạy trên production.

## Kiểm tra lại trước publish

Nhánh `feature/SF-145-reset-customer-passwords` lấy từ `origin/develop`
`62951a9` sau khi SF-144 merge qua PR #147. Chỉ áp diff riêng SF-145; giữ nguyên
các thay đổi schema, email và đăng ký đã merge. 22 đường dẫn source/test khớp
bản kiểm thử tích hợp ở trên, gồm cả hai lớp cũ được đổi tên.

- `bash backend/mvnw -f backend/pom.xml -B spotless:check clean verify`: 218 test
  qua, 0 failure/error/skip; đóng gói JAR thành công.
- `npm --prefix frontend run build`: qua.
- `npm --prefix frontend run lint:ci`: qua, 0 warning/error.
- `git diff --cached --check`: qua.

Không chạy lại browser ở lượt publish local; kết quả 77 test và live-backend
ở trên thuộc lượt tích hợp trước. GitHub CI chạy browser trên commit của PR.
Không gửi email thật, đổi DNS, sửa secret hoặc triển khai production.
