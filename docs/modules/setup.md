# Module setup: nền móng (tuần 2)

Mục tiêu là mốc M1 ngày 31/10/2026 (mốc đầu tiên trong roadmap của dự án; roadmap không nằm trong repo): đạt đủ điều kiện ở mục 12. Module này không làm chức năng `CN-xx` nào; nó dựng khung để các module sau chỉ việc thêm code vào đúng chỗ.

## 1. Làm và không làm

Làm:

- Cấu trúc repo: `backend/`, `frontend/`, `db/migrations/`, `db/seeds/`, `docker/`, `compose.yaml` ở gốc.
- Docker Compose cho KeyCloak, Redis, Kafka + Kafka UI, Mailpit; Dockerfile cho backend và cho frontend (bản build React chạy trong Nginx).
- Khung Spring Boot và khung React chạy được.
- Flyway tạo 12 bảng theo `docs/diagrams/erd.drawio`, sequence, index.
- Dữ liệu mẫu giả: danh mục, nhân viên demo, khách hàng, tài khoản.
- MyBatis đọc bảng `branch`, test bằng Testcontainers.
- GitHub Actions + JaCoCo; branch protection cho `main` và `develop`.
- Phần cho Claude Code trên máy (mục 10).

Không làm, để đúng tuần của nó:

- Realm KeyCloak, đăng nhập, Spring Security, Spring Session (tuần 3). Tuần này KeyCloak chỉ cần chạy được.
- Function cho Tổng quan (tuần 5); Camunda (tuần 6); Stored Procedure và pg_cron (tuần 7); POI, Jasper (tuần 8); Kafka producer, consumer, Redis cache (tuần 9).
- Thư viện chưa dùng tới. Tuần nào cần thư viện nào thì thêm tuần đó.

## 2. Cấu trúc repo

```
account-lifecycle-portal/
├── compose.yaml            Docker Compose (chạy ở gốc repo)
├── .env.example            tên biến môi trường, không có giá trị
├── backend/                Spring Boot, Maven Wrapper (mvnw)
│   └── src/main/java/io/github/arielne/alp/
│       ├── common/         cấu hình dùng chung
│       └── catalog/        tuần này chỉ có Branch + BranchMapper
├── frontend/               React + Vite
├── db/
│   ├── migrations/         script Flyway V<số>__<mô_tả>.sql
│   └── seeds/              dữ liệu mẫu giả
├── docker/
│   └── nginx/              cấu hình Nginx (realm và theme KeyCloak thêm ở tuần 3)
└── docs/
```

Package của các module khác (bảng module trong `CLAUDE.md`) tạo khi bắt đầu module đó, không tạo sẵn thư mục rỗng.

## 3. Docker Compose

| Dịch vụ | Image | Cổng trên máy | Ghi chú |
| --- | --- | --- | --- |
| keycloak | `quay.io/keycloak/keycloak:26.8.1` | 8080 | `start-dev`; tài khoản admin lấy từ `.env` (`KC_BOOTSTRAP_ADMIN_USERNAME`, `KC_BOOTSTRAP_ADMIN_PASSWORD`); volume giữ file H2 |
| redis | Redis, ghim số phiên bản | 6379 | |
| kafka | Apache Kafka chế độ KRaft (không ZooKeeper), 1 broker | 9092 | |
| kafka-ui | Kafka UI, chọn image còn được duy trì | 8090 | |
| mailpit | `axllent/mailpit` | 1025 (SMTP), 8025 (web) | |
| backend | build từ `backend/` | 8081 | Cổng 8081 vì 8080 là KeyCloak; chỉ mở cho `127.0.0.1`, máy khác phải đi qua Nginx |
| web | build từ `frontend/` | 80 | Nginx trả bản build React, chuyển `/api/`, `/oauth2/`, `/login/oauth2/`, `/logout` sang `backend:8081` |

- Ghim số phiên bản cho mọi image, không dùng `latest`.
- Không gắn profile cho 5 dịch vụ hạ tầng; `backend` và `web` thuộc profile `app`. Hằng ngày `docker compose up -d` chỉ chạy hạ tầng; chạy đủ bộ như lúc demo thì `docker compose --profile app up -d`.
- Không chạy `docker compose down -v` (mất user tạo thêm trong KeyCloak).
- Hằng ngày: backend chạy từ IDE hoặc `mvnw spring-boot:run` (cổng 8081); frontend chạy `npm run dev`. Vite proxy chuyển 4 đường dẫn ở trên sang `localhost:8081`, để React và backend cùng origin như khi đi qua Nginx.

