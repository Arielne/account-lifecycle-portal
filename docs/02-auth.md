# Đăng nhập và phân quyền

Thiết kế phần đăng nhập của Account Lifecycle Portal: CN-01 đến CN-05 (đăng nhập, đăng xuất, đăng ký, quên mật khẩu, chờ cấp quyền) và phần KeyCloak của CN-22 (quản lý nhân viên). Danh sách chức năng và quy tắc đầy đủ ở `docs/01-chuc-nang.md`.

## 1. Quyết định đã chốt

- KeyCloak 26.8.1 làm SSO, cấp JWT theo chuẩn OIDC.
- Session phía server: backend giữ token trong session (Spring Session trên Redis), trình duyệt chỉ có cookie phiên. React không cầm token.
- KeyCloak giữ tài khoản, mật khẩu và role. Supabase (schema `app`) giữ hồ sơ nhân viên và chi nhánh, nối với KeyCloak bằng `keycloak_id` (trường `sub` trong token).
- Role chỉ lưu ở KeyCloak, gồm 3 realm role `MAKER`, `CHECKER`, `ADMIN`. DB không có bảng role, không giữ bản sao role.
- KeyCloak là nguồn chuẩn của họ tên và email. Bảng `employee` giữ bản sao để hiển thị và gửi mail, cập nhật lại ở mỗi lần đăng nhập.
- Đăng nhập bằng email (Email as username). Không bắt xác minh email khi tự đăng ký. Bật chặn đoán mật khẩu.
- Đăng nhập, đăng ký, quên mật khẩu dùng trang có sẵn của KeyCloak, đổi giao diện bằng theme của realm. App không tự viết trang đăng nhập, không lưu mật khẩu.
- Truy cập DB chỉ dùng MyBatis.

| Thành phần | Làm gì | Không làm gì |
| --- | --- | --- |
| KeyCloak | Trang đăng nhập, đăng ký, quên mật khẩu; lưu tài khoản và mật khẩu đã hash; quản lý role; cấp JWT | Không biết gì về dữ liệu nghiệp vụ |
| Spring Boot | Chuyển người dùng sang KeyCloak, nhận và kiểm tra token, giữ session, kiểm tra quyền ở mỗi request, gọi Admin API của KeyCloak | Không lưu mật khẩu, không tự ký token |
| React | Hiển thị theo role, gọi `/api` kèm cookie, xử lý lỗi 401 | Không cầm token |
| Supabase | Bảng `employee` (hồ sơ, chi nhánh), nối bằng `keycloak_id` | Không chứa mật khẩu, không chứa role |
| Redis | Lưu session đăng nhập; cache danh mục | Không lưu dữ liệu lâu dài |

## 2. Luồng hoạt động

### Đăng nhập

1. Người dùng mở portal. React gọi `GET /api/me`; chưa đăng nhập nên backend trả 401.
2. React chuyển trình duyệt tới `/oauth2/authorization/keycloak` (đường dẫn có sẵn của Spring Security).
3. Spring Boot chuyển tiếp sang trang đăng nhập của KeyCloak.
4. Người dùng nhập tài khoản trên trang KeyCloak. Đúng thì KeyCloak chuyển về `/login/oauth2/code/keycloak` kèm một mã dùng một lần (authorization code).
5. Spring Boot gọi thẳng KeyCloak, đưa mã đó cùng client secret để đổi lấy token (ID token và access token, đều là JWT). Spring Boot kiểm tra chữ ký bằng khóa công khai của KeyCloak, đọc role, tạo session lưu ở Redis và gửi cookie phiên `SESSION` cho trình duyệt.
6. Backend thêm hoặc cập nhật dòng hồ sơ trong bảng `employee` theo token: lần đầu thì thêm, các lần sau thì cập nhật họ tên và email (mục 4).
7. Trình duyệt quay về portal. Từ giờ mọi request tới `/api` tự kèm cookie phiên.

Mật khẩu chỉ đi giữa trình duyệt và KeyCloak; Spring Boot và React không bao giờ thấy mật khẩu.

### Mỗi request sau đó

Cookie phiên → Spring Session tìm session trong Redis → biết người dùng và role → kiểm tra quyền → vào Controller. Session hết hạn thì trả 401, React chuyển sang đăng nhập lại; nếu phiên SSO ở KeyCloak còn, người dùng vào lại ngay mà không phải gõ mật khẩu.

