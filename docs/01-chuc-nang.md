# Danh sách chức năng

Tài liệu này ghi ai làm được gì trong Account Lifecycle Portal, có những màn hình nào và những quy tắc phải giữ. Sơ đồ Use Case và ERD vẽ dựa trên file này; code và test cũng đối chiếu với nó.

Nguồn: phiếu chốt đề tài, tài liệu thiết kế phần đăng nhập, bản thiết kế giao diện. Số màn (ví dụ 3.9) là số màn trong bản thiết kế giao diện.

Dùng file này để:

- Vẽ Use Case: mỗi vai trò ở mục 1 là một actor, mỗi chức năng `CN-xx` ở mục 4 là một use case.
- Vẽ ERD: mỗi đối tượng ở mục 2 là một bảng (hoặc vài bảng); các trạng thái là cột trạng thái của bảng đó.
- Viết code: mỗi API và mỗi màn ghi rõ nó làm chức năng `CN-xx` nào.
- Viết test: mỗi quy tắc `QT-xx` ở mục 5 có ít nhất một test chứng minh quy tắc được giữ.
- Viết tài liệu Word: chương phân tích yêu cầu lấy nội dung từ đây.
- Giữ phạm vi: chức năng nào không có trong file thì không làm. Muốn thêm thì sửa file này trước.

## 1. Vai trò

| Vai trò | Role trong KeyCloak | Làm được | Không làm được |
| --- | --- | --- | --- |
| Giao dịch viên | `MAKER` | Tra cứu khách hàng, tài khoản; tạo phiếu, sửa và gửi lại phiếu bị từ chối, hủy phiếu của mình; import mở tài khoản theo lô; xuất báo cáo | Duyệt phiếu; quản trị danh mục, nhân viên |
| Kiểm soát viên | `CHECKER` | Tra cứu khách hàng, tài khoản; duyệt hoặc từ chối phiếu; xuất báo cáo | Tạo phiếu; duyệt phiếu do chính mình tạo |
| Admin | `ADMIN` | Quản lý danh mục Loại tài khoản và Chi nhánh; quản lý nhân viên (thêm, sửa, gán role, gán chi nhánh, khóa); xem Audit log | Xem hoặc thay đổi khách hàng, tài khoản, phiếu |
| Chờ cấp quyền | (chưa có role) | Đăng nhập được nhưng chỉ thấy trang "Tài khoản đang chờ cấp quyền" và nút Đăng xuất | Mọi chức năng khác |
| Hệ thống | (không đăng nhập) | Việc tự chạy: gửi mail sau khi duyệt, chuyển tài khoản sang Ngủ đông mỗi đêm, ghi Audit log | |

Ghi chú:

- Một nhân viên có thể giữ cả `MAKER` và `CHECKER` (tài khoản demo `anh.vd`). Menu là hợp các role, nhưng người đó vẫn không duyệt được phiếu do chính mình tạo.
- Admin chỉ gán được role Giao dịch viên hoặc Kiểm soát viên khi nhân viên đã có chi nhánh.
- Đổi role xong, nhân viên phải đăng nhập lại mới nhận quyền mới.
- Không có nút đổi người dùng. Muốn đổi tài khoản thì đăng xuất rồi đăng nhập tài khoản khác.

## 2. Đối tượng nghiệp vụ

| Đối tượng | Ghi chú |
| --- | --- |
| Khách hàng | Chỉ khách hàng cá nhân, định danh bằng số CCCD/Căn cước 12 chữ số. Một CCCD là một khách hàng. |
| Tài khoản | Thuộc 1 khách hàng, 1 loại tài khoản, 1 chi nhánh mở. Có 5 trạng thái (mục 2.1). Số tài khoản sinh bằng Sequence. |
| Phiếu yêu cầu | Mọi thay đổi trạng thái tài khoản do người làm đều đi qua phiếu. Mỗi phiếu chạy 1 quy trình Camunda. Mã phiếu dạng `PYC-2026-0001`, sinh bằng Sequence. |
| Giấy tờ đính kèm | File đính kèm phiếu, lưu ở Supabase Storage (bucket `giay-to`). |
| Lô import | 1 file Excel mở tài khoản theo lô. Lưu ở bảng `import_batch` (thông tin lô) và `import_batch_row` (từng dòng). File gốc lưu ở bucket `import`. |
| Loại tài khoản | Danh mục. Mỗi loại có cài đặt "Chuyển ngủ đông sau (tháng không giao dịch)": tài khoản loại này không giao dịch quá số tháng đó thì job ban đêm chuyển sang Ngủ đông. Đây không phải thời hạn của tài khoản. |
| Chi nhánh | Danh mục. Nơi mở tài khoản và nơi nhân viên làm việc. |
| Nhân viên | Tài khoản đăng nhập và role nằm ở KeyCloak; hồ sơ và chi nhánh nằm ở Supabase, nối bằng `keycloak_id`. DB của app không giữ bản sao role. |
| Audit log | Nhật ký thao tác. Chỉ đọc, không sửa, không xóa. |