## 4. Cấu hình và biến môi trường

- `.env` ở gốc repo (đã có trong `.gitignore`); `.env.example` ghi tên biến, không ghi giá trị. Biến tuần này:
  - `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`: Supabase Session pooler.
  - `KC_BOOTSTRAP_ADMIN_USERNAME`, `KC_BOOTSTRAP_ADMIN_PASSWORD`.
  - Biến của các tuần sau (secret client KeyCloak, khóa S3, `DEMO_PASSWORD`) thêm khi cần.
- Compose tự đọc `.env`. Spring Boot nạp `.env` bằng `spring.config.import` kèm gợi ý kiểu `[.properties]`; đường dẫn chốt khi dựng.
- Profile Spring: mặc định (chạy trên máy, nối Supabase), `test` (Testcontainers), `demo` (bật nạp dữ liệu mẫu).
- Người dùng tự điền giá trị vào `.env`. Claude Code không đọc, không in giá trị trong `.env`.

## 5. Database và Flyway

- Flyway chỉ quản lý schema `app` và tự tạo schema nếu chưa có; bảng lịch sử của Flyway nằm trong `app`. Bảng của Camunda không do Flyway tạo.
- Script ở `db/migrations/` (gốc repo). Backend trỏ tới bằng `spring.flyway.locations`; Dockerfile của backend chép thư mục này vào image.
- Chia script theo thứ tự khóa ngoại:

| Script | Tạo |
| --- | --- |
| `V1__danh_muc.sql` | `branch`, `account_type` |
| `V2__nhan_vien.sql` | `employee` |
| `V3__khach_hang_tai_khoan.sql` | `customer`, `account`, sequence số tài khoản |
| `V4__import_theo_lo.sql` | `import_batch`, `import_batch_row` |
| `V5__phieu_yeu_cau.sql` | `ticket`, `attachment`, `ticket_history`, sequence mã phiếu, index UNIQUE có điều kiện cho QT-03 |
| `V6__audit_outbox.sql` | `audit_log`, `outbox_event` |

- Schema `app` do Flyway tạo trước khi chạy V1 (bảng lịch sử của Flyway cần có chỗ nằm trước), nên không script nào viết `CREATE SCHEMA`.
- Cột, kiểu, khóa, cột được rỗng: đúng như `erd.drawio`. Cột trạng thái có ràng buộc CHECK theo danh sách giá trị ghi trong ERD.
- Index: `account.status`, `ticket.status`; `cccd`, `account_number` đã có index nhờ UNIQUE.
- Không sửa script đã chạy; muốn đổi bảng thì thêm script mới.
- Ghi chú cho tuần 7: Postgres trong Testcontainers không có pg_cron, nên test của job Ngủ đông gọi thẳng Stored Procedure.

## 6. Dữ liệu mẫu

- Tách khỏi migration, chỉ chạy ở profile `demo`. Profile này thêm `db/seeds/` vào `spring.flyway.locations`; file đặt tên `R__<mô_tả>.sql` (repeatable: Flyway chạy lại khi nội dung file đổi). Viết bằng `INSERT ... ON CONFLICT DO NOTHING`, chạy lại nhiều lần không trùng.
- Supabase là một database dùng cho cả chạy thường và demo. Đặt `spring.flyway.ignore-migration-patterns: "*:future,repeatable:missing"` để database đã chạy seed ở `demo`, khi chạy lại profile thường (không có `db/seeds/`), Flyway không báo lỗi "Detected applied migration not resolved locally".
- Tuần này có:
  - Chi nhánh và loại tài khoản lấy theo bản thiết kế (màn 3.2, 3.3).
  - 4 nhân viên demo (`lan.nt`, `khoa.tm`, `anh.vd`, `phuc.lh`) với `keycloak_id` cố định. Tuần 3 dùng lại đúng các id này trong realm JSON. Role không lưu ở đây.
  - Khoảng 30 khách hàng và 60 tài khoản ở trạng thái Hoạt động, Ngủ đông, Đã đóng. Một số tài khoản Hoạt động có `last_transaction_at` cũ để demo job Ngủ đông.
- Tài khoản Chờ mở, Phong tỏa và các phiếu không chèn bằng SQL. Chúng được tạo bằng code Java ở profile `demo`, gọi đúng service tạo và duyệt phiếu, khi đã có Camunda (tuần 7).
- Toàn bộ là dữ liệu giả: tên giả, số CCCD giả đủ 12 chữ số, email đuôi `nhdemo.local`.

## 7. Backend