### Đăng xuất

1. Người dùng bấm Đăng xuất ở góc phải header. React gửi `POST /logout` bằng axios, kèm CSRF token như mọi request.
2. Backend xóa session trong Redis, rồi trả về (dạng JSON) địa chỉ trang đăng xuất của KeyCloak kèm `id_token_hint` và `post_logout_redirect_uri`. Backend không trả chuyển hướng.
3. React chuyển trình duyệt tới địa chỉ đó. KeyCloak kết thúc phiên SSO rồi chuyển về trang chủ portal.

Thiếu bước 3 thì bấm đăng nhập lại sẽ vào thẳng mà không phải nhập mật khẩu, vì KeyCloak vẫn nhớ phiên.

### Chờ cấp quyền

- Người tự đăng ký trên trang KeyCloak không có role nào. Default roles của realm không gồm `MAKER`, `CHECKER`, `ADMIN`.
- Không bắt xác minh email: đăng ký xong là đăng nhập được và vào thẳng trang chờ cấp quyền. Admin kiểm tra người đó trước khi cấp quyền.
- Đăng nhập thành công nhưng không có role: `GET /api/me` trả danh sách role rỗng; React chỉ hiện trang "Tài khoản đang chờ cấp quyền" với nút Đăng xuất (CN-05). Mọi API nghiệp vụ trả 403.
- Người này hiện ở đầu danh sách màn Nhân viên với trạng thái Chờ cấp quyền. Admin gán chi nhánh rồi gán role; hệ thống gửi mail báo tài khoản đã được kích hoạt.

### Đổi role, khóa nhân viên

- Role nằm trong token lấy lúc đăng nhập, nên được thêm role thì nhân viên phải đăng nhập lại mới nhận quyền mới.
- Bị bỏ role thì không chờ người đó tự đăng nhập lại: nếu không, phiên đang mở vẫn giữ role vừa bỏ tới khi họ đăng xuất. Backend ghi `employee.sessions_invalid_before` là thời điểm ngay sau khi KeyCloak bỏ role; phiên nào tạo trước thời điểm đó bị đẩy ra ở thao tác kế tiếp. Phiên SSO ở KeyCloak vẫn còn, nên bấm đăng nhập lại là vào ngay với token không còn role đã bỏ.
- Khóa nhân viên: backend cập nhật `employee.status = LOCKED` và `sessions_invalid_before`, đặt `enabled = false` qua Admin API và đăng xuất các phiên của người đó ở KeyCloak. Phiếu họ đã tạo giữ nguyên.
- Người bị khóa hoặc bị bỏ role có thể đang đăng nhập, mà session trong Redis chỉ hết hạn sau 30 phút không hoạt động. Vì vậy mỗi request vào `/api` đi qua một bộ lọc (`RevokedSessionFilter`) đọc `employee.status` và `sessions_invalid_before` của người trong session; gặp `LOCKED`, hoặc session tạo trước `sessions_invalid_before`, thì xóa session hiện tại và trả 401. Người bị khóa bị đẩy ra ngay ở thao tác kế tiếp và không đăng nhập lại được.
- Mở khóa: đặt `enabled = true` và `status = ACTIVE`.

### Đổi tài khoản và demo nhiều vai trò

App không có nút đổi người dùng. Muốn đổi tài khoản thì bấm Đăng xuất, đi hết 3 bước ở trên, rồi đăng nhập tài khoản khác.

- Không làm nút đổi người dùng vì maker–checker cần hai người khác nhau: một người tự chuyển qua lại được thì tự tạo rồi tự duyệt, và Audit log ghi sai người làm.
- Demo: mở 2 cửa sổ trình duyệt riêng, ví dụ Chrome thường và Chrome ẩn danh. Mỗi cửa sổ giữ cookie riêng nên là 2 phiên riêng. Hai tab trong cùng một cửa sổ dùng chung cookie, tức là cùng một người. Cần thêm Admin thì mở thêm một profile Chrome.
- Tài khoản demo khai báo sẵn trong file realm JSON với id cố định, để dữ liệu mẫu trong `db/seeds/` dùng đúng `keycloak_id`. Mật khẩu ghi bằng biến `${DEMO_PASSWORD}`, giá trị thật nằm trong `.env`.

