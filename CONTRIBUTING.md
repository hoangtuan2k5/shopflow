# Contributing Conventions — ShopFlow

Tài liệu này mô tả quy ước Git/Jira cho dự án ShopFlow để cả team làm việc nhất quán.

## Branching strategy

Dự án dùng mô hình **Git Flow rút gọn** với 6 nhóm branch chính:

| Branch | Vai trò |
|---|---|
| `main` | Branch production. Chỉ nhận merge từ `develop` (hoặc `hotfix/*`) sau khi đã review và pass CI. Không commit trực tiếp. |
| `develop` | Branch tích hợp. Tất cả `feature/*`, `bugfix/*`, `docs/*` và `chore/*` merge vào đây trước khi promote lên `main`. |
| `feature/*` | Branch cho feature mới hoặc task. Tách ra từ `develop`, merge ngược về `develop` qua Pull Request. |
| `bugfix/*` | Branch cho việc sửa bug đã phát hiện. Tách ra từ `develop`, merge ngược về `develop` qua Pull Request. |
| `docs/*` | Branch cho thay đổi tài liệu (README, CONTRIBUTING, API docs, v.v.). Tách từ `develop`, merge về `develop` qua Pull Request. |
| `chore/*` | Branch cho việc bảo trì (cập nhật dependency, config, CI, tooling). Tách từ `develop`, merge về `develop` qua Pull Request. |

### Naming convention

Tên branch luôn bắt đầu bằng prefix, theo sau là **Jira issue key**, rồi mô tả ngắn dạng kebab-case:

```
<prefix>/<JIRA-KEY>-<short-description>
```

Với `docs/*` và `chore/*`, Jira key không bắt buộc nếu thay đổi không gắn với issue cụ thể.

Ví dụ:

| Loại | Ví dụ |
|---|---|
| Feature | `feature/SF-3-create-customer-order` |
| Feature | `feature/SF-24-configure-openapi` |
| Bugfix | `bugfix/SF-42-stock-validation-error` |
| Bugfix | `bugfix/SF-58-fix-payment-status-update` |
| Docs | `docs/SF-60-update-api-reference` |
| Docs | `docs/update-merge-strategy-convention` |
| Chore | `chore/SF-61-upgrade-spring-boot-3` |
| Chore | `chore/remove-unused-dependencies` |

Quy tắc:

- Tên branch viết **lowercase**, không dấu, dùng dấu gạch ngang.
- Issue key phải đúng format `SF-<number>`.
- Phần mô tả ngắn (3–6 từ) đủ để hình dung nội dung.
- Mỗi branch chỉ phục vụ **một issue chính** trong Jira.

### Workflow chuẩn

1. Đảm bảo `develop` mới nhất:

   ```bash
   git checkout develop
   git pull origin develop
   ```

2. Tạo branch mới từ `develop`:

   ```bash
   git checkout -b feature/SF-3-create-customer-order
   ```

3. Commit theo convention (xem phần dưới).
4. Push và mở Pull Request về `develop`:

   ```bash
   git push -u origin feature/SF-3-create-customer-order
   ```

5. Sau review và CI pass, **Squash & Merge** về `develop`.
6. Khi `develop` đủ ổn định để release, dùng **Merge commit** (`--no-ff`) để promote `develop` vào `main`, sau đó tag version:

   ```bash
   git tag v<major>.<minor>.<patch>
   git push origin v<major>.<minor>.<patch>
   ```

> **Vì sao release `develop → main` dùng merge commit, không squash?**
> `develop` và `main` đều là branch sống song song và tồn tại lâu dài. Squash sẽ tạo một commit mới
> có SHA khác toàn bộ commit gốc, khiến hai branch diverge vĩnh viễn: `git log main...develop` báo
> lệch sai, dễ phát sinh conflict giả và mất truy vết feature ở lần release sau. Merge commit
> (`--no-ff`) giữ nguyên lịch sử commit gốc nên `main` thực sự chứa toàn bộ `develop`. Ngược lại,
> branch `feature/*`, `bugfix/*`, `docs/*`, `chore/*` bị xoá ngay sau khi merge nên squash là phù
> hợp ở tầng đó.

### Branch protection

| Branch | Direct push | PR review | CI pass | Merge strategy |
|---|---|---|---|---|
| `main` | Cấm | Bắt buộc 1 reviewer | Bắt buộc | Merge commit (`--no-ff`) từ `develop` |
| `develop` | Cấm | Bắt buộc 1 reviewer | Bắt buộc | Squash & Merge |
| `feature/*`, `bugfix/*`, `docs/*`, `chore/*` | Cho phép | (không) | (không) | — |

