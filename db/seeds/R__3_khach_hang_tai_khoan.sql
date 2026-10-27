-- Dữ liệu mẫu giả: 30 khách hàng và 60 tài khoản (mỗi khách 2 tài khoản).
-- Toàn bộ là dữ liệu giả (QT-12): tên giả; CCCD bắt đầu bằng 099 là mã tỉnh không tồn tại; email đuôi nhdemo.local.
-- Giới tính lưu bằng chữ tiếng Việt: Nam hoặc Nữ.
-- Số tài khoản dùng dải 1020000001-1020000060, dưới dải của app.account_number_seq (bắt đầu 1030000001).
-- Tài khoản Chờ mở, Phong tỏa và các phiếu không chèn bằng SQL; chúng được tạo bằng code Java ở tuần 7.

INSERT INTO app.customer (cccd, full_name, date_of_birth, gender, id_issue_date, id_issue_place, phone, email, address,
                          branch_id)
SELECT v.cccd, v.full_name, v.date_of_birth::date, v.gender, v.id_issue_date::date, 'Cục CS QLHC về TTXH',
       v.phone, v.email, v.address, b.id
FROM (VALUES
    ('099085000001', 'Phạm Văn An', '1985-02-08', 'Nam', '2022-06-04', '0900000001', 'an.pham@nhdemo.local', 'Số 3 Đường số 2, P. Tân Phú, TP. Hồ Chí Minh', 'CN-TP'),
    ('099190000002', 'Đỗ Thị Bình', '1990-03-15', 'Nữ', '2023-11-07', '0900000002', 'binh.do@nhdemo.local', 'Số 6 Đường số 3, P. Tân Phú, TP. Hồ Chí Minh', 'CN-TP'),
    ('099178000003', 'Hoàng Minh Châu', '1978-04-22', 'Nữ', '2024-04-10', '0900000003', 'chau.hoang@nhdemo.local', 'Số 9 Đường số 4, P. Tân Phú, TP. Hồ Chí Minh', 'CN-TP'),
    ('099082000004', 'Bùi Quốc Dũng', '1982-05-02', 'Nam', '2021-09-13', '0900000004', 'dung.bui@nhdemo.local', 'Số 12 Đường số 5, P. Tân Phú, TP. Hồ Chí Minh', 'CN-TP'),
    ('099195000005', 'Ngô Thị Hà', '1995-06-09', 'Nữ', '2022-02-16', '0900000005', 'ha.ngo@nhdemo.local', 'Số 15 Đường số 6, P. Tân Phú, TP. Hồ Chí Minh', 'CN-TP'),
    ('099070000006', 'Đặng Văn Hải', '1970-07-16', 'Nam', '2023-07-19', '0900000006', 'hai.dang@nhdemo.local', 'Số 18 Đường số 7, P. Tân Phú, TP. Hồ Chí Minh', 'CN-TP'),
    ('099188000007', 'Trịnh Thu Hằng', '1988-08-23', 'Nữ', '2024-12-22', '0900000007', 'hang.trinh@nhdemo.local', 'Số 21 Đường số 8, P. Tân Phú, TP. Hồ Chí Minh', 'CN-TP'),
    ('099201000008', 'Lý Gia Huy', '2001-09-03', 'Nam', '2021-05-25', '0900000008', 'huy.ly@nhdemo.local', 'Số 24 Đường số 9, P. Tân Phú, TP. Hồ Chí Minh', 'CN-TP'),
    ('099193000009', 'Dương Ngọc Lan', '1993-10-10', 'Nữ', '2022-10-01', '0900000009', 'lan.duong@nhdemo.local', 'Số 27 Đường số 1, P. Tân Phú, TP. Hồ Chí Minh', 'CN-TP'),
    ('099075000010', 'Phan Văn Long', '1975-11-17', 'Nam', '2023-03-04', '0900000010', 'long.phan@nhdemo.local', 'Số 30 Đường số 2, P. Tân Phú, TP. Hồ Chí Minh', 'CN-TP'),
    ('099168000011', 'Mai Thị Mai', '1968-12-24', 'Nữ', '2024-08-07', '0900000011', 'mai.mai@nhdemo.local', 'Số 33 Đường số 3, P. Sài Gòn, TP. Hồ Chí Minh', 'CN-Q1'),
    ('099099000012', 'Cao Đức Minh', '1999-01-04', 'Nam', '2021-01-10', '0900000012', 'minh.cao@nhdemo.local', 'Số 36 Đường số 4, P. Sài Gòn, TP. Hồ Chí Minh', 'CN-Q1'),
    ('099186000013', 'Tạ Thị Ngọc', '1986-02-11', 'Nữ', '2022-06-13', '0900000013', 'ngoc.ta@nhdemo.local', 'Số 39 Đường số 5, P. Sài Gòn, TP. Hồ Chí Minh', 'CN-Q1'),
    ('099080000014', 'Lương Văn Phong', '1980-03-18', 'Nam', '2023-11-16', '0900000014', 'phong.luong@nhdemo.local', 'Số 42 Đường số 6, P. Sài Gòn, TP. Hồ Chí Minh', 'CN-Q1'),
    ('099197000015', 'Hồ Thị Phương', '1997-04-25', 'Nữ', '2024-04-19', '0900000015', 'phuong.ho@nhdemo.local', 'Số 45 Đường số 7, P. Sài Gòn, TP. Hồ Chí Minh', 'CN-Q1'),
    ('099203000016', 'Châu Minh Quân', '2003-05-05', 'Nam', '2021-09-22', '0900000016', 'quan.chau@nhdemo.local', 'Số 48 Đường số 8, P. Sài Gòn, TP. Hồ Chí Minh', 'CN-Q1'),
    ('099191000017', 'Kiều Thị Quỳnh', '1991-06-12', 'Nữ', '2022-02-25', '0900000017', 'quynh.kieu@nhdemo.local', 'Số 51 Đường số 9, P. Sài Gòn, TP. Hồ Chí Minh', 'CN-Q1'),
    ('099072000018', 'Lâm Văn Sơn', '1972-07-19', 'Nam', '2023-07-01', '0900000018', 'son.lam@nhdemo.local', 'Số 54 Đường số 1, P. Sài Gòn, TP. Hồ Chí Minh', 'CN-Q1'),
    ('099184000019', 'Văn Thị Tâm', '1984-08-26', 'Nữ', '2024-12-04', '0900000019', 'tam.van@nhdemo.local', 'Số 57 Đường số 2, P. Bàn Cờ, TP. Hồ Chí Minh', 'CN-Q3'),
    ('099079000020', 'Tô Quang Thắng', '1979-09-06', 'Nam', '2021-05-07', '0900000020', 'thang.to@nhdemo.local', 'Số 60 Đường số 3, P. Bàn Cờ, TP. Hồ Chí Minh', 'CN-Q3'),
    ('099302000021', 'Đinh Thị Thảo', '2002-10-13', 'Nữ', '2022-10-10', '0900000021', 'thao.dinh@nhdemo.local', 'Số 63 Đường số 4, P. Bàn Cờ, TP. Hồ Chí Minh', 'CN-Q3'),
    ('099065000022', 'Quách Văn Thịnh', '1965-11-20', 'Nam', '2023-03-13', '0900000022', 'thinh.quach@nhdemo.local', 'Số 66 Đường số 5, P. Bàn Cờ, TP. Hồ Chí Minh', 'CN-Q3'),
    ('099194000023', 'Hà Thị Thu', '1994-12-27', 'Nữ', '2024-08-16', '0900000023', 'thu.ha@nhdemo.local', 'Số 69 Đường số 6, P. Thạnh Mỹ Tây, TP. Hồ Chí Minh', 'CN-BT'),
    ('099087000024', 'La Minh Tiến', '1987-01-07', 'Nam', '2021-01-19', '0900000024', 'tien.la@nhdemo.local', 'Số 72 Đường số 7, P. Thạnh Mỹ Tây, TP. Hồ Chí Minh', 'CN-BT'),
    ('099300000025', 'Từ Thị Trang', '2000-02-14', 'Nữ', '2022-06-22', '0900000025', 'trang.tu@nhdemo.local', 'Số 75 Đường số 8, P. Thạnh Mỹ Tây, TP. Hồ Chí Minh', 'CN-BT'),
    ('099083000026', 'Mạc Văn Trung', '1983-03-21', 'Nam', '2023-11-25', '0900000026', 'trung.mac@nhdemo.local', 'Số 78 Đường số 9, P. Thạnh Mỹ Tây, TP. Hồ Chí Minh', 'CN-BT'),
    ('099196000027', 'Giang Thị Uyên', '1996-04-01', 'Nữ', '2024-04-01', '0900000027', 'uyen.giang@nhdemo.local', 'Số 81 Đường số 1, P. Thủ Đức, TP. Hồ Chí Minh', 'CN-TD'),
    ('099077000028', 'Thái Quốc Việt', '1977-05-08', 'Nam', '2021-09-04', '0900000028', 'viet.thai@nhdemo.local', 'Số 84 Đường số 2, P. Thủ Đức, TP. Hồ Chí Minh', 'CN-TD'),
    ('099189000029', 'Ông Thị Xuân', '1989-06-15', 'Nữ', '2022-02-07', '0900000029', 'xuan.ong@nhdemo.local', 'Số 87 Đường số 3, P. Thủ Đức, TP. Hồ Chí Minh', 'CN-TD'),
    ('099204000030', 'Doãn Văn Yên', '2004-07-22', 'Nam', '2023-07-10', '0900000030', 'yen.doan@nhdemo.local', 'Số 90 Đường số 4, P. Thủ Đức, TP. Hồ Chí Minh', 'CN-TD')
) AS v (cccd, full_name, date_of_birth, gender, id_issue_date, phone, email, address, branch_code)
JOIN app.branch b ON b.code = v.branch_code
ON CONFLICT DO NOTHING;

