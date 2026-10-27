-- Dữ liệu mẫu giả: chi nhánh (màn 3.3) và loại tài khoản (màn 3.2), lấy theo bản thiết kế.
-- Chỉ chạy ở profile demo. File R__ (repeatable): Flyway chạy lại khi nội dung file đổi;
-- ON CONFLICT DO NOTHING bỏ qua dòng đã có (trùng mã), nên chạy lại nhiều lần không bị trùng.

INSERT INTO app.branch (code, name, address, phone) VALUES
    ('HS',    'Hội sở',               '120 Hàm Nghi, P. Sài Gòn, TP. Hồ Chí Minh',                 '02839112200'),
    ('CN-Q1', 'Chi nhánh Quận 1',     '45 Lê Thánh Tôn, P. Sài Gòn, TP. Hồ Chí Minh',              '02838234510'),
    ('CN-Q3', 'Chi nhánh Quận 3',     '210 Võ Văn Tần, P. Bàn Cờ, TP. Hồ Chí Minh',                '02839307712'),
    ('CN-TP', 'Chi nhánh Tân Phú',    '318 Lũy Bán Bích, P. Tân Phú, TP. Hồ Chí Minh',             '02838601188'),
    ('CN-BT', 'Chi nhánh Bình Thạnh', '76 Xô Viết Nghệ Tĩnh, P. Thạnh Mỹ Tây, TP. Hồ Chí Minh',    '02835126630'),
    ('CN-TD', 'Chi nhánh Thủ Đức',    '15 Võ Văn Ngân, P. Thủ Đức, TP. Hồ Chí Minh',               '02838964402')
ON CONFLICT DO NOTHING;

INSERT INTO app.account_type (code, name, description, dormant_after_months) VALUES
    ('TTCN', 'Thanh toán cá nhân',     'Giao dịch hằng ngày cho khách cá nhân', 12),
    ('TKL',  'Tài khoản lương',        'Nhận lương qua doanh nghiệp đối tác',   12),
    ('TKKH', 'Tiết kiệm không kỳ hạn', 'Gửi, rút linh hoạt',                    24),
    ('TKSV', 'Tài khoản sinh viên',    'Miễn phí quản lý cho sinh viên',        6)
ON CONFLICT DO NOTHING;