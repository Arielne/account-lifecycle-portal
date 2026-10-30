# Module quản trị: danh mục, nhân viên, audit log (tuần 4)

Tuần 4 trong roadmap (9–14/11/2026) làm phần quản trị của mốc M3 (21/11): CRUD Loại tài khoản và Chi nhánh, màn Nhân viên qua Admin API của KeyCloak, kiểm tra dữ liệu và trả lỗi theo một dạng chung, Audit log bằng AOP và màn 3.5. File này là spec chung cho 3 module `catalog` (CN-20, CN-21), `employee` (CN-22), `audit` (CN-23, CN-26) và phần dùng chung trong `common`. Điều kiện xong ở mục 10.

Đọc kèm: `docs/01-chuc-nang.md` mục 4.6 và 5; `docs/02-auth.md` các mục "Đổi role, khóa nhân viên" và "Màn Nhân viên (CN-22)"; bản thiết kế `docs/design/` các màn `DanhMucLoaiTK`, `DanhMucChiNhanh`, `NhanVien`, `AuditLog`, `TrangThaiPhu`. Chỗ nào file này ghi cụ thể hơn hoặc khác `docs/02-auth.md` thì làm theo file này, và sửa `docs/02-auth.md` cho khớp trong cùng Pull Request.

## 1. Làm và không làm

Làm:

- Dạng lỗi chung cho mọi API, kiểm tra dữ liệu bằng Bean Validation (mục 3).
- Audit log: ghi bằng AOP cho mọi API ghi của tuần này (mục 4); màn 3.5 (mục 7).
- Loại tài khoản (màn 3.2) và Chi nhánh (màn 3.3): xem, tìm, thêm, sửa, xóa (mục 5).
- Nhân viên (màn 3.4): xem, tìm, lọc, thêm, sửa hồ sơ, gán chi nhánh, gán role, khóa, mở khóa (mục 6).
- 3 việc dời từ tuần 3: bộ lọc chặn nhân viên bị khóa, mail báo tài khoản đã được kích hoạt, lỗi email trùng khi đăng nhập (mục 6).

Không làm, để đúng tuần của nó:

- Cache Redis cho danh mục (tuần 9).
- Audit cho lượt xuất báo cáo (tuần 8), các bước Camunda (tuần 6–7), job Ngủ đông (tuần 7).
- Chuyển mail sang Outbox + Kafka (tuần 9).
- Đổi logo, màu trang đăng nhập KeyCloak (tuần 10).

## 2. File mới và file sửa

```
backend/src/main/java/io/github/arielne/alp/
├── common/        dạng lỗi chung, lỗi nghiệp vụ, mã request, @Audited, AuditNote
├── audit/         AuditAspect, AuditLogMapper + XML, AuditLogService, AuditLogController
├── catalog/       AccountType*, Branch* (Controller, Service, Mapper, DTO)
├── employee/      Employee* (Controller, Service, Mapper), lớp gọi Admin API của KeyCloak
├── notification/  gửi mail kích hoạt (tạm gọi thẳng, tuần 9 đổi sang Kafka)
└── auth/          bộ lọc phiên bị thu hồi (bị khóa, bị bỏ role); xử lý email trùng khi đăng nhập
frontend/src/
├── api/              đọc lỗi dạng chung, đưa lỗi từng ô vào Form
├── pages/catalog/    màn Loại tài khoản, màn Chi nhánh
├── pages/employee/   màn Nhân viên, Drawer thêm và sửa
├── pages/audit/      màn Audit log
└── pages/auth/       trang báo lỗi đăng nhập (email trùng)
docker/keycloak/import/alp-realm.json   thêm quyền view-realm cho alp-admin (mục 6)
compose.yaml                            backend: secret của alp-admin, địa chỉ Mailpit
```

- Chiều gọi theo bảng module trong `CLAUDE.md`: `auth` → `employee` → `catalog`; module nào cũng gọi được `common`.
- Không module nào gọi thẳng `audit`. Service chỉ gắn `@Audited` và ghi nội dung vào `AuditNote` (cả hai nằm ở `common`); phần lưu do `AuditAspect` làm.
- Không đổi bảng, không thêm script Flyway: `account_type`, `branch`, `employee`, `audit_log` đã đủ cột.