### 2.1 Trạng thái tài khoản

| Trạng thái | Mã | Nghĩa |
| --- | --- | --- |
| Chờ mở | `PENDING_OPEN` | Có phiếu Mở tài khoản đang chờ duyệt |
| Hoạt động | `ACTIVE` | Dùng bình thường |
| Phong tỏa | `BLOCKED` | Tạm khóa theo phiếu Phong tỏa |
| Ngủ đông | `DORMANT` | Hệ thống tự chuyển khi lâu không giao dịch |
| Đã đóng | `CLOSED` | Trạng thái cuối, chỉ xem, không mở lại được |

Các lần chuyển trạng thái hợp lệ:

| Từ | Sang | Khi nào |
| --- | --- | --- |
| (chưa có) | Chờ mở | Giao dịch viên gửi phiếu Mở tài khoản |
| Chờ mở | Hoạt động | Phiếu Mở tài khoản được duyệt |
| Chờ mở | Đã đóng | Người tạo hủy phiếu Mở tài khoản |
| (chưa có) | Hoạt động | Phiếu Mở tài khoản theo lô được duyệt (tài khoản tạo thẳng ở trạng thái Hoạt động) |
| Hoạt động | Phong tỏa | Phiếu Phong tỏa được duyệt |
| Phong tỏa | Hoạt động | Phiếu Gỡ phong tỏa được duyệt |
| Ngủ đông | Hoạt động | Phiếu Kích hoạt lại được duyệt |
| Hoạt động | Ngủ đông | Job ban đêm: không giao dịch quá số tháng "Chuyển ngủ đông sau" của loại tài khoản |
| Hoạt động, Phong tỏa, Ngủ đông | Đã đóng | Phiếu Đóng tài khoản được duyệt |

Tài khoản được sinh ra theo 2 cách:

- Mở từng tài khoản: tài khoản tạo ngay lúc gửi phiếu, ở trạng thái Chờ mở. Duyệt xong mới sang Hoạt động; người tạo hủy phiếu thì sang Đã đóng.
- Mở theo lô: lúc gửi phiếu chưa tạo tài khoản nào. Trong lúc chờ duyệt, dữ liệu nằm ở bảng `import_batch_row`, mỗi dòng đóng vai "chờ mở". Duyệt xong mới tạo cả lô, vào thẳng Hoạt động. Phiếu lô bị từ chối hoặc bị hủy thì không có tài khoản nào được tạo.

Phiếu lô không tạo trước tài khoản Chờ mở vì: lô bị hủy sẽ để lại hàng trăm tài khoản Đã đóng chưa từng dùng và đốt mất số tài khoản; dòng sai sửa ngay trong lô rồi gửi lại; "tạo hết hoặc không tạo gì" làm gọn trong 1 transaction lúc duyệt; Tổng quan không bị đếm thêm tài khoản của phiếu chưa duyệt.

Code dùng State pattern cho phần này: mỗi trạng thái là một lớp, tự biết được chuyển sang đâu. Lần chuyển nào không có trong bảng thì báo lỗi.

### 2.2 Loại phiếu

