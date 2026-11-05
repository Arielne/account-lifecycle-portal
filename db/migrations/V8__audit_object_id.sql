-- V8: nới cột audit_log.object_id cho vừa email của nhân viên.
-- Đối tượng EMPLOYEE lấy email làm mã; email được tới 150 ký tự (employee.email, Bean Validation) mà cột chỉ có 50,
-- nên thao tác trên nhân viên có email dài hơn 50 ký tự bị lỗi lúc ghi audit và bị hủy cả thao tác.

ALTER TABLE app.audit_log ALTER COLUMN object_id TYPE varchar(150);