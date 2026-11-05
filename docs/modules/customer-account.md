# Module khách hàng và tài khoản

Spec chung cho module `customer` (CN-07, CN-08) và `account` (CN-09, CN-10): 4 màn chỉ đọc cho Giao dịch viên và Kiểm soát viên, che thông tin cá nhân của khách từ backend, nút tạo phiếu theo trạng thái tài khoản. Điều kiện xong ở mục 9.

Đọc kèm: `docs/01-chuc-nang.md` mục 2, 2.1, 3, 4.3 và QT-03 đến QT-07; bản thiết kế các màn `KhachHang`, `ChiTietKhachHang`, `TaiKhoan`, `ChiTietTaiKhoan`, `TrangThaiPhu`. Chỗ nào bản thiết kế khác file này thì làm theo file này.

## 1. Làm và không làm

Làm:

- Màn 3.6 Khách hàng, 3.7 Chi tiết khách hàng, 3.8 Tài khoản, 2.3 Chi tiết tài khoản và các API đọc của chúng.
- Che CCCD, điện thoại, email của khách ở backend (mục 3).
- Tìm họ tên gần đúng, không dấu (mục 4).
- Dấu hiệu tài khoản đang có phiếu mở và khóa nút tạo phiếu (QT-03, mục 5).
- Nút tạo phiếu theo trạng thái tài khoản, bấm thì sang Tạo phiếu với dữ liệu điền sẵn.

Không làm:

- Tạo, sửa khách hàng và tài khoản: chỉ đi qua phiếu (CN-11, CN-18).
- Lịch sử phiếu (tab Phiếu yêu cầu ở chi tiết khách hàng, bảng Lịch sử phiếu ở chi tiết tài khoản), Timeline vòng đời, "Mở bởi", "Duyệt mở bởi": cần dữ liệu và API của module phiếu, làm trong spec phiếu. Lần này dựng sẵn khung, hiện trạng thái rỗng (C3). Spec phiếu phải nối đủ 4 chỗ này.
- Job chuyển Ngủ đông (CN-25): làm cùng các việc chạy nền.
- State pattern cho việc đổi trạng thái: làm cùng module phiếu, vì lần này chưa có thao tác nào đổi trạng thái.

## 2. File mới và file sửa

```
backend/src/main/java/io/github/arielne/alp/
├── common/     PersonalDataMasker: che CCCD, điện thoại, email
├── customer/   Customer* (Controller, Service, Mapper + XML, DTO)
├── account/    Account* (Controller, Service, Mapper + XML, DTO), AccountStatus (5 mã trạng thái)
└── auth/       SecurityConfig: /api/customers/**, /api/accounts/** chỉ cho MAKER, CHECKER
db/migrations/  2 script: tìm tên không dấu, index tài khoản theo khách (mục 6)
frontend/src/
├── components/     AccountStatusTag: nhãn, màu 5 trạng thái, dùng chung cho các màn sau
├── pages/customer/ màn Khách hàng, Chi tiết khách hàng
├── pages/account/  màn Tài khoản, Chi tiết tài khoản
└── router.tsx      thêm route chi tiết /khach-hang/:id, /tai-khoan/:id
```

- Route chi tiết không có trong menu nên phải tự bọc `RequireRole` với cùng role như màn danh sách; Admin gõ thẳng URL thì ra trang 403.
- `SecurityConfig` thêm luật cho `/api/customers/**`, `/api/accounts/**` (chỉ `MAKER`, `CHECKER`) đặt trước luật chung `/api/**` đang cho cả `ADMIN`.

- Chiều gọi theo `CLAUDE.md`: `account` → `customer`. `account` không gọi Service của `customer`; Mapper của `account` JOIN bảng `customer`, `branch`, `account_type`, `ticket` để đọc. Mapper của `customer` đọc bảng `account` để hiện Tag trạng thái.
- Không ghi bảng nào, nên không có `@Audited` và không ghi Audit log, kể cả lượt tìm khách theo CCCD (C8).
- `PersonalDataMasker` nằm ở `common` vì module phiếu, báo cáo sau này cũng dùng.