| Tài khoản | Vai trò | Chi nhánh | Dùng để demo |
| --- | --- | --- | --- |
| `lan.nt@nhdemo.local` | Giao dịch viên | Tân Phú | Tạo phiếu, import, hủy phiếu |
| `khoa.tm@nhdemo.local` | Kiểm soát viên | Tân Phú | Duyệt, từ chối phiếu |
| `anh.vd@nhdemo.local` | Giao dịch viên + Kiểm soát viên | Quận 1 | Không được tự duyệt phiếu mình tạo |
| `phuc.lh@nhdemo.local` | Admin | Không cần | Nhân viên, danh mục, audit log |

### Đăng ký và quên mật khẩu

- Trang đăng nhập của KeyCloak có sẵn link đăng ký và quên mật khẩu khi bật trong realm. App không viết code cho hai việc này.
- Quên mật khẩu: KeyCloak gửi mail đặt lại mật khẩu qua Mailpit; mở `http://localhost:8025` để xem mail.
- App chỉ biết người tự đăng ký khi họ đăng nhập lần đầu (bước 6 ở trên).

## 3. Cấu hình KeyCloak

| Thứ | Cấu hình | Ghi chú |
| --- | --- | --- |
| Realm | Realm riêng cho project | Không dùng realm `master`, realm đó để quản trị KeyCloak |
| Client `alp-portal` cho backend | Bật Client authentication (có secret), bật Standard flow, tắt Direct access grants | Valid redirect URIs: `/login/oauth2/code/keycloak` của portal; Post logout redirect URIs: trang chủ portal |
| Client `alp-admin` quản lý user | Client riêng, bật Service accounts, gán role `manage-users`, `view-users` và `view-realm` (để đọc role và thành viên của role) của client `realm-management` | Backend dùng client này gọi Admin API (từ tuần 4) |
| Ngôn ngữ | Bật Internationalization, chỉ tiếng Việt (`vi`) | KeyCloak có sẵn bản dịch tiếng Việt |
| Realm roles | `MAKER`, `CHECKER`, `ADMIN` | Bật mapper "realm roles" thêm role vào ID token để backend đọc được ngay lúc đăng nhập |
| Đăng ký, quên mật khẩu | Realm settings → Login: bật User registration, Forgot password và Remember me; tắt Verify email | Default roles không gồm 3 role trên |
| Đăng nhập bằng email | Realm settings → Login: bật Email as username | Tài khoản demo và nhân viên dùng email làm tên đăng nhập |
| Chặn đoán mật khẩu | Realm settings → Security defenses: bật Brute force detection, nhập sai 5 lần thì khóa tạm | Lúc demo gõ sai nhiều lần sẽ bị khóa tạm |
| Thời gian phiên | Realm settings → Sessions: SSO Session Idle 30 phút | Khớp với 30 phút của Spring Session; đổi một bên thì đổi cả bên kia |
| Gửi mail | Realm settings → Email: host `mailpit`, port `1025`, người gửi `no-reply@nhdemo.local` tên "Ngân hàng Demo" | Mailpit chỉ giữ mail để xem, không gửi ra ngoài |
| Mật khẩu | Authentication → Policies: độ dài tối thiểu, chữ hoa, chữ số | Hash mặc định là Argon2 có salt |
| Giao diện | Tuần 3 dùng giao diện mặc định của KeyCloak, chữ tiếng Việt, tên hiển thị "Ngân hàng Demo"; đổi logo và màu (màn 3.1) ở tuần 10 | Quick Theme của KeyCloak 26.8.1 vẫn là tính năng thử nghiệm |
| Đường dẫn | KeyCloak chạy ở cổng riêng `http://localhost:8080` | Xem lỗi "issuer không khớp" ở mục 4 |

- Lưu cấu hình: realm viết hết trong `docker/keycloak/import/alp-realm.json`, không bấm tay trong Admin Console; container khởi động với `--import-realm` để tự dựng lại realm. KeyCloak chỉ nạp khi realm chưa có: sửa file thì xóa realm `alp` trong Admin Console rồi `docker compose restart keycloak` (chi tiết ở mục 3 của `docs/modules/auth.md`).
- Dữ liệu user tạo thêm nằm trong file H2 của container, gắn Docker volume. Không chạy `docker compose down -v`. KeyCloak không dùng Supabase vì KeyCloak mặc định mở tới 100 kết nối, gói Free của Supabase chỉ cho 60 kết nối trực tiếp.
- Secret: mật khẩu admin KeyCloak, secret của 2 client, `DEMO_PASSWORD` để trong `.env`, không commit. Repo chỉ có `.env.example` ghi tên biến.

