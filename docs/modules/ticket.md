# Module phiếu yêu cầu

Spec cho module `ticket` (CN-11 đến CN-17) và phần gửi mail kết quả CN-24 (module `notification`, Outbox, Kafka, Mailpit): tạo 4 loại phiếu lẻ, sửa và gửi lại, hủy, màn Chờ duyệt, Tất cả phiếu, Chi tiết phiếu, duyệt và từ chối qua Camunda, đổi trạng thái tài khoản bằng State pattern. Điều kiện xong ở mục 15; quyết định nghiệp vụ đã chốt ở mục 16; quyết định kỹ thuật đã chốt ở mục 17.

Đọc kèm: `docs/01-chuc-nang.md` mục 2.1–2.3, 3, 4.4, 4.7 và 5; `docs/modules/customer-account.md` (che dữ liệu, tìm tên không dấu, 4 chỗ để trống); `docs/modules/admin.md` mục 3, 4 (dạng lỗi, audit); bản thiết kế `TaoPhieu`, `ChiTietPhieu`, `PhieuChoDuyet` (bản 31/10), `TongQuan` (bản 31/10), `ChiTietTaiKhoan`, `ChiTietKhachHang`. Chỗ nào bản thiết kế khác file này thì làm theo file này (danh sách ở mục 18).

## 1. Làm và không làm

Làm:

- 4 loại phiếu lẻ `MO_TAI_KHOAN`, `PHONG_TOA`, `GO_PHONG_TOA`, `DONG_TAI_KHOAN`: tạo, sửa và gửi lại, hủy, duyệt, từ chối (CN-11 đến CN-13, CN-16, CN-17).
- Màn 3.9 Tạo phiếu, 3.10 Phiếu chờ duyệt, 3.10 Tất cả phiếu, 2.4 Chi tiết phiếu (CN-14, CN-15).
- Quy trình Camunda cho mọi phiếu, kể cả nhánh của phiếu lô (mục 4).
- State pattern đổi trạng thái tài khoản và bảng lịch sử trạng thái (mục 5).
- Mail kết quả qua Outbox, Kafka, Mailpit (CN-24, mục 9).
- Lưu giấy tờ trên Supabase Storage qua `FileStorage` (QT-10, mục 11.1).
- Nối 4 chỗ đang để trống ở chi tiết khách hàng và chi tiết tài khoản (mục 10.6).
- Sửa cách ghi log lỗi database để không lộ số CCCD (mục 11.3).

Không làm:

- Import và phiếu lô (CN-18): kiểm tra file, tạo cả lô khi duyệt, mail tổng kết, bảng khách trong lô ở trang 2.4. Spec này chỉ chừa chỗ nối: loại phiếu, nhánh BPMN gọi bean của `importbatch`, cột phải màn Chờ duyệt chỉ có số dòng và nút mở trang 2.4.
- Màn Tổng quan (CN-06): làm theo spec riêng. Spec này tạo hàm "quá 24 giờ" (mục 3.2) để Tổng quan gọi lại, không tự định nghĩa lần nữa.
- Job Ngủ đông (CN-25): làm cùng các việc chạy nền; job đó phải ghi bảng lịch sử trạng thái ở mục 3.3 và bỏ qua tài khoản có phiếu Chờ duyệt (D7).
- Đổi mail "Tài khoản đã được kích hoạt" sang Outbox: Pull Request riêng sau khi đã có Kafka (spec admin, B2).

## 2. File mới và file sửa

```
backend/src/main/java/io/github/arielne/alp/
├── ticket/           Ticket* (Controller, Service, Mapper + XML, DTO), TicketStatus, TicketType, TicketAction
├── ticket/workflow/  delegate của các service task, tên process và message
├── account/          AccountState và 5 lớp trạng thái, mở tài khoản Chờ mở, đọc lịch sử trạng thái
├── customer/         tạo, sửa hồ sơ khách (chỉ account gọi)
├── notification/     Kafka consumer, mail kết quả phiếu
├── common/           FileStorage (S3), kiểm loại file, TextNormalizer, OutboxWriter, OutboxPublisher
└── auth/             SecurityConfig: luật cho /api/tickets/**
backend/src/main/resources/bpmn/ticket-approval.bpmn
db/migrations/        4 script (mục 3)
db/seeds/             R__3: lịch sử trạng thái của tài khoản mẫu
frontend/src/
├── components/       TicketStatusTag, CccdInput
├── pages/ticket/     Tạo phiếu (dùng lại cho Sửa), Chờ duyệt, Tất cả phiếu, Chi tiết phiếu, TicketDetailBody
├── pages/customer/   tab Phiếu yêu cầu
├── pages/account/    Lịch sử phiếu, Vòng đời, Mở bởi, Duyệt mở bởi
├── pages/audit/      nhãn hành động và đối tượng của phiếu
└── router.tsx        /phieu/:id, /phieu/:id/sua
docker/nginx/default.conf   client_max_body_size cho /api/
compose.yaml, .env.example  biến Kafka và S3 cho backend
docs/diagrams/        erd.drawio (cột và bảng mới), ảnh sơ đồ BPMN xuất từ Camunda Modeler
```

- Chiều gọi theo `CLAUDE.md`: `ticket` → `account` → `customer`. `ticket` không gọi thẳng `customer`: mở tài khoản cho khách mới là một hàm của `account`, hàm đó gọi `customer` tạo hồ sơ. `ticket` đọc bảng `account`, `customer`, `employee`, `branch` bằng JOIN trong Mapper.
- Không module nào gọi `notification`: nó nhận sự kiện qua Kafka, rồi gọi Service của `ticket` để ghi "đã gửi mail".
- Outbox nằm ở `common` (T2), để module nào cũng ghi được sự kiện trong transaction của mình mà không phải gọi `notification`.
- Thư viện thêm vào `pom.xml`: `camunda-bpm-spring-boot-starter` 7.24, `camunda-bpm-assert` (test), `spring-kafka`, AWS SDK v2 `s3` (T6), module `kafka` và `minio` của Testcontainers (test). Kiểm `mvnw dependency:tree` xem bản MyBatis mà Camunda kéo theo có lệch bản của `mybatis-spring-boot-starter` không.

## 3. Database

4 script Flyway, số V tiếp theo V11, mỗi script một việc. Script chạy lên Supabase dùng chung khi backend khởi động, và lần đầu có Camunda thì Camunda tự tạo khoảng 50 bảng trong schema `camunda`: hỏi người dùng trước khi chạy backend lần đầu sau mỗi Pull Request có script. Cột và bảng mới thêm vào `docs/diagrams/erd.drawio` trong cùng Pull Request.

### 3.1 V12: cột mới của phiếu và giấy tờ (bảng `ticket`, `attachment`)

- `last_submitted_at timestamptz NOT NULL DEFAULT now()`: lần gửi gần nhất. Lúc tạo bằng `created_at` (cùng mặc định `now()` trong một câu INSERT nên bằng nhau), mỗi lần gửi lại đặt bằng `now()`. Thời gian chờ, "quá 24 giờ" và thứ tự "gửi sớm nhất" đều tính từ cột này (CN-14). Script thêm cột cho phép rỗng, chép `created_at` sang cho dòng đã có, rồi mới đặt mặc định và `NOT NULL`.
- Đổi tên `approved_by` thành `reviewed_by`, thêm `reviewed_at timestamptz`: người và lúc duyệt hoặc từ chối lần gần nhất; gửi lại thì đặt rỗng (T4). Cột "Người duyệt" ở các bảng phiếu, ô "Duyệt mở bởi" và báo cáo "Phiếu đã duyệt" (CN-19) đọc 2 cột này.
- `new_customer boolean NOT NULL DEFAULT false`: phiếu Mở tài khoản này đã tạo hồ sơ khách mới (hoặc ghi đè hồ sơ chưa từng mở được tài khoản, D6). Dùng để hiện "Khách hàng mới" và để biết phiếu có được sửa hồ sơ khách khi gửi lại không (D2).
- `attachment.removed_at timestamptz`, `attachment.removed_by bigint` (FK `employee`), cùng rỗng được: giấy tờ bị bỏ khi sửa phiếu (D3). Bỏ mềm: dòng và file vẫn còn, chỉ không còn tính là giấy tờ của phiếu.
- `ticket_history.action` lưu mã: `SUBMITTED`, `RESUBMITTED`, `REJECTED`, `APPROVED`, `CANCELLED`, `COMPLETED` (enum `TicketAction`).

### 3.2 V13: một định nghĩa thời gian chờ và "quá 24 giờ"

```sql
CREATE FUNCTION app.ticket_waiting_minutes(last_submitted_at timestamptz) RETURNS integer
    LANGUAGE sql STABLE STRICT
    RETURN floor(extract(epoch FROM now() - last_submitted_at) / 60)::integer;

CREATE FUNCTION app.ticket_is_overdue(last_submitted_at timestamptz) RETURNS boolean
    LANGUAGE sql STABLE STRICT
    RETURN now() - last_submitted_at > interval '24 hours';
```

- Đây là chỗ duy nhất có con số 24 giờ. Mapper màn Chờ duyệt gọi 2 hàm này; Function của Tổng quan (CN-06) phải gọi `app.ticket_is_overdue`, không tự viết lại điều kiện. Java và React không tự so 24 giờ.
- Tính bằng đồng hồ của database (`now()`): mọi máy dùng chung một đồng hồ, theo giờ đồng hồ kể cả đêm và ngày nghỉ (CN-14). Trừ 2 giá trị `timestamptz` nên không phụ thuộc múi giờ.
- `STABLE` chứ không `IMMUTABLE` vì kết quả đổi theo `now()`; chỉ gọi trong câu SELECT.
- "Quá" là hơn hẳn 24 giờ: chờ đúng 24 giờ 0 phút chưa tô màu cảnh báo.
- React chỉ đổi số phút backend trả về sang chữ: dưới 1 phút "Vừa gửi", rồi "20 phút", "20 giờ", "2 ngày 20 giờ".