## 3. Dạng lỗi chung và kiểm tra dữ liệu

Mọi lỗi của `/api` trả JSON cùng một dạng, mở rộng `ApiErrorResponse` đã có:

```json
{
  "status": 400,
  "message": "Dữ liệu chưa hợp lệ",
  "fieldErrors": [{ "field": "phone", "message": "Số điện thoại phải có 10–11 chữ số" }]
}
```

| Trường hợp | Mã | `message` |
| --- | --- | --- |
| Dữ liệu không qua Bean Validation | 400 | "Dữ liệu chưa hợp lệ"; `fieldErrors` ghi lỗi từng ô |
| Vi phạm quy tắc nghiệp vụ: mã trùng, đang dùng nên không xóa được, email trùng... | 409 | Câu tiếng Việt nói rõ lý do |
| Không tìm thấy | 404 | Ví dụ "Không tìm thấy chi nhánh" |
| Chưa đăng nhập, sai role | 401, 403 | Spring Security trả; thêm 403 dạng JSON cho `/api` (401 đã có từ tuần 3) |
| Lỗi khác | 500 | "Lỗi hệ thống. Vui lòng thử lại sau."; ghi log kèm mã request, không trả chi tiết lỗi ra ngoài |

- Một class `@RestControllerAdvice` trong `common` xử lý tập trung; Controller không tự bắt lỗi. Lỗi nghiệp vụ ném một class `BusinessException` dùng chung.
- Bean Validation đặt trên DTO (`@NotBlank`, `@Pattern`, `@Size`, `@Min`, `@Max`...), câu báo lỗi tiếng Việt ghi ngay trong annotation.
- React: lỗi có `fieldErrors` thì đưa vào đúng ô của Form; lỗi khác hiện `message.error` với câu `message` của backend. Form của AntD kiểm tra trước cùng quy tắc, backend vẫn kiểm lại (QT-07).
- Mã request: một filter trong `common` gán mỗi request vào `/api` một mã UUID, ghi vào log và vào `audit_log.request_id`.

## 4. Audit log: ghi bằng AOP (CN-26)

Cách ghi:

1. Method ghi dữ liệu của Service gắn `@Audited(action = "...", objectType = "...")`.
2. Trong method, Service ghi nội dung nghiệp vụ vào `AuditNote` (bean theo request): mã đối tượng, mô tả, và các trường đổi giá trị (nhãn tiếng Việt, giá trị trước, giá trị sau). Service tự so từng trường; không dùng reflection.
3. Method chạy xong không lỗi thì `AuditAspect` thêm phần chung rồi lưu 1 dòng `audit_log`: người làm (`employee.id` của người trong session), thời gian, IP người dùng (`request.getRemoteAddr()`, Spring đã đọc header của Nginx; Nginx ghi đè `X-Forwarded-For` nên người dùng không tự đặt được IP), mã request, và `source` là tên class và method.
4. Method lỗi thì không ghi. Dòng audit nằm trong cùng transaction với thao tác: thao tác bị hủy thì không có dòng audit; lưu audit lỗi thì thao tác cũng bị hủy.

Hành động và đối tượng của tuần 4. Database lưu mã; màn 3.5 hiện nhãn:

| `object_type` | Nhãn đối tượng | `action` và nhãn |
| --- | --- | --- |
| `ACCOUNT_TYPE` | Loại TK | `CREATE` Thêm danh mục, `UPDATE` Sửa danh mục, `DELETE` Xóa danh mục |
| `BRANCH` | Chi nhánh | Như dòng trên |
| `EMPLOYEE` | Nhân viên | `CREATE` Thêm nhân viên, `UPDATE` Sửa nhân viên, `ASSIGN_ROLE` Gán role, `LOCK` Khóa nhân viên, `UNLOCK` Mở khóa nhân viên, `SELF_REGISTER` Tự đăng ký |