## 4. Backend (Spring Boot)

### Thư viện

- `spring-boot-starter-security` và `spring-boot-starter-oauth2-client`: đăng nhập qua KeyCloak.
- `spring-session-data-redis` và `spring-boot-starter-data-redis`: lưu session ở Redis.
- `keycloak-admin-client` 26.0.x để gọi Admin API, cùng màn Nhân viên. Mọi lời gọi gom trong `employee/KeycloakUserClient`.

### SecurityConfig

- Bật `oauth2Login()` để đăng nhập qua KeyCloak.
- `/api/**` phải đăng nhập. Với `/api`, chưa đăng nhập thì trả 401 dạng JSON thay vì tự chuyển sang trang đăng nhập, để React tự xử lý.
- Phân quyền theo role ngay ở backend, ví dụ API duyệt phiếu cần `hasRole("CHECKER")`.
- Đổi role trong token (`realm_access.roles`) thành quyền của Spring (`ROLE_MAKER`, `ROLE_CHECKER`, `ROLE_ADMIN`).
- CSRF theo cách Spring Security 6.5 hướng dẫn cho SPA: `CookieCsrfTokenRepository.withHttpOnlyFalse()` cùng `SpaCsrfTokenRequestHandler`. Token nằm trong cookie `XSRF-TOKEN`, React gửi lại qua header `X-XSRF-TOKEN`.
- Logout: xóa session, rồi trả về dạng JSON địa chỉ đăng xuất của KeyCloak kèm `id_token_hint` và `post_logout_redirect_uri`.

### Hồ sơ nhân viên

Bảng `app.employee` (chi tiết ở `docs/diagrams/erd.drawio`):

| Cột | Kiểu | Ghi chú |
| --- | --- | --- |
| `id` | bigint | PK |
| `keycloak_id` | uuid | UNIQUE, chính là `sub` trong token |
| `email` | varchar(150) | UNIQUE |
| `full_name` | varchar(100) | |
| `branch_id` | bigint, null | FK → `branch`; rỗng khi chưa được gán |
| `status` | varchar(20) | `ACTIVE`, `LOCKED`. Chờ cấp quyền không phải một giá trị ở đây: đó là người chưa có role trong KeyCloak |
| `created_at`, `updated_at` | timestamptz | |
| `sessions_invalid_before` | timestamptz, null | Phiên tạo trước thời điểm này đã bị thu hồi (bị bỏ role, bị khóa); rỗng khi chưa thu hồi lần nào |

- Họ tên ghép kiểu Việt từ ID token: `family_name` + dấu cách + `given_name`, ví dụ "Nguyễn Thị Lan". Không dùng claim `name`, vì KeyCloak ghép Tên trước Họ sau.
- Mỗi lần đăng nhập: `INSERT ... ON CONFLICT (keycloak_id) DO UPDATE` lấy họ tên và email từ token. Lần đầu thì thêm dòng, các lần sau thì cập nhật, chạy bao nhiêu lần cũng không bị trùng. Lệnh này không đụng tới `branch_id` và `status`.
- Người dùng đổi email trong trang tài khoản của KeyCloak thì lần đăng nhập sau `employee` tự khớp lại.
- Email trong token đã thuộc hồ sơ có `keycloak_id` khác: không lưu hồ sơ, xóa session, đăng xuất khỏi KeyCloak rồi về trang `/loi-dang-nhap` của React. Không nối hồ sơ theo email vì realm không bắt xác minh email (chi tiết ở mục 6 của `docs/modules/admin.md`).
- `GET /api/me` trả họ tên, email, role (lấy từ session) và chi nhánh (lấy từ `employee`) của người đang đăng nhập. React gọi API này khi mở app.

### Chi nhánh của nhân viên

