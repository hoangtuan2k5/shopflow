# Kiểm chứng đăng ký email — SF-144

Ngày 2026-11-05; branch `feature/SF-144-customer-email-registration`, worktree
`/home/ubuntu/workspace/shopflow-sf144`, base develop `b9413f9`.

## Nền và phạm vi

SF-142/143 chưa commit/merge. Worktree này chứa bản sao thay đổi schema/session
và Resend để chạy tích hợp. Hai worktree gốc được đối chiếu byte với bản nền và
không thay đổi. Danh sách file nền và bản sao trước sửa SF-144 ở `/tmp/sf144-dependency-manifest.json`
và `/tmp/sf144-dependency-baseline`; khi chuẩn bị commit/PR cần lấy SF-142/143
vào develop trước rồi áp riêng diff SF-144, không commit lẫn các task. Patch riêng SF-144 để review: `/tmp/sf144-only.patch`.

Triển khai ba endpoint: `/auth/registrations`, `/auth/registrations/verify`,
`/auth/otp/resend`; mở rộng login cho email đã xác minh, giữ demo username.
Có màn hình `/register` Việt/Anh, link từ login và return path nội bộ theo role.
Response/session vẫn bốn field user cũ; không auto-login sau xác minh.

Môi trường: Java21, Maven wrapper, PostgreSQL17 cho migration/browser thật,
PostgreSQL18.4 từ cấu hình Testcontainers hiện có, H2 cho các test baseline.
Luồng OTP dùng PostgreSQL advisory/row locks và ON CONFLICT; không dùng H2 để
kết luận các khóa/quota hoạt động. Không có dependency mới; frontend cài bằng
`npm ci` theo lockfile hiện có, không sửa lockfile.

## Kiểm tra tự động

Đóng gói PR trên `feature/SF-144-customer-email-signup`, base develop `dd7ba59`
đã có SF-142/143: chạy lại backend toàn bộ qua 209 test (0 failure/error/skip),
frontend build/lint qua. Mã nguồn/test khớp bản SF-144 đã nghiệm thu; browser
được chạy lại bởi GitHub CI trên PR.


Các lệnh Maven chạy từ `backend/`, npm chạy từ `frontend/`.

| Kiểm tra | Lệnh / phạm vi | Kết quả |
| --- | --- | --- |
| Tập trung đăng ký và login | `bash ./mvnw -B spotless:apply -Dtest=RegistrationTests,CustomerCredentialsTests,AuthControllerTests,LoginRateLimiterTests test` | 30 test qua |
| Backend cuối, gồm kiểm tra FAILED và race gửi muộn | `bash ./mvnw -B spotless:apply clean verify` | 209 test qua, 0 failure/error/skip; build JAR thành công |
| Test bổ sung race gửi muộn và config thiếu | `bash ./mvnw -B spotless:apply -Dtest=RegistrationTests,CustomerCredentialsTests,ApiContractTests,ArchitectureTests test` | 17 test qua, 0 failure/error/skip |
| Frontend build | `npm run build` | Qua type-check và build; còn warning PURE annotation từ dependency |
| Frontend lint | `npm run lint:ci` | Qua Oxlint và ESLint |
| Browser toàn bộ | `npm run test:e2e` | 73 test qua, không retry ở lần chạy đầy đủ |
| Diff | `git diff --check` | Qua |

Một lần chạy tập trung login/registration ban đầu có test reload session cũ
thất bại khi chạy song song. Test đó qua3 lần liên tiếp khi chạy riêng và qua
trong toàn suite73; chưa xác định nguyên nhân lần lỗi đầu. Không thay assertion
hoặc thêm sleep/retry để che lỗi.

## Hành vi được kiểm chứng

- Mật khẩu mới kiểm Unicode Lu/Ll, ASCII digit, P/S, ít nhất8 code point,
  không trim, tối đa72 byte UTF-8. Test7/8 ký tự, thiếu từng nhóm, Unicode,
  72/73 byte và surrogate không hợp lệ. Login giữ policy demo; quá72 byte
  trả rejection chung trước BCrypt, identifier/rate keys cùng normalization.