| Loại phiếu | Mã | Đối tượng | Giấy tờ cần có |
| --- | --- | --- | --- |
| Mở tài khoản | `MO_TAI_KHOAN` | Khách hàng mới, hoặc khách đã có hồ sơ (mở thêm tài khoản) | CCCD 2 mặt, đơn đề nghị mở tài khoản có chữ ký |
| Phong tỏa | `PHONG_TOA` | 1 tài khoản Hoạt động | Đơn đề nghị phong tỏa hoặc văn bản yêu cầu |
| Gỡ phong tỏa / Kích hoạt lại | `GO_PHONG_TOA` | 1 tài khoản Phong tỏa hoặc Ngủ đông | Đơn đề nghị gỡ phong tỏa, CCCD |
| Đóng tài khoản | `DONG_TAI_KHOAN` | 1 tài khoản Hoạt động, Phong tỏa hoặc Ngủ đông | Đơn đề nghị đóng tài khoản có chữ ký |
| Mở tài khoản theo lô | `MO_TK_THEO_LO` | Nhiều khách hàng mới từ 1 file Excel | (không cần, dữ liệu nằm trong file) |

Phiếu `GO_PHONG_TOA` hiện tên "Gỡ phong tỏa" khi tài khoản đang Phong tỏa, và "Kích hoạt lại" khi tài khoản đang Ngủ đông.

### 2.3 Trạng thái phiếu

| Trạng thái | Mã | Nghĩa |
| --- | --- | --- |
| Chờ duyệt | `PENDING_APPROVAL` | Đang ở bước kiểm soát viên |
| Bị từ chối | `REJECTED` | Trả về giao dịch viên sửa |
| Đã duyệt | `APPROVED` | Đã duyệt, hệ thống đang gửi mail |
| Hoàn tất | `COMPLETED` | Đã gửi mail, phiếu đóng |
| Đã hủy | `CANCELLED` | Người tạo rút phiếu khi phiếu đang Chờ duyệt hoặc Bị từ chối |

Luồng quy trình: Giao dịch viên tạo phiếu → Kiểm soát viên duyệt → Hệ thống gửi mail → Hoàn tất. Nhánh từ chối: Kiểm soát viên từ chối → phiếu về Giao dịch viên sửa → gửi lại → Chờ duyệt.

## 3. Màn hình theo vai trò

| Màn | Route | Giao dịch viên | Kiểm soát viên | Admin |
| --- | --- | --- | --- | --- |
| 2.2 Tổng quan | `/` | Có | Có | Có |
| 3.6 Khách hàng, 3.7 Chi tiết khách hàng | `/khach-hang` | Có | Có | |
| 3.8 Tài khoản, 2.3 Chi tiết tài khoản | `/tai-khoan` | Có | Có | |
| 3.9 Tạo phiếu | `/phieu/tao-moi` | Có | | |
| 3.10 Phiếu chờ duyệt | `/phieu/cho-duyet` | | Có | |
| 3.10 Tất cả phiếu, 2.4 Chi tiết phiếu | `/phieu` | Có | Có | |
| 3.2 Danh mục Loại tài khoản | `/danh-muc/loai-tai-khoan` | | | Có |
| 3.3 Danh mục Chi nhánh | `/danh-muc/chi-nhanh` | | | Có |
| 3.4 Nhân viên | `/nhan-vien` | | | Có |
| 3.11 Import Excel | `/bao-cao/import` | Có | | |
| 3.12 Xuất báo cáo | `/bao-cao/export` | Có | Có | |
| 3.5 Audit log | `/audit-log` | | | Có |

Màn dùng chung: 2.1 Layout (menu theo role, header có tên, chi nhánh, role và nút Đăng xuất), 3.1 Đăng nhập (trang của KeyCloak), 3.13 Trạng thái phụ (bảng rỗng, đang tải, 403, 404, 500, chờ cấp quyền), 3.14 Mẫu báo cáo PDF.

Ẩn menu chưa đủ: route trong React cũng chặn theo role, và backend chặn thêm một lần nữa. Gõ thẳng URL trái quyền thì ra trang 403.

## 4. Chức năng chi tiết

Mã `CN-xx` dùng để nối với Use Case, API và test.

### 4.1 Đăng nhập và tài khoản người dùng

Phần này do KeyCloak làm, app không tự viết trang đăng nhập hay lưu mật khẩu.

