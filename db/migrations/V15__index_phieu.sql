-- V15: index cho module phiếu (spec phiếu mục 3.4).

-- Màn Chờ duyệt: phiếu Chờ duyệt của một chi nhánh, gửi sớm nhất ở trên. Index có điều kiện chỉ chứa phiếu đang chờ.
CREATE INDEX idx_ticket_pending_branch ON app.ticket (branch_id, last_submitted_at)
    WHERE status = 'PENDING_APPROVAL';

-- Lịch sử phiếu của một tài khoản: index QT-03 có sẵn chỉ chứa phiếu đang mở.
CREATE INDEX idx_ticket_account ON app.ticket (account_id);

-- Postgres không tự tạo index cho khóa ngoại: lịch sử xử lý và giấy tờ luôn đọc theo phiếu.
CREATE INDEX idx_ticket_history_ticket ON app.ticket_history (ticket_id);
CREATE INDEX idx_attachment_ticket ON app.attachment (ticket_id);

-- Phần đẩy sự kiện sang Kafka chỉ đọc các dòng chưa gửi, theo thứ tự id.
CREATE INDEX idx_outbox_event_unpublished ON app.outbox_event (id) WHERE published_at IS NULL;