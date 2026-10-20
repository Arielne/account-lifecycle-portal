# Account Lifecycle Portal

Admin Portal quản lý vòng đời tài khoản ngân hàng của khách hàng cá nhân, có phê duyệt maker–checker. Đây là Final Assignment của kỳ thực tập.

> Toàn bộ dữ liệu trong project (tên người, số CCCD, số tài khoản) là dữ liệu giả.

## Trạng thái

Tuần 1 (21–24/10/2026): làm tài liệu và sơ đồ. Chưa có code.

## Thư mục

| Thư mục | Nội dung |
| --- | --- |
| `docs/` | Tài liệu dự án. `01-chuc-nang.md`: danh sách chức năng theo vai trò |
| `docs/diagrams/` | Sơ đồ draw.io: Use Case, ERD |

## Công nghệ

- Frontend: React 19 + TypeScript + Vite, Ant Design 6, TanStack Query
- Backend: Java 21, Spring Boot 3.5, MyBatis, Camunda 7.24 (embedded)
- Đăng nhập: KeyCloak 26, session phía server (Spring Session trên Redis)
- Dữ liệu: Supabase (Postgres 17 và Storage)
- Khác: Kafka, Redis, Apache POI, JasperReports, Docker Compose

## Nhánh Git

- `main`: bản ổn định, chỉ nhận merge từ `develop` khi chốt bản
- `develop`: nhánh tích hợp, mọi Pull Request vào đây
- `feature/*`, `fix/*`, `hotfix/*`: nhánh làm việc
- `add/*`: nhánh thêm tài liệu và sơ đồ, ví dụ `add/docs`

Commit theo Conventional Commits, ví dụ `feat: thêm màn danh mục chi nhánh`.