| Mã | Chức năng | Ai | Ghi chú |
| --- | --- | --- | --- |
| CN-01 | Đăng nhập | Mọi người | Trang đăng nhập của KeyCloak. Backend giữ token trong session ở Redis, trình duyệt chỉ có cookie phiên. |
| CN-02 | Đăng xuất | Mọi người | Xóa session ở backend rồi đăng xuất luôn ở KeyCloak. Nút ở góc phải header, có trên mọi màn. |
| CN-03 | Đăng ký | Người chưa có tài khoản | Trang đăng ký của KeyCloak. Đăng ký xong ở trạng thái Chờ cấp quyền đến khi Admin gán role và chi nhánh. |
| CN-04 | Quên mật khẩu | Mọi người | KeyCloak gửi mail đặt lại mật khẩu qua Mailpit. Mật khẩu hash bằng Argon2 (mặc định của KeyCloak). |
| CN-05 | Xem trang chờ cấp quyền | Chờ cấp quyền | Hiện khi đăng nhập thành công nhưng chưa có role. Chỉ có nút Đăng xuất. Hệ thống gửi mail khi tài khoản được kích hoạt. |

### 4.2 Tổng quan

| Mã | Chức năng | Ai | Ghi chú |
| --- | --- | --- | --- |
| CN-06 | Xem tổng quan | Mọi role | Giao dịch viên và Kiểm soát viên thấy số tài khoản theo 5 trạng thái; bấm một thẻ thì mở danh sách Tài khoản đã lọc theo trạng thái đó. Khối giữa đổi theo role: Kiểm soát viên thấy "Phiếu chờ tôi duyệt" (đã loại phiếu do mình tạo); Giao dịch viên thấy "Phiếu của tôi" (phiếu bị từ chối luôn ở trên cùng); Admin thấy "Nhân viên chờ cấp quyền". Khối "Hoạt động gần đây" lấy 6 dòng mới nhất của Audit log. Số liệu tính bằng Function trong DB. |

### 4.3 Khách hàng và tài khoản

| Mã | Chức năng | Ai | Ghi chú |
| --- | --- | --- | --- |
| CN-07 | Tra cứu khách hàng | Giao dịch viên, Kiểm soát viên | Tìm theo họ tên (gần đúng, gõ không dấu cũng được) hoặc theo số CCCD (đủ 12 số, khớp tuyệt đối). Bảng có phân trang; cột Tài khoản hiện mỗi tài khoản của khách bằng một Tag trạng thái. |
| CN-08 | Xem chi tiết khách hàng | Giao dịch viên, Kiểm soát viên | Thông tin khách (CCCD, điện thoại, email đã che từ backend), tab Tài khoản và tab Phiếu yêu cầu. Giao dịch viên có nút "Mở thêm tài khoản": sang Tạo phiếu với khách hàng đã điền sẵn. |
| CN-09 | Tra cứu tài khoản | Giao dịch viên, Kiểm soát viên | Tìm theo số tài khoản; lọc theo trạng thái (chọn nhiều), loại tài khoản, chi nhánh. Tài khoản đang có phiếu chờ duyệt có dấu hiệu riêng, Tooltip ghi mã phiếu. |
| CN-10 | Xem chi tiết tài khoản | Giao dịch viên, Kiểm soát viên | Thông tin tài khoản, lịch sử phiếu, Timeline vòng đời. Nút thao tác chỉ hiện với Giao dịch viên và đổi theo trạng thái (mục 2.1); bấm nút thì sang Tạo phiếu với loại phiếu và tài khoản đã điền sẵn. Tài khoản đang có phiếu chờ duyệt thì khóa mọi nút. Tài khoản Đã đóng không còn nút nào. |

### 4.4 Phiếu yêu cầu