## 3. Che thông tin khách (QT-05, QT-06)

| Trường | Cách che | Ví dụ |
| --- | --- | --- |
| Số CCCD (12 số) | Giữ 4 số đầu, 4 số cuối | `0790****1234` |
| Điện thoại (10–11 số) | Giữ 3 số đầu, 3 số cuối, phần giữa thay bằng `*` | `090****321` |
| Email | Giữ 3 ký tự đầu của phần trước `@` (phần đó dưới 4 ký tự thì giữ 1), thêm `***`, giữ nguyên phần từ `@`; không có `@` thì cả chuỗi thành `***` | `an.***@nhdemo.local` |

- Service che trước khi trả về. DTO của các API ở mục 4, 5 không có trường chứa CCCD, điện thoại, email đầy đủ, nên không có cách nào lỡ trả bản đầy đủ xuống trình duyệt.
- Không đưa CCCD lên URL: tìm theo CCCD gửi bằng POST (C1); màn Khách hàng giữ từ khóa trong state của trang, không ghi lên thanh địa chỉ; link sang Tạo phiếu dùng id khách hàng hoặc số tài khoản.
- Họ tên, ngày sinh không che. Chi tiết khách hàng không hiện địa chỉ, theo bản thiết kế (C5).

## 4. Khách hàng (CN-07, CN-08)

### API (`MAKER`, `CHECKER`; `ADMIN` nhận 403)

| Method | Đường dẫn | Làm gì |
| --- | --- | --- |
| POST | `/api/customers/search` | Thân request `{ "searchBy": "NAME" \| "CCCD", "keyword": "...", "page": 1 }`; trả `{ items, total }`, 10 dòng mỗi trang |
| GET | `/api/customers/{id}` | Chi tiết khách, đã che; không có thì 404 "Không tìm thấy khách hàng" |

Tab Tài khoản của chi tiết khách hàng gọi `GET /api/accounts?customerId=` của module `account` (mục 5).

### Tìm kiếm

- `NAME`: gần đúng, không phân biệt hoa thường, gõ không dấu cũng được. So cột sinh sẵn `customer.full_name_search` với từ khóa đã qua cùng hàm `app.unaccent_vi` (mục 6), bằng `strpos` chứ không bằng `LIKE`, để ký tự `%`, `_` người dùng gõ không thành ký tự đại diện. Java chuẩn hóa từ khóa trước: bỏ khoảng trắng hai đầu, gộp nhiều khoảng trắng thành một, chuẩn hóa Unicode NFC (chữ có dấu gõ từ bàn phím khác nhau có thể lưu khác byte). Từ khóa rỗng thì liệt kê mọi khách; tối đa 100 ký tự. Vì cả hai bên đều bỏ dấu trước khi so, gõ "Bình" cũng ra "Bính", "Bịnh": đó là cách tìm gần đúng, không phải lỗi.
- `CCCD`: đúng 12 chữ số, sai thì 400 "Số CCCD phải đủ 12 chữ số"; khớp tuyệt đối, dùng index UNIQUE sẵn có.
- Hồ sơ mới tạo ở trên (`created_at`, rồi `id` giảm dần). Trang nhỏ hơn 1 tính là trang 1, offset tính bằng `long` như màn Audit log.
- Cột Tài khoản lấy bằng 2 câu: câu 1 lấy 10 khách của trang, câu 2 lấy tài khoản của đúng 10 khách đó (`customer_id IN (...)`), Service ghép lại. Không JOIN rồi `LIMIT` một lần, vì `LIMIT` sẽ cắt theo dòng tài khoản chứ không theo khách.
- Ngày "Tạo hồ sơ" đổi `created_at` sang ngày theo giờ Việt Nam ở backend.

