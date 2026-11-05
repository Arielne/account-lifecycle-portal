package io.github.arielne.alp.customer;

import java.time.LocalDate;

/**
 * Chi tiết một khách hàng (CN-08). cccd, phone, email đã che (QT-06). Không có địa chỉ: bản thiết kế không hiện
 * (spec khách hàng tài khoản, C5). createdDate là ngày tạo hồ sơ theo giờ Việt Nam.
 */
public record CustomerDetail(
        Long id,
        String fullName,
        LocalDate dateOfBirth,
        String gender,
        String cccd,
        LocalDate idIssueDate,
        String idIssuePlace,
        String phone,
        String email,
        String branchName,
        LocalDate createdDate) {
}