- Canonical email không bỏ dấu chấm hoặc plus-tag. User chưa tồn tại trước
  proof; pending BCrypt không phải password plaintext; session binding/verifier
  HMAC32 byte. Secret tách khỏi DB và provider keys, CSPRNG cho mã6 số.
- Quota DB theo email/purpose, IP peer và ngàyUTC; reserve atomically trước
  side effect. Hết global budget không tăng một phần IP/email counters.
  UUID verify rác chỉ tạo IP bucket, X-Forwarded-For giả không đổi khóa quota.
- Gửi mail ngoài DB transaction; PENDING_SEND commit trước HTTP. Kết quả gửi
  cũ không đổi generation mới. Provider failure trả202, không tạo code usable;
  FAILED vẫn chịu failure budget để không phân biệt bằng phản hồi lần thử5.
- Synthetic giữ response/floor/quota tương đương, không gửi mail, không thay
  account có sẵn. Verify synthetic không tạo user. Accepted issue/resend dùng
  floor4 giây thực, không tắt floor/hash trong test. Không hứa constant-time mạng.
- Sai session, superseded, replay, expired và mã sai bị chặn; trước mốc600 giây
  còn dùng được, đúng mốc hết hạn. Lần sai5 commit counter rồi trả429. Resend
  đổi UUID/verifier và giữ failure budget cấp email. Payload clear sau consume/expire.
- Hai đăng ký cùng email chỉ một generation được nhận trong cooldown; hai
  verify đồng thời chỉ một204, một400. User Google xuất hiện giữa issue/verify
  không thừa kế pending password. DB uniqueness là lớp cuối; duplicate insert
  được chuyển thành lỗi proof chung sau rollback thay vì trả500.
- CSRF/Origin chặn trước side effect, auth no-store, unknown role/fields trả400,
  role CUSTOMER do server gán. Internal username không thành email login alias.
  Demo login, authorization, CSRF roundtrip và API contract không regress.
- API snapshot chỉ thêm3 path và schema request/response mới; path cũ không đổi,
  schema cũ chỉ đổi LoginRequest username max100 thành254. Giữ format Jackson
  để tránh đổi whitespace toàn snapshot.

## Browser với backend thật

Harness tạm ngoài repo khởi động PostgreSQL17 dùng một lần, Spring Boot trên
loopback18085, fake Resend loopback18086 và Vite4184. Playwright Python dùng
helper `webapp-testing/scripts/with_server.py`. Fake mail giữ mã trong memory;
không dùng API key thật, không gửi ra Resend, không persist/log OTP.

Desktop1280 và mobile390 đã đi qua form thật → phát mã → sai OTP → đúng OTP →
không auto-login → email login → reload vẫn CUSTOMER. CSRF cookie/header và
JSESSIONID thực được dùng, không mock endpoint auth. Ảnh form đã xem trực tiếp:
`/tmp/sf144-desktop-registration.png`, `/tmp/sf144-mobile-registration.png`.
Mock browser tests còn kiểm VN/EN, focus, local validation, resend cooldown,
429/503, reload xóa challenge/password và redirect ngoài/role sai bị từ chối.

Harness, fake mail và PostgreSQL riêng đã được dừng; không ảnh hưởng container sẵn có.

## Cấu hình và giới hạn

`OTP_HMAC_SECRET` chưa có trong workspace env gốc hoặc `/opt/shopflow/.env`
ở lần kiểm tra chỉ đọc. Không sửa env thật hoặc tự cấp production secret.
Phải cấp Base64 ít nhất32 byte ngẫu nhiên trước triển khai; hướng dẫn ở
`docs/deployment.md`. Thiếu key/mail config trả503 cho OTP, catalog/demo vẫn chạy.

IP hiện dùng servlet peer, không tin raw forwarding header. Sau Caddy có thể
chia sẻ quota cho nhiều khách tới khi pin trusted proxy chain; test spoof chỉ
chứng minh header tùy ý không đổi key, chưa nghiệm thu proxy chain production.
Session vẫn in-memory. Không xác nhận Inbox/Spam, Google, reset password, cart
persistence hoặc ownership checkout; các task tương ứng chưa bật.

Kiểm thử này không đổi DNS, gửi thư thật hoặc triển khai production. Không đánh dấu
Jira Done trước review/merge. Các public domain chưa chạy tính năng này.