### Màn 3.6 Khách hàng (`/khach-hang`)

- Select chọn "Họ tên" hoặc "Số CCCD" ghép với ô tìm; câu gợi ý dưới ô như bản thiết kế. Đổi lựa chọn thì xóa ô tìm. Chế độ CCCD kiểm tra đủ 12 số ngay ở Form, backend kiểm lại.
- Bảng: Họ tên, Số CCCD, Ngày sinh, Điện thoại, Chi nhánh quản lý, Tài khoản (mỗi tài khoản một `AccountStatusTag`), Tạo hồ sơ, Xem. Phân trang do backend làm.

### Màn 3.7 Chi tiết khách hàng (`/khach-hang/:id`)

- Tiêu đề là họ tên; dòng phụ "Khách hàng từ dd/MM/yyyy · <chi nhánh> quản lý".
- Nút "Mở thêm tài khoản" chỉ Giao dịch viên thấy; bấm thì sang `/phieu/tao-moi?loai=MO_TAI_KHOAN&khachHang=<id>`.
- Descriptions 3 cột: Họ tên, Ngày sinh, Giới tính, Số CCCD, Ngày cấp, Nơi cấp, Điện thoại, Email, Chi nhánh quản lý.
- Tab Tài khoản: Số tài khoản, Loại tài khoản, Trạng thái, Chi nhánh mở, Ngày mở, Phiếu đang xử lý (mã, loại và trạng thái của phiếu đang mở, không có thì "—"), Xem.
- Tab Phiếu yêu cầu: khung bảng, hiện "Chưa có phiếu" (C3).
- id không có: trang báo không tìm thấy, có nút về danh sách.

## 5. Tài khoản (CN-09, CN-10)

### API (`MAKER`, `CHECKER`; `ADMIN` nhận 403)

| Method | Đường dẫn | Làm gì |
| --- | --- | --- |
| GET | `/api/accounts` | Tham số lọc: `accountNumber`, `statuses` (nhiều giá trị), `accountTypeId`, `branchId`, `customerId`, `page`; trả `{ items, total }`, 10 dòng mỗi trang |
| GET | `/api/accounts/{id}` | Chi tiết tài khoản; không có thì 404 "Không tìm thấy tài khoản" |

- Mỗi dòng: id, số tài khoản, id và họ tên chủ tài khoản, CCCD đã che, tên loại tài khoản, tên chi nhánh mở, trạng thái, ngày mở, phiếu đang mở (`{ code, type, status }` hoặc `null`). Chi tiết có thêm ngày đóng, ngày giao dịch gần nhất.
- `accountNumber`: 1–10 chữ số, khớp từ đầu số. Chỉ nhận chữ số nên không có ký tự đại diện lọt vào câu tìm.
- `statuses`: chỉ nhận 5 mã ở mục 2.1 của `docs/01-chuc-nang.md` (enum `AccountStatus`); mã khác thì 400.
- Phiếu đang mở: LEFT JOIN `app.ticket` với trạng thái `PENDING_APPROVAL`, `REJECTED`. Index `uq_ticket_open_per_account` (QT-03) bảo đảm mỗi tài khoản tối đa 1 phiếu như vậy, nên JOIN không làm nhân dòng.
- Tài khoản mới tạo ở trên (`created_at`, rồi `id` giảm dần).

### Màn 3.8 Tài khoản (`/tai-khoan`)