Mã đối tượng (cột `object_id`): mã của danh mục, hoặc email với `EMPLOYEE`. Cột dài 150 ký tự (V8) cho vừa email, vì email được tới 150 ký tự.
Với `EMPLOYEE`, câu INSERT ghi thêm `object_employee_id` (V9) theo email đang lưu lúc thao tác, nên người đó đổi email sau này thì màn 3.5 vẫn hiện đúng họ tên.

Mô tả (cột `description`) viết bằng lời nghiệp vụ, theo bản thiết kế:

- Thêm, xóa: tên đối tượng, ví dụ "Chi nhánh Quận 7".
- Sửa 1 trường: "Nhãn: trước → sau", ví dụ "Chuyển ngủ đông sau: 18 → 12 tháng không giao dịch".
- Sửa từ 2 trường: "Sửa 2 trường: Địa chỉ, Số điện thoại"; giá trị trước và sau nằm ở cột `changes`, màn 3.5 mở rộng dòng để xem.
- Gán role: "Thêm Kiểm soát viên", "Bỏ Giao dịch viên".
- Tự đăng ký: "Chờ cấp quyền". Ghi khi lần đăng nhập đầu tạo hồ sơ mới; người làm là chính người đó.

Cột `changes` lưu JSON dạng `[{"label": "Địa chỉ", "before": "...", "after": "..."}]`, giá trị đã đổi sang chữ hiển thị (ví dụ "Hoạt động", không lưu `ACTIVE`). Không có API sửa hay xóa audit log (QT-09).

## 5. Danh mục (CN-20, CN-21)

API. Đọc: mọi role (các màn sau dùng để chọn và lọc). Ghi: chỉ `ADMIN`.

| Method | Đường dẫn | Việc |
| --- | --- | --- |
| GET | `/api/account-types` | Danh sách, kèm số tài khoản đang dùng mỗi loại |
| POST | `/api/account-types` | Thêm |
| PUT | `/api/account-types/{id}` | Sửa, không đổi mã |
| DELETE | `/api/account-types/{id}` | Xóa |
| GET | `/api/branches` | Danh sách, kèm số nhân viên và số tài khoản mỗi chi nhánh |
| GET | `/api/branches/code-available?code=` | Mã chi nhánh còn trống không |
| POST, PUT, DELETE | `/api/branches`, `/api/branches/{id}` | Thêm, sửa, xóa |

Quy tắc:

| | Loại tài khoản | Chi nhánh |
| --- | --- | --- |
| Mã | 2–10 ký tự in hoa hoặc số; không trùng; không sửa được sau khi tạo | 2–10 ký tự in hoa, số hoặc dấu `-` (ví dụ `CN-Q1`); không trùng; sửa được, vẫn kiểm tra trùng |
| Tên | Bắt buộc, tối đa 100 ký tự | Bắt buộc, tối đa 100 ký tự |
| Trường khác | Mô tả không bắt buộc, tối đa 255; "Chuyển ngủ đông sau" là số nguyên từ 1 đến 120 tháng | Địa chỉ bắt buộc, tối đa 255; điện thoại 10–11 chữ số, lưu chỉ chữ số |
| Không xóa được khi | Còn tài khoản dùng loại này | Còn nhân viên, khách hàng, tài khoản hoặc phiếu thuộc chi nhánh |

- Xóa bị chặn ở backend: 409, câu báo nói rõ còn gì và bao nhiêu (QT-08). Màn Loại tài khoản khóa nút Xóa kèm Tooltip số tài khoản đang dùng; màn Chi nhánh hỏi xác nhận rồi hiện lỗi của backend, đúng như bản thiết kế.
- Mã trùng: backend trả 409. Màn Chi nhánh còn kiểm tra khi rời ô Mã bằng `code-available`.
- Màn hình theo bản thiết kế: ô tìm kiếm, bảng, Modal thêm và sửa. Danh mục ít dòng nên tìm và phân trang ngay ở React.
- Điện thoại hiện theo nhóm số cho dễ đọc: 11 số nhóm 3-4-4 (`028 3911 2200`), 10 số nhóm 4-3-3 (`0901 234 567`).
- Giảm "Chuyển ngủ đông sau" vẫn lưu bình thường; Form ghi chú như bản thiết kế, audit ghi giá trị trước và sau.
- `AccountTypeMapper`: nếu `develop` đã có bản người dùng tự viết thì dùng lại, chỉ thêm hàm còn thiếu.

