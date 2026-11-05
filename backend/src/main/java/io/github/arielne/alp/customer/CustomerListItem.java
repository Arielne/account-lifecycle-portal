package io.github.arielne.alp.customer;

import java.time.LocalDate;
import java.util.List;

/**
 * Một dòng của màn Khách hàng (CN-07). cccd, phone đã che (QT-06): record này không có trường nào chứa bản đầy đủ.
 * createdDate là ngày tạo hồ sơ theo giờ Việt Nam; accountStatuses là trạng thái từng tài khoản, mỗi cái một Tag.
 */
public record CustomerListItem(
        Long id,
        String fullName,
        String cccd,
        LocalDate dateOfBirth,
        String phone,
        String branchName,
        LocalDate createdDate,
        List<String> accountStatuses) {
}