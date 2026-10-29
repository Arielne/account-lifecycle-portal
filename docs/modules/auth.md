# Module auth: đăng nhập (tuần 3)

Mục tiêu là mốc M2 ngày 7/11/2026 trong roadmap: đăng nhập, đăng xuất qua KeyCloak chạy trọn; menu đúng theo role. Điều kiện cụ thể ở mục 10. Module này làm CN-01 đến CN-05 và `GET /api/me`.

Thiết kế nằm ở `docs/02-auth.md`; file này ghi tuần 3 làm phần nào và làm thế nào. Chỗ nào file này ghi cụ thể hơn hoặc khác `docs/02-auth.md` thì làm theo file này, và sửa `docs/02-auth.md` cho khớp trong cùng Pull Request.

## 1. Làm và không làm

Làm:

- Realm `alp` nạp từ file JSON khi KeyCloak khởi động, có sẵn 4 tài khoản demo khớp `keycloak_id` trong `db/seeds/R__2_nhan_vien.sql`.
- Đăng nhập bằng `oauth2Login`, session trên Redis, CSRF cho SPA, đăng xuất cả ở KeyCloak.
- Hồ sơ `employee` tự thêm hoặc cập nhật sau mỗi lần đăng nhập; `GET /api/me`.
- Phân quyền chung cho `/api`: người chưa có role chỉ gọi được `/api/me`.
- React: gọi API, người đang đăng nhập, layout, menu theo role, chặn route, màn Chờ cấp quyền, trang 403, trang 404.
- Đăng ký và quên mật khẩu: chỉ bật trong realm, dùng trang của KeyCloak, không viết code.
- Test theo mục 8.

Không làm, để đúng tuần của nó:

- Màn Nhân viên, code gọi Admin API của KeyCloak, gán role, gán chi nhánh, khóa nhân viên kể cả bộ lọc chặn người bị khóa (tuần 4, A1).
- Mail báo tài khoản đã được kích hoạt (tuần 4, đi cùng thao tác gán role).
- Nội dung màn Tổng quan và các màn nghiệp vụ; số đếm trên menu (Chờ duyệt, Nhân viên) thêm ở tuần có API tương ứng.
- Các trạng thái còn lại của màn 3.13: bảng rỗng, đang tải, lỗi 500 (tuần 10).
- Đổi logo và màu trang đăng nhập KeyCloak (tuần 10, A2).

## 2. File mới và file sửa

```
docker/keycloak/import/alp-realm.json   realm alp (KeyCloak bắt buộc tên file dạng <realm>-realm.json)
compose.yaml                            keycloak nạp realm, cấu hình hostname; backend nối Redis, KeyCloak
.env.example                            biến mới ở mục 4
backend/src/main/java/io/github/arielne/alp/
├── auth/        SecurityConfig, đổi role, xử lý sau đăng nhập, đăng xuất, /api/me
└── employee/    Employee, EmployeeMapper + XML, EmployeeService
frontend/src/
├── api/         axios dùng chung, xử lý 401
├── layout/      AppLayout (Sider + Header), danh sách mục menu
├── pages/auth/  người đang đăng nhập (React Context), màn Chờ cấp quyền
└── components/  chặn route theo role, trang 403, trang 404, trang "màn làm ở tuần N"
docs/02-auth.md                         sửa cho khớp file này
```

- `auth` gọi `employee`, một chiều theo bảng module trong `CLAUDE.md`. Mapper của `employee` JOIN bảng `branch` để đọc tên chi nhánh.
- Bỏ trang tạm `pages/setup/SetupCheckPage` của tuần 2.

## 3. Realm `alp`

Cấu hình realm nằm hết trong `alp-realm.json`, không bấm tay trong Admin Console. Giá trị bí mật ghi dạng `${TÊN_BIẾN}`: lúc nạp, KeyCloak thay bằng biến môi trường của container.

