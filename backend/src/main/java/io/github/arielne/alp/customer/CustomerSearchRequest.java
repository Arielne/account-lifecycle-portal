package io.github.arielne.alp.customer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Thân request của POST /api/customers/search (CN-07). Gửi bằng POST để số CCCD không nằm trên URL,
 * tức không vào log của Nginx và lịch sử trình duyệt (spec khách hàng tài khoản, C1).
 * keyword rỗng thì liệt kê mọi khách; tìm theo CCCD thì CustomerService kiểm đủ 12 chữ số.
 */
public record CustomerSearchRequest(
        @NotNull(message = "Chọn tìm theo họ tên hoặc số CCCD") CustomerSearchBy searchBy,
        @Size(max = 100, message = "Từ khóa tối đa 100 ký tự") String keyword,
        Integer page) {
}