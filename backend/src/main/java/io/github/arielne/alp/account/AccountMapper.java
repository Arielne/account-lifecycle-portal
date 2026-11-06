package io.github.arielne.alp.account;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper MyBatis của bảng app.account (CN-09, CN-10, CN-11). Câu SQL nằm trong AccountMapper.xml.
 * Tài khoản chỉ được tạo, đổi qua phiếu (QT-02): hàm ghi chỉ chạy khi ticket gọi AccountService.
 */
@Mapper
public interface AccountMapper {

    /** Thêm tài khoản Chờ mở; số tài khoản lấy từ app.account_number_seq, id và số được gán lại vào record. */
    int insertPending(AccountRecord record);

    /** Loại tài khoản có tồn tại không (đọc chéo bảng account_type của catalog). */
    boolean accountTypeExists(Long accountTypeId);

    /** Một trang tài khoản khớp điều kiện lọc, mới tạo ở trên. */
    List<AccountRow> findPage(AccountFilter filter);

    /** Tổng số tài khoản khớp điều kiện lọc (không tính phân trang). */
    long count(AccountFilter filter);

    /** Một tài khoản theo id; không có thì trả null. */
    AccountRow findById(Long id);
}