- Hàng lọc: ô tìm số tài khoản, Select nhiều trạng thái, Select loại tài khoản, Select chi nhánh (lấy từ API danh mục đã có), nút "Xóa lọc". Dòng nhắc: "Mọi thay đổi trạng thái đi qua phiếu yêu cầu — mở chi tiết tài khoản để tạo phiếu".
- Bộ lọc và số trang ghi lên URL (`?trang-thai=DORMANT&trang-thai=BLOCKED&loai=...&chi-nhanh=...&so-tk=...&trang=...`), để Tổng quan (CN-06) mở thẳng danh sách đã lọc và nút Back của trình duyệt giữ bộ lọc. Các tham số này không chứa thông tin cá nhân (C7).
- Đổi bộ lọc thì về trang 1, để không rơi vào trang trống. Giá trị lạ trên URL (trạng thái không có trong 5 mã, số trang không phải số) thì bỏ qua, không gửi lên API.
- Bảng: Số tài khoản (có biểu tượng cảnh báo khi có phiếu đang mở, Tooltip "Có phiếu <mã> đang chờ duyệt" hoặc "bị từ chối"), Chủ tài khoản (họ tên và CCCD đã che), Loại tài khoản, Chi nhánh, Trạng thái, Ngày mở ("—" khi Chờ mở), Xem.

### Màn 2.3 Chi tiết tài khoản (`/tai-khoan/:id`)

- Breadcrumb "Tài khoản / <số>"; tiêu đề "Tài khoản <số>" có nút sao chép số; Tag trạng thái; dòng phụ "<chủ tài khoản> · <loại> · <chi nhánh>".
- Nút chỉ Giao dịch viên thấy, theo trạng thái; bấm thì sang `/phieu/tao-moi?loai=<mã loại phiếu>&taiKhoan=<số tài khoản>`:

| Trạng thái | Nút | Loại phiếu |
| --- | --- | --- |
| Hoạt động | Phong tỏa; Đóng tài khoản | `PHONG_TOA`; `DONG_TAI_KHOAN` |
| Phong tỏa | Gỡ phong tỏa; Đóng tài khoản | `GO_PHONG_TOA`; `DONG_TAI_KHOAN` |
| Ngủ đông | Kích hoạt lại; Đóng tài khoản | `GO_PHONG_TOA`; `DONG_TAI_KHOAN` |
| Chờ mở | Không có nút; dòng "Chưa mở — phiếu <mã> đang chờ duyệt" (hoặc "bị từ chối") | |
| Đã đóng | Không có nút; dòng "Tài khoản đã đóng ngày <ngày> — không còn thao tác." | |

- Nút hiện cho Giao dịch viên của mọi chi nhánh, vì xem tài khoản được mọi chi nhánh và phiếu thuộc chi nhánh của người tạo (QT-04).
- Có phiếu đang mở: Alert "Tài khoản đang có phiếu <mã> chờ duyệt" (hoặc "bị từ chối, cần sửa hoặc hủy") và khóa mọi nút (QT-03). Tài khoản Chờ mở luôn có phiếu Mở tài khoản đang mở, đã có dòng riêng ở bảng trên nên không hiện thêm Alert. Khóa nút chỉ để giao diện rõ ràng; API tạo phiếu sau này kiểm lại QT-03, và database còn index chặn thêm một lớp.
- Kiểm soát viên không có nút; dòng "Chỉ giao dịch viên tạo được phiếu thay đổi trạng thái."
- Descriptions 2 cột: Số tài khoản, Loại tài khoản, Chủ tài khoản (link sang chi tiết khách hàng; PR tài khoản hiện chữ thường, PR khách hàng mới đổi thành link, vì trước đó route chi tiết khách hàng chưa có và link sẽ ra trang 404), Số CCCD (đã che), Chi nhánh mở, Trạng thái, Ngày mở, Giao dịch gần nhất, Mở bởi, Duyệt mở bởi. Hai ô cuối hiện "—" cho tới khi có module phiếu (C3).
- Lịch sử phiếu, Vòng đời tài khoản: khung và trạng thái rỗng (C3).

## 6. Database

2 script Flyway, mỗi script một việc, số V lấy số kế tiếp lúc làm:

1. Tìm tên không dấu: hàm `app.unaccent_vi(text)` khai báo `IMMUTABLE`, dùng `translate` đổi chữ có dấu (cả chữ hoa lẫn chữ thường, kể cả `Đ`, `đ`) sang không dấu rồi `lower`; thêm cột `customer.full_name_search` sinh sẵn từ `full_name` qua hàm này (`GENERATED ALWAYS AS ... STORED`). Đổi cả chữ hoa trong `translate` để không phụ thuộc collation của database. Dữ liệu mẫu và khách tạo sau đều tự có giá trị.
   - Bảng đổi dấu là 2 chuỗi dài phải khớp từng vị trí; lệch 1 ký tự là sai cả bảng, nên có test đi qua đủ mọi nguyên âm có dấu và chữ đ (mục 7).
   - Không ghi được vào cột sinh sẵn: câu INSERT khách hàng sau này phải ghi rõ tên cột, không dùng `INSERT ... VALUES` theo thứ tự cột của bảng.
   - Thêm cột này vào `docs/diagrams/erd.drawio` trong cùng Pull Request. Không sửa hàm tại chỗ về sau: muốn đổi thì thêm script mới và tính lại cột.
   - Họ tên phải lưu ở dạng Unicode NFC thì hàm mới nhận ra chữ có dấu. Dữ liệu mẫu đã là NFC; khi tạo khách hàng (module phiếu, import theo lô) phải chuẩn hóa NFC trước khi lưu, ghi vào spec của 2 module đó.
   - Không có index cho tìm gần đúng: quét bảng `customer` vẫn nhanh với vài nghìn khách. Nhiều hơn thì cần extension `pg_trgm`, chưa làm.
2. Index `account (customer_id)` cho tab Tài khoản của chi tiết khách hàng và câu lấy Tag ở danh sách khách.

Cả hai chạy trên Supabase dùng chung khi backend khởi động, nên hỏi người dùng trước khi chạy backend lần đầu sau khi thêm script. Test chậm chạy trên Postgres của Testcontainers.

## 7. Thứ tự làm và test

Thứ tự trong mỗi Pull Request: backend và test backend trước, giao diện sau.

Backend, test nhanh:

1. `PersonalDataMasker`: CCCD; điện thoại 10 và 11 số; email có phần trước `@` dài và ngắn.
2. Mỗi API: chưa đăng nhập → 401; Admin → 403; Giao dịch viên, Kiểm soát viên → 200.
3. 400: tìm theo CCCD 11 số; `statuses` có mã lạ; `accountNumber` có chữ.
4. 404: khách hàng, tài khoản không có.
5. JSON của 4 API không có CCCD, điện thoại, email đầy đủ (QT-06).

Backend, test chậm (`*IT`, Testcontainers):

6. Tìm "do thi binh", "BÌNH", "bình" đều ra "Đỗ Thị Bình"; tìm "%" không ra mọi khách; hàm `app.unaccent_vi` đổi đúng mọi nguyên âm có dấu (chữ hoa và chữ thường) và chữ đ.
7. Tìm CCCD đúng 12 số ra đúng 1 khách.
8. Trang 2 của danh sách khách có đủ 10 khách khi mỗi khách nhiều tài khoản; Tag đúng của từng khách.
9. Lọc nhiều trạng thái cùng loại tài khoản, chi nhánh; tìm theo đầu số tài khoản.
10. Tài khoản có phiếu Chờ duyệt thì có mã phiếu; phiếu đã Hoàn tất thì không (QT-03).
11. Giao dịch viên chi nhánh A xem được khách và tài khoản của chi nhánh B (QT-04).

Frontend (Vitest + React Testing Library):