- Chi nhánh là dữ liệu nghiệp vụ nên nằm ở `employee.branch_id`, không nằm trong KeyCloak. Admin gán ở màn Nhân viên.
- Chi nhánh mở tài khoản và chi nhánh của phiếu là chi nhánh của người tạo. Backend lấy từ hồ sơ của người trong session, không nhận chi nhánh do frontend gửi lên; nếu nhận, người dùng sửa request là làm được việc cho chi nhánh khác (QT-04).

### Màn Nhân viên (CN-22)

| Thao tác | Làm ở đâu |
| --- | --- |
| Xem danh sách | Đọc bảng `employee` bằng MyBatis; gọi Admin API lấy thành viên của từng role (3 lần gọi, mỗi role một lần) rồi ghép theo `keycloak_id`. Người không có role nào là Chờ cấp quyền, xếp đầu danh sách |
| Lọc theo role | Lấy danh sách `keycloak_id` của role đó từ KeyCloak, rồi lọc hồ sơ theo danh sách đó |
| Thêm nhân viên | Admin API tạo tài khoản và gửi mail để người đó tự đặt mật khẩu (execute actions email với `UPDATE_PASSWORD`); lấy id KeyCloak trả về rồi thêm dòng `employee` bằng MyBatis |
| Sửa hồ sơ | Họ tên, email: sửa trên KeyCloak qua Admin API trước, thành công mới lưu vào `employee` bằng MyBatis |
| Gán chi nhánh | MyBatis cập nhật `branch_id`, chọn từ danh mục Chi nhánh |
| Gán role | Admin API gán realm role. Chỉ cho gán `MAKER` hoặc `CHECKER` khi hồ sơ đã có chi nhánh |
| Khóa, mở khóa | Hỏi xác nhận; cập nhật `status`; Admin API đặt `enabled`; khi khóa thì đăng xuất các phiên ở KeyCloak, bộ lọc ở mục 2 đẩy người đó ra ở request kế tiếp |

Không có transaction chung giữa KeyCloak và Supabase. Mỗi lần ghi lên KeyCloak, backend đăng ký việc làm ngược lại và chạy việc đó khi transaction của database bị hủy: tạo user xong mà lưu hồ sơ hay ghi audit lỗi thì xóa user vừa tạo; sửa, gán role, khóa, mở khóa thì trả KeyCloak về như cũ (`docs/modules/admin.md`, mục Thêm nhân viên).

### Chặn tự duyệt và duyệt khác chi nhánh

Trước khi báo Camunda hoàn thành task Duyệt phiếu hoặc Từ chối phiếu, backend so `keycloak_id` của người bấm với người tạo phiếu, và so chi nhánh của người bấm với chi nhánh của phiếu (QT-01, QT-04). Khóa nút trên giao diện chỉ là lớp thứ hai.

### Lỗi hay gặp

- **Issuer không khớp:** trình duyệt gọi KeyCloak bằng `http://localhost:8080`, còn backend trong Docker gọi bằng tên container `http://keycloak:8080`. Trường `iss` trong token phải khớp cấu hình của Spring. Cách xử lý: cố định hostname của KeyCloak là `http://localhost:8080`, cho backend gọi bằng địa chỉ trong Docker, và khai báo riêng từng endpoint trong `application.yml` thay vì chỉ một `issuer-uri`. Cụ thể: KeyCloak đặt `KC_HOSTNAME=http://localhost:8080` và `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true`; backend có `KEYCLOAK_URL` (đường trình duyệt, cho trang đăng nhập, đăng xuất) và `KEYCLOAK_BACKCHANNEL_URL` (đường backend gọi, để đổi mã lấy token và lấy khóa công khai).
- **Trang Account của KeyCloak báo "Đã xảy ra lỗi":** tài khoản khai báo trong file realm chỉ nhận đúng các role ghi trong file, không tự nhận role mặc định như người tự đăng ký. Thiếu `default-roles-alp` thì không có quyền `view-profile`, `manage-account` của client `account`. Mỗi tài khoản demo trong `alp-realm.json` phải có `default-roles-alp`.
- **Chuyển hướng sai địa chỉ:** request đi qua Nginx nên Spring phải đọc header `X-Forwarded-*` (`server.forward-headers-strategy`) để tạo đúng redirect URI.
- **Đăng xuất bằng axios mà backend trả chuyển hướng:** axios đi theo chuyển hướng trong nền, gặp lỗi CORS và trình duyệt không chuyển trang. Vì vậy backend trả địa chỉ dạng JSON.
- **Route React trùng đường dẫn của Spring:** không đặt route React là `/login`, `/logout`, `/oauth2`.
- **Tutorial cũ:** không dùng `keycloak-spring-boot-starter`, adapter này đã ngừng hỗ trợ.