## 6. Nhân viên (CN-22) và 3 việc dời từ tuần 3

### API (chỉ `ADMIN`)

| Method | Đường dẫn | Việc |
| --- | --- | --- |
| GET | `/api/employees` | Toàn bộ nhân viên kèm role và trạng thái |
| POST | `/api/employees` | Thêm nhân viên |
| PUT | `/api/employees/{id}` | Sửa họ tên, email, chi nhánh |
| PUT | `/api/employees/{id}/roles` | Gán role: gửi danh sách role mới |
| POST | `/api/employees/{id}/lock`, `/api/employees/{id}/unlock` | Khóa, mở khóa |

### Quyền của client `alp-admin`

Đã kiểm trong mã nguồn KeyCloak 26.8.1: `manage-users` và `view-users` đủ để tạo user, sửa user, gán role cho từng user và đăng xuất phiên của user. Nhưng lấy danh sách thành viên của một role (`GET /roles/{role}/users`) và đọc một role theo tên (`GET /roles/{role}`) cần thêm `view-realm`. Vì vậy:

- Thêm `view-realm` của client `realm-management` cho service account của `alp-admin` trong `alp-realm.json`. `view-realm` chỉ cho đọc cấu hình realm, không cho sửa.
- KeyCloak chỉ nạp file realm khi realm chưa có, nên realm đang chạy phải nạp lại (B4): sửa `alp-realm.json`, người dùng xóa realm `alp` trong Admin Console, rồi `docker compose restart keycloak`. Mất user tự đăng ký `thu.dangky@nhdemo.local`; hồ sơ của người này vẫn còn trong database, dùng để thử lỗi email trùng.

### Danh sách

- Hồ sơ đọc từ `employee` JOIN `branch`. Role lấy từ KeyCloak: 3 lần gọi Admin API, mỗi role một lần, truyền `max = -1` để lấy đủ (mặc định KeyCloak chỉ trả 100 người), rồi ghép theo `keycloak_id`. Không lưu bản sao role.
- Trạng thái: `LOCKED` → Đã khóa; chưa có role nào → Chờ cấp quyền; còn lại → Hoạt động.
- Người Chờ cấp quyền luôn ở đầu, sau đó xếp theo họ tên. Tìm (họ tên, email), lọc (chi nhánh, role, trạng thái) và phân trang 10 dòng ngay ở React.
- Dòng của chính Admin đang đăng nhập ghi "(bạn)" và chỉ có nút Sửa, theo bản thiết kế. Backend cũng từ chối (409) khi Admin tự khóa mình hoặc tự đổi role của mình, để không ai lỡ tay làm mất quyền quản trị.

### Thêm nhân viên

1. Kiểm tra: họ tên có ít nhất 2 từ; email đúng dạng và chưa có trong `employee` (trùng thì 409 "Email đã có hồ sơ nhân viên"); muốn gán `MAKER` hoặc `CHECKER` thì phải chọn chi nhánh.
2. Admin API tạo user: username và email là email đã nhập; Họ là phần trước từ cuối, Tên là từ cuối của họ tên ("Nguyễn Thị Lan" → Họ "Nguyễn Thị", Tên "Lan"); `enabled = true`. KeyCloak báo email đã có tài khoản thì trả 409 với câu tiếng Việt.
3. Lưu dòng `employee` (`status = ACTIVE`, chi nhánh đã chọn) và gán role đã chọn qua Admin API.
4. Gửi mail để người đó tự đặt mật khẩu (execute actions email, `UPDATE_PASSWORD`); KeyCloak gửi qua SMTP của realm tới Mailpit.
5. Bước 3 hoặc 4 lỗi thì transaction hủy dòng `employee`, và backend xóa user vừa tạo trên KeyCloak để hai bên không lệch.

