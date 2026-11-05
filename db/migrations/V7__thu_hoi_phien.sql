-- V7: thu hồi phiên đăng nhập của nhân viên (CN-22).
-- Role nằm trong session từ lúc đăng nhập, nên Admin bỏ role trên KeyCloak thì phiên đang mở vẫn còn role đó.
-- Phiên tạo trước thời điểm này bị bộ lọc ở module auth đẩy ra ở request kế tiếp; rỗng là chưa thu hồi lần nào.

ALTER TABLE app.employee ADD COLUMN sessions_invalid_before timestamptz;