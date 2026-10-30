package io.github.arielne.alp.catalog;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Một dòng của bảng app.account_type (loại tài khoản), dùng cho CN-20 Quản lý loại tài khoản.
 * Cũng là JSON trả về cho React ở GET /api/account-types.
 */
@Getter
@Setter
public class AccountType {

    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer dormantAfterMonths;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    // Số tài khoản đang dùng loại này. Không phải cột của bảng: câu SELECT đếm trong bảng account.
    private Long accountCount;
}