| Mục | Cấu hình |
| --- | --- |
| Realm | Tên `alp`, tên hiển thị "Ngân hàng Demo" |
| Ngôn ngữ | Bật Internationalization, chỉ tiếng Việt (`vi`), mặc định `vi`. KeyCloak 26.8.1 có sẵn bản dịch tiếng Việt cho trang đăng nhập và đăng ký |
| Đăng nhập | Email as username; cho đăng nhập bằng email; bật User registration, Forgot password, Remember me; tắt Verify email |
| Chặn đoán mật khẩu | Brute force detection, sai 5 lần thì khóa tạm |
| Phiên | SSO Session Idle 30 phút |
| Mật khẩu | Ít nhất 8 ký tự, có chữ hoa, có chữ số |
| Gửi mail | Host `mailpit`, port 1025, người gửi `no-reply@nhdemo.local`, tên người gửi "Ngân hàng Demo" |
| Realm role | `MAKER`, `CHECKER`, `ADMIN`. Default roles của realm không gồm 3 role này |
| Client `alp-portal` | Có secret `${KC_PORTAL_CLIENT_SECRET}`; bật Standard flow; tắt Direct access grants. Redirect URI: `http://localhost/login/oauth2/code/keycloak` và `http://localhost:5173/login/oauth2/code/keycloak`. Post logout redirect URI: `http://localhost/` và `http://localhost:5173/`. Có mapper đưa realm role vào ID token, tên claim `realm_access.roles` |
| Client `alp-admin` | Bật Service accounts, tắt Standard flow; gán role `manage-users` và `view-users` của client `realm-management`; secret `${KC_ADMIN_CLIENT_SECRET}`. Tuần 3 chưa có code gọi tới (A3) |

Tài khoản demo, `id` phải đúng bằng `keycloak_id` trong seed:

| Email | id | Họ (lastName) | Tên (firstName) | Role |
| --- | --- | --- | --- | --- |
| `lan.nt@nhdemo.local` | `5b30439a-4e15-42ac-bf7d-c4e8dba67784` | Nguyễn Thị | Lan | `MAKER` |
| `khoa.tm@nhdemo.local` | `f862d39b-b598-4d60-9a20-ae0c70ac22bc` | Trần Minh | Khoa | `CHECKER` |
| `anh.vd@nhdemo.local` | `6356f260-3832-45bc-bb17-9ce71e0a4407` | Võ Đức | Anh | `MAKER`, `CHECKER` |
| `phuc.lh@nhdemo.local` | `927c941a-3244-4299-8a41-7c78b44d08e6` | Lê Hoàng | Phúc | `ADMIN` |

- Cả 4 người dùng chung mật khẩu `${DEMO_PASSWORD}`, không bắt đổi ở lần đầu, email đã xác minh.
- Họ tên kiểu Việt: KeyCloak tách Họ và Tên. Backend ghép `family_name` + dấu cách + `given_name` thành `full_name`, ví dụ "Nguyễn Thị Lan", khớp với seed. Không dùng claim `name`, vì KeyCloak ghép theo thứ tự Tên trước Họ sau.

Nạp realm:

- Container chạy `start-dev --import-realm`, gắn thư mục `docker/keycloak/import/` vào `/opt/keycloak/data/import` ở chế độ chỉ đọc.
- KeyCloak chỉ nạp khi realm chưa có; realm đã có thì bỏ qua file. Sửa file JSON sau khi đã nạp thì: Admin Console → realm `alp` → Realm settings → Action → Delete, rồi `docker compose restart keycloak`. Xóa realm thì mất các user tự đăng ký thêm. Không dùng `docker compose down -v`.
- Đổi secret hay `DEMO_PASSWORD` trong `.env` sau khi đã nạp thì KeyCloak vẫn giữ giá trị cũ: phải nạp lại realm như trên.

## 4. Compose, `.env` và địa chỉ KeyCloak

Trình duyệt mở KeyCloak ở `http://localhost:8080`, còn backend chạy trong Docker gọi KeyCloak bằng `http://keycloak:8080`. Để token luôn ghi cùng một `iss`:

- KeyCloak: `KC_HOSTNAME=http://localhost:8080` và `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true`. Đường trình duyệt cố định; đường backend gọi vào được tính theo địa chỉ backend đã dùng.
- Backend có 2 biến: `KEYCLOAK_URL` (đường trình duyệt, mặc định `http://localhost:8080`) cho trang đăng nhập và trang đăng xuất; `KEYCLOAK_BACKCHANNEL_URL` (đường backend gọi, mặc định bằng `KEYCLOAK_URL`, trong Docker là `http://keycloak:8080`) để đổi mã lấy token và lấy khóa công khai.
- Khai báo từng endpoint trong `application.yml`, không dùng `issuer-uri`. Có `issuer-uri` thì Spring gọi KeyCloak ngay lúc khởi động: CI không có KeyCloak nên test hỏng, còn trong Docker thì gặp lỗi issuer không khớp.
- Không gọi userinfo: họ, tên, email, role đều lấy từ ID token.

Biến mới trong `.env.example`; người dùng tự điền giá trị vào `.env`:

| Biến | Dùng cho | Cách điền |
| --- | --- | --- |
| `KC_PORTAL_CLIENT_SECRET` | Secret của client `alp-portal`; KeyCloak và backend cùng đọc | Chuỗi ngẫu nhiên, ví dụ chạy `[guid]::NewGuid().ToString('N')` trong PowerShell |
| `KC_ADMIN_CLIENT_SECRET` | Secret của client `alp-admin`; KeyCloak đọc từ tuần 3, backend dùng từ tuần 4 | Như trên |
| `DEMO_PASSWORD` | Mật khẩu chung của 4 tài khoản demo | Tự đặt, đúng chính sách mật khẩu ở mục 3 |

Compose:

- `keycloak`: thêm `--import-realm`, volume import, `KC_HOSTNAME`, `KC_HOSTNAME_BACKCHANNEL_DYNAMIC` và các biến trong bảng trên.
- `backend`: thêm `KC_PORTAL_CLIENT_SECRET`, `KEYCLOAK_BACKCHANNEL_URL=http://keycloak:8080`, địa chỉ Redis là `redis`; chờ `keycloak` và `redis` chạy trước.

Cả hai cách chạy đều phải đăng nhập được:

- Hằng ngày: `docker compose up -d`, `mvnw spring-boot:run`, `npm run dev`, mở `http://localhost:5173`.
- Như lúc demo: `docker compose --profile app up -d --build`, mở `http://localhost`.

## 5. Backend

### Thư viện

`spring-boot-starter-security`, `spring-boot-starter-oauth2-client`, `spring-session-data-redis`, `spring-boot-starter-data-redis`; cho test thêm `spring-security-test`. Chưa thêm `keycloak-admin-client` (tuần 4).

### Cấu hình

- Client đăng ký tên `keycloak`, nên đường dẫn là `/oauth2/authorization/keycloak` và `/login/oauth2/code/keycloak`, khớp Nginx và Vite proxy. Scope `openid profile email`.
- Spring Session trên Redis: cookie `SESSION`, HttpOnly, SameSite=Lax; hết hạn sau 30 phút không hoạt động, khớp SSO Session Idle của realm.
- `server.forward-headers-strategy: framework` để đọc header Nginx gửi kèm.

### Phân quyền (`SecurityConfig`)

| Đường dẫn | Ai gọi được |
| --- | --- |
| `/actuator/health` | Mọi người |
| `/api/me` | Người đã đăng nhập, kể cả chưa có role |
| `/api/**` còn lại | Người có ít nhất 1 trong 3 role; chưa có role thì 403 |

- Chưa đăng nhập gọi `/api/**` thì trả 401 dạng JSON, không chuyển hướng.
- Role lấy từ `realm_access.roles` trong ID token, đổi thành `ROLE_MAKER`, `ROLE_CHECKER`, `ROLE_ADMIN`.
- CSRF đúng mẫu "Single-Page Applications" của Spring Security 6.5: `CookieCsrfTokenRepository.withHttpOnlyFalse()` cùng `SpaCsrfTokenRequestHandler`.
- Phân quyền riêng cho từng API (ví dụ duyệt phiếu cần `CHECKER`) thêm vào ở tuần làm API đó.