| Mã | Chức năng | Ai | Ghi chú |
| --- | --- | --- | --- |
| CN-11 | Tạo phiếu | Giao dịch viên | 4 bước: chọn loại phiếu → khách hàng hoặc tài khoản → lý do và giấy tờ → xác nhận. Chi tiết ở dưới bảng. |
| CN-12 | Sửa và gửi lại phiếu bị từ chối | Giao dịch viên (người tạo) | Sửa theo lý do từ chối rồi gửi lại; phiếu quay về Chờ duyệt, số lần gửi tăng thêm 1. |
| CN-13 | Hủy phiếu | Giao dịch viên (người tạo) | Chỉ khi phiếu Chờ duyệt hoặc Bị từ chối. Phiếu sang Đã hủy, backend dừng quy trình Camunda. Phiếu Mở tài khoản bị hủy thì tài khoản Chờ mở sang Đã đóng. Phiếu bị từ chối mà người tạo không sửa, không hủy thì tài khoản giữ nguyên Chờ mở; hệ thống không tự hủy. |
| CN-14 | Xem danh sách phiếu | Giao dịch viên, Kiểm soát viên | Tab theo trạng thái (Tất cả, Chờ duyệt, Đã duyệt, Bị từ chối, Hoàn tất, Đã hủy); lọc theo mã phiếu, loại phiếu, chi nhánh, khoảng ngày. Danh sách Chờ duyệt của Kiểm soát viên: không gồm phiếu do mình tạo, phiếu gửi sớm nhất ở trên, chờ quá 24 giờ thì tô màu cảnh báo, không có nút duyệt nhanh. |
| CN-15 | Xem chi tiết phiếu | Giao dịch viên, Kiểm soát viên | Steps cho biết phiếu đang ở bước nào; nội dung yêu cầu (trạng thái trước → sau); giấy tờ đính kèm (xem và tải, không xóa); Timeline lịch sử xử lý. Phiếu lô có thêm bảng khách trong lô, phân trang ở backend. |
| CN-16 | Duyệt phiếu | Kiểm soát viên | Hỏi xác nhận trước khi duyệt. Backend kiểm tra người duyệt khác người tạo rồi mới báo Camunda hoàn thành task. Phiếu lô: xem CN-18. |
| CN-17 | Từ chối phiếu | Kiểm soát viên | Bắt buộc nhập lý do (tối đa 500 ký tự). Phiếu về Bị từ chối, giao dịch viên thấy lý do. Trạng thái tài khoản không đổi. |

Chi tiết CN-11 (Tạo phiếu):

1. Chọn 1 trong 4 loại phiếu ở mục 2.2 (trừ phiếu lô, phiếu lô tạo từ màn Import).
2. Phiếu Mở tài khoản: chọn "Khách hàng mới" thì nhập hồ sơ (họ tên, ngày sinh, giới tính, số CCCD, ngày cấp, nơi cấp, điện thoại, email nhận thông báo, địa chỉ thường trú, loại tài khoản); chọn "Khách hàng đã có" thì tìm theo đủ 12 số CCCD rồi chọn loại tài khoản mở thêm. Ô CCCD kiểm tra ngay: số đã có hồ sơ thì báo. Các phiếu khác: tìm tài khoản theo số; tài khoản phải đang ở trạng thái cho phép và không có phiếu nào đang chờ, nếu không thì báo lỗi dưới ô tìm và khóa nút Tiếp tục.
3. Nhập lý do (tối đa 500 ký tự) và đính kèm giấy tờ: PDF, JPG, PNG; tối đa 5 file, mỗi file tối đa 5 MB.
4. Xem lại toàn bộ, tick "Tôi đã đối chiếu thông tin với giấy tờ gốc của khách hàng" rồi gửi.

Sau khi gửi: phiếu Chờ duyệt, quy trình Camunda bắt đầu. Phiếu Mở tài khoản tạo luôn hồ sơ khách hàng (nếu là khách mới) và tài khoản ở trạng thái Chờ mở. Chi nhánh mở là chi nhánh của giao dịch viên, không chọn. Phiếu đã gửi không sửa được cho tới khi bị trả về.

### 4.5 Import và báo cáo

| Mã | Chức năng | Ai | Ghi chú |
| --- | --- | --- | --- |
| CN-18 | Import mở tài khoản theo lô | Giao dịch viên | Chi tiết ở dưới bảng. |
| CN-19 | Xuất báo cáo | Giao dịch viên, Kiểm soát viên | 3 loại báo cáo: Danh sách tài khoản (lọc theo trạng thái, chi nhánh, ngày mở; nhóm theo trạng thái hoặc chi nhánh), Tài khoản ngủ đông (lọc theo chi nhánh, loại tài khoản, khoảng ngày chuyển ngủ đông; kèm ngày giao dịch gần nhất), Phiếu đã duyệt (lọc theo khoảng ngày duyệt, loại phiếu, người duyệt). Định dạng Excel (Apache POI) hoặc PDF (JasperReports); PDF tối đa 2.000 dòng, nhiều hơn thì chọn Excel. Có xem trước 5 dòng đầu và số dòng ước tính. Bấm Xuất thì nút quay vòng, file tải thẳng về máy; không chạy nền, không lưu lịch sử file. Mỗi lần xuất ghi 1 dòng Audit log. |

