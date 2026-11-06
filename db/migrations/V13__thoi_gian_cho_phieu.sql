-- V13: một định nghĩa duy nhất cho thời gian chờ duyệt và "quá 24 giờ" (spec phiếu mục 3.2, CN-14).
-- Đây là chỗ duy nhất có con số 24 giờ: màn Chờ duyệt và Tổng quan (CN-06) đều gọi 2 hàm này, Java và React không tự so.
-- Tính bằng đồng hồ của database (now()) nên mọi máy dùng chung một đồng hồ; trừ 2 giá trị timestamptz nên không
-- phụ thuộc múi giờ. STABLE chứ không IMMUTABLE vì kết quả đổi theo now(); chỉ gọi trong câu SELECT.

-- Số phút đã chờ kể từ lần gửi gần nhất, làm tròn xuống.
CREATE FUNCTION app.ticket_waiting_minutes(last_submitted_at timestamptz) RETURNS integer
    LANGUAGE sql STABLE STRICT
    RETURN floor(extract(epoch FROM now() - last_submitted_at) / 60)::integer;

-- "Quá" là hơn hẳn 24 giờ: chờ đúng 24 giờ 0 phút chưa tính là quá.
CREATE FUNCTION app.ticket_is_overdue(last_submitted_at timestamptz) RETURNS boolean
    LANGUAGE sql STABLE STRICT
    RETURN now() - last_submitted_at > interval '24 hours';