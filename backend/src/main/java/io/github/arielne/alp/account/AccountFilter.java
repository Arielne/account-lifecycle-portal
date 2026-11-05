package io.github.arielne.alp.account;

import lombok.Getter;
import lombok.Setter;

/**
 * Điều kiện lọc và phân trang của màn Tài khoản (CN-09), truyền cho AccountMapper.
 * Trường để null là không lọc theo điều kiện đó; file XML dùng <if> để bỏ qua.
 */
@Getter
@Setter
public class AccountFilter {
    /** Số tài khoản bắt đầu bằng chuỗi này (chỉ gồm chữ số). */
    private String accountNumberPrefix;
    /** Mã trạng thái, ví dụ {"ACTIVE", "DORMANT"}; mảng để SQL so bằng = ANY(...). */
    private String[] statuses;
    private Long accountTypeId;
    private Long branchId;
    private Long customerId;
    /** Số dòng bỏ qua; kiểu long vì trang lớn nhân 10 vượt giới hạn của int. */
    private long offset;
    private int size;
}