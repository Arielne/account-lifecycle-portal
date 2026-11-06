-- V12: cột mới của phiếu và giấy tờ cho module phiếu (spec phiếu, mục 3.1).

-- Lần gửi gần nhất: thời gian chờ, "quá 24 giờ" và thứ tự "gửi sớm nhất" đều tính từ cột này (CN-14).
-- Thêm cột cho phép rỗng, chép created_at sang cho dòng đã có, rồi mới đặt NOT NULL.
-- Mặc định now(): trong cùng một câu INSERT, now() trả đúng giá trị của created_at, nên phiếu mới có
-- last_submitted_at bằng created_at mà không phải ghi tay.
ALTER TABLE app.ticket ADD COLUMN last_submitted_at timestamptz;
UPDATE app.ticket SET last_submitted_at = created_at;
ALTER TABLE app.ticket ALTER COLUMN last_submitted_at SET DEFAULT now();
ALTER TABLE app.ticket ALTER COLUMN last_submitted_at SET NOT NULL;

-- Người và lúc duyệt hoặc từ chối lần gần nhất (T4): cột cũ approved_by giữ cả người từ chối nên đổi tên cho đúng nghĩa.
ALTER TABLE app.ticket RENAME COLUMN approved_by TO reviewed_by;
ALTER TABLE app.ticket ADD COLUMN reviewed_at timestamptz;

-- Phiếu Mở tài khoản này tạo hồ sơ khách mới (hoặc ghi đè hồ sơ chưa từng mở được tài khoản, D6).
ALTER TABLE app.ticket ADD COLUMN new_customer boolean NOT NULL DEFAULT false;

-- Giấy tờ bị bỏ khi sửa phiếu (D3): bỏ mềm, dòng và file vẫn còn, chỉ không tính là giấy tờ của phiếu nữa.
ALTER TABLE app.attachment ADD COLUMN removed_at timestamptz;
ALTER TABLE app.attachment ADD COLUMN removed_by bigint REFERENCES app.employee (id);

-- Mã hành động trong lịch sử xử lý phiếu (enum TicketAction); database chặn mã gõ sai.
ALTER TABLE app.ticket_history ADD CONSTRAINT ticket_history_action_check
    CHECK (action IN ('SUBMITTED', 'RESUBMITTED', 'REJECTED', 'APPROVED', 'CANCELLED', 'COMPLETED'));