Chi tiết CN-18 (Import theo lô):

1. Tải file Excel mẫu. Cột CCCD trong file mẫu để kiểu Text để không mất số 0 ở đầu.
2. Điền mỗi dòng một khách hàng mới.
3. Tải file lên. Trong request, backend chỉ kiểm tra nhanh cả file: đúng `.xlsx`, tối đa 5 MB, đúng dòng tiêu đề của file mẫu, tối đa 1.000 dòng. Sai thì báo lỗi ngay, không tạo lô. Đúng thì lưu file gốc vào bucket `import`, tạo lô ở trạng thái "Đang kiểm tra" và trả mã lô về ngay.
4. Kiểm tra từng dòng chạy nền (`@Async` với thread pool giới hạn số lô kiểm tra cùng lúc). Mỗi dòng ghi kết quả Hợp lệ hoặc Lỗi kèm lý do vào `import_batch_row`, cập nhật tiến độ; xong thì lô sang "Kiểm tra xong". React hỏi tiến độ qua `GET /api/imports/{id}` 2 giây một lần. Lỗi thường gặp: số CCCD không đủ 12 chữ số; CCCD đã có hồ sơ khách hàng (khách cũ thì dùng Mở thêm tài khoản); CCCD lặp trong cùng file; CCCD đang nằm trong một lô khác đang chờ duyệt; mã loại tài khoản không tồn tại; email không hợp lệ; ngày sinh không ở quá khứ.
5. Sửa dòng lỗi: bấm "Tải file lỗi" để lấy file Excel cùng cột với file mẫu, thêm cột "Lý do lỗi". Sửa trong Excel rồi tải lên thành một lô mới; lô mới đi lại từ bước 3. Dòng lỗi của lô cũ không bao giờ được gửi.
6. Gửi: các dòng hợp lệ gộp thành 1 phiếu Mở tài khoản theo lô (`POST /api/imports/{id}/submit`), dù file có 1 dòng hay nhiều dòng. Dòng lỗi không được gửi.

Quy tắc của phiếu lô:

- 1 file = 1 phiếu = 1 quy trình Camunda.
- Phần chạy nền chỉ ghi vào `import_batch` và `import_batch_row`. Bảng khách hàng và tài khoản chỉ được ghi một lần, lúc kiểm soát viên duyệt.
- Lúc gửi chưa tạo tài khoản nào. Kiểm soát viên duyệt xong mới tạo khách hàng và tài khoản (trạng thái Hoạt động), trong 1 transaction: tạo hết hoặc không tạo gì.
- Trước khi tạo, kiểm tra lại CCCD một lần nữa. Có dòng hỏng thì phiếu quay về người tạo kèm danh sách dòng hỏng.
- Cả lô chỉ gửi 1 mail và phát 1 sự kiện Kafka.

### 4.6 Quản trị