### Sửa hồ sơ, gán chi nhánh

- Họ tên, email: sửa trên KeyCloak trước (tách Họ, Tên như trên), thành công mới lưu vào `employee`. Email mới trùng hồ sơ khác thì 409.
- Chi nhánh chọn từ danh mục. Bỏ chi nhánh của người đang giữ `MAKER` hoặc `CHECKER` thì 409.
- Màn này không đổi `status`; việc đó dùng Khóa, Mở khóa.

### Gán role

- Danh sách role mới thay cho danh sách cũ: backend so với role hiện có trên KeyCloak rồi thêm hoặc bỏ từng role. Mỗi lần gán ghi 1 dòng audit.
- `MAKER`, `CHECKER` chỉ gán được khi đã có chi nhánh; `ADMIN` không cần chi nhánh.
- Người đang Chờ cấp quyền được gán role đầu tiên thì gửi mail "Tài khoản đã được kích hoạt", nhắc đăng nhập lại (CN-05). Tuần 4 gửi thẳng bằng Spring Mail tới Mailpit qua một class trong `notification`, comment ghi rõ là tạm; tuần 9 đổi sang Outbox + Kafka (B2). Trên máy gửi tới `localhost:1025`; trong Docker gửi tới `mailpit:1025`.
- Role nằm trong token nên nhân viên phải đăng nhập lại mới nhận quyền mới.
- Có bỏ role thì backend ghi `employee.sessions_invalid_before` (thời điểm ngay sau khi KeyCloak bỏ role): phiên đang mở của người đó bị bộ lọc trong `auth` đẩy ra ở thao tác kế tiếp, để role vừa bỏ không còn dùng được. Chỉ thêm role thì không đẩy ra.
- Màn hình: một Drawer dùng chung cho Thêm, Sửa, Gán role theo bản thiết kế. Drawer Gán role có cả ô Chi nhánh; đổi chi nhánh thì lưu chi nhánh trước rồi mới gán role.

### Khóa và mở khóa (A1 của tuần 3)

- Khóa: hỏi xác nhận theo màn 3.13 ("Khóa nhân viên ...? Người này bị đăng xuất ngay và không đăng nhập được nữa. Phiếu họ đã tạo vẫn giữ nguyên.", nút "Khóa" màu đỏ). Backend đặt `status = LOCKED`, Admin API đặt `enabled = false` và đăng xuất mọi phiên KeyCloak của người đó.
- Mở khóa: `status = ACTIVE`, `enabled = true`.
- Khóa cũng ghi `sessions_invalid_before`, nên mở khóa trước khi người đó gọi API thì phiên cũ vẫn không dùng lại được.
- Bộ lọc trong `auth` (`RevokedSessionFilter`): mỗi request vào `/api` của người đã đăng nhập đọc `employee.status` và `sessions_invalid_before` theo `keycloak_id`; gặp `LOCKED`, hoặc session tạo trước `sessions_invalid_before`, thì xóa session và trả 401 dạng JSON. React hiện hộp thoại phiên hết hạn như tuần 3; đăng nhập lại thì KeyCloak từ chối vì tài khoản đã tắt (bị khóa), hoặc cấp token có role mới (bị bỏ role).

### Email trùng khi đăng nhập (lỗi phát hiện ở tuần 3)

