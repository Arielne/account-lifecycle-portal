package io.github.arielne.alp.account;

import io.github.arielne.alp.common.BusinessException;
import io.github.arielne.alp.common.PersonalDataMasker;
import io.github.arielne.alp.customer.CustomerProfile;
import io.github.arielne.alp.customer.CustomerService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Đọc tài khoản cho màn Tài khoản (CN-09) và Chi tiết tài khoản (CN-10): xem được tài khoản của mọi chi nhánh
 * (QT-04), CCCD của chủ tài khoản được che trước khi trả về (QT-06).
 * Mở tài khoản Chờ mở cho phiếu Mở tài khoản (CN-11) và đổi trạng thái khi duyệt, hủy phiếu (CN-16, CN-13): chỉ
 * ticket gọi (QT-02), audit nằm ở dòng của phiếu. Mỗi lần tạo hoặc đổi trạng thái ghi 1 dòng account_status_history.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final int PAGE_SIZE = 10;
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AccountMapper accountMapper;
    private final CustomerService customerService;

    /** Một trang 10 tài khoản khớp điều kiện lọc, mới tạo ở trên, kèm tổng số để vẽ phân trang. */
    public AccountPage findPage(AccountQuery query) {
        AccountFilter filter = toFilter(query);
        List<AccountItem> items = new ArrayList<>();
        for (AccountRow row : accountMapper.findPage(filter)) {
            items.add(toItem(row));
        }
        return new AccountPage(items, accountMapper.count(filter));
    }

    /** Chi tiết một tài khoản; không có thì 404. */
    public AccountItem findById(Long id) {
        AccountRow row = accountMapper.findById(id);
        if (row == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản");
        }
        return toItem(row);
    }

    /**
     * Mở tài khoản Chờ mở cho khách đã có (phiếu Mở tài khoản, CN-11). Chỉ ticket gọi, trong transaction tạo phiếu.
     * branchId: chi nhánh của người tạo phiếu.
     */
    public PendingAccount openPendingForCustomer(Long customerId, Long accountTypeId, Long branchId) {
        return insertPendingAccount(customerId, accountTypeId, branchId);
    }

    /**
     * Khách mới: tạo hồ sơ, hoặc ghi đè hồ sơ chưa từng mở được tài khoản khi reusableCustomerId có giá trị (D6),
     * rồi mở tài khoản Chờ mở. Ghi bảng customer qua CustomerService, vì account không tự ghi bảng của module khác.
     */
    public PendingAccount openPendingForNewCustomer(CustomerProfile profile, Long reusableCustomerId,
                                                    Long accountTypeId, Long branchId) {
        Long customerId = reusableCustomerId;
        if (customerId == null) {
            customerId = customerService.create(profile, branchId);
        } else {
            customerService.overwrite(customerId, profile, branchId);
        }
        return insertPendingAccount(customerId, accountTypeId, branchId);
    }

    /**
     * Ghi dòng lịch sử "(chưa có) → Chờ mở" cho tài khoản vừa mở bằng phiếu Mở tài khoản (V14). Tách khỏi hàm mở tài
     * khoản vì dòng lịch sử trỏ tới phiếu, mà phiếu lại trỏ tới tài khoản: tài khoản phải có trước, rồi tới phiếu,
     * rồi mới ghi được dòng này. ticket gọi ngay sau khi INSERT phiếu, cùng transaction.
     */
    public void recordPendingOpened(Long accountId, Long ticketId, Long creatorId) {
        accountMapper.insertStatusHistory(accountId, null, AccountStatus.PENDING_OPEN.name(), ticketId, creatorId);
    }

    /**
     * Đổi trạng thái tài khoản theo phiếu (QT-02, spec phiếu mục 5). Chỉ ticket gọi, trong transaction duyệt hoặc hủy
     * phiếu: khóa dòng tài khoản, kiểm trạng thái vẫn như lúc tạo phiếu, kiểm lần chuyển hợp lệ bằng State pattern,
     * rồi UPDATE và ghi 1 dòng lịch sử. actorId: người duyệt (hoặc người hủy).
     */
    public void changeStatus(Long accountId, AccountStatus expectedFrom, AccountStatus target, Long ticketId,
                             Long actorId) {
        AccountStatus current = lockCurrentStatus(accountId);
        if (current != expectedFrom) {
            // Ví dụ job vừa chuyển Ngủ đông trong lúc phiếu Phong tỏa chờ duyệt (D7).
            throw new BusinessException(HttpStatus.CONFLICT, "Trạng thái tài khoản đã đổi từ " + expectedFrom.getLabel()
                    + " sang " + current.getLabel() + " sau khi tạo phiếu. Hãy từ chối phiếu này.");
        }
        if (!AccountState.of(current).canMoveTo(target)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Không chuyển được tài khoản từ " + current.getLabel()
                    + " sang " + target.getLabel());
        }
        boolean activating = target == AccountStatus.ACTIVE;
        boolean closing = target == AccountStatus.CLOSED;
        accountMapper.updateStatus(accountId, target.name(), activating, closing, LocalDate.now(VIETNAM));
        accountMapper.insertStatusHistory(accountId, current.name(), target.name(), ticketId, actorId);
    }

    private AccountStatus lockCurrentStatus(Long accountId) {
        String status = accountMapper.lockStatus(accountId);
        if (status == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản");
        }
        return AccountStatus.valueOf(status);
    }

    /**
     * Loại tài khoản không có thì 404. Ngày giao dịch gần nhất là cột bắt buộc nên tạm đặt hôm nay (giờ Việt Nam);
     * job Ngủ đông chỉ xét tài khoản Hoạt động nên giá trị tạm này không ảnh hưởng.
     */
    private PendingAccount insertPendingAccount(Long customerId, Long accountTypeId, Long branchId) {
        if (!accountMapper.accountTypeExists(accountTypeId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy loại tài khoản");
        }
        AccountRecord record = new AccountRecord();
        record.setCustomerId(customerId);
        record.setAccountTypeId(accountTypeId);
        record.setBranchId(branchId);
        record.setLastTransactionAt(LocalDate.now(VIETNAM));
        accountMapper.insertPending(record);
        return new PendingAccount(record.getId(), record.getAccountNumber(), customerId);
    }

    /** Trang nhỏ hơn 1 tính là trang 1, như màn Audit log. */
    private AccountFilter toFilter(AccountQuery query) {
        AccountFilter filter = new AccountFilter();
        filter.setAccountNumberPrefix(query.accountNumber());
        filter.setStatuses(toStatusCodes(query.statuses()));
        filter.setAccountTypeId(query.accountTypeId());
        filter.setBranchId(query.branchId());
        filter.setCustomerId(query.customerId());
        int page = 1;
        if (query.page() != null && query.page() > 1) {
            page = query.page();
        }
        // Tính bằng long: trang gần Integer.MAX_VALUE nhân 10 sẽ tràn int thành số âm.
        filter.setOffset((page - 1L) * PAGE_SIZE);
        filter.setSize(PAGE_SIZE);
        return filter;
    }

    /** Danh sách enum thành mảng mã cho câu SQL; không chọn trạng thái nào thì null, tức không lọc. */
    private String[] toStatusCodes(List<AccountStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return null;
        }
        String[] codes = new String[statuses.size()];
        for (int index = 0; index < statuses.size(); index++) {
            codes[index] = statuses.get(index).name();
        }
        return codes;
    }

    private AccountItem toItem(AccountRow row) {
        AccountItem.OpenTicket openTicket = null;
        if (row.getOpenTicketCode() != null) {
            openTicket = new AccountItem.OpenTicket(row.getOpenTicketCode(), row.getOpenTicketType(),
                    row.getOpenTicketStatus());
        }
        return new AccountItem(row.getId(), row.getAccountNumber(), row.getCustomerId(), row.getCustomerName(),
                PersonalDataMasker.maskCccd(row.getCustomerCccd()), row.getAccountTypeName(), row.getBranchName(),
                row.getStatus(), row.getOpenedAt(), row.getClosedAt(), row.getLastTransactionAt(), openTicket);
    }
}