### 3.3 V14: lịch sử trạng thái tài khoản (bảng của `account`)

Bảng `app.account_status_history`:

| Cột | Kiểu | Ghi chú |
| --- | --- | --- |
| `id` | bigint, IDENTITY | |
| `account_id` | bigint, FK `account` | |
| `from_status` | varchar(20), rỗng được | Rỗng khi tài khoản vừa được tạo |
| `to_status` | varchar(20) | CHECK 5 mã ở mục 2.1 |
| `ticket_id` | bigint, FK `ticket`, rỗng được | Rỗng khi job Ngủ đông chuyển |
| `changed_by` | bigint, FK `employee`, rỗng được | Người tạo (lúc tạo, hủy) hoặc người duyệt; rỗng là Hệ thống |
| `changed_at` | timestamptz, mặc định `now()` | |

- Index `(account_id, changed_at)`.
- Mỗi lần đổi trạng thái qua State pattern (mục 5) ghi 1 dòng trong cùng transaction. Stored Procedure của job Ngủ đông (CN-25) cũng phải ghi.
- Dùng cho Timeline "Vòng đời tài khoản" (mục 10.6) (T5). Không ghép từ `audit_log`: audit chỉ Admin được xem, và mô tả là chữ tự do.
- Tài khoản mẫu chưa có dòng nào: `db/seeds/R__3_khach_hang_tai_khoan.sql` thêm dòng mở tài khoản (và ngủ đông, đóng nếu có) cho tài khoản mẫu; mỗi loại dòng chỉ thêm khi tài khoản chưa có dòng loại đó (`WHERE NOT EXISTS`) để chạy lại file vẫn an toàn, kể cả với tài khoản mẫu đã có dòng từ phiếu. Các dòng mẫu không có phiếu và người làm (Hệ thống): rỗng → Hoạt động vào ngày mở; Hoạt động → Ngủ đông vào ngày giao dịch gần nhất cộng số tháng "Chuyển ngủ đông sau" của loại tài khoản; Hoạt động → Đã đóng vào ngày đóng (người dùng chốt 07/11/2026).

### 3.4 V15: index

- `ticket (branch_id, last_submitted_at) WHERE status = 'PENDING_APPROVAL'` cho màn Chờ duyệt.
- `ticket (account_id)` cho lịch sử phiếu của tài khoản (index QT-03 có sẵn chỉ chứa phiếu đang mở).
- `ticket_history (ticket_id)`, `attachment (ticket_id)`: Postgres không tự tạo index cho khóa ngoại.
- `outbox_event (id) WHERE published_at IS NULL` cho phần đẩy sự kiện sang Kafka (mục 9).

## 4. Quy trình Camunda

### 4.1 Cài đặt

- Camunda 7.24 chạy trong app (embedded), dùng chung DataSource và transaction manager của Spring. Vì vậy `taskService.complete(...)` gọi trong method `@Transactional` nằm chung transaction với các câu UPDATE của MyBatis: một bên lỗi thì cả hai cùng bị hủy (dùng ở mục 8).
- Bảng của Camunda ở schema `camunda` (T1): Flyway khai `schemas: app,camunda` chỉ để tạo schema rỗng (bảng lịch sử của Flyway vẫn ở `app`, script vẫn chỉ đụng `app`); kết nối đặt `search_path` là `camunda` bằng `spring.datasource.hikari.connection-init-sql`, để Camunda tạo bảng ở đó; `camunda.bpm.database.schema-name: camunda` kèm `table-prefix: camunda.` (Camunda bắt khai cả hai: tiền tố ghép trước tên bảng ở mọi câu SQL của Camunda). Mọi câu SQL của app đã ghi đủ `app.` nên không bị ảnh hưởng. Test chậm đầu tiên của PR 1 kiểm điều này trước khi làm phần khác.
- `camunda.bpm.database.schema-update: true` (tự tạo bảng lần đầu), `history-level: audit`, `job-execution.enabled: false`: quy trình không có bước bất đồng bộ hay hẹn giờ, tắt để Camunda không hỏi database vài giây một lần (T7). Không dùng giao diện web Cockpit.
- File `backend/src/main/resources/bpmn/ticket-approval.bpmn` vẽ bằng Camunda Modeler, tự deploy khi khởi động; xuất ảnh vào `docs/diagrams/` cho tài liệu. App không hiện sơ đồ (mục 6 của file chức năng).
- Mỗi phiếu một process instance, business key là mã phiếu. Biến quy trình: `ticketCode`, `ticketType`, `decision` (`APPROVED` hoặc `REJECTED`). Không đưa dữ liệu khách vào biến của Camunda.
- Danh sách Chờ duyệt đọc bảng `ticket`, không hỏi Camunda: lọc theo chi nhánh, người tạo và sắp xếp bằng SQL dễ hơn. Camunda giữ quy trình đang ở bước nào; trạng thái phiếu nằm ở `ticket.status`.

### 4.2 Các bước

| Id | Loại | Tên | Làm gì |
| --- | --- | --- | --- |
| `review` | User task | Kiểm soát viên duyệt | Chờ API duyệt hoặc từ chối hoàn thành task |
| `decisionGateway` | Exclusive gateway | Kết quả | `decision = APPROVED` sang nhánh duyệt (tách tiếp theo `ticketType`: lô hay lẻ); còn lại sang nhánh từ chối |
| `applyAccountChange` | Service task | Đổi trạng thái tài khoản | Delegate trong `ticket.workflow` gọi `AccountService.changeStatus` (mục 5) |
| `createBatchAccounts` | Service task | Tạo khách và tài khoản của lô | `${batchAccountCreator}`, bean của `importbatch` (CN-18). Có dòng hỏng thì ném BPMN error `BATCH_ROWS_INVALID`; nhánh lỗi trả phiếu về người tạo |
| `queueApprovedMail`, `queueRejectedMail` | Service task | Gửi mail kết quả | Cùng một delegate, ghi 1 dòng outbox (mục 9) |
| `mailSent` | Message catch event | Chờ gửi mail xong | `notification` gửi xong thì `ticket` correlate message này (T8) |
| `revise` | User task | Giao dịch viên sửa | Chờ API sửa và gửi lại, xong quay về `review` |
| `completed` | End event | Hoàn tất | |

- Nhánh duyệt: `review` → `decisionGateway` → `applyAccountChange` (hoặc `createBatchAccounts`) → `queueApprovedMail` → `mailSent` → `completed`.
- Nhánh từ chối: `review` → `decisionGateway` → `queueRejectedMail` → `revise` → `review`.
- Hủy phiếu: `runtimeService.deleteProcessInstance` khi quy trình đang dừng ở `review` hoặc `revise` (CN-13).
- Mọi service task chạy đồng bộ, trong transaction của API vừa gọi `complete`, không có `asyncBefore`. Phần chạy tách sau là gửi mail, do Outbox và Kafka làm (mục 9), không do job executor của Camunda.
- Test quy trình bằng camunda-bpm-assert, delegate thay bằng mock (mục 13).

## 5. Đổi trạng thái tài khoản (State pattern, QT-02)

- Interface `AccountState` và 5 lớp `PendingOpenState`, `ActiveState`, `BlockedState`, `DormantState`, `ClosedState`; mỗi lớp trả các trạng thái được chuyển tới theo bảng ở mục 2.1. Việc tạo mới (chưa có → Chờ mở) là hàm riêng `openPending`, không đi qua lớp trạng thái.
- `AccountService.changeStatus(accountId, expectedFrom, target, ticketId, actorId)`:
  1. Khóa dòng tài khoản (`SELECT ... FOR UPDATE`).
  2. Trạng thái hiện tại khác `expectedFrom` (ví dụ job vừa chuyển Ngủ đông trong lúc phiếu Phong tỏa chờ duyệt) → 409 "Trạng thái tài khoản đã đổi từ <A> sang <B> sau khi tạo phiếu. Hãy từ chối phiếu này." (D7).
  3. Lớp trạng thái không cho → 409 "Không chuyển được tài khoản từ <A> sang <B>".
  4. UPDATE `status`; sang Hoạt động lần đầu thì đặt `opened_at` và `last_transaction_at` là hôm nay (theo giờ Việt Nam); sang Đã đóng thì đặt `closed_at`. Ghi 1 dòng `account_status_history`.
- `openPending(...)`: nhận id khách đã có hoặc hồ sơ khách mới (khách mới thì gọi `customer` tạo hồ sơ trước), lấy số tài khoản từ `app.account_number_seq`, tạo tài khoản Chờ mở ở chi nhánh của người tạo (`last_transaction_at` tạm là hôm nay vì cột bắt buộc), ghi dòng lịch sử rỗng → Chờ mở.
- Chỉ `ticket` gọi `changeStatus` và `openPending` (cùng job CN-25 chạy trong database). Không có API nào đổi trạng thái tài khoản trực tiếp.

| Loại phiếu | Tài khoản lúc tạo phiếu | Duyệt xong | Người tạo hủy |
| --- | --- | --- | --- |
| `MO_TAI_KHOAN` | Chờ mở (tạo ngay lúc gửi) | Hoạt động | Đã đóng |
| `PHONG_TOA` | Hoạt động | Phong tỏa | Giữ nguyên |
| `GO_PHONG_TOA` | Phong tỏa hoặc Ngủ đông | Hoạt động | Giữ nguyên |
| `DONG_TAI_KHOAN` | Hoạt động, Phong tỏa hoặc Ngủ đông | Đã đóng | Giữ nguyên |

- Bị từ chối: trạng thái tài khoản không đổi (CN-17). `ticket.from_status`, `to_status` ghi lúc tạo phiếu và không đổi.

## 6. API

Mọi đường dẫn `/api/tickets/**` thuộc `ticket`; `ADMIN` luôn nhận 403.

