# Kiểm chứng refactor backend — SF-130

Kiểm tra ngày 2026-10-19 trên `chore/SF-130-layered-mvc-refactor`, base develop
`0c73a96c4bb56f9c6541aba335a76c05763dbc3f`. Source của base trùng với main
`a6e363e67a531af3f3325ecb3b0325f0b2e3498c` tại thời điểm bắt đầu.

Mục tiêu là tách tầng và nơi truy cập dữ liệu, giữ hành vi HTTP, nghiệp vụ và
transaction. [Kiến trúc](architecture/backend.md) và
[bảng chuyển file](architecture/backend-refactor-map.md) mô tả source sau thay đổi.

## Môi trường và phạm vi

- Ubuntu, OpenJDK 21.0.12.1, Maven wrapper của repo, Node 22.22.3, npm 10.9.8.
- Backend dùng profile test, database test và PostgreSQL/Qdrant Testcontainers
  dùng một lần theo từng bộ test hiện có. R2/Gemini/Gemma dùng mock trong các test
  protocol, không dùng credential production.
- Frontend không đổi source, cấu hình hoặc dependency; dùng dependency đã cài từ
  cùng lockfile trong worktree hiện có. Không chạy lại `npm ci`.
- Không truy cập/ghi dữ liệu production, deploy, reindex catalog thật hay thay
  object R2. Không có migration hoặc thay đổi cấu hình runtime.

## Kết quả tự động

| Kiểm tra | Lệnh / phạm vi | Kết quả |
|---|---|---|
| Baseline trước refactor | `bash backend/mvnw -f backend/pom.xml -B spotless:check clean verify` | 174 test, 0 failure/error/skip |
| Inventory và receiving | Controller/concurrency của hai nghiệp vụ + `ApiContractTests` | 20 test qua |
| Order, payment, reservation | Controller, data model, concurrency, stock reservation | 30 test qua |
| Delivery và returns | Controller và concurrency | 15 test qua |
| Auth, security, dashboard, catalog, ảnh, search, chat | Các test tương ứng + API contract | 109 test qua |
| Chặn dependency sai tầng | `-Dtest=ArchitectureTests test` | 1 test qua; phép thử mutation bên dưới bị chặn |
| Backend cuối cùng | `bash backend/mvnw -f backend/pom.xml -B spotless:check clean verify` | **176 test, 0 failure/error/skip; BUILD SUCCESS** |
| Frontend lint | `npm --prefix frontend run lint:ci` | Qua |
| Frontend type-check và build | `npm --prefix frontend run build` | Qua; còn warning PURE annotation của dependency |
| Frontend E2E toàn bộ | `CI=1 npm --prefix frontend run test:e2e` | 67 qua ngay, 1 flaky qua khi retry; exit 0 |
| Chạy riêng test E2E flaky | `CI=1 npm --prefix frontend run test:e2e -- --grep 'Owner has an entry link' --repeat-each=10 --retries=0 --workers=1` | 10/10 qua, không retry |

Hai test bổ sung so với baseline là `ApiContractTests` và `ArchitectureTests`.
Không cộng các suite focused vào tổng 176 vì có phần giao nhau.

E2E flaky tại `product-image.spec.ts:131` chờ redirect từ trang ảnh của chủ shop
về `/warehouse` sau khi mock đổi role. Test dùng mock session/catalog, không kết
nối backend vừa refactor. Source frontend và test này không thay đổi; lần chạy
riêng 10 lượt không tái hiện lỗi. Chưa xác định được nguyên nhân của lần lỗi đầu,
nên không coi kết quả E2E là 68 lượt qua ngay hoặc kết luận đã sửa flakiness.

## Đối chiếu contract và invariant