## Commit convention

ShopFlow áp dụng **Conventional Commits** kết hợp **Jira issue key**.

### Cấu trúc

```
<type>(<scope>): <subject>

<body>

<footer>
```

| Phần | Mô tả |
|---|---|
| `type` | Loại thay đổi. Bắt buộc. |
| `scope` | Phạm vi thay đổi (module, package). Tùy chọn nhưng khuyến khích. |
| `subject` | Tóm tắt ngắn ở thì hiện tại, lowercase, không dấu chấm cuối. ≤ 72 ký tự. |
| `body` | Mô tả chi tiết WHY/HOW khi cần. Cách subject 1 dòng trống. |
| `footer` | Tham chiếu Jira hoặc breaking change. |

### Allowed types

| Type | Khi nào dùng |
|---|---|
| `feat` | Tính năng mới |
| `fix` | Sửa bug |
| `docs` | Thay đổi tài liệu |
| `style` | Format code, không đổi logic |
| `refactor` | Refactor không thêm tính năng, không sửa bug |
| `perf` | Cải thiện performance |
| `test` | Thêm hoặc cập nhật test |
| `build` | Thay đổi build system, dependencies |
| `ci` | Thay đổi CI/CD configuration |
| `chore` | Việc bảo trì lặt vặt, không thuộc các nhóm trên |
| `revert` | Revert một commit trước đó |

### Scope khuyến nghị

Theo cấu trúc package backend hoặc module frontend:

- Backend: `catalog`, `order`, `payment`, `delivery`, `inventory`, `receiving`, `customerreturn`, `dashboard`, `access`, `common`, `config`
- Frontend: `ui`, `routing`, `state`, `api-client`, `layout`
- Cross-cutting: `backend`, `frontend`, `monorepo`, `ci`, `docs`

### Tham chiếu Jira và Smart Commit

Mỗi commit nên link tới ít nhất một Jira issue ở dòng riêng cuối commit message.
Nếu muốn Jira tự chuyển status, đặt Smart Commit command ngay sau issue key trên cùng dòng đó:

```
SF-24 #in-progress
```

Khi commit liên quan nhiều issue:

```
SF-3 #in-progress
SF-11 #in-progress
```

Không dùng prefix `Refs:` cho Smart Commit command. `Refs: SF-24 #in-progress` vẫn có thể parse được,
nhưng `Refs:` là chữ thừa và làm commit message khó đọc hơn.

### Ví dụ commit message hợp lệ

Commit đơn giản:

```
feat(catalog): add product list endpoint

SF-2 #in-progress
```

Commit có body chi tiết:

```
feat(order): implement stock validation for order creation

Validate available stock before creating order, reserve inventory on success,
release on payment failure.

SF-3 #in-progress
SF-11 #in-progress
```

Commit fix bug:

```
fix(inventory): prevent negative stock after manual adjustment

SF-42 #in-progress
```

Breaking change:

```
refactor(order)!: rename OrderStatus.PENDING to PENDING_PAYMENT

BREAKING CHANGE: API consumers cần cập nhật theo enum mới.

SF-50 #in-progress
```

### Squash merge subject cho PR vào `develop`

Khi **Squash & Merge** một PR vào `develop`, subject của commit squash **bắt buộc** kết thúc bằng
suffix ` (#<số-PR>)`:

```
docs(monorepo): clarify merge strategy for develop to main release (#17)
```

Lý do: mỗi commit squash trên `develop` đại diện cho cả một PR đã review. Suffix `(#<số-PR>)` cho
phép từ `git log` nhảy thẳng sang PR để xem review discussion, review comments và lịch sử CI — đặc
biệt hữu ích khi điều tra regression, vì subject squash là điểm vào duy nhất dẫn về toàn bộ ngữ cảnh
của thay đổi đó.

Suffix này phải được **thêm thủ công** khi merge, không dựa vào GitHub tự sinh. Repo đang đặt
`squash_merge_commit_title = COMMIT_OR_PR_TITLE`, nên GitHub **không** đảm bảo chèn `(#<số-PR>)` vào
subject. Người merge tự gõ suffix vào ô subject trong GitHub UI, hoặc truyền qua CLI:

```bash
gh pr merge <số-PR> --squash --subject "docs(monorepo): clarify merge strategy ... (#<số-PR>)"
```