- Tình huống: đăng nhập KeyCloak thành công nhưng email trong token đã thuộc một hồ sơ `employee` có `keycloak_id` khác (đăng ký lại sau khi xóa realm, hoặc đổi email trên KeyCloak trùng người khác). Hiện tại câu `INSERT` vi phạm UNIQUE của cột `email` và người dùng thấy trang lỗi.
- Sửa: không lưu hồ sơ, xóa session, chuyển trình duyệt tới route `/loi-dang-nhap` của React. Trang này nằm ngoài phần cần đăng nhập (không gọi `/api/me`), hiện Result "Email ... đã gắn với một hồ sơ nhân viên khác. Liên hệ Admin để được hỗ trợ." kèm cách đăng xuất khỏi KeyCloak để đăng nhập tài khoản khác.
- Không nối hồ sơ theo email: realm không bắt xác minh email, nên ai cũng đăng ký được bằng email của người khác.
- Viết test tái hiện đúng lỗi trước, chạy thấy đỏ, rồi mới sửa.

### Gọi Admin API

- Dùng thư viện `keycloak-admin-client` bản 26.0.x (B1); ghi thêm thư viện này vào mục "Công nghệ" của `CLAUDE.md`. Mọi lời gọi gom vào một class trong `employee` (ví dụ `KeycloakUserClient`) để test thay class này bằng mock; CI không có KeyCloak.
- Dùng client `alp-admin`, lấy token bằng client credentials qua địa chỉ backchannel. Thêm `KC_ADMIN_CLIENT_SECRET` cho container backend trong Compose.

## 7. Màn Audit log (CN-23, màn 3.5)

- `GET /api/audit-logs` (chỉ `ADMIN`): lọc theo người thực hiện, khoảng thời gian, hành động, đối tượng; phân trang ở backend (10 dòng), mới nhất ở trên. Câu lọc dùng `<where>` và `<if>` của MyBatis.
- Bảng theo bản thiết kế: Thời gian, Người thực hiện ("Hệ thống" khi `employee_id` rỗng), Hành động, Đối tượng, Mô tả, IP. Hành động và đối tượng hiện nhãn, không hiện mã.
- Đối tượng là nhân viên thì hiện họ tên hiện tại, nối theo `object_employee_id`, không nối theo email.
- Chỉ dòng có từ 2 trường thay đổi mới mở rộng được, hiện bảng Thông tin, Trước, Sau.
- Mã request và `source` lưu để tra lỗi, không hiện trên màn.
- Ô "Người thực hiện" lấy danh sách từ `/api/employees`, thêm lựa chọn "Hệ thống".

## 8. Thứ tự làm và test

Thứ tự trong mỗi Pull Request: backend và test backend trước, giao diện sau.

Backend, test nhanh (`*Test`, `@WebMvcTest`, service KeyCloak và mail thay bằng mock):

1. Mỗi API ghi: chưa đăng nhập → 401; sai role → 403.
2. Thêm loại tài khoản sai mã → 400 có `fieldErrors`; mã trùng → 409; sửa không đổi được mã.
3. Xóa loại tài khoản, chi nhánh đang được dùng → 409.
4. Thêm nhân viên trùng email → 409; tạo trên KeyCloak xong mà lưu hồ sơ lỗi → user trên KeyCloak bị xóa.
5. Gán `MAKER` cho người chưa có chi nhánh → 409; Admin tự khóa mình hoặc tự đổi role của mình → 409.
6. Gán role đầu tiên cho người Chờ cấp quyền → gửi mail kích hoạt; thêm role cho người đã có role → không gửi.
7. Khóa → gọi tắt tài khoản và đăng xuất phiên trên KeyCloak; người bị khóa gọi `/api` → 401 và session bị xóa. Bỏ role → ghi `sessions_invalid_before` sau khi KeyCloak bỏ role; chỉ thêm role → không ghi; session tạo trước mốc đó gọi `/api` → 401, tạo sau → đi qua.
8. Đăng nhập bằng email đã thuộc hồ sơ khác → chuyển tới `/loi-dang-nhap`, không có session, không lỗi 500.

Backend, test chậm (`*IT`, Testcontainers):

9. Thêm chi nhánh → có 1 dòng audit đúng người làm, hành động, đối tượng, mã request; sửa 2 trường → `changes` có 2 mục; thao tác lỗi giữa chừng → không có dòng audit.
10. Câu đếm số tài khoản, số nhân viên của từng danh mục; câu lọc audit log theo từng điều kiện.
11. Không có API sửa hay xóa audit log (QT-09).

