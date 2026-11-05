package io.github.arielne.alp.account;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper MyBatis đọc bảng app.account (CN-09, CN-10). Câu SQL nằm trong AccountMapper.xml.
 * Chỉ có hàm đọc: tài khoản chỉ đổi qua phiếu (QT-02), phần ghi làm cùng module phiếu.
 */
@Mapper
public interface AccountMapper {

    /** Một trang tài khoản khớp điều kiện lọc, mới tạo ở trên. */
    List<AccountRow> findPage(AccountFilter filter);

    /** Tổng số tài khoản khớp điều kiện lọc (không tính phân trang). */
    long count(AccountFilter filter);

    /** Một tài khoản theo id; không có thì trả null. */
    AccountRow findById(Long id);
}