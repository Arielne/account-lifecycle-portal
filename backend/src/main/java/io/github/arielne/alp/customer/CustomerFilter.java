package io.github.arielne.alp.customer;

import lombok.Getter;
import lombok.Setter;

/**
 * Điều kiện tìm và phân trang của màn Khách hàng (CN-07), truyền cho CustomerMapper.
 * Chỉ một trong hai trường nameKeyword, cccd có giá trị; cả hai null thì liệt kê mọi khách.
 */
@Getter
@Setter
public class CustomerFilter {
    /** Họ tên cần tìm, đã chuẩn hóa NFC và gộp khoảng trắng; SQL bỏ dấu cả hai bên rồi mới so. */
    private String nameKeyword;
    /** Đủ 12 chữ số, khớp tuyệt đối. */
    private String cccd;
    /** Số dòng bỏ qua; kiểu long vì trang lớn nhân 10 vượt giới hạn của int. */
    private long offset;
    private int size;
}