> Không bật `squash_merge_commit_title = PR_TITLE` để GitHub tự thêm, vì khi đó subject commit sẽ
> lấy nguyên PR title dạng `[SF-XX] ...` — phá vỡ format Conventional Commits `type(scope): subject`
> của commit. Giữ PR title và commit subject ở hai format riêng, thêm suffix bằng tay.

Quy tắc này **chỉ áp dụng cho squash merge vào `develop`**. Release `develop → main` dùng merge
commit giữ nguyên các commit gốc nên không thêm suffix.

### Squash merge body — Jira key

Nếu PR có gắn Jira issue, **bắt buộc** đặt Jira key vào body của commit squash:

```
docs(monorepo): clarify merge strategy for develop to main release (#17)

SF-33
```

Không dùng `#in-progress`, `#done`, hay bất kỳ Smart Commit command nào trong squash
merge body — ticket đã được transition từ feature branch commit trước đó.
Body chỉ chứa Jira key thuần:

```bash
gh pr merge <số-PR> --squash \
  --subject "docs(catalog): add product catalog API specification (#22)" \
  --body "SF-36"
```

### Smart Commits với Jira (tùy chọn)

Có thể kích hoạt **Smart Commits** để tự động chuyển status Jira ngay khi push. Dạng clean nhất là đặt Smart Commit trên dòng riêng ở cuối:

```
SF-24 #in-progress
```

Khuyến nghị cho team:

| Khi nào | Cách dùng |
|---|---|
| Commit đầu tiên trên branch | Có thể dùng `#in-progress` để mark ticket bắt đầu |
| Commit giữa | Chỉ ghi issue key ở dòng riêng, hoặc bỏ Smart Commit nếu không cần trigger transition |
| Hoàn thành | Dùng PR + Jira Automation rule, **không** dùng `#done` trong commit |

Lý do: `#done` trong commit có thể chuyển ticket sang Done trước khi review xong. Để PR merge mới đổi status là an toàn hơn.

### Anti-patterns cần tránh

| Sai | Đúng |
|---|---|
| `update code` | `fix(order): handle null assignee in order creation` |
| `Fix bug` | `fix(payment): release reserved stock when payment fails` |
| `WIP` | Tách thành commit có ý nghĩa hoặc dùng draft PR |
| `feat: ABC.` (có dấu chấm) | `feat: ABC` |
| Subject tiếng Việt | Luôn viết English cho subject |
| Subject dài hơn 72 ký tự | Cắt gọn, đẩy phần dài xuống body |

## Pull Request convention

### Title PR

Format:

```
[<JIRA-KEY>] <subject>
```

Hoặc theo Conventional Commits:

```
<type>(<scope>): <subject> (<JIRA-KEY>)
```

Ví dụ:

```
[SF-24] Configure OpenAPI and base package structure
feat(order): create customer order (SF-3)
```

### Description PR

Đề xuất template:

```markdown
## Summary
Tóm tắt thay đổi chính.

## What changed
- Bullet 1
- Bullet 2

## How to test
1. Bước 1
2. Bước 2

## Related issues
SF-XX
```

### Quy trình review

1. Tác giả mở PR từ `feature/*` hoặc `bugfix/*` về `develop`.
2. CI tự chạy build và test.
3. Tối thiểu 1 reviewer approve.
4. Resolve mọi comment.
5. **Squash & Merge only** cho PR về `develop`. Không dùng merge commit hoặc rebase merge ở tầng này. Subject của commit squash phải kết thúc bằng suffix ` (#<số-PR>)` (xem mục Squash merge subject). Riêng release `develop → main` dùng **Merge commit** (`--no-ff`).
6. Xóa branch sau khi merge (không áp dụng cho `develop` và `main`).

## Tóm tắt nhanh

- Branch: `feature/SF-XX-...`, `bugfix/SF-XX-...`, `docs/SF-XX-...` hoặc `chore/SF-XX-...`, tách từ `develop`.
- Commit subject: `<type>(<scope>): <subject>`, lowercase, không dấu chấm cuối, ≤ 72 ký tự.
- Footer: `SF-XX #in-progress` khi cần transition, hoặc `SF-XX` khi chỉ cần link issue.
- PR title chứa Jira key. Merge về `develop` bằng **Squash & Merge**; promote `develop → main` bằng **Merge commit** (`--no-ff`) kèm tag version.
- Chỉ release manager merge `develop` vào `main`.
