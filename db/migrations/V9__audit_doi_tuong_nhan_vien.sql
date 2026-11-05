-- V9: audit_log nối thẳng tới nhân viên là đối tượng (object_type EMPLOYEE), không nối qua email.
-- Trước đây màn audit log tìm họ tên đối tượng theo email trong object_id, nên đổi email (Admin sửa,
-- hoặc đổi trên KeyCloak rồi đăng nhập lại) thì các dòng cũ mất họ tên. object_id vẫn giữ email lúc thao tác.

-- Rỗng khi đối tượng không phải nhân viên.
ALTER TABLE app.audit_log ADD COLUMN object_employee_id bigint REFERENCES app.employee (id);

-- Dòng đã có: nối theo email hiện tại. Nhân viên đã đổi email trước khi chạy script này thì dòng cũ của họ vẫn rỗng.
UPDATE app.audit_log a
SET object_employee_id = e.id
FROM app.employee e
WHERE a.object_type = 'EMPLOYEE' AND e.email = a.object_id;