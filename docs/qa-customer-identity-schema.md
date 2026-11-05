# Kiểm chứng schema Customer Identity — SF-142

Ngày 2026-11-05, branch `feature/SF-142-customer-account-schema`, base develop
`b9413f90b626d606b6054573bdf615ff618ea624` (contract SF-141, PR #143).

## Phạm vi và môi trường

OpenJDK 21.0.12.1, Maven wrapper, PostgreSQL 17.10 trong Testcontainers riêng và
H2 2.4.240 profile test. Migration test chỉ clean schema trong container do
chính test tạo; không kết nối database production/dev đang chạy.

V7 thêm email/Google/auth_version, challenge/quota và owner FK nullable.
V1–V6 không sửa. DDL tham chiếu đồng bộ; deploy vẫn phải dùng Flyway.
Principal giữ userId/version nội bộ; filter kiểm active/version mỗi request có
AccountPrincipal. Payload login/session giữ nguyên. Username login chỉ nhận
legacy có email null và local hash khác null.

## Kết quả

Lệnh Maven chạy từ `backend/`.

| Kiểm tra | Lệnh | Kết quả |
| --- | --- | --- |
| Tập trung ban đầu | `bash ./mvnw -B spotless:apply -Dtest=CustomerIdentityMigrationTests,AuthControllerTests test` | 21 test qua |
| Toàn bộ backend | `bash ./mvnw -B spotless:apply clean verify` | 186 test qua; 0 failure/error/skip; JAR build thành công |
| Sau bổ sung test DDL và assertion fixture | `bash ./mvnw -B spotless:apply -Dtest=CustomerIdentityMigrationTests,AuthControllerTests,ArchitectureTests test` | 24 test qua: 8 migration, 15 auth, 1 kiến trúc; 0 failure/error/skip |
| Chuẩn bị PR SF-142 | `bash backend/mvnw -f backend/pom.xml -B spotless:check clean verify` (từ repo root) | 187 test qua, 0 failure/error/skip; build JAR thành công |
| Format/whitespace cuối | `bash ./mvnw -B spotless:check`; `git diff --check` | Qua |

Sau lần chạy toàn bộ chỉ sửa test/fixture/tài liệu, không sửa production source.
186 test chưa gồm test DDL mới thêm; test đó qua ở lần chạy 24 cuối.

Các kiểm chứng chính:

- Fresh PostgreSQL/H2 tới V7; upgrade V6 và Flyway validate qua. ID/hash/role
  demo, customer FK, snapshot order/item, payment, products, inventory/reservation
  và stock movement giữ nguyên. Legacy owner null, không backfill email.
- DB từ chối email không canonical/ASCII/verified, Google identity trùng,
  account thiếu credential và version âm. Hai insert cạnh tranh email hoặc
  Google subject chỉ một bên thành công trên PostgreSQL thật.
- Challenge giữ slot email/purpose duy nhất, verifier 32 byte, TTL10 phút,
  tối đa5 lần sai và payload theo trạng thái. FAILED giữ pending registration;
  EXPIRED clear payload; không thể ISSUED thiếu verifier.
- Hai conditional UPDATE consume cùng challenge chỉ một bên cập nhật, verifier
  được clear. Đây là kiểm chứng schema/SQL nguyên tử, **chưa phải** test API
  reset/signup/Google hoặc bằng chứng lock order của các flow đó.
- Quota scope/digest/counter/window hợp lệ; owner FK từ chối user không tồn tại
  và ngăn hard-delete user đã được order tham chiếu.
- DDL tham chiếu chạy thành công trên schema trống trong PostgreSQL tạm.
- Login ba demo đúng role; Google-only bị từ chối qua password; version/hash
  không lộ qua login response. Hai session cũ cùng user mất hiệu lực sau tăng
  auth_version; inactive/deleted user bị invalidate; login mới vẫn hoạt động.

## Giới hạn và rollout

Chưa có issue/verify OTP, quota enforcement, HMAC computation, reset, Resend,
Google callback hoặc chặn guest checkout. Schema không tự thực thi các flow.
`OTP_HMAC_SECRET` cần cấp ở phần OTP: ít nhất32 byte ngẫu nhiên, ngoài Git/DB,
độc lập provider secrets; SF-142 không đọc hoặc thay key.

Mỗi authenticated request có AccountPrincipal thêm một lookup DB. Session vẫn
trong memory, không thêm Spring Session JDBC. Invalidation áp dụng request kế
tiếp, không hủy request đã được cấp quyền trước transaction reset commit.

Không chạy frontend/browser, gửi mail thật, Google consent/callback thật,
production backup/restore hoặc benchmark tải. Merge/deploy nằm ngoài phạm vi kiểm thử này.
V7 cần review/CI/duyệt rollout trước production; không drop cột/bảng để rollback.
Owner enforcement và rollback policy được kiểm tra tiếp ở SF-148 trước khi bật
checkout mới.
