package io.github.arielne.alp.account;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    /** Đổi loại tài khoản của tài khoản Chờ mở (sửa phiếu Mở tài khoản); tài khoản không còn Chờ mở thì không đổi. */
    int updatePendingAccountType(@Param("accountId") Long accountId, @Param("accountTypeId") Long accountTypeId);

    /** Khóa dòng tài khoản tới hết transaction (SELECT ... FOR UPDATE) và trả trạng thái hiện tại; không có thì null. */
    String lockStatus(Long accountId);

    /**
     * Đổi trạng thái. activating: đang sang Hoạt động, lần đầu thì đặt ngày mở và ngày giao dịch gần nhất là today;
     * closing: đang sang Đã đóng, đặt ngày đóng là today.
     */
    int updateStatus(@Param("accountId") Long accountId, @Param("status") String status,
                     @Param("activating") boolean activating, @Param("closing") boolean closing,
                     @Param("today") LocalDate today);

    /** Ghi 1 dòng lịch sử trạng thái (V14). fromStatus rỗng: tài khoản vừa được tạo; changedBy rỗng: Hệ thống. */
    int insertStatusHistory(@Param("accountId") Long accountId, @Param("fromStatus") String fromStatus,
                            @Param("toStatus") String toStatus, @Param("ticketId") Long ticketId,
                            @Param("changedBy") Long changedBy);

    /** Một trang tài khoản khớp điều kiện lọc, mới tạo ở trên. */
    List<AccountRow> findPage(AccountFilter filter);

    /** Tổng số tài khoản khớp điều kiện lọc (không tính phân trang). */
    long count(AccountFilter filter);

    /** Một tài khoản theo id, kèm "Mở bởi", "Duyệt mở bởi"; không có thì trả null. */
    AccountRow findById(Long id);

    /** Vòng đời của một tài khoản, mới nhất ở trên. */
    List<AccountStatusHistoryItem> findStatusHistory(Long accountId);
}