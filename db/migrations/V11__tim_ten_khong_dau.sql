-- V11: tìm họ tên khách gần đúng, gõ không dấu cũng được (CN-07, spec khách hàng tài khoản mục 6).
-- Hàm app.unaccent_vi đổi chữ có dấu tiếng Việt sang không dấu rồi đổi sang chữ thường.
-- IMMUTABLE: cùng đầu vào luôn cho cùng kết quả, nên dùng được cho cột sinh sẵn bên dưới.
-- translate có cả chữ hoa để không phụ thuộc collation của database (lower() có thể bỏ qua Đ, Ê...).
-- 2 chuỗi dài phải khớp từng vị trí: ký tự thứ n của chuỗi đầu đổi thành ký tự thứ n của chuỗi sau.
-- Không dùng extension unaccent: Flyway chỉ quản lý schema app.
CREATE FUNCTION app.unaccent_vi(input text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT
    RETURN lower(translate(input,
        'àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđÀÁẠẢÃÂẦẤẬẨẪĂẰẮẶẲẴÈÉẸẺẼÊỀẾỆỂỄÌÍỊỈĨÒÓỌỎÕÔỒỐỘỔỖƠỜỚỢỞỠÙÚỤỦŨƯỪỨỰỬỮỲÝỴỶỸĐ',
        'aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyydAAAAAAAAAAAAAAAAAEEEEEEEEEEEIIIIIOOOOOOOOOOOOOOOOOUUUUUUUUUUUYYYYYD'));

-- Cột sinh sẵn: Postgres tự tính lại khi thêm hoặc sửa full_name; không ghi trực tiếp vào cột này được.
-- Dữ liệu mẫu và khách tạo sau đều tự có giá trị. Họ tên phải lưu ở dạng Unicode NFC thì hàm mới nhận ra chữ có dấu.
ALTER TABLE app.customer
    ADD COLUMN full_name_search text GENERATED ALWAYS AS (app.unaccent_vi(full_name)) STORED;