### Sau khi đăng nhập

Ngay khi đăng nhập thành công, `auth` gọi `EmployeeService` thêm hoặc cập nhật hồ sơ theo token, bằng câu `INSERT ... ON CONFLICT` ở mục 4 của `docs/02-auth.md`. Lần đầu thêm dòng với `status = ACTIVE` và `branch_id` rỗng; các lần sau chỉ cập nhật email và họ tên, không đụng `branch_id`, `status`. Xong thì chuyển về trang chủ `/`.

### `GET /api/me`

```json
{
  "keycloakId": "5b30439a-4e15-42ac-bf7d-c4e8dba67784",
  "fullName": "Nguyễn Thị Lan",
  "email": "lan.nt@nhdemo.local",
  "roles": ["MAKER"],
  "branch": { "id": 4, "code": "CN-TP", "name": "Chi nhánh Tân Phú" }
}
```

- `roles` lấy từ session; người chưa có role nhận `[]`.
- `branch` lấy từ `employee` JOIN `branch`; chưa gán chi nhánh thì là `null`.

### Đăng xuất

`POST /logout`, kèm CSRF token như mọi request ghi. Backend xóa session ở Redis rồi trả `{"logoutUrl": "..."}`: địa chỉ đăng xuất của KeyCloak (đường trình duyệt) kèm `id_token_hint` và `post_logout_redirect_uri`. `post_logout_redirect_uri` là trang chủ của đúng địa chỉ người dùng đang mở (`http://localhost/` hoặc `http://localhost:5173/`). Không trả chuyển hướng; lý do ở mục "Lỗi hay gặp" của `docs/02-auth.md`.

### Bộ lọc chặn nhân viên bị khóa

Không làm ở tuần 3 (A1). Tuần 4 làm cùng thao tác Khóa trên màn Nhân viên.

## 6. Frontend

### Gọi API

- Một instance axios dùng chung trong `api/`. Axios tự đọc cookie `XSRF-TOKEN` và gửi header `X-XSRF-TOKEN` cho request cùng origin, không cần viết thêm.
- Gặp 401:
  - Lúc mở app (`GET /api/me`): chuyển thẳng trình duyệt tới `/oauth2/authorization/keycloak`.
  - Các API khác (phiên hết hạn khi đang dùng): hiện `Modal.warning` "Phiên đăng nhập đã hết hạn" theo màn 3.13; bấm "Đăng nhập lại" mới chuyển.

### Người đang đăng nhập

- Gọi `/api/me` một lần khi mở app bằng TanStack Query, để kết quả trong React Context.
- Đang tải: Spin giữa trang.
- `roles` rỗng: chỉ hiện màn Chờ cấp quyền (Result, không có layout, chỉ có nút Đăng xuất), theo `docs/design/TrangThaiPhu.dc.html`.

### Layout (màn 2.1, `docs/design/Layout.dc.html`)

- Sider rộng 232, Menu nền tối; trên cùng là "Ngân hàng Demo" và "Admin Portal".
- Header: họ tên, tên chi nhánh, một Tag cho mỗi role, nút Đăng xuất. Người chưa gán chi nhánh (Admin) thì không hiện phần chi nhánh.
- Content padding 24, nền `colorBgLayout`. Màu lấy từ `theme/theme.ts` đã có, không viết CSS đè class của AntD.
- Đăng xuất: gọi `POST /logout`, rồi chuyển trình duyệt tới `logoutUrl` nhận được.

### Menu theo role

Một mảng mục menu, mỗi mục có route và danh sách role được thấy; người giữ nhiều role thấy hợp các role. Route theo mục 3 của `docs/01-chuc-nang.md`:

| Mục | Route | `MAKER` | `CHECKER` | `ADMIN` | Màn thật làm ở |
| --- | --- | --- | --- | --- | --- |
| Tổng quan | `/` | Có | Có | Có | Tuần 5 |
| Khách hàng | `/khach-hang` | Có | Có | | Tuần 5 |
| Tài khoản | `/tai-khoan` | Có | Có | | Tuần 5 |
| Phiếu yêu cầu › Tạo phiếu | `/phieu/tao-moi` | Có | | | Tuần 6 |
| Phiếu yêu cầu › Chờ duyệt | `/phieu/cho-duyet` | | Có | | Tuần 6 |
| Phiếu yêu cầu › Tất cả phiếu | `/phieu` | Có | Có | | Tuần 6 |
| Danh mục › Loại tài khoản | `/danh-muc/loai-tai-khoan` | | | Có | Tuần 4 |
| Danh mục › Chi nhánh | `/danh-muc/chi-nhanh` | | | Có | Tuần 4 |
| Nhân viên | `/nhan-vien` | | | Có | Tuần 4 |
| Báo cáo & Import › Import Excel | `/bao-cao/import` | Có | | | Tuần 8 |
| Báo cáo & Import › Xuất báo cáo | `/bao-cao/export` | Có | Có | | Tuần 8 |
| Audit log | `/audit-log` | | | Có | Tuần 4 |

Bản thiết kế ghi một route `/bao-cao` cho "Báo cáo & Import"; làm theo 2 route của `docs/01-chuc-nang.md` vì đó là nguồn chuẩn.

### Chặn route

- Mỗi route bọc bằng một component kiểm role, dùng chung mảng role với menu. Không đủ role thì hiện trang 403: Result 403, câu "Bạn không có quyền truy cập trang này.", nút "Về Tổng quan".
- Route không tồn tại: trang 404 đơn giản (Result 404).
- Màn chưa làm: hiện trang "Màn này làm ở tuần N", lấy N ở cột cuối của bảng menu (A5).
- Không đặt route `/login`, `/logout`, `/oauth2`.

## 7. Thứ tự làm

1. Realm JSON, Compose, `.env.example`. Kiểm: KeyCloak có realm `alp`, đăng nhập được trang Account của realm bằng `lan.nt@nhdemo.local`.
2. Backend: thư viện, cấu hình, `SecurityConfig`, hồ sơ nhân viên, `/api/me`, đăng xuất, test. Kiểm khi chưa có giao diện: chạy `npm run dev`, mở `http://localhost:5173/oauth2/authorization/keycloak`, đăng nhập, rồi mở `http://localhost:5173/api/me` thấy JSON.
3. Frontend: gọi API, người đang đăng nhập, layout, menu, chặn route, các trang phụ, test.

## 8. Test

Backend, test nhanh (`*Test`, dùng `@WebMvcTest` với `oidcLogin()` và `csrf()`, không cần database, Redis, KeyCloak):

1. Chưa đăng nhập gọi `/api/me` → 401 dạng JSON.
2. Đăng nhập với role `MAKER` gọi `/api/me` → 200, đúng họ tên, role, chi nhánh.
3. Chưa có role gọi `/api/me` → 200, `roles` rỗng; gọi một đường dẫn `/api` khác → 403.
4. Có role gọi một đường dẫn `/api` chưa có → 404, không phải 403.
5. `POST /logout` thiếu CSRF → 403; đủ CSRF → 200, `logoutUrl` có `id_token_hint` và `post_logout_redirect_uri`.
6. Đổi `realm_access.roles` thành quyền của Spring: có role, không có role, thiếu claim.

Backend, test chậm (`*IT`, Testcontainers):

7. `EmployeeMapperIT`: lần đầu thêm đúng 1 dòng; lần hai không thêm dòng, cập nhật email và họ tên; `branch_id` và `status` giữ nguyên.

- Test nào khởi động cả app (ví dụ `HealthEndpointIT`) thì có thêm container Redis cùng image với Compose. Project có Redis thì health của Spring Boot kiểm cả Redis; thiếu Redis là health báo DOWN.
- CI không có `.env`, KeyCloak, Redis thật: mọi test phải chạy được mà không cần secret thật.

Frontend (Vitest + React Testing Library):

