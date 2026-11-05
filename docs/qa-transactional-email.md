# Kiểm chứng Resend email — SF-143

Ngày 2026-11-05, branch `feature/SF-143-resend-transactional-email`, worktree
`/home/ubuntu/workspace/shopflow-sf143`, base develop `b9413f9` (SF-141).
SF-142 vẫn ở worktree/nhánh riêng; SF-143 không phụ thuộc migration V7.

## Phạm vi

Client RestClient gọi Resend HTTPS, không thêm dependency/SMTP/queue/retry.
Có thư text và HTML song ngữ Việt–Anh cho đăng ký, reset và thông báo đổi mật
khẩu. Dữ liệu động được escape trong HTML; OTP chỉ nhận sáu chữ số ASCII.
Các service auth sẽ gọi client ở SF-144/145; task này chưa thêm endpoint.

`requireConfigured()` dùng trước eligibility checks trong auth service tương
lai; lỗi provider được chuyển thành `EmailUnavailableException` không kèm
cause/body. Service phải xử lý lỗi recipient bằng response202 thống nhất,
chỉ chuyển generation đúng sang ISSUED sau accepted, không giữ transaction/DB
lock khi gọi HTTP và không rollback password đã reset vì mail thông báo lỗi.
Không coi client là nơi thực thi quota, HMAC, OTP expiry hay one-time use.

## Kiểm tra tự động

Chuẩn bị PR trên develop có SF-142 (`e442a7d`):
`bash backend/mvnw -f backend/pom.xml -B spotless:check clean verify` qua 194 test,
0 failure/error/skip; build JAR thành công.


Môi trường: Java21, Maven wrapper, H2 profile test, PostgreSQL18.4 do cấu hình
Testcontainers hiện có dùng `postgres:latest`. Riêng client test dùng HTTP
server loopback thật cùng fake credentials, không kết nối Resend.

Các lệnh Maven chạy từ `backend/`:

| Lệnh | Kết quả |
| --- | --- |
| `bash ./mvnw -B spotless:apply -Dtest=ResendEmailClientTests test` | 7 test qua, 0 failure/error/skip |
| `bash ./mvnw -B spotless:check clean verify` | 183 test qua, 0 failure/error/skip; JAR build thành công |
| Sau bổ sung timeout đồng thời header/body: `bash ./mvnw -B spotless:apply -Dtest=ResendEmailClientTests test` | 7 test qua; production source không đổi sau full suite |
| `git diff --check` | Qua |

Bộ client test kiểm sender/recipient/header, text và HTML, escape displayName,
OTP có số0 đầu, hai purpose, idempotency UUID và key riêng cho notice.
Missing config không gửi HTTP; 301/400/401/403/429/500/503 không retry, không
follow redirect, không giữ cause hoặc log key/recipient/OTP/provider body.
Response success thiếu/malformed ID bị từ chối. Transport timeout kiểm cả
headers chậm, body chậm và cả hai cùng chậm; ngân sách3 giây (assert dưới4 giây có tolerance cho test). Catalog/demo/auth/controller tests tiếp tục qua khi
mail config rỗng trong test profile.

## Kiểm tra cấu hình chỉ đọc

- Workspace `.env` và `/opt/shopflow/.env` có `RESEND_API_KEY`, `MAIL_FROM`,
  quyền600; sender khớp `ShopFlow <shopflow@hoangtuan.dev>`. Không in giá trị key,
  không copy hoặc sửa hai file.
- GET Resend `/domains`: HTTP200, domain `hoangtuan.dev` có status verified
  tại thời điểm kiểm tra. Không gửi thư, không thay DNS hoặc quyền key.
- Render Compose vào bộ nhớ với `--env-file /opt/shopflow/.env`, chỉ assert
  biến mail và DB/R2/Qdrant vẫn có rồi in kết quả boolean. Không in config.
  Không chạy up/recreate container. File env workspace không phải bộ cấu hình
  production đầy đủ; không dùng thay thế production env.
- `.env.example` mới chỉ minh họa mail, không chứa secret và không được dùng
  để ghi đè cấu hình production. Google env wiring thuộc task Google.

## Giới hạn

Chưa gửi email thật hoặc kiểm Inbox/Spam, chưa gọi client từ API đăng ký/reset,
chưa kiểm flow auth frontend, quota hoặc HMAC. Dashboard domain verified và
accepted provider ID không chứng minh đã vào Inbox. Hướng dẫn kiểm DKIM/SPF,
DMARC và thử người nhận có owner approval nằm trong `docs/deployment.md`.

Các kiểm tra trên không triển khai production. Client hiện chưa có caller
runtime vì các service auth là task tiếp theo. Phải kết nối và kiểm uniform202,
generation CAS, response floor và quota ở những task đó trước khi bật mail.