- Spring Boot 3.5.16, Java 21, Maven Wrapper (máy không cần cài Maven). Package gốc `io.github.arielne.alp`.
- Thư viện tuần này: web, validation, actuator (kiểm tra sống cho Compose), JDBC + driver PostgreSQL, MyBatis starter, Flyway (kèm module PostgreSQL), Lombok. Test: spring-boot-starter-test, Testcontainers (PostgreSQL). Plugin JaCoCo.
- `catalog`: `Branch`, `BranchMapper` + `BranchMapper.xml` với `findAll`. Chưa cần Controller.
- MyBatis: `map-underscore-to-camel-case: true`, `type-aliases-package`, `mapper-locations` trỏ tới file XML.

## 8. Frontend

- Vite + React 19 + TypeScript; Ant Design 6 + `@ant-design/icons`; React Router; TanStack Query; axios; SCSS.
- `ConfigProvider` với locale tiếng Việt và theme token; màu chính lấy theo Design System trong bản thiết kế.
- Một trang tạm để chắc mọi thứ chạy. Layout thật (menu theo role, header) làm ở tuần 3.
- Vitest + React Testing Library, 1 test mẫu.

## 9. Test, CI, coverage

- Test nhanh: unit và API, không cần Docker, tên lớp kết thúc bằng `Test`. Test chậm: dùng Testcontainers, tên lớp kết thúc bằng `IT`. `mvnw test` chỉ chạy test nhanh; `mvnw verify` chạy tất cả và tạo báo cáo JaCoCo.
- GitHub Actions (`.github/workflows/ci.yml`) chạy khi có Pull Request vào `develop`, `main` và khi push lên `develop`:
  - Backend: JDK 21, `./mvnw verify`, tải báo cáo JaCoCo lên làm artifact. Runner Ubuntu của GitHub có Docker nên Testcontainers chạy được.
  - JaCoCo tuần này chỉ đo, chưa đặt ngưỡng tối thiểu. Tuần 5 có số thật mới đặt.
  - Frontend: Node 24, `npm ci`, `npm run build`, `npm test`.
- Branch protection, bật sau khi workflow đã chạy ít nhất 1 lần để chọn được tên check: `main` và `develop` bắt buộc Pull Request và bắt buộc CI xanh.

## 10. Phần cho Claude Code (chỉ trên máy, không lên Git)

- Hook chạy test nhanh sau mỗi lượt Claude Code sửa code; test đỏ thì chưa được dừng. Cấu hình theo tài liệu hooks của Claude Code lúc dựng.
- Điền mục "Thư mục" và "Lệnh" trong `CLAUDE.md`; sửa dòng thư mục vì `compose.yaml` nằm ở gốc.
- `backend/CLAUDE.md`: chuyển quy ước Java, MyBatis từ file gốc sang. `frontend/CLAUDE.md`: chuyển quy ước React sang.
- `.claude/rules/migrations.md` chỉ áp dụng cho `db/migrations/**`: đặt tên script, mỗi script một việc, không sửa script đã chạy.
- `LEARNING.md` ở gốc repo, chỉ để trên máy (thêm vào `.git/info/exclude`): bắt đầu ghi Docker Compose, Flyway, MyBatis, Testcontainers.

## 11. Nhánh và Pull Request

1. `feature/setup-docker-ci`: cấu trúc repo, Compose, Dockerfile, khung backend và frontend, CI, JaCoCo. Merge xong thì bật branch protection.
2. `feature/db-migrations`: Flyway 12 bảng, dữ liệu mẫu, `BranchMapper` và test Testcontainers.

## 12. Xong khi

- `docker compose --profile app up -d` lên đủ 7 container; mở được KeyCloak (8080), Mailpit (8025), Kafka UI (8090) và trang React qua Nginx (cổng 80).
- Flyway tạo đủ 12 bảng trong schema `app` trên Supabase.
- `mvnw verify` xanh trên máy và trên GitHub Actions; có báo cáo JaCoCo.
- Branch protection đã bật cho `main` và `develop`.
- Tự kiểm: tự chạy Compose và nói được mỗi container làm gì; tự viết 1 câu SELECT có JOIN và 1 mapper, không nhờ AI.

## 13. Đã chốt (25/10/2026)

1. Package gốc Java: `io.github.arielne.alp`.
2. Dữ liệu mẫu: Flyway chạy thêm `db/seeds/`, chỉ ở profile `demo`, file `R__`.
3. JaCoCo: tuần 2 chỉ đo, chưa đặt ngưỡng.
4. `LEARNING.md`: chỉ để trên máy, không lên Git.
5. Cột trạng thái có ràng buộc CHECK (mục 5).