## 5. React và Nginx

### React

- Không dùng `keycloak-js`, không lưu token ở đâu cả.
- Gọi API bằng axios với đường dẫn tương đối (`/api/...`), bọc bằng TanStack Query. Cùng domain nên cookie phiên tự được gửi.
- CSRF: axios mặc định đọc cookie `XSRF-TOKEN` và gắn header `X-XSRF-TOKEN` cho request cùng domain, khớp với cấu hình Spring ở mục 4.
- Interceptor phản hồi: gặp 401 thì chuyển trình duyệt tới `/oauth2/authorization/keycloak`.
- Khi mở app: gọi `/api/me`, lưu người dùng, role và chi nhánh vào React Context. Menu dựng từ một mảng mục, mỗi mục có danh sách role; route cũng chặn theo role, vào URL trái quyền thì hiện trang 403.
- Ẩn nút theo role chỉ để giao diện gọn; quyền thật kiểm tra ở backend.
- Đăng xuất: gọi `POST /logout`, nhận địa chỉ đăng xuất của KeyCloak, rồi chuyển trình duyệt tới địa chỉ đó.

### Nginx

Nginx là cửa vào của portal, để React và backend dùng chung một domain thì cookie mới gửi được.

| Đường dẫn | Đi tới |
| --- | --- |
| `/api/`, `/oauth2/`, `/login/oauth2/`, `/logout` | Backend Spring Boot |
| Còn lại | File build của React |

KeyCloak chạy ở cổng riêng `http://localhost:8080`; khi đăng nhập, trình duyệt sang thẳng địa chỉ đó. Demo chạy trên máy bằng localhost.

Spring tin mọi header `X-Forwarded-*` và `Forwarded` gửi tới (`server.forward-headers-strategy: framework`), kể cả header trình duyệt tự gửi. Vì vậy Nginx ghi đè `X-Forwarded-For` bằng IP mà Nginx thấy (`$remote_addr`, không nối thêm), và bỏ hẳn `Forwarded`, `X-Forwarded-Host`, `X-Forwarded-Port`, `X-Forwarded-Prefix`, `X-Forwarded-Ssl`. Compose chỉ mở cổng 8081 của backend cho `127.0.0.1`, để máy khác không gọi thẳng backend mà bỏ qua Nginx.

## 6. Bảo mật

| Mối nguy | Là gì | Thiết kế này chặn thế nào |
| --- | --- | --- |
| XSS | Script lạ chạy trong trang của mình | Token không xuống trình duyệt; cookie phiên HttpOnly nên JavaScript không đọc được. React tự escape nội dung; không dùng `dangerouslySetInnerHTML` |
| CSRF | Trang lạ lừa trình duyệt gửi request kèm cookie của mình | Cookie `SameSite=Lax` và CSRF token: trang lạ không đọc được cookie `XSRF-TOKEN` nên không gửi được header đúng |
| Lộ database | Ai đó đọc được dữ liệu trong DB | Supabase không chứa mật khẩu; KeyCloak lưu hash Argon2 có salt |
| Vượt quyền từ giao diện | Tự hiện nút bị ẩn hoặc gọi API trực tiếp | Mọi quyền kiểm tra ở backend, kể cả chặn tự duyệt và duyệt khác chi nhánh |
| Giả IP trong audit log | Tự gửi header `X-Forwarded-For` với IP khác | Nginx ghi đè header này bằng IP thật nó thấy; backend chỉ mở cổng cho `127.0.0.1` (mục 5, Nginx) |

- Cookie phiên: HttpOnly, SameSite=Lax; nếu sau này chạy HTTPS thì thêm Secure.
- Session tự hết hạn sau 30 phút không hoạt động (mặc định của Spring Boot), khớp với SSO Session Idle của realm.
- Nhập sai mật khẩu 5 lần thì KeyCloak khóa tạm tài khoản (Brute force detection).
- Redis là một phần của đăng nhập: Redis tắt thì mọi người bị đăng xuất.
- Secret chỉ nằm trong `.env`.

