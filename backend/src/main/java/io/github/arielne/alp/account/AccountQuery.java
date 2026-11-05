package io.github.arielne.alp.account;

import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * Tham số lọc của GET /api/accounts (CN-09), ví dụ ?statuses=ACTIVE,DORMANT&branchId=2&page=2.
 * Tham số nào không gửi thì là null, tức không lọc theo điều kiện đó.
 * statuses gửi nhiều mã cách nhau bằng dấu phẩy; Spring tự tách thành danh sách enum, mã lạ thì 400.
 */
public record AccountQuery(
        @Pattern(regexp = "\\d{1,10}", message = "Số tài khoản chỉ gồm 1–10 chữ số") String accountNumber,
        List<AccountStatus> statuses,
        Long accountTypeId,
        Long branchId,
        Long customerId,
        Integer page) {
}