| Mã | Chức năng | Ai | Ghi chú |
| --- | --- | --- | --- |
| CN-20 | Quản lý loại tài khoản | Admin | Thêm, sửa, xóa, tìm. Mã loại: 2–10 ký tự in hoa hoặc số, không trùng, không sửa được sau khi tạo. Có tên, mô tả, số tháng "Chuyển ngủ đông sau (tháng không giao dịch)". Giảm số tháng thì ngay đêm đó job có thể chuyển nhiều tài khoản sang Ngủ đông cùng lúc; mỗi lần sửa ghi Audit log. Không xóa được khi còn tài khoản dùng loại này (nút Xóa khóa, Tooltip ghi số tài khoản đang dùng). Danh mục được cache ở Redis (Cache-Aside). |
| CN-21 | Quản lý chi nhánh | Admin | Thêm, sửa, xóa, tìm. Mã chi nhánh không trùng (kiểm tra ngay khi nhập), có tên, địa chỉ, số điện thoại 10–11 chữ số. Không xóa được khi chi nhánh còn nhân viên hoặc tài khoản. Cache ở Redis như loại tài khoản. |
| CN-22 | Quản lý nhân viên | Admin | Xem danh sách, tìm, lọc theo chi nhánh, role, trạng thái; nhân viên Chờ cấp quyền luôn ở đầu danh sách. Thêm nhân viên: tạo tài khoản trên KeyCloak, KeyCloak gửi mail để người đó tự đặt mật khẩu, rồi lưu hồ sơ. Sửa hồ sơ. Gán chi nhánh làm việc. Gán role (ghi vào KeyCloak). Khóa hoặc mở khóa (hỏi xác nhận, đồng bộ sang KeyCloak). Trạng thái nhân viên: Hoạt động, Đã khóa, Chờ cấp quyền. Role chỉ lưu ở KeyCloak: khi tải danh sách, backend gọi Admin API lấy thành viên của từng role (3 lần gọi) rồi ghép với hồ sơ trong DB; lọc theo role thì lấy danh sách `keycloak_id` từ KeyCloak rồi lọc hồ sơ theo danh sách đó. Người không có role nào là Chờ cấp quyền. |
| CN-23 | Xem Audit log | Admin | Chỉ đọc. Lọc theo người thực hiện, khoảng thời gian, hành động, đối tượng. Cột Mô tả viết bằng lời nghiệp vụ (ví dụ "Hoạt động → Phong tỏa · theo phiếu PYC-2026-0415"). Chỉ dòng sửa dữ liệu từ 2 trường trở lên mới mở rộng được, để xem bảng trước và sau; tên trường hiện bằng nhãn tiếng Việt, giá trị hiện bằng nhãn (không hiện mã như `BLOCKED`). Mã request và tên bước kỹ thuật lưu trong DB để tra lỗi, không hiện trên màn. |

### 4.7 Việc hệ thống tự làm

| Mã | Chức năng | Chạy khi | Ghi chú |
| --- | --- | --- | --- |
| CN-24 | Gửi mail kết quả | Phiếu được duyệt | Service task của Camunda; sự kiện đi qua Kafka theo Outbox pattern; mail gửi tới khách hàng và giao dịch viên qua Mailpit. Gửi xong thì phiếu sang Hoàn tất. |
| CN-25 | Chuyển tài khoản sang Ngủ đông | Mỗi đêm | pg_cron gọi Stored Procedure: tài khoản Hoạt động không giao dịch quá số tháng "Chuyển ngủ đông sau" của loại tài khoản thì chuyển sang Ngủ đông. Ghi Audit log với người thực hiện là "Hệ thống (job)". |
| CN-26 | Ghi Audit log | Mỗi thao tác ghi dữ liệu | Dùng AOP. Ghi cho mọi API ghi, mỗi lần xuất báo cáo và các bước Camunda. Lưu người làm, thời gian, hành động, đối tượng, mô tả, IP, giá trị trước và sau. |

## 5. Quy tắc chung

| Mã | Quy tắc |
| --- | --- |
| QT-01 | Người duyệt phải khác người tạo phiếu. Backend so `keycloak_id` của hai người trước khi báo Camunda; giao diện khóa nút Duyệt, Từ chối và hiện Tooltip "Bạn không thể duyệt phiếu do chính mình tạo" chỉ là lớp chặn thứ hai. |
| QT-02 | Mọi thay đổi trạng thái tài khoản do người làm đều đi qua phiếu. Ngoại lệ duy nhất là job chuyển Ngủ đông. |
| QT-03 | Một tài khoản chỉ có tối đa 1 phiếu đang chờ duyệt. |
| QT-04 | Chi nhánh mở tài khoản là chi nhánh của giao dịch viên. Backend lấy từ hồ sơ của người trong session, không nhận chi nhánh do frontend gửi lên. |
| QT-05 | Số CCCD chỉ kiểm tra đủ 12 chữ số, không phân tích gì thêm để hạn chế xử lý thông tin cá nhân. Lưu dạng chuỗi. Màn hình hiện che phần giữa (ví dụ `0790****1234`); ô nhập hiện đủ khi đang gõ, che lại khi rời ô. |
| QT-06 | CCCD, điện thoại, email của khách hàng che từ backend, không gửi bản đầy đủ xuống trình duyệt khi chỉ để xem. |
| QT-07 | Phân quyền kiểm tra ở backend cho mọi API. React chỉ ẩn menu và chặn route để người dùng không thấy những gì không dùng được. |
| QT-08 | Danh mục đang được dùng thì không xóa được. |
| QT-09 | Audit log không có API sửa hay xóa. |
| QT-10 | File đính kèm và file import chỉ tải qua backend. Backend kiểm tra người đó có quyền xem phiếu rồi mới trả file; không có link công khai. Backend kiểm tra loại file thật, không chỉ xem đuôi file. Tên file khi lưu là UUID. |
| QT-11 | Hai kiểm soát viên cùng mở một phiếu: người bấm sau nhận thông báo "Phiếu này vừa được người khác xử lý", không xử lý lần hai. |
| QT-12 | Toàn bộ dữ liệu trong project là dữ liệu giả. Không dùng số CCCD thật hay tên, logo ngân hàng thật. |
| QT-13 | Một CCCD không được nằm cùng lúc trong 2 lô đang chờ duyệt. Lúc kiểm tra file, CCCD đã có hồ sơ hoặc đang nằm trong lô khác chưa xử lý xong thì dòng đó bị báo lỗi. |