## 7. Kiểm thử

| Loại test | Kiểm tra gì | Cần gì |
| --- | --- | --- |
| Unit test | Một hàm trong Service | Mockito giả lập mapper và phần gọi KeyCloak; không cần DB, không cần KeyCloak |
| Test API (MockMvc) | Một endpoint: mã trạng thái, JSON, phân quyền | Spring Security giả lập người đã đăng nhập bằng `oidcLogin()` kèm role, và `csrf()` cho request ghi dữ liệu; không cần KeyCloak thật |
| Integration (Testcontainers) | Câu SQL chạy thật trên Postgres | Docker; DB tạm tự tạo rồi tự xóa |
| Postman (test tay) | Gọi thử API khi đang phát triển | Đăng nhập trên trình duyệt, chép cookie `SESSION` và `XSRF-TOKEN` sang Postman, gửi kèm header `X-XSRF-TOKEN` |

Tối thiểu phải có:

- Chưa đăng nhập gọi `/api/me` → 401.
- Người không có role gọi API nghiệp vụ → 403.
- Sai role → 403 (ví dụ `MAKER` gọi API duyệt phiếu).
- Người tạo phiếu tự duyệt phiếu của mình → bị từ chối, kể cả khi giữ cả `MAKER` và `CHECKER`.
- Kiểm soát viên khác chi nhánh duyệt phiếu → bị từ chối.
- Đăng nhập lần đầu tạo đúng 1 dòng `employee`; đăng nhập lần hai không tạo thêm, và cập nhật email nếu token có email mới.
- Nhân viên bị khóa gọi API ở request kế tiếp → 401, session bị xóa.

Không dùng Newman chạy tự động, vì khi đó không có ai đăng nhập qua trang KeyCloak.

## 8. MyBatis cho bảng `employee`

Mỗi bảng có một cặp file: interface Java khai báo method và file XML chứa câu SQL. MyBatis tự sinh class cài đặt interface lúc chạy, nên không viết class `...Impl` nào.

4 quy tắc ghép interface với XML:

1. `namespace` trong XML là tên đầy đủ của interface.
2. `id` của `<select>`, `<insert>`, `<update>` trùng tên method.
3. Method có từ 2 tham số trở lên thì đặt `@Param("tên")` để XML gọi được `#{tên}`.
4. `resultType` dùng tên ngắn nhờ `type-aliases-package` trong `application.yml`.

Ví dụ (rút gọn từ `backend/src/main/resources/io/github/arielne/alp/employee/EmployeeMapper.xml`; bản thật JOIN thêm `app.branch` để đọc tên chi nhánh):

```xml
<mapper namespace="io.github.arielne.alp.employee.EmployeeMapper">
  <select id="findByKeycloakId" resultType="Employee">
    SELECT id, keycloak_id, email, full_name, branch_id, status
    FROM app.employee
    WHERE keycloak_id = CAST(#{keycloakId} AS uuid)
  </select>

  <insert id="upsertFromLogin">
    INSERT INTO app.employee (keycloak_id, email, full_name, status)
    VALUES (CAST(#{keycloakId} AS uuid), #{email}, #{fullName}, 'ACTIVE')
    ON CONFLICT (keycloak_id) DO UPDATE
    SET email = EXCLUDED.email,
        full_name = EXCLUDED.full_name,
        updated_at = now()
  </insert>
</mapper>
```

- `#{keycloakId}` được đổi thành dấu `?` của PreparedStatement; giá trị gửi riêng, không ghép vào chuỗi SQL, nên an toàn với SQL injection.
- `${...}` dán thẳng giá trị vào câu SQL. Chỉ dùng cho tên cột khi sắp xếp, và phải kiểm tra theo danh sách cho phép trước.
- `map-underscore-to-camel-case: true`: cột `keycloak_id` tự đổ vào field `keycloakId`, không phải viết `resultMap`.
- `useGeneratedKeys="true" keyProperty="id"`: sau INSERT, MyBatis gán id vừa sinh vào object.
- `<insert>`, `<update>`, `<delete>` trả về số dòng bị thay đổi.
- `EXCLUDED` là dòng vừa định thêm; `ON CONFLICT ... DO UPDATE` lấy giá trị từ đó để cập nhật dòng đã có.