-- Ngày ghi theo số tháng tính lùi từ ngày chạy seed, để dữ liệu luôn "mới" so với lúc demo:
-- tài khoản Ngủ đông có ngày giao dịch gần nhất quá số tháng của loại tài khoản;
-- 6 tài khoản Hoạt động (1020000002, 1020000008, 1020000018, 1020000028, 1020000040, 1020000050) cũng đã quá hạn, để demo job chuyển Ngủ đông.
INSERT INTO app.account (account_number, customer_id, account_type_id, branch_id, status,
                         opened_at, closed_at, last_transaction_at)
SELECT v.account_number, c.id, t.id, c.branch_id, v.status,
       (CURRENT_DATE - make_interval(months => v.opened_months_ago))::date,
       (CURRENT_DATE - make_interval(months => v.closed_months_ago))::date,
       (CURRENT_DATE - make_interval(months => v.last_transaction_months_ago))::date
FROM (VALUES
    ('1020000001', '099085000001', 'TTCN', 'ACTIVE' ,  8, NULL,  1),
    ('1020000002', '099085000001', 'TKL' , 'ACTIVE' , 21, NULL, 13),
    ('1020000003', '099190000002', 'TTCN', 'ACTIVE' ,  9, NULL,  0),
    ('1020000004', '099190000002', 'TKKH', 'DORMANT', 40, NULL, 30),
    ('1020000005', '099178000003', 'TTCN', 'ACTIVE' , 13, NULL,  2),
    ('1020000006', '099178000003', 'TKL' , 'CLOSED' , 21,    9,  9),
    ('1020000007', '099082000004', 'TTCN', 'ACTIVE' , 14, NULL,  1),
    ('1020000008', '099082000004', 'TKKH', 'ACTIVE' , 39, NULL, 25),
    ('1020000009', '099195000005', 'TTCN', 'ACTIVE' , 15, NULL,  0),
    ('1020000010', '099195000005', 'TKL' , 'DORMANT', 30, NULL, 14),
    ('1020000011', '099070000006', 'TTCN', 'ACTIVE' , 19, NULL,  2),
    ('1020000012', '099070000006', 'TKKH', 'ACTIVE' , 18, NULL,  0),
    ('1020000013', '099188000007', 'TTCN', 'ACTIVE' , 20, NULL,  1),
    ('1020000014', '099188000007', 'TKL' , 'CLOSED' , 29,    9,  9),
    ('1020000015', '099201000008', 'TTCN', 'ACTIVE' , 21, NULL,  0),
    ('1020000016', '099201000008', 'TKSV', 'DORMANT', 31, NULL,  9),
    ('1020000017', '099193000009', 'TTCN', 'ACTIVE' , 25, NULL,  2),
    ('1020000018', '099193000009', 'TKL' , 'ACTIVE' , 37, NULL, 13),
    ('1020000019', '099075000010', 'TTCN', 'ACTIVE' , 26, NULL,  1),
    ('1020000020', '099075000010', 'TKKH', 'DORMANT', 52, NULL, 26),
    ('1020000021', '099168000011', 'TTCN', 'ACTIVE' , 27, NULL,  0),
    ('1020000022', '099168000011', 'TKL' , 'CLOSED' , 37,    9,  9),
    ('1020000023', '099099000012', 'TTCN', 'ACTIVE' , 31, NULL,  2),
    ('1020000024', '099099000012', 'TKKH', 'ACTIVE' ,  6, NULL,  0),
    ('1020000025', '099186000013', 'TTCN', 'ACTIVE' ,  8, NULL,  1),
    ('1020000026', '099186000013', 'TKL' , 'DORMANT', 23, NULL, 15),
    ('1020000027', '099080000014', 'TTCN', 'ACTIVE' ,  9, NULL,  0),
    ('1020000028', '099080000014', 'TKKH', 'ACTIVE' , 35, NULL, 25),
    ('1020000029', '099197000015', 'TTCN', 'ACTIVE' , 13, NULL,  2),
    ('1020000030', '099197000015', 'TKL' , 'CLOSED' , 21,    9,  9),
    ('1020000031', '099203000016', 'TTCN', 'ACTIVE' , 14, NULL,  1),
    ('1020000032', '099203000016', 'TKSV', 'DORMANT', 24, NULL, 10),
    ('1020000033', '099191000017', 'TTCN', 'ACTIVE' , 15, NULL,  0),
    ('1020000034', '099191000017', 'TKL' , 'ACTIVE' , 17, NULL,  1),
    ('1020000035', '099072000018', 'TTCN', 'ACTIVE' , 19, NULL,  2),
    ('1020000036', '099072000018', 'TKKH', 'DORMANT', 45, NULL, 27),
    ('1020000037', '099184000019', 'TTCN', 'ACTIVE' , 20, NULL,  1),
    ('1020000038', '099184000019', 'TKL' , 'CLOSED' , 29,    9,  9),
    ('1020000039', '099079000020', 'TTCN', 'ACTIVE' , 21, NULL,  0),
    ('1020000040', '099079000020', 'TKKH', 'ACTIVE' , 47, NULL, 25),
    ('1020000041', '099302000021', 'TTCN', 'ACTIVE' , 25, NULL,  2),
    ('1020000042', '099302000021', 'TKSV', 'DORMANT', 34, NULL, 10),
    ('1020000043', '099065000022', 'TTCN', 'ACTIVE' , 26, NULL,  1),
    ('1020000044', '099065000022', 'TKKH', 'ACTIVE' , 28, NULL,  2),
    ('1020000045', '099194000023', 'TTCN', 'ACTIVE' , 27, NULL,  0),
    ('1020000046', '099194000023', 'TKL' , 'CLOSED' , 37,    9,  9),
    ('1020000047', '099087000024', 'TTCN', 'ACTIVE' , 31, NULL,  2),
    ('1020000048', '099087000024', 'TKKH', 'DORMANT', 35, NULL, 29),
    ('1020000049', '099300000025', 'TTCN', 'ACTIVE' ,  8, NULL,  1),
    ('1020000050', '099300000025', 'TKSV', 'ACTIVE' , 15, NULL,  7),
    ('1020000051', '099083000026', 'TTCN', 'ACTIVE' ,  9, NULL,  0),
    ('1020000052', '099083000026', 'TKKH', 'CLOSED' , 17,    7,  7),
    ('1020000053', '099196000027', 'TTCN', 'ACTIVE' , 13, NULL,  2),
    ('1020000054', '099196000027', 'TKL' , 'DORMANT', 30, NULL, 18),
    ('1020000055', '099077000028', 'TTCN', 'ACTIVE' , 14, NULL,  1),
    ('1020000056', '099077000028', 'TKKH', 'DORMANT', 41, NULL, 27),
    ('1020000057', '099189000029', 'TTCN', 'ACTIVE' , 15, NULL,  0),
    ('1020000058', '099189000029', 'TKL' , 'CLOSED' , 21,    5,  5),
    ('1020000059', '099204000030', 'TTCN', 'ACTIVE' , 19, NULL,  2),
    ('1020000060', '099204000030', 'TKSV', 'DORMANT', 26, NULL,  8)
) AS v (account_number, cccd, account_type_code, status, opened_months_ago, closed_months_ago,
        last_transaction_months_ago)
JOIN app.customer c ON c.cccd = v.cccd
JOIN app.account_type t ON t.code = v.account_type_code
ON CONFLICT DO NOTHING;