| Method | Đường dẫn | Role | CN | Làm gì |
| --- | --- | --- | --- | --- |
| GET | `/api/tickets` | MAKER, CHECKER | CN-14 | Tất cả phiếu. Lọc `statuses` (nhiều giá trị), `code`, `type`, `branchId`, `createdFrom`, `createdTo`, `accountId`, `customerId`, `page`; trả `{ items, total, statusCounts }`, 10 dòng mỗi trang |
| GET | `/api/tickets/pending-review` | CHECKER | CN-14 | Màn Chờ duyệt. Lọc `code`, `type`; tối đa 200 phiếu, mỗi phiếu kèm `waitingMinutes`, `overdue` |
| GET | `/api/tickets/pending-review/summary` | CHECKER | CN-14 | `{ pendingCount, overdueCount }` cho badge của menu (D9) |
| GET | `/api/tickets/{id}` | MAKER, CHECKER | CN-15 | Chi tiết, giấy tờ, lịch sử xử lý, kèm cờ `canApprove`, `canReject`, `canEdit`, `canCancel`, `blockedReason` tính cho người đang xem |
| GET | `/api/tickets/{id}/attachments/{attachmentId}` | MAKER, CHECKER | CN-15 | Trả file (QT-10); `?download=true` thì tải về |
| GET | `/api/tickets/{id}/edit-data` | MAKER (người tạo) | CN-12 | Dữ liệu để sửa phiếu Bị từ chối, kể cả hồ sơ khách đầy đủ (D2) |
| GET | `/api/tickets/eligibility` | MAKER | CN-11 | `?type=&accountNumber=`: tài khoản (CCCD đã che), trạng thái trước → sau, `allowed`, `message` |
| POST | `/api/tickets/customer-check` | MAKER | CN-11 | `{ "cccd": "..." }` (POST để CCCD không lên URL): trả `state` là `NEW` (chưa có hồ sơ), `EXISTS` (đã có, dùng "Khách hàng đã có"), `REUSABLE` (hồ sơ chưa từng mở được tài khoản, phiếu mới sẽ ghi đè, D6) hoặc `PENDING` (hồ sơ đang chờ duyệt ở phiếu khác, D5), kèm `customerId`, `ticketCode` khi có |
| POST | `/api/tickets` | MAKER | CN-11 | multipart: phần `data` (JSON, mục 7.1) và `files` (1–5 file); trả `{ id, code }` |
| PUT | `/api/tickets/{id}` | MAKER (người tạo) | CN-12 | multipart: sửa rồi gửi lại |
| POST | `/api/tickets/{id}/cancel` | MAKER (người tạo; người cùng chi nhánh khi người tạo đã bị khóa, D12) | CN-13 | `{ "reason": "..." }` (D4) |
| POST | `/api/tickets/{id}/approve` | CHECKER | CN-16 | Không có thân request |
| POST | `/api/tickets/{id}/reject` | CHECKER | CN-17 | `{ "reason": "..." }`, 1–500 ký tự |
| GET | `/api/accounts/{id}/status-history` | MAKER, CHECKER | CN-10 | Thuộc `account`: Vòng đời tài khoản (mục 10.6) |

Mã lỗi, theo dạng lỗi chung ở spec admin mục 3:

| Mã | Khi nào | `message` |
| --- | --- | --- |
| 400 | Sai Bean Validation; không có file, quá 5 file, file quá 5 MB, sai loại file; `type` lạ | `fieldErrors`, hoặc câu nói rõ file nào sai |
| 401 | Chưa đăng nhập | |
| 403 | Sai role; không phải người tạo (sửa, `edit-data`); hủy phiếu mà không phải người tạo, cũng không thuộc trường hợp D12; tự duyệt phiếu mình tạo (QT-01); khác chi nhánh với phiếu (QT-04) | "Bạn không thể duyệt phiếu do chính mình tạo"; "Chỉ kiểm soát viên Chi nhánh <tên> được duyệt phiếu này"; "Chỉ người tạo phiếu được sửa"; "Chỉ người tạo phiếu được hủy" |
| 404 | Không có phiếu, tài khoản, khách hàng; giấy tờ không có hoặc không thuộc phiếu trên URL | "Không tìm thấy phiếu"... |
| 409 | Tài khoản không ở trạng thái cho phép; đang có phiếu mở (QT-03); CCCD đã có hồ sơ; hồ sơ khách đang chờ duyệt (D5); phiếu không còn ở trạng thái cho thao tác (QT-11); trạng thái tài khoản đã đổi (mục 5); người tạo chưa có chi nhánh | "Tài khoản đang có phiếu <mã> chờ duyệt"; "Phiếu này vừa được người khác xử lý" |

- `SecurityConfig` thêm luật theo method và đường dẫn, đặt trước luật chung `/api/**`: `/api/tickets/pending-review/**`, `/api/tickets/*/approve`, `/api/tickets/*/reject` chỉ `CHECKER`; POST `/api/tickets`, PUT `/api/tickets/*`, `/api/tickets/*/cancel`, `/api/tickets/*/edit-data`, `/api/tickets/eligibility`, `/api/tickets/customer-check` chỉ `MAKER`; còn lại của `/api/tickets/**` cho `MAKER`, `CHECKER`. Luật "người tạo", "cùng chi nhánh" kiểm trong Service vì phải đọc phiếu.
- Người làm và chi nhánh lấy từ hồ sơ `employee` theo `keycloak_id` trong session; DTO không có trường chi nhánh hay người tạo (QT-04).
- QT-11 trả 409 chứ không 403: người bấm sau có quyền, chỉ là phiếu đã đổi trạng thái.

## 7. Tạo, sửa, hủy phiếu (CN-11, CN-12, CN-13)

### 7.1 Tạo phiếu

Phần `data` của request:

```json
{
  "type": "MO_TAI_KHOAN",
  "customerId": null,
  "newCustomer": {
    "fullName": "...", "dateOfBirth": "1990-05-12", "gender": "Nữ", "cccd": "...",
    "idIssueDate": "2021-04-10", "idIssuePlace": "...", "phone": "...", "email": "...", "address": "..."
  },
  "accountTypeId": 3,
  "accountNumber": null,
  "reason": "...",
  "confirmed": true
}
```

- Phiếu Mở tài khoản: đúng một trong `customerId` và `newCustomer`, kèm `accountTypeId`. 3 loại còn lại: chỉ `accountNumber` (10 chữ số).
- Hồ sơ khách mới: họ tên 2–100 ký tự, ít nhất 2 từ; ngày sinh ở quá khứ; CCCD đúng 12 chữ số (QT-05); ngày cấp không sau hôm nay và không trước ngày sinh; nơi cấp, địa chỉ bắt buộc; điện thoại 10–11 chữ số; email đúng dạng; giới tính `Nam`, `Nữ` hoặc rỗng. `reason` 1–500 ký tự; `confirmed` phải là `true`.
- Các bước, trong 1 transaction:
  1. Kiểm: khách mới thì CCCD chưa có hồ sơ, hoặc hồ sơ có số đó chưa từng mở được tài khoản (D6); khách đã có thì hồ sơ không đang chờ duyệt (D5); 3 loại còn lại thì tài khoản đúng trạng thái theo bảng ở mục 5 và không có phiếu Chờ duyệt hoặc Bị từ chối (QT-03).
  2. Lưu file lên Storage (mục 11.1).
  3. Phiếu Mở tài khoản: `AccountService.openPending` (mục 5). Khách mới có hồ sơ cũ chưa từng mở được tài khoản thì `account` gọi `customer` ghi đè hồ sơ đó bằng dữ liệu mới (giữ `id`, nên các tài khoản Đã đóng cũ vẫn gắn đúng hồ sơ), thay vì tạo hồ sơ mới.
  4. Lấy mã phiếu: `PYC-<năm theo giờ Việt Nam>-<số từ app.ticket_code_seq, đủ 4 chữ số>`.
  5. Bắt đầu process instance với business key là mã phiếu. Cột `process_instance_id` bắt buộc nên phải bắt đầu trước khi INSERT; quy trình dừng ngay ở `review`, chưa chạy service task nào.
  6. INSERT `ticket` (`PENDING_APPROVAL`, `submit_count` 1, `last_submitted_at` bằng `created_at`, chi nhánh của người tạo, `from_status`, `to_status`, `new_customer`), `attachment`, `ticket_history` `SUBMITTED`; audit `CREATE`.
- 2 request tạo phiếu cho cùng tài khoản đến cùng lúc có thể cùng qua bước 1; index `uq_ticket_open_per_account` chặn ở bước 6 và trả 409 cùng câu báo (mục 11.3). Không khóa dòng `account` từ `ticket`.
- Người tạo chưa có chi nhánh thì 409 (bình thường không xảy ra vì Admin chỉ gán `MAKER` khi đã có chi nhánh).
- Trạng thái CCCD (dùng chung cho `customer-check` và bước 1), đọc bảng `customer`, `account`, `ticket`:
  - `NEW`: chưa có hồ sơ.
  - `PENDING` (D5): có phiếu Mở tài khoản `new_customer` đang Chờ duyệt hoặc Bị từ chối trỏ tới hồ sơ đó.
  - `REUSABLE` (D6): không `PENDING`, và mọi tài khoản của hồ sơ đều Đã đóng với `opened_at` rỗng (chưa bao giờ Hoạt động).
  - `EXISTS`: còn lại.

### 7.2 Chuẩn hóa chữ của hồ sơ khách

- Họ tên, nơi cấp, địa chỉ: bỏ khoảng trắng hai đầu, gộp nhiều khoảng trắng thành một, chuẩn hóa Unicode NFC (`java.text.Normalizer`) trước khi kiểm và lưu. Hàm `app.unaccent_vi` chỉ nhận ra chữ có dấu ở dạng NFC; họ tên dán từ file PDF hay gõ bằng một số bộ gõ có thể ở dạng NFD (chữ cái và dấu tách rời), khi đó tìm "binh" sẽ không ra.
- Chuẩn hóa nằm trong `customer`, nơi INSERT và UPDATE bảng `customer`, nên mọi đường tạo, sửa hồ sơ đều đi qua: phiếu Mở tài khoản, sửa phiếu, phiếu lô.
- Hàm chuẩn hóa đặt ở `common` (`TextNormalizer`), thay đoạn đang viết riêng trong `CustomerService` (chuẩn hóa từ khóa tìm). Spec importbatch dùng lại hàm này.