8. Menu đúng cho `MAKER`, `CHECKER`, `ADMIN` và cho người giữ cả `MAKER` lẫn `CHECKER`.
9. Vào route trái quyền → trang 403.
10. `roles` rỗng → màn Chờ cấp quyền, không có menu.
11. `/api/me` trả 401 → chuyển tới `/oauth2/authorization/keycloak`.

## 9. Nhánh và Pull Request

Chia 2 Pull Request (A4):

1. `feature/auth-backend`: bước 1 và 2 ở mục 7, kèm file này và phần sửa `docs/02-auth.md`.
2. `feature/auth-frontend`: bước 3, tạo nhánh sau khi PR thứ nhất đã merge.

Phần cho Claude Code trên máy, không lên Git: thêm vào mục "Lệnh" của `CLAUDE.md` cách nạp lại realm; ghi vào `LEARNING.md` các mục OAuth2 Login, Spring Session với Redis, CSRF cho SPA, nạp realm KeyCloak.

## 10. Xong khi

- Đăng nhập được cả 4 tài khoản demo; header hiện đúng họ tên, chi nhánh, role.
- Lan và Khoa đăng nhập cùng lúc trong 2 cửa sổ trình duyệt (một cửa sổ thường, một cửa sổ ẩn danh), mỗi người thấy đúng menu của mình.
- Tự đăng ký một tài khoản mới → vào màn Chờ cấp quyền; bảng `employee` có thêm 1 dòng, chi nhánh rỗng.
- Quên mật khẩu → mail đặt lại mật khẩu hiện trong Mailpit (`http://localhost:8025`).
- Đăng xuất xong bấm đăng nhập lại thì phải nhập mật khẩu.
- Lan gõ thẳng `/nhan-vien` → trang 403. Nhập sai mật khẩu 5 lần → KeyCloak khóa tạm.
- Session nằm trong Redis: lúc đang đăng nhập, `docker compose exec redis redis-cli --scan --pattern "spring:session:*"` có kết quả.
- Chạy được cả 2 cách ở mục 4.
- `mvnw verify` và `npm test` xanh trên máy và trên GitHub Actions.
- Tự kiểm: vẽ lại luồng đăng nhập không nhìn tài liệu; giải thích cookie `SESSION` và `XSRF-TOKEN` dùng để làm gì.

## 11. Đã chốt (28/10/2026)

1. A1: bộ lọc chặn nhân viên bị khóa làm ở tuần 4, cùng thao tác Khóa. Khóa chỉ trọn vẹn khi cùng lúc đổi `status`, tắt tài khoản trên KeyCloak và đăng xuất các phiên KeyCloak; hai việc sau cần Admin API. Làm riêng bộ lọc thì khi thử, trình duyệt chạy vòng: bị 401 → về KeyCloak → phiên KeyCloak còn nên vào lại ngay → lại bị 401.
2. A2: tuần 3 dùng giao diện mặc định của KeyCloak, chữ tiếng Việt, tên "Ngân hàng Demo"; đổi logo và màu (màn 3.1) ở tuần 10. Quick Theme trong KeyCloak 26.8.1 vẫn là tính năng thử nghiệm và là phần trang trí; bản dịch tiếng Việt có sẵn.
3. A3: client `alp-admin` khai báo luôn trong `alp-realm.json` ở tuần 3. KeyCloak chỉ nạp file realm khi realm chưa có; để tuần 4 mới thêm thì phải xóa realm rồi nạp lại (mất user tự đăng ký) hoặc bấm tay, làm file JSON lệch với KeyCloak.
4. A4: chia 2 Pull Request, backend trước, frontend sau. Mỗi PR nhỏ, đọc và giải thích được trước khi merge; backend kiểm được riêng trước khi có giao diện.
5. A5: menu hiện đủ mục theo role; màn chưa làm hiện trang "Màn này làm ở tuần N". Kiểm được "menu đúng theo role" của M2 ngay ở tuần 3 với cả 3 role; các tuần sau chỉ thay trang chờ bằng màn thật.