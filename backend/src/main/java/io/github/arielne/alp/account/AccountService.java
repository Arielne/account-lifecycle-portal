package io.github.arielne.alp.account;

import io.github.arielne.alp.common.BusinessException;
import io.github.arielne.alp.common.PersonalDataMasker;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Đọc tài khoản cho màn Tài khoản (CN-09) và Chi tiết tài khoản (CN-10). Chỉ đọc, không ghi bảng nào nên không có
 * @Audited. Xem được tài khoản của mọi chi nhánh (QT-04): không lọc theo chi nhánh của người đang xem.
 * CCCD của chủ tài khoản được che trước khi trả về (QT-06).
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final int PAGE_SIZE = 10;

    private final AccountMapper accountMapper;

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