### 7.3 Sửa và gửi lại (CN-12)

- Chỉ người tạo (so `keycloak_id`), chỉ khi phiếu `REJECTED`. Khóa dòng phiếu (`FOR UPDATE`) trước khi kiểm.
- Sửa được: lý do; giấy tờ (thêm file mới, bỏ mềm file cũ, tối đa 5 file đang dùng; D3); phiếu Mở tài khoản: loại tài khoản, và hồ sơ khách khi `new_customer` là true (D2). Không đổi loại phiếu, tài khoản, khách hàng: muốn đổi thì hủy phiếu rồi tạo phiếu mới.
- Hồ sơ khách sửa qua `account` → `customer`, chuẩn hóa như mục 7.2; đổi CCCD thì kiểm trùng lại.
- Kiểm lại tài khoản như lúc tạo: trạng thái hiện tại vẫn bằng `from_status` của phiếu. Phiếu có thể nằm ở Bị từ chối nhiều ngày, trong lúc đó job Ngủ đông có thể đã đổi trạng thái; khác thì 409 "Trạng thái tài khoản đã đổi từ <A> sang <B>. Hãy hủy phiếu này và tạo phiếu mới." Không kiểm lại thì phiếu gửi lại được nhưng chắc chắn bị chặn ở bước duyệt.
- Lưu: `PENDING_APPROVAL`, `submit_count` + 1, `last_submitted_at` = `now()`, `reviewed_by`, `reviewed_at`, `reject_reason` về rỗng (lý do cũ vẫn ở `ticket_history`); lịch sử `RESUBMITTED`; hoàn thành task `revise` để quy trình về `review`; audit `RESUBMIT`.
- Phiếu gửi lại xếp theo `last_submitted_at` mới, nên rơi xuống dưới danh sách Chờ duyệt và thời gian chờ đếm lại từ đầu (CN-14).

### 7.4 Hủy phiếu (CN-13)

- Người tạo, khi phiếu `PENDING_APPROVAL` hoặc `REJECTED`; khóa dòng phiếu trước.
- Người tạo đã bị khóa (`employee.status = LOCKED`) thì Giao dịch viên cùng chi nhánh với phiếu cũng được hủy, không được sửa (D12). Lịch sử, audit ghi người hủy thật; mô tả audit ghi thêm "hủy thay người tạo đã bị khóa".
- Phiếu sang `CANCELLED`. Phiếu Mở tài khoản: tài khoản Chờ mở → Đã đóng qua `changeStatus`; hồ sơ khách giữ nguyên (D6). Xóa process instance. Lịch sử `CANCELLED` kèm lý do (D4); audit `CANCEL`.
- Không gửi mail khi hủy (CN-24 chỉ gửi khi duyệt, từ chối).
- Kiểm soát viên đang mở phiếu vừa bị hủy: bấm Duyệt nhận 409 của QT-11.

## 8. Duyệt và từ chối (CN-16, CN-17)

### 8.1 Duyệt trong 1 transaction

`TicketService.approve(ticketId)` có `@Transactional`; mọi bước dưới đây nằm trong 1 transaction:

1. `SELECT ... FROM app.ticket WHERE id = ? FOR UPDATE`. Không có thì 404.
2. Trạng thái khác `PENDING_APPROVAL` → 409 "Phiếu này vừa được người khác xử lý" (QT-11). Hai kiểm soát viên bấm cùng lúc: người sau đợi khóa dòng của người trước, rồi thấy trạng thái đã đổi.
3. `keycloak_id` người duyệt bằng của người tạo → 403 (QT-01). Chi nhánh người duyệt khác `ticket.branch_id` → 403 (QT-04).
4. Tìm task `review` của `process_instance_id`; không có → 409 như bước 2.
5. UPDATE phiếu `APPROVED`, `reviewed_by`, `reviewed_at`; lịch sử `APPROVED`.
6. `taskService.complete(task, decision = APPROVED)`. Camunda chạy tiếp ngay trong thread này: `applyAccountChange` đổi trạng thái tài khoản (mục 5), `queueApprovedMail` ghi 1 dòng outbox, rồi dừng ở `mailSent`.
7. Audit `APPROVE` (AOP), cùng transaction.
8. Commit: phiếu, tài khoản, lịch sử, outbox, audit và bảng runtime của Camunda được lưu cùng lúc. Bước nào lỗi (kể cả trong service task) thì tất cả cùng bị hủy: task `review` vẫn còn, phiếu vẫn Chờ duyệt, tài khoản giữ trạng thái cũ.

- Không gửi mail trong transaction này, chỉ ghi `outbox_event`. Mail đi sau khi commit (mục 9): máy gửi mail chậm hay tắt không làm API duyệt chậm hay lỗi, và không có mail cho một lần duyệt đã bị hủy.
- Bước 5 đứng trước bước 6 để nhánh lỗi của phiếu lô (dòng hỏng) đổi lại phiếu sang Bị từ chối trong cùng transaction (spec importbatch).

### 8.2 Từ chối

- Bước 1–4 như duyệt; `reason` 1–500 ký tự sau khi bỏ khoảng trắng hai đầu.
- UPDATE `REJECTED`, `reject_reason`, `reviewed_by`, `reviewed_at`; lịch sử `REJECTED` kèm lý do; `complete(task, decision = REJECTED)` → `queueRejectedMail` → dừng ở `revise`; audit `REJECT`. Cùng 1 transaction như mục 8.1. Trạng thái tài khoản không đổi.

### 8.3 Nút và hộp xác nhận

- Duyệt: Modal.confirm "Duyệt phiếu <mã>?" với nội dung "Tài khoản <số>: <A> → <B>. Hãy chắc bạn đã đối chiếu giấy tờ đính kèm." Không bắt mở từng giấy tờ trước (CN-16).
- Từ chối: Modal có Form, TextArea bắt buộc, `maxLength` 500, `showCount`; câu "Phiếu sẽ trả về <người tạo> để sửa và gửi lại. Hệ thống gửi mail kèm lý do bên dưới."
- Nút hiện và khóa theo cờ của API chi tiết: người tạo thấy nút khóa kèm Tooltip "Bạn không thể duyệt phiếu do chính mình tạo" (QT-01, lớp chặn thứ hai); kiểm soát viên khác chi nhánh không thấy nút, có dòng "Phiếu thuộc Chi nhánh <tên>" (D11). Backend vẫn kiểm lại mọi trường hợp.

## 9. Mail kết quả (CN-24): Outbox, Kafka, Mailpit

1. Ghi sự kiện (`common/OutboxWriter`): INSERT `app.outbox_event` trong transaction đang chạy. `aggregate_type` `TICKET`, `aggregate_id` là id phiếu, `event_type` `TICKET_APPROVED` hoặc `TICKET_REJECTED`, `payload` `{ "ticketId": ..., "ticketCode": "..." }`. Payload không có tên, CCCD, email: ai đọc được Kafka cũng không thấy dữ liệu khách.
2. Đẩy sang Kafka (`common/OutboxPublisher`, `@Scheduled` 2 giây một lần): trong 1 transaction lấy tối đa 50 dòng `published_at IS NULL` theo `id`, kèm `FOR UPDATE SKIP LOCKED` để 2 backend chạy cùng lúc không gửi trùng; gửi từng dòng tới topic `ticket-events` với key là id phiếu (mọi sự kiện của một phiếu vào cùng partition, giữ đúng thứ tự), đợi Kafka xác nhận rồi đặt `published_at`. Kafka tắt thì dòng giữ nguyên, lượt sau gửi lại; ghi log cảnh báo.
3. Nhận sự kiện (`notification`, `@KafkaListener`, group `alp-notification`): đọc phiếu (JOIN `ticket`, `account`, `customer`, `employee`), gửi bằng Spring Mail tới Mailpit, người gửi `no-reply@nhdemo.local`:
   - `TICKET_APPROVED`: 1 mail cho khách (email nhận thông báo; số tài khoản và trạng thái mới; không có CCCD), 1 mail cho người tạo phiếu. Gửi xong gọi `TicketService.markMailSent(ticketId)`: khóa phiếu; đang `APPROVED` thì sang `COMPLETED`, lịch sử `COMPLETED` với người làm rỗng (Hệ thống), correlate message `mailSent` để quy trình kết thúc, audit `COMPLETE` (T3); phiếu đã `COMPLETED` thì bỏ qua (sự kiện tới lần hai).
   - `TICKET_REJECTED`: 1 mail cho người tạo kèm lý do. Phiếu giữ Bị từ chối.
4. Gửi mail lỗi (Mailpit tắt): ném lỗi để Spring Kafka thử lại, khoảng chờ tăng dần tới tối đa 1 phút, không giới hạn số lần. Trong lúc đó phiếu hiện "Đã duyệt · đang gửi mail". Kafka giao "ít nhất một lần": backend tắt giữa 2 mail thì lần sau gửi lại cả 2; chấp nhận, ghi rõ trong comment.

- Topic tạo bằng bean `NewTopic`: 1 partition, 1 bản sao, khớp Kafka 1 broker trong Compose. Backend chạy bằng `mvnw` nối Kafka ở cổng 9092 trên máy; trong Docker nối `kafka:19092` (biến `SPRING_KAFKA_BOOTSTRAP_SERVERS` trong `compose.yaml`).
- Phiếu lô dùng cùng đường này với loại sự kiện riêng (spec importbatch).
- Xem kết quả: Kafka UI (cổng 8090) có sự kiện trong topic; giao diện web của Mailpit (cổng 8025) có mail.