Frontend (Vitest + React Testing Library):

12. Form danh mục: báo lỗi đúng ô khi backend trả `fieldErrors`; nút Xóa loại tài khoản bị khóa khi còn tài khoản dùng.
13. Màn Nhân viên: người Chờ cấp quyền ở đầu; dòng "(bạn)" chỉ có nút Sửa; Khóa phải xác nhận.
14. Màn Audit log: chỉ dòng có từ 2 trường thay đổi mới mở rộng được; hiện nhãn, không hiện mã.

## 9. Nhánh và Pull Request

Chia 3 Pull Request (B3), nhánh sau tạo khi PR trước đã merge:

1. `feature/catalog`: mục 3, mục 4 (phần ghi), mục 5.
2. `feature/employee`: mục 6.
3. `feature/audit-log`: mục 7.

Phần cho Claude Code trên máy, không lên Git: thêm vào bảng "Đọc gì trước khi làm" của `CLAUDE.md` dòng cho file này; ghi vào `LEARNING.md` các kỹ thuật dùng lần đầu (`@RestControllerAdvice`, Bean Validation, AOP, `@RequestScope`, Admin API của KeyCloak, Spring Mail, `useMutation` của TanStack Query, Form của AntD).

## 10. Xong khi

- Phúc thêm, sửa, xóa được Loại tài khoản và Chi nhánh; nhập mã trùng bị báo ngay; xóa danh mục đang dùng bị chặn.
- Phúc thêm 1 nhân viên mới → Mailpit có mail đặt mật khẩu → người đó đặt mật khẩu và đăng nhập được với đúng menu.
- Người tự đăng ký → Phúc gán chi nhánh và role → Mailpit có mail kích hoạt → người đó đăng nhập lại có menu.
- Khóa Khoa khi Khoa đang mở app ở cửa sổ khác → thao tác kế tiếp của Khoa bị đẩy ra; đăng nhập lại bị KeyCloak từ chối; mở khóa thì vào lại được.
- Tái hiện email trùng → thấy trang `/loi-dang-nhap`, không phải trang lỗi.
- Màn Audit log hiện đủ các thao tác trên, lọc được; dòng sửa từ 2 trường mở rộng được.
- `mvnw verify` và `npm test` xanh trên máy và trên GitHub Actions.
- Tự kiểm: giải thích AOP ghi audit log vào lúc nào; tự thêm 1 trường mới cho Chi nhánh từ bảng đến màn hình.

## 11. Đã chốt (29/10/2026)

1. B1: gọi Admin API bằng thư viện `keycloak-admin-client` 26.0.x. Thư viện tự lấy và làm mới token, code ngắn và dễ đọc; bọc trong một class của `employee` để test thay bằng mock.
2. B2: mail "Tài khoản đã được kích hoạt" tuần 4 gửi thẳng bằng Spring Mail tới Mailpit, qua một class trong `notification`, ghi rõ là tạm; tuần 9 đổi sang Outbox + Kafka cùng các mail khác. Giống cách roadmap định cho mail duyệt phiếu (tuần 7 gửi thẳng, tuần 9 chuyển sang Kafka). Lý do cuối cùng đi qua Kafka: thao tác chính không phải chờ máy gửi mail, mail không mất khi Kafka hay Mailpit tắt, mọi mail đi một đường và đúng quy tắc "không module nào gọi thẳng `notification`".
3. B3: chia 3 Pull Request: danh mục cùng phần dùng chung và ghi audit; nhân viên cùng 3 việc dời từ tuần 3; màn Audit log. Mỗi PR đọc và giải thích được trước khi merge; màn Audit log làm sau cùng thì đã có đủ loại dòng audit để thử.
4. B4: thêm `view-realm` cho `alp-admin` bằng cách sửa `alp-realm.json`, xóa realm `alp` rồi nạp lại. File JSON và KeyCloak chắc chắn khớp nhau; hồ sơ còn lại của user tự đăng ký dùng để thử lỗi email trùng.