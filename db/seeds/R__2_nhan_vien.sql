-- Dữ liệu mẫu giả: 4 nhân viên demo (docs/02-auth.md, mục tài khoản demo).
-- keycloak_id cố định: tuần 3 dùng lại đúng các id này trong file realm của KeyCloak.
-- Role không lưu ở đây, chỉ lưu ở KeyCloak: Lan là Giao dịch viên, Khoa là Kiểm soát viên,
-- Anh giữ cả hai role, Phúc là Admin nên không gắn chi nhánh.

INSERT INTO app.employee (keycloak_id, email, full_name, branch_id, status)
SELECT v.keycloak_id, v.email, v.full_name, b.id, 'ACTIVE'
FROM (VALUES
    ('5b30439a-4e15-42ac-bf7d-c4e8dba67784'::uuid, 'lan.nt@nhdemo.local',  'Nguyễn Thị Lan', 'CN-TP'),
    ('f862d39b-b598-4d60-9a20-ae0c70ac22bc'::uuid, 'khoa.tm@nhdemo.local', 'Trần Minh Khoa', 'CN-TP'),
    ('6356f260-3832-45bc-bb17-9ce71e0a4407'::uuid, 'anh.vd@nhdemo.local',  'Võ Đức Anh',     'CN-Q1'),
    ('927c941a-3244-4299-8a41-7c78b44d08e6'::uuid, 'phuc.lh@nhdemo.local', 'Lê Hoàng Phúc',  NULL)
) AS v (keycloak_id, email, full_name, branch_code)
-- LEFT JOIN để Phúc (không có mã chi nhánh) vẫn được thêm, với branch_id rỗng.
LEFT JOIN app.branch b ON b.code = v.branch_code
ON CONFLICT DO NOTHING;