12. Màn Khách hàng: đổi chế độ tìm thì xóa ô; CCCD chưa đủ 12 số báo lỗi, không gọi API; bảng hiện Tag trạng thái.
13. Chi tiết tài khoản: nút đúng theo trạng thái; có phiếu đang mở thì khóa nút và hiện Alert; Kiểm soát viên không thấy nút.
14. Màn Tài khoản đọc bộ lọc từ URL: `?trang-thai=DORMANT` thì Select đã chọn Ngủ đông và gọi API đúng tham số; đổi bộ lọc khi đang ở trang 3 thì về trang 1; trạng thái lạ trên URL bị bỏ qua.

## 8. Nhánh và Pull Request

Chia 2 Pull Request (C6), nhánh sau tạo khi PR trước đã merge:

1. `feature/account`: mục 3, mục 5, script index của mục 6, `SecurityConfig`, `AccountStatusTag`.
2. `feature/customer`: mục 4, script tìm tên không dấu của mục 6, cột mới trong ERD; đổi ô Chủ tài khoản ở chi tiết tài khoản thành link.

Phần cho Claude Code trên máy, không lên Git: thêm dòng cho file này vào bảng "Đọc gì trước khi làm" của `CLAUDE.md`; ghi vào `LEARNING.md` các kỹ thuật dùng lần đầu (cột sinh sẵn `GENERATED ALWAYS AS`, Function `IMMUTABLE`, `strpos`, `Normalizer` NFC, `useSearchParams` của React Router).

## 9. Xong khi

- Lan (Giao dịch viên) tìm "binh" ra Đỗ Thị Bình; tìm đúng 12 số CCCD ra 1 khách; CCCD, điện thoại, email luôn bị che, kể cả khi xem phản hồi API trong DevTools.
- Khoa (Kiểm soát viên) xem được khách và tài khoản của mọi chi nhánh, không thấy nút tạo phiếu.
- Mở `/tai-khoan?trang-thai=DORMANT` ra đúng các tài khoản Ngủ đông của dữ liệu mẫu.
- Lan mở một tài khoản Hoạt động thấy nút Phong tỏa và Đóng tài khoản; bấm thì sang Tạo phiếu với loại phiếu và số tài khoản trên URL.
- Phúc (Admin) không thấy menu Khách hàng, Tài khoản; gõ thẳng URL ra trang 403; gọi API ra 403.
- `mvnw verify` và `npm test` xanh trên máy và trên GitHub Actions.

## 10. Đã chốt (05/11/2026)

1. C1: tìm theo CCCD gửi bằng POST, không đưa CCCD lên URL. Số CCCD trên URL sẽ nằm trong log của Nginx và lịch sử trình duyệt.
2. C2: tìm tên không dấu bằng Function tự viết và cột sinh sẵn trong schema `app`, không dùng extension `unaccent`. Flyway chỉ quản lý schema `app`; hàm tự viết khai báo được `IMMUTABLE` nên dùng được cho cột sinh sẵn; dữ liệu mẫu nạp bằng SQL cũng tự có giá trị.
3. C3: Lịch sử phiếu, Vòng đời, Mở bởi, Duyệt mở bởi để module phiếu làm. Theo `CLAUDE.md`, React lấy lịch sử phiếu từ API của `ticket`, mà module này chưa có; database cũng chưa có phiếu nào nên hiện rỗng là đúng dữ liệu.
4. C4: che email giữ 3 ký tự đầu, ít hơn ví dụ trong bản thiết kế, để lộ ít thông tin hơn.
5. C5: chi tiết khách hàng không hiện địa chỉ, theo bản thiết kế.
6. C6: chia 2 Pull Request, tài khoản trước, vì tab Tài khoản của chi tiết khách hàng gọi API của tài khoản.
7. C7: bộ lọc của màn Tài khoản ghi lên URL vì không chứa thông tin cá nhân; từ khóa của màn Khách hàng thì không, vì có thể là số CCCD.
8. C8: không ghi Audit log khi xem hoặc tìm khách, kể cả tìm theo CCCD, vì CN-26 chỉ ghi thao tác ghi dữ liệu. Muốn lưu dấu vết tra cứu thì sửa CN-26 trước.