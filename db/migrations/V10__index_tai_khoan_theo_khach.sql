-- V10: tìm tài khoản theo khách hàng (tab Tài khoản ở chi tiết khách hàng, cột Tài khoản ở danh sách khách).
-- Postgres không tự tạo index cho khóa ngoại, nên thiếu index này thì mỗi lần tìm phải quét cả bảng account.
CREATE INDEX idx_account_customer ON app.account (customer_id);