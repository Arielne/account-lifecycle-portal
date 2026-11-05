package io.github.arielne.alp.customer;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Một khách hàng MyBatis đọc ra, kèm tên chi nhánh quản lý. CCCD, điện thoại, email là bản đầy đủ,
 * chỉ dùng bên trong backend: CustomerService che trước khi trả về (QT-06).
 */
@Getter
@Setter
public class CustomerRow {
    private Long id;
    private String cccd;
    private String fullName;
    private LocalDate dateOfBirth;
    // Rỗng khi khách không khai giới tính.
    private String gender;
    private LocalDate idIssueDate;
    private String idIssuePlace;
    private String phone;
    private String email;
    private String branchName;
    private OffsetDateTime createdAt;
}