## 6. Ngoài phạm vi

- Tài khoản doanh nghiệp.
- Giao dịch tiền: nạp, rút, chuyển khoản, số dư.
- Nút đổi người dùng ngay trong app.
- Sơ đồ quy trình BPMN trong app. Sơ đồ vẽ bằng Camunda Modeler, chỉ đặt trong tài liệu và báo cáo.
- Lưu lịch sử các file báo cáo đã xuất.
- Link công khai khi demo; demo chạy trên máy bằng localhost.
- Tự viết đăng nhập, đăng ký, hash mật khẩu (KeyCloak làm).

## 7. Điểm còn mở

Các điểm dưới đây chưa có trong phiếu chốt, hoặc bản thiết kế chưa thống nhất. Cần chốt trước khi vẽ ERD và viết code phần liên quan.

1. **Phạm vi chi nhánh khi duyệt và xem.** Màn 3.9 ghi phiếu chuyển tới "kiểm soát viên Chi nhánh Tân Phú", nhưng ở màn 3.10, danh sách chờ duyệt của Trần Minh Khoa (Chi nhánh Tân Phú) có cả phiếu của Chi nhánh Quận 1 và Quận 3. Cần chốt: kiểm soát viên duyệt phiếu của mọi chi nhánh hay chỉ chi nhánh mình; giao dịch viên và kiểm soát viên xem khách hàng, tài khoản của mọi chi nhánh hay chỉ chi nhánh mình.
2. **Ngày giao dịch gần nhất lấy từ đâu.** App không có chức năng giao dịch, nên job Ngủ đông (CN-25) chỉ có dữ liệu để chạy nếu dữ liệu mẫu điền sẵn cột này.
3. **Phiếu Bị từ chối có chặn phiếu mới không.** QT-03 hiện chỉ nói phiếu Chờ duyệt. Nếu tài khoản có phiếu Bị từ chối (chưa gửi lại, chưa hủy) mà vẫn cho tạo phiếu mới, thì khi phiếu cũ được gửi lại sẽ có 2 phiếu chồng nhau.
4. **Mail khi phiếu bị từ chối.** Luồng đã chốt chỉ gửi mail sau khi duyệt. Giao dịch viên hiện chỉ thấy phiếu bị từ chối ở Tổng quan và danh sách phiếu.
5. **Ghi bên gửi danh sách của lô.** Lô chỉ là cách nhập nhiều khách cá nhân cùng lúc; tài khoản lương vẫn là tài khoản cá nhân. Ba mức: A. giữ nguyên; B. thêm ô "Đơn vị đề nghị" (chỉ là chữ, ví dụ "Công ty ABC") vào phiếu lô; C. quản lý khách hàng doanh nghiệp (trái tên đề tài, phải xin duyệt lại). Đề xuất: B.
6. **Gửi lại phiếu lô bị trả về.** Đề xuất: người tạo chỉ được bỏ các dòng hỏng rồi gửi lại; người bị bỏ ra đưa vào lô mới. Cho sửa từng dòng trên màn hình thì phải làm thêm một màn.
7. **Mail của phiếu lô gửi cho ai.** Đã chốt 1 mail cho cả lô; đề xuất gửi cho người tạo lô.
8. **Loại "Thanh toán đồng sở hữu" trong danh mục mẫu.** Mỗi tài khoản trong app chỉ có 1 chủ, nên loại này lệch phạm vi. Đề xuất: bỏ khỏi dữ liệu mẫu.