## 10. Màn hình

### 10.1 Route

| Route | Màn | Role |
| --- | --- | --- |
| `/phieu/tao-moi` | 3.9 Tạo phiếu | MAKER |
| `/phieu/:id/sua` | 3.9 dùng lại để sửa và gửi lại (D10) | MAKER |
| `/phieu/cho-duyet` | 3.10 Phiếu chờ duyệt | CHECKER |
| `/phieu` | 3.10 Tất cả phiếu | MAKER, CHECKER |
| `/phieu/:id` | 2.4 Chi tiết phiếu | MAKER, CHECKER |

- `/phieu/:id`, `/phieu/:id/sua` không có trong menu nên tự bọc `RequireRole` như các route chi tiết đã có. React Router ưu tiên đoạn cố định, nên `/phieu/tao-moi`, `/phieu/cho-duyet` không bị hiểu là `:id`.
- `TicketStatusTag` (5 trạng thái, màu theo bản thiết kế: Chờ duyệt xanh dương, Đã duyệt xanh lá, Bị từ chối đỏ, Hoàn tất nền xanh lá đậm chữ trắng, Đã hủy xám) đặt ở `components/`, dùng ở mọi màn có phiếu.

### 10.2 Tạo phiếu (3.9)

- Steps 4 bước như thiết kế: Loại phiếu → Khách hàng & tài khoản → Lý do & giấy tờ → Xác nhận; một Form giữ giá trị qua các bước. Dòng phụ: "Phiếu sẽ chuyển tới kiểm soát viên Chi nhánh <tên> để duyệt".
- Tham số URL: `loai` hợp lệ thì bỏ bước 1 (đi từ Tổng quan hoặc chi tiết tài khoản); `taiKhoan` điền sẵn số và kiểm ngay bằng `eligibility`; `khachHang` chọn sẵn "Khách hàng đã có" với khách đó (đi từ chi tiết khách hàng). Giá trị lạ thì bỏ qua.
- Bước 2, khách mới: Form hồ sơ như bản thiết kế. Ô CCCD dùng `CccdInput`: hiện đủ khi đang gõ, che khi rời ô (QT-05). Gõ đủ 12 số thì gọi `customer-check`: `EXISTS` thì báo dưới ô và gợi ý chọn "Khách hàng đã có", khóa Tiếp tục; `PENDING` thì báo "Hồ sơ khách đang chờ duyệt ở phiếu <mã>", khóa Tiếp tục; `REUSABLE` thì hiện Alert "Số CCCD này có hồ sơ cũ chưa từng mở được tài khoản. Gửi phiếu sẽ thay hồ sơ đó bằng thông tin bạn nhập." và vẫn cho đi tiếp. Bước 4 nhắc lại câu này.
- Bước 2, khách đã có: nhập đủ 12 số CCCD, hiện thẻ khách (CCCD đã che, ngày sinh, chi nhánh, Tag các tài khoản đang có), rồi chọn loại tài khoản mở thêm.
- Bước 2, 3 loại còn lại: ô tìm số tài khoản gọi `eligibility`; hiện thẻ tài khoản và trạng thái trước → sau; không hợp lệ thì báo dưới ô (ví dụ "Tài khoản đang có phiếu <mã> chờ duyệt") và khóa nút Tiếp tục.
- Bước 3: TextArea 500 ký tự có đếm; Upload.Dragger nhận PDF, JPG, PNG, tối đa 5 file, mỗi file 5 MB, kiểm trước ở trình duyệt (backend kiểm lại); dòng "Cần có: ..." theo loại phiếu (mục 2.2 của file chức năng). File giữ trong Form, gửi cùng request ở bước 4.
- Bước 4: Descriptions xem lại toàn bộ, dòng "Người duyệt: Kiểm soát viên Chi nhánh <tên> (trừ bạn)", Checkbox "Tôi đã đối chiếu thông tin với giấy tờ gốc của khách hàng" bắt buộc, câu "Sau khi gửi, phiếu không sửa được cho tới khi kiểm soát viên trả về."
- Gửi xong: Result "Đã gửi phiếu <mã>", nút "Xem phiếu" và "Tạo phiếu khác".
- Chế độ sửa (`/phieu/:id/sua`): bỏ bước 1, điền sẵn từ `edit-data`, hiện lý do từ chối ở đầu Form, nút cuối là "Gửi lại". `RequireRole` chỉ kiểm role, nên Giao dịch viên khác mở link này nhận 403 từ `edit-data` và trang hiện trang 403; phiếu không còn Bị từ chối (409) thì hiện thông báo kèm nút về chi tiết phiếu.

### 10.3 Phiếu chờ duyệt (3.10)

- 2 cột. Trái: ô mã phiếu, Select loại phiếu và danh sách; mỗi dòng có mã phiếu, thời gian chờ, loại phiếu, nhãn "Gửi lại lần N" với N là `submit_count − 1`, chỉ hiện khi N từ 1 trở lên (D1), hoặc đơn vị đề nghị của phiếu lô, họ tên khách và số tài khoản ("Khách hàng mới" với phiếu mở cho khách mới). Phải: `TicketDetailBody` và nút Từ chối, Duyệt.
- Dòng phụ: "<n> phiếu của Chi nhánh <tên> · không gồm phiếu do bạn tạo · gửi sớm nhất lên trên". Thời gian chờ tô màu cảnh báo khi `overdue`. Không có nút duyệt nhanh ở danh sách.
- Mở màn: chọn phiếu theo `?phieu=<id>` nếu phiếu đó có trong danh sách (nút "Mở để duyệt" ở Tổng quan), không thì chọn phiếu đầu.
- Duyệt hoặc từ chối xong: toast "Đã duyệt <mã> · đang mở <mã kế>", tải lại danh sách theo bộ lọc đang bật rồi mở phiếu đầu. Nút Duyệt, Từ chối khóa từ lúc bấm tới khi chi tiết phiếu kế tải xong. Nhận 409 thì hiện `message.warning` với câu của backend rồi tải lại danh sách.
- Phiếu lô: cột phải chỉ có số dòng và nút "Mở trang đầy đủ" sang `/phieu/:id` (CN-14).
- Hết phiếu: Empty "Không còn phiếu nào chờ bạn duyệt".
- Màn hẹp (dưới breakpoint `md` của AntD): chỉ có danh sách, bấm vào mở `/phieu/:id`; duyệt hoặc từ chối ở đó xong thì quay về `/phieu/cho-duyet`.

### 10.4 Tất cả phiếu (3.10)

- Segmented trạng thái: Tất cả, Chờ duyệt, Đã duyệt, Bị từ chối, Hoàn tất, Đã hủy, mỗi mục kèm số phiếu (D8). Ô mã phiếu (khớp từ đầu, chỉ nhận chữ, số và `-`), Select loại phiếu, Select chi nhánh (API danh mục có sẵn), RangePicker ngày tạo. React gửi `createdFrom`, `createdTo` dạng ngày (`2026-11-05`); backend đổi thành khoảng từ 0 giờ ngày đầu tới trước 0 giờ ngày sau ngày cuối, theo giờ Việt Nam, rồi so với `created_at`. So thẳng ngày với `timestamptz` theo giờ UTC thì phiếu tạo từ 0 giờ tới 7 giờ sáng bị tính sang ngày trước.
- Bảng: Mã phiếu (link), Loại phiếu, Đối tượng (họ tên và số tài khoản; phiếu lô là số khách và tên file), Trạng thái (Tag, dưới là lý do từ chối khi Bị từ chối, "Đang gửi mail" khi Đã duyệt), Người tạo, Người duyệt, Cập nhật. Mới cập nhật ở trên (`updated_at`, rồi `id` giảm dần). Phân trang ở backend.
- Bộ lọc và số trang ghi lên URL như màn Tài khoản (`?trang-thai=&ma=&loai=&chi-nhanh=&tu=&den=&trang=`); các tham số này không chứa thông tin cá nhân. Đổi bộ lọc thì về trang 1; giá trị lạ thì bỏ qua.
- Xem được phiếu của mọi chi nhánh (QT-04).

### 10.5 Chi tiết phiếu (2.4) và `TicketDetailBody`

- Đầu trang: Breadcrumb theo nơi đi tới (Chờ duyệt hoặc Tất cả phiếu); tiêu đề "<mã> · <loại phiếu>"; Tag trạng thái; dòng "Tạo bởi <người tạo> · <ngày tạo> · Gửi lần <N>".
- Nút theo cờ của API: người tạo thấy "Sửa và gửi lại" (khi Bị từ chối) và "Hủy phiếu" (khi Chờ duyệt, Bị từ chối); kiểm soát viên thấy Từ chối, Duyệt (mục 8.3).
- Hủy phiếu: Modal nói rõ hậu quả theo loại phiếu (Mở tài khoản: "Tài khoản đang Chờ mở sẽ chuyển sang Đã đóng."; loại khác: "Tài khoản <số> giữ nguyên trạng thái <A>."), phiếu Mở tài khoản cho khách mới thêm câu "Sai thông tin khách thì Sửa và gửi lại, đừng hủy: hồ sơ khách vẫn được giữ sau khi hủy." (D6), ô lý do (D4), nút "Xác nhận hủy phiếu". Người hủy thay người tạo đã bị khóa (D12) thấy thêm dòng "Người tạo <tên> đã bị khóa; bạn hủy thay."
- `TicketDetailBody`, dùng chung với cột phải màn Chờ duyệt (CN-15):
  - Steps: Tạo phiếu → Kiểm soát viên duyệt → Gửi mail → Hoàn tất. Bị từ chối, Đã hủy: bước đang dừng hiện trạng thái lỗi kèm chữ "Bị từ chối" hoặc "Đã hủy".
  - Nội dung yêu cầu: "Tài khoản <số> sẽ chuyển <A> → <B> khi phiếu được duyệt" (đã duyệt thì "đã chuyển"; đã hủy thì "Tài khoản <số> không chuyển <A> → <B> vì phiếu đã hủy", người dùng chốt 11/11/2026); Descriptions: Loại phiếu, Loại tài khoản, Chủ tài khoản (link sang chi tiết khách hàng), Số CCCD (đã che), Người tạo, Chi nhánh tạo, Ngày tạo, Số lần gửi, Lý do; phiếu Bị từ chối có thêm Lý do từ chối.
  - Giấy tờ đính kèm: chỉ giấy tờ đang dùng (`removed_at` rỗng); tên, dung lượng, nút Xem (mở tab mới) và Tải; không có nút xóa. Giấy tờ đã bỏ chỉ còn tên trong Lịch sử xử lý.
  - Lịch sử xử lý: Timeline từ `ticket_history`, mới nhất ở trên; phiếu đang chờ thì dòng đầu là "Hiện tại: Chờ kiểm soát viên duyệt".