| Invariant | Bằng chứng |
|---|---|
| URL/method/parameter/request/response/schema OpenAPI | Capture `paths` và `components.schemas` từ source gốc vào `api-contract.json`; so sánh JsonNode bằng `ApiContractTests`: 21 path, 39 schema giữ nguyên. Không so metadata server phụ thuộc môi trường. |
| Status/error, role và CSRF | Giữ các assertion controller, `AuthorizationTests`, auth/session/rate-limit; giữ scope từng ControllerAdvice. |
| Đơn, payment, reserve/release | Giữ test data model, concurrent order/payment và stock reservation; JPA/JDBC tiếp tục chung transaction, giữ flush và `MANDATORY`. |
| Nhập/điều chỉnh/giao/hoàn kho | Giữ test concurrency/rollback, điều kiện không âm/không dưới reserved, giới hạn số lượng và chỉ ghi movement đúng transition. |
| Dashboard | Giữ test aggregate, khoảng ngày/timezone, zero-fill; tiền vẫn dựa payment `SUCCESS` theo `paid_at`, isolation `REPEATABLE_READ`. |
| R2 | Giữ test kiểm tra ảnh/role/CSRF và cleanup theo transaction `afterCompletion`. |
| Search/chat | Giữ test request provider, quota/fallback, grounded response, batch failure/stale deletion; Qdrant integration vẫn dùng container thật. |
| SQL | So sánh Counter các literal bắt đầu bằng SELECT/INSERT/UPDATE/WITH/DELETE trước và sau, chuẩn hóa khoảng trắng: **58 occurrence khớp**. Đây là đối chiếu nội dung, không phải đo query count hoặc benchmark. |
| Schema/migration | So sánh byte từng file migration với Git base; 6 file V1–V6 không đổi. Checksum ở bảng dưới là SHA-256 của file, không phải checksum số của Flyway. |
| Source liên quan | POM, main resources, frontend, Compose và CI không có diff; 5 comment `shortcut:`/`ponytail:` hiện hữu giữ nguyên nội dung. |

| Migration | SHA-256 trước và sau |
|---|---|
| `V1__init.sql` | `875950471f4499efe72a61ca36c860766133979e49700007bc41b0520db314b1` |
| `V2__seed_demo_catalog.sql` | `1d773e5221fa9d6cafe1921780600305d373c8b86a9ad4ba1a30405c94c23a69` |
| `V3__create_users.sql` | `5869dd81f9a8fac492859c06f47fc3460272bd9cc044b878622e5dfa4d20e03c` |
| `V4__add_order_ref.sql` | `75203354079836fd1cbc6bdaf4b234d34869c27f73671aa32919b554fa0f979e` |
| `V5__normalize_legacy_catalog_prices.sql` | `0d27a052fc2710deaad37dd45c75a3de94636db974e633c3f9b1c3e3e9e169a6` |
| `V6__add_product_image.sql` | `81ab9a241962cfc40493b8b2b6d79d2fe47108166aeab1e61bb65b3988f606a5` |

## Kiểm tra kiến trúc có bắt lỗi

Tạm thêm một field `org.springframework.jdbc.core.JdbcTemplate` vào
`InventoryService`, chạy:

```bash
bash backend/mvnw -f backend/pom.xml -B -Dtest=ArchitectureTests test
```

Test thất bại đúng tại `service/InventoryService.java` với thông báo database
access belongs in repositories. Khôi phục nguyên byte file trước phép thử;
suite backend cuối cùng ở trên chạy trên source đã khôi phục và qua đủ 176 test.
Mutation không nằm trong diff.

Guard này kiểm tra source/import ở mức cơ bản, không phải phân tích toàn bộ
bytecode/dependency Java. OpenAPI snapshot cũng không thay thế test nghiệp vụ và
phân quyền hiện có.

## Giới hạn và bước tiếp

- Chưa sửa ba N+1 ở deliveries, returns, returnable orders; chưa đo SELECT budget
  hoặc latency. Việc đó thuộc SF-136, tách khỏi thay đổi cấu trúc này.
- Lombok/MapStruct và cấu hình processor chưa sử dụng còn trong POM; xử lý ở
  SF-132. Không thêm dependency mới cho đợt refactor.
- Chưa gọi AI/R2 thật, chạy load test hoặc kiểm tra deploy production; đây là
  refactor nội bộ, protocol được kiểm tra bằng suite hiện có.
- Chưa có kết quả CI cho thay đổi này. Commit, push, PR và merge cần owner duyệt
  riêng. Epic SF-130 và tổng nghiệm thu SF-139 vẫn còn việc theo dõi, không đánh
  Done toàn bộ khi SF-132/SF-136 và review/CI chưa hoàn tất.
