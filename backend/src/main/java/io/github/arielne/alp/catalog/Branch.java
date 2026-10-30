package io.github.arielne.alp.catalog;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Một dòng của bảng app.branch (chi nhánh), dùng cho CN-21 Quản lý chi nhánh.
 * MyBatis đọc từng cột vào thuộc tính cùng tên: cột created_at vào createdAt
 * (nhờ map-underscore-to-camel-case trong application.yml).
 * Cột kiểu timestamptz (có múi giờ) nên dùng OffsetDateTime.
 */
@Getter
@Setter
public class Branch {

    private Long id;
    private String code;
    private String name;
    private String address;
    private String phone;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    // Số nhân viên, số tài khoản thuộc chi nhánh. Không phải cột của bảng: câu SELECT đếm ở bảng employee, account.
    private Long employeeCount;
    private Long accountCount;
}