- Tên loại `GO_PHONG_TOA` theo `from_status` của phiếu: Ngủ đông thì "Kích hoạt lại", Phong tỏa thì "Gỡ phong tỏa". Ở chi tiết tài khoản, tên này vẫn theo trạng thái hiện tại của tài khoản.

### 10.6 Nối 4 chỗ đang để trống

| Chỗ | Lấy từ | Hiện |
| --- | --- | --- |
| Tab Phiếu yêu cầu, chi tiết khách hàng | `GET /api/tickets?customerId=`: phiếu của mọi tài khoản của khách, kể cả phiếu lô đã tạo tài khoản cho khách | Mã phiếu, Loại phiếu, Tài khoản, Trạng thái, Người tạo, Ngày tạo; số phiếu trên nhãn tab |
| Lịch sử phiếu, chi tiết tài khoản | `GET /api/tickets?accountId=` | Mã phiếu (kèm ngày tạo), Loại phiếu, Trạng thái (dưới là lý do từ chối), Người tạo, Người duyệt; số phiếu ở tiêu đề Card |
| Vòng đời tài khoản | `GET /api/accounts/{id}/status-history` | Timeline mới nhất ở trên, màu chấm theo trạng thái đích; mỗi mục: thời điểm, tên bước, Tag trạng thái đích, "<người> tạo", "<người> duyệt" hoặc "Hệ thống", mã phiếu, "Lý do: ..." |
| Mở bởi, Duyệt mở bởi | `GET /api/accounts/{id}` thêm 2 trường | Người tạo phiếu mở tài khoản (lẻ hoặc lô); người duyệt khi phiếu đó Đã duyệt hoặc Hoàn tất; tài khoản mẫu không có phiếu thì "—" |

- Tên bước của Vòng đời: rỗng → Chờ mở "Tạo phiếu mở tài khoản"; Chờ mở → Hoạt động "Mở tài khoản"; rỗng → Hoạt động "Mở tài khoản theo lô" khi có mã phiếu lô, "Mở tài khoản" khi không có mã phiếu (tài khoản mẫu); Chờ mở → Đã đóng "Hủy phiếu mở tài khoản"; sang Phong tỏa "Phong tỏa"; Phong tỏa → Hoạt động "Gỡ phong tỏa"; Ngủ đông → Hoạt động "Kích hoạt lại"; sang Đã đóng "Đóng tài khoản"; Hoạt động → Ngủ đông "Tự chuyển trạng thái" (job, ghi chú "Không phát sinh giao dịch trong <n> tháng").
- `AccountMapper` (đọc chéo) JOIN `ticket`, `import_batch_row`, `employee` để lấy Mở bởi, Duyệt mở bởi và lý do của phiếu; `account` không gọi Service của `ticket`.
- Sửa comment đầu file `AccountDetailPage.tsx`, `CustomerDetailPage.tsx` (đang ghi "làm cùng module phiếu").

## 11. Bảo vệ dữ liệu

### 11.1 Giấy tờ (QT-10)

- `FileStorage` là interface ở `common`; cài đặt `S3FileStorage` gọi API S3 của Supabase Storage bằng AWS SDK v2, bucket private `giay-to`; test dùng MinIO của Testcontainers. Biến `STORAGE_S3_ENDPOINT`, `STORAGE_S3_REGION`, `STORAGE_S3_ACCESS_KEY`, `STORAGE_S3_SECRET_KEY` nằm trong `.env`, `.env.example` chỉ ghi tên. Người dùng tự tạo bucket và khóa S3 trên Supabase.
- Kiểm loại thật bằng các byte đầu file: PDF bắt đầu bằng `%PDF-`, PNG bằng `89 50 4E 47 0D 0A 1A 0A`, JPG bằng `FF D8 FF`; đuôi file phải khớp loại đọc được. Không thêm thư viện nhận dạng file.
- Tên lưu là UUID; tên gốc lưu ở `attachment.original_name` (bỏ ký tự đường dẫn) chỉ để hiển thị.
- File lưu lên Storage trước câu INSERT: transaction bị hủy thì xóa các file vừa lưu (`TransactionSynchronization`, giống hoàn tác KeyCloak ở spec admin). Giấy tờ bị bỏ khi sửa phiếu không bị xóa khỏi Storage (bỏ mềm, D3); API tải trả 404 cho giấy tờ đã bỏ, file chỉ còn để tra cứu khi cần.
- Tải file chỉ qua backend: kiểm role, kiểm giấy tờ thuộc đúng phiếu trên URL; trả `Content-Type` đã kiểm lúc lưu, `X-Content-Type-Options: nosniff`, `Content-Disposition` là `inline` (Xem) hoặc `attachment` (Tải) với tên gốc mã hóa UTF-8. Không có link công khai, không dùng link ký sẵn (presigned URL).
- Giới hạn cỡ: `spring.servlet.multipart.max-file-size: 5MB`, `max-request-size: 26MB`; Nginx thêm `client_max_body_size 26m` cho `/api/`. Mặc định Nginx chỉ nhận 1 MB, file lớn hơn bị Nginx trả 413 trước khi tới backend.

### 11.2 Che thông tin (QT-05, QT-06)

- Mọi API đọc của `ticket` trả CCCD đã che bằng `PersonalDataMasker`, không trả điện thoại, email của khách. DTO không có trường chứa bản đầy đủ, như spec khách hàng tài khoản mục 3.
- Ngoại lệ duy nhất: `edit-data` (D2). Response có header `Cache-Control: no-store` để trình duyệt không lưu bản đầy đủ vào bộ nhớ đệm.
- CCCD chỉ đi trong thân request POST, PUT, không lên URL. Mail và payload Kafka không chứa CCCD.

### 11.3 Lỗi database không ghi CCCD vào log

- Hiện tại `ApiExceptionHandler.handleDataIntegrityViolation` ghi `getMostSpecificCause().getMessage()`. Với Postgres, câu này có dòng `Detail: Key (cccd)=(<12 số>) already exists`: tạo trùng CCCD là số CCCD đầy đủ nằm trong log.
- Sửa 2 lớp:
  1. Driver: `spring.datasource.hikari.data-source-properties.logServerErrorDetail: false`. Driver Postgres không ghép phần `Detail` (giá trị của dòng dữ liệu) vào message của lỗi nữa, nên mọi chỗ ghi log lỗi đều sạch, kể cả `handleUnexpectedException` và lỗi ném ra từ trong Camunda.
  2. Handler: chỉ ghi SQLState và tên constraint (lấy từ `PSQLException.getServerErrorMessage()`), không ghi message. Tên constraint đổi sang câu báo nghiệp vụ: `customer_cccd_key` → 409 "Số CCCD đã có hồ sơ khách hàng"; `uq_ticket_open_per_account` → 409 "Tài khoản đang có phiếu khác chờ duyệt hoặc bị từ chối"; constraint khác giữ câu chung đang có.
- Code của module phiếu ghi log bằng mã phiếu và id; không ghi họ tên, CCCD, điện thoại, email.

## 12. Audit log

| `object_type` | Nhãn đối tượng | `action` và nhãn |
| --- | --- | --- |
| `TICKET` | Phiếu | `CREATE` Tạo phiếu, `RESUBMIT` Gửi lại phiếu, `CANCEL` Hủy phiếu, `APPROVE` Duyệt phiếu, `REJECT` Từ chối phiếu, `COMPLETE` Hoàn tất phiếu |

- `object_id` là mã phiếu. Mô tả theo lời nghiệp vụ: "Phong tỏa · TK 1024118603 · Hoạt động → Phong tỏa"; từ chối và hủy kèm lý do (cắt còn 100 ký tự); hủy thay người tạo đã bị khóa ghi thêm "hủy thay <người tạo>" (D12); tạo phiếu ghi đè hồ sơ cũ ghi thêm "cập nhật hồ sơ chưa từng mở tài khoản" (D6).
- Mỗi API ghi 1 dòng. Việc đổi trạng thái tài khoản nằm trong mô tả của dòng duyệt; chi tiết từng tài khoản ở `account_status_history`.
- `COMPLETE` do hệ thống làm từ luồng Kafka, không có request và người đăng nhập (T3).
- Thêm nhãn mới vào `pages/audit/auditLabels.ts`.

## 13. Thứ tự làm và test

Thứ tự trong mỗi Pull Request: backend và test backend trước, giao diện sau. Mỗi `QT-xx` liên quan có ít nhất 1 test, ghi mã QT ở tên hoặc comment của test.

Backend, test nhanh (`*Test`):

1. Mỗi API của mục 6: chưa đăng nhập → 401; `ADMIN` → 403; sai role → 403 (`CHECKER` gọi POST `/api/tickets`, PUT, cancel, `eligibility`, `customer-check`; `MAKER` gọi approve, reject, `pending-review`) (QT-07).
2. Tạo phiếu sai dữ liệu → 400 có `fieldErrors`: lý do quá 500 ký tự, CCCD 11 số, điện thoại sai, thiếu `confirmed`, gửi cả `customerId` lẫn `newCustomer`.
3. File: không có file, 6 file, file 6 MB, file `.pdf` mà byte đầu là file khác → 400 (QT-10).
4. State pattern: chạy đủ 25 cặp (5 trạng thái × 5 trạng thái); đúng các cặp ở mục 2.1 thì qua, còn lại 409 (QT-02).
5. `TicketService`: người duyệt là người tạo → 403, kể cả người giữ cả `MAKER` và `CHECKER` (QT-01); khác chi nhánh → 403 (QT-04); phiếu tạo ra mang chi nhánh của người tạo dù request có gửi thêm trường chi nhánh (QT-04).
6. JSON của API danh sách, chi tiết, `eligibility` không có CCCD, điện thoại, email đầy đủ (QT-06).
7. `TextNormalizer`: họ tên dạng NFD ra NFC; khoảng trắng thừa bị gộp.
8. Kiểm loại file bằng byte đầu: PDF, PNG, JPG đúng; file đổi đuôi bị từ chối.

Backend, test chậm (`*IT`, Testcontainers Postgres, MinIO, Kafka):

9. Camunda tạo bảng trong schema `camunda`, schema `app` không có bảng `ACT_*`; process `ticket-approval` đã deploy (T1).
10. Tạo phiếu Mở tài khoản cho khách mới, họ tên gửi ở dạng NFD → hồ sơ lưu NFC, tìm "binh" ra khách; tài khoản Chờ mở; phiếu Chờ duyệt, `last_submitted_at` có giá trị; quy trình dừng ở `review`; file nằm trong MinIO với tên UUID; có dòng lịch sử trạng thái. `customer-check` trả đúng 4 trạng thái; số CCCD của hồ sơ chưa từng mở được tài khoản (`REUSABLE`) → phiếu mới ghi đè hồ sơ, giữ `id`, không tạo hồ sơ thứ hai; hồ sơ đang `PENDING` → 409 (D5, D6).
11. QT-03: tạo phiếu thứ 2 cho tài khoản đang có phiếu → 409; 2 request cùng lúc cho cùng tài khoản → 1 thành công, 1 nhận 409 với câu báo nghiệp vụ.
12. Duyệt: sau khi duyệt, phiếu Đã duyệt, tài khoản sang trạng thái mới, có lịch sử trạng thái, có 1 dòng outbox, quy trình dừng ở `mailSent`. Cho `queueApprovedMail` ném lỗi → mọi thứ bị hủy: phiếu Chờ duyệt, tài khoản giữ trạng thái cũ, không có outbox, task `review` vẫn còn.
13. QT-11: 2 thread cùng duyệt một phiếu → 1 thành công, 1 nhận 409 "Phiếu này vừa được người khác xử lý"; chỉ có 1 dòng outbox.
14. Từ chối → Bị từ chối, có lý do, outbox `TICKET_REJECTED`, quy trình ở `revise`. Sửa và gửi lại → Chờ duyệt, `submit_count` 2, `last_submitted_at` mới, quy trình về `review`. Bỏ 1 giấy tờ khi sửa → dòng có `removed_at`, file vẫn trong MinIO, chi tiết phiếu không còn file đó, tải file đó → 404 (D3). Tài khoản đổi trạng thái trong lúc phiếu Bị từ chối → gửi lại nhận 409.
15. Hủy phiếu Mở tài khoản → tài khoản Đã đóng, process instance không còn; hủy phiếu Phong tỏa → tài khoản giữ nguyên. Người khác hủy → 403. Người tạo bị khóa: Giao dịch viên cùng chi nhánh hủy được, lịch sử ghi đúng người hủy; Giao dịch viên khác chi nhánh → 403; người cùng chi nhánh vẫn không sửa được (D12).
16. Hàm 24 giờ: gửi cách đây 23 giờ 59 phút → chưa quá; 24 giờ 1 phút → quá; danh sách Chờ duyệt xếp theo lần gửi gần nhất, phiếu vừa gửi lại nằm dưới.
17. Danh sách Chờ duyệt không có phiếu của chính mình, của chi nhánh khác, phiếu không ở Chờ duyệt (QT-01, QT-04).
18. Tải giấy tờ: id giấy tờ của phiếu khác → 404; có `nosniff` và đúng `Content-Type` (QT-10).
19. Trạng thái tài khoản đã đổi sau khi tạo phiếu (cập nhật thẳng bằng SQL trong test) → duyệt nhận 409, không đổi gì.
20. Outbox tới mail: duyệt → publisher đẩy sang Kafka → consumer gửi 2 mail (JavaMailSender thay bằng mock) → phiếu Hoàn tất, quy trình kết thúc. Gửi cùng sự kiện 2 lần → mail chỉ gửi 1 lần. Kafka chưa sẵn sàng → `published_at` vẫn rỗng, lượt sau gửi được.
21. Lỗi database không lộ CCCD: tạo 2 khách trùng CCCD trên Postgres thật, đọc log bằng `OutputCaptureExtension`: không có chuỗi 12 số đó; API trả 409 "Số CCCD đã có hồ sơ khách hàng".
22. 4 chỗ nối: `GET /api/tickets?customerId=`, `?accountId=` ra đúng phiếu; `status-history` đúng thứ tự; chi tiết tài khoản có Mở bởi, Duyệt mở bởi.

Quy trình (camunda-bpm-assert):

23. Nhánh duyệt: `review` → `applyAccountChange` → `queueApprovedMail` → chờ `mailSent` → correlate → kết thúc.
24. Nhánh từ chối: `review` → `queueRejectedMail` → `revise` → `review`.
25. Phiếu lô: `createBatchAccounts` ném `BATCH_ROWS_INVALID` → về `revise` (bean lô thay bằng mock).

Frontend (Vitest + React Testing Library):

26. Tạo phiếu: `?loai=PHONG_TOA&taiKhoan=...` mở thẳng bước 2 đã điền sẵn; tài khoản không hợp lệ thì báo dưới ô và khóa Tiếp tục; `CccdInput` che khi rời ô (QT-05); `customer-check` trả `EXISTS`, `PENDING` thì khóa Tiếp tục, `REUSABLE` thì hiện Alert mà vẫn đi tiếp được; chưa tick Checkbox thì không gửi được.
27. Chờ duyệt: tự chọn phiếu đầu; duyệt xong tải lại và mở phiếu kế; nút khóa trong lúc gửi; nhận 409 thì hiện cảnh báo và tải lại; phiếu lô chỉ có số dòng và nút mở trang 2.4; `submit_count` 1 không có nhãn, `submit_count` 2 hiện "Gửi lại lần 1" (D1).
28. Chi tiết phiếu: người tạo giữ cả `CHECKER` thấy nút Duyệt khóa kèm Tooltip (QT-01); người tạo thấy Sửa và gửi lại, Hủy phiếu khi Bị từ chối.
29. Đổi số phút sang chữ: 0 → "Vừa gửi", 20 → "20 phút", 1200 → "20 giờ", 4080 → "2 ngày 20 giờ".
30. Tất cả phiếu đọc bộ lọc từ URL; chi tiết tài khoản hiện Lịch sử phiếu, Vòng đời, Mở bởi; chi tiết khách hàng hiện tab Phiếu yêu cầu.

## 14. Nhánh và Pull Request

Chia 5 Pull Request, nhánh sau tạo khi PR trước đã merge:

1. `feature/ticket-tao-phieu`: Camunda (T1) và BPMN đủ các bước, V12, `FileStorage`, `TextNormalizer`, sửa log lỗi database (mục 11.3), API tạo phiếu, `eligibility`, `customer-check`, chi tiết phiếu, tải giấy tờ, `SecurityConfig`, Nginx; màn Tạo phiếu, trang 2.4 chỉ xem, `TicketStatusTag`, `CccdInput`.
2. `feature/ticket-duyet`: State pattern, V13, V14, V15, duyệt, từ chối (ghi outbox, chưa đẩy sang Kafka), màn Chờ duyệt, nút Duyệt, Từ chối ở trang 2.4.
3. `feature/ticket-sua-huy`: sửa và gửi lại, `edit-data`, hủy, màn Tất cả phiếu, 4 chỗ nối (mục 10.6), lịch sử trạng thái của tài khoản mẫu.
4. `feature/ticket-mail`: Kafka, `OutboxPublisher`, consumer và mail trong `notification`, Hoàn tất phiếu, message `mailSent`, audit cho bước hệ thống (T3).
5. `feature/ticket-du-lieu-mau`: phiếu mẫu cho profile `demo`, tạo bằng code Java gọi đúng Service tạo và duyệt phiếu (`docs/modules/setup.md`), chỉ chạy khi chưa có phiếu nào; hỏi người dùng trước khi chạy trên Supabase.

- Giữa PR 2 và PR 4, phiếu được duyệt dừng ở Đã duyệt vì chưa có phần gửi mail; tài khoản vẫn đổi trạng thái đúng.
- Phần cho Claude Code trên máy, không lên Git: thêm dòng cho file này vào bảng "Đọc gì trước khi làm" của `CLAUDE.md`; sửa câu về Flyway theo T1; ghi vào `LEARNING.md` các kỹ thuật dùng lần đầu (Camunda embedded, BPMN, camunda-bpm-assert, State pattern, `SELECT ... FOR UPDATE`, `SKIP LOCKED`, Outbox, Kafka producer và consumer, `@Scheduled`, upload multipart, API S3, kiểm loại file bằng byte đầu, `TransactionSynchronization` cho file).

## 15. Xong khi

- Lan (Giao dịch viên, Tân Phú) tạo phiếu Phong tỏa cho một tài khoản Hoạt động, kèm 1 PDF và 1 ảnh; tạo phiếu thứ 2 cho cùng tài khoản bị chặn ngay ở bước 2.
- Lan mở tài khoản cho khách mới; tìm họ tên không dấu ở màn Khách hàng ra khách đó; tài khoản Chờ mở có ô "Mở bởi" là Lan.
- Khoa (Kiểm soát viên, Tân Phú) thấy phiếu ở màn Chờ duyệt, gửi sớm nhất ở trên; duyệt thì tài khoản đổi trạng thái ngay, màn tự mở phiếu kế; vài giây sau Mailpit có mail cho khách và cho Lan, phiếu sang Hoàn tất, Kafka UI có sự kiện, Vòng đời tài khoản có mục mới.
- Khoa từ chối một phiếu → Lan nhận mail kèm lý do, sửa và gửi lại → phiếu về Chờ duyệt với nhãn "Gửi lại lần 1", thời gian chờ đếm lại.
- Anh (cả 2 role, Quận 1) không thấy phiếu của Tân Phú ở màn Chờ duyệt; phiếu Anh tự tạo có nút Duyệt bị khóa, gọi API thẳng nhận 403.
- 2 cửa sổ của Khoa cùng mở một phiếu: người bấm sau thấy "Phiếu này vừa được người khác xử lý".
- Tắt Mailpit rồi duyệt: phiếu ở "Đã duyệt · đang gửi mail"; bật lại thì mail tới và phiếu sang Hoàn tất.
- Phúc (Admin) không thấy menu Phiếu yêu cầu, gõ thẳng URL ra trang 403, gọi API ra 403; màn Audit log có các dòng của phiếu.
- Tạo trùng CCCD: log backend không có số CCCD đầy đủ.
- `mvnw verify` và `npm test` xanh trên máy và trên GitHub Actions.

## 16. Đã chốt (05/11/2026)

Nghiệp vụ và giao diện, người dùng chốt; đã ghi vào `docs/01-chuc-nang.md` (CN-11, CN-12, CN-13, CN-14, CN-16, CN-25, mục 3, mục 7).

1. D1: nhãn "Gửi lại lần N" có N là số lần gửi lại (`submit_count − 1`): gửi lần đầu rồi gửi lại 1 lần thì hiện "Gửi lại lần 1"; chi tiết phiếu ghi "Gửi lần 2" (tổng số lần gửi). Đọc theo đúng chữ đã chốt ở CN-14 thì không hiểu nhầm; bản thiết kế dùng tổng số lần gửi nên ghi "Gửi lại lần 2".
2. D2: sửa phiếu Mở tài khoản cho khách mới được sửa mọi ô hồ sơ, kể cả CCCD (kiểm trùng lại). Form sửa nhận hồ sơ đầy đủ qua `edit-data`, chỉ người tạo, chỉ khi Bị từ chối, vì QT-06 chỉ cấm gửi bản đầy đủ "khi chỉ để xem". An toàn nhờ D5: không phiếu nào khác trỏ tới hồ sơ đang chờ duyệt.
3. D3: khi sửa phiếu được thêm và bỏ giấy tờ, tối đa 5 file đang dùng. Bỏ mềm: ghi `attachment.removed_at`, `removed_by`, file giữ trên Storage, không hiện ở danh sách giấy tờ, lịch sử xử lý ghi tên file đã bỏ. Giấy tờ là bằng chứng kiểm soát viên đã xem lúc từ chối; xóa hẳn thì mất dấu vết.
4. D4: hủy phiếu bắt nhập lý do (tối đa 500 ký tự), ghi vào lịch sử xử lý, như bản thiết kế 2.4.
5. D5: không cho chọn "Khách hàng đã có" khi hồ sơ khách đang chờ duyệt ở một phiếu Mở tài khoản khác; báo "Hồ sơ khách đang chờ duyệt ở phiếu <mã>", để 2 phiếu không cùng sửa một hồ sơ.
6. D6: hủy phiếu Mở tài khoản cho khách mới thì giữ hồ sơ khách. Hồ sơ chưa từng mở được tài khoản (mọi tài khoản đều Đã đóng, `opened_at` rỗng) thì phiếu Mở tài khoản cho khách mới với cùng số CCCD được ghi đè hồ sơ đó, màn Tạo phiếu báo trước (mục 7.1, 10.2). Lý do: gõ nhầm CCCD rồi hủy thay vì sửa thì hồ sơ sai không giữ số đó mãi, và app không có chức năng sửa hồ sơ khách. Hộp Hủy phiếu nhắc "Sai thông tin khách thì Sửa và gửi lại, đừng hủy".
7. D7: job Ngủ đông bỏ qua tài khoản có phiếu Chờ duyệt, đêm sau khi phiếu xong mới xét. Tài khoản có phiếu Bị từ chối vẫn được chuyển, vì phiếu Bị từ chối có thể nằm đó mãi (hệ thống không tự hủy); gửi lại thì bị kiểm lại trạng thái tài khoản (mục 7.3). Duyệt khi trạng thái tài khoản đã khác lúc tạo vẫn báo 409 (mục 5).
8. D8: Tất cả phiếu hiện số phiếu cạnh từng trạng thái, đếm theo các bộ lọc khác đang bật; khoảng ngày lọc theo ngày tạo phiếu (không đổi khi gửi lại), tính theo giờ Việt Nam.
9. D9: mục menu "Chờ duyệt" có badge số phiếu chờ; chỉ gọi `pending-review/summary` khi người dùng có role `CHECKER`, gọi lại khi đổi trang và sau mỗi lần duyệt.
10. D10: sửa phiếu ở route `/phieu/:id/sua`, dùng lại màn Tạo phiếu, bỏ bước 1.
11. D11: kiểm soát viên khác chi nhánh mở chi tiết phiếu thì không thấy nút Duyệt, Từ chối, có dòng "Phiếu thuộc Chi nhánh <tên>".
12. D12: người tạo bị khóa khi phiếu đang Chờ duyệt hoặc Bị từ chối thì Giao dịch viên cùng chi nhánh với phiếu được hủy (không được sửa) phiếu đó; lịch sử và audit ghi người hủy thật. Không có cách này thì phiếu kẹt mãi, tài khoản bị coi là "có phiếu mở" (QT-03), tài khoản Chờ mở không bao giờ mở được; Admin không được xem phiếu nên không gỡ được.

## 17. Kỹ thuật đã chốt (05/11/2026)

1. T1: bảng Camunda ở schema `camunda`, cùng DataSource với app để chung transaction; Flyway chỉ tạo schema rỗng, `search_path` đặt bằng `connection-init-sql`. Không dùng DataSource riêng cho Camunda vì khi đó API duyệt không còn 1 transaction.
2. T2: Outbox (ghi và đẩy sang Kafka) ở `common`; `notification` chỉ nhận từ Kafka. Comment của V6 ghi `outbox_event` thuộc `notification`: sửa ghi chú trên ERD cho khớp. Kafka giao "ít nhất một lần": chấp nhận mail có thể gửi lặp khi backend tắt giữa chừng, không thêm bảng chống gửi lặp (mục 9).
3. T3: audit cho bước hệ thống. `AuditNote` đang là bean `@RequestScope` và `AuditAspect` đọc request để lấy IP, người làm, nên luồng Kafka không ghi audit được. `AuditAspect` tự tạo `AuditNote` cho mỗi lần gọi (giữ trong `ThreadLocal`, xóa trong `finally`); không có request thì người làm, IP, mã request để rỗng và màn 3.5 hiện "Hệ thống".
4. T4: đổi tên `ticket.approved_by` thành `reviewed_by`, thêm `reviewed_at`, vì cột này giữ cả người từ chối.
5. T5: bảng `account_status_history` cho Vòng đời tài khoản, thay vì ghép từ `ticket` và `audit_log`.
6. T6: thêm AWS SDK v2 `s3` để gọi API S3 của Supabase Storage (ghi thêm vào mục "Công nghệ" của `CLAUDE.md`); nhận dạng file bằng byte đầu, không thêm thư viện.
7. T7: tắt job executor của Camunda; mọi service task chạy đồng bộ, phần chạy tách sau giao cho Outbox và Kafka.
8. T8: message catch event `mailSent`, để process instance kết thúc đúng lúc phiếu Hoàn tất thay vì kết thúc ngay sau khi ghi outbox.

## 18. Chỗ bản thiết kế khác file này

Làm theo file này:

1. Hộp xác nhận Duyệt: bản thiết kế chỉ tả thay đổi trạng thái; file này thêm câu nhắc đối chiếu giấy tờ (CN-16).
2. Phiếu lô ở cột phải màn Chờ duyệt: bản thiết kế có nút Duyệt, Từ chối; file này chỉ có số dòng và nút mở trang 2.4 (CN-14).
3. Lọc trạng thái ở Tất cả phiếu: CN-14 ghi "tab", bản thiết kế dùng Segmented có số đếm; dùng Segmented, cùng công năng; số đếm theo D8.
4. Hộp Hủy phiếu ở trang 2.4 có ô lý do bắt buộc, CN-13 chưa có: theo D4.
5. Ghi chú trong bản thiết kế 2.4 dùng đường dẫn `/api/requests/{id}/cancel`; API thật là `/api/tickets/{id}/cancel`.
6. Nhãn "Gửi lại lần N": bản thiết kế dùng N là tổng số lần gửi ("Gửi lại lần 2" cho phiếu mới gửi lại 1 lần); file này dùng số lần gửi lại (D1).
7. Badge số phiếu ở menu "Chờ duyệt": bản thiết kế có, file chức năng chưa ghi: theo D9.
8. Trang 2.4 (bản thiết kế chưa cập nhật từ 20/10) luôn ghi Breadcrumb "Chờ duyệt"; file này ghi theo nơi người dùng đi tới.
9. Cho spec Tổng quan, không làm ở đây: bản thiết kế 31/10 ghi "thời gian chờ tính ở frontend từ lúc gửi", file chức năng chốt backend tính sẵn từ lần gửi gần nhất (mục 3.2); thẻ "Phiếu bị trả lại" có thêm nút "Hủy phiếu" mà CN-06 chỉ ghi nút "Sửa và gửi lại".