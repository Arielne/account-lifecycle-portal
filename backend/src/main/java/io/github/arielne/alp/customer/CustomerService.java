package io.github.arielne.alp.customer;

import io.github.arielne.alp.common.BusinessException;
import io.github.arielne.alp.common.PersonalDataMasker;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Tìm và xem khách hàng cho màn Khách hàng (CN-07) và Chi tiết khách hàng (CN-08). Chỉ đọc, không ghi bảng nào
 * nên không có @Audited, kể cả khi tìm theo CCCD (spec khách hàng tài khoản, C8). Xem được khách của mọi chi nhánh
 * (QT-04). CCCD, điện thoại, email được che trước khi trả về (QT-06).
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final int PAGE_SIZE = 10;
    private static final Pattern CCCD_PATTERN = Pattern.compile("\\d{12}");
    // Ngày tạo hồ sơ hiện theo giờ Việt Nam; cột created_at lưu kèm múi giờ.
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final CustomerMapper customerMapper;

    /** Một trang 10 khách khớp điều kiện tìm, kèm trạng thái tài khoản của từng khách và tổng số khách. */
    public CustomerPage search(CustomerSearchRequest request) {
        CustomerFilter filter = toFilter(request);
        List<CustomerRow> rows = customerMapper.findPage(filter);
        Map<Long, List<String>> statusesByCustomer = accountStatusesOf(rows);
        List<CustomerListItem> items = new ArrayList<>();
        for (CustomerRow row : rows) {
            List<String> accountStatuses = statusesByCustomer.getOrDefault(row.getId(), List.of());
            items.add(new CustomerListItem(row.getId(), row.getFullName(), PersonalDataMasker.maskCccd(row.getCccd()),
                    row.getDateOfBirth(), PersonalDataMasker.maskPhone(row.getPhone()), row.getBranchName(),
                    toVietnamDate(row.getCreatedAt()), accountStatuses));
        }
        return new CustomerPage(items, customerMapper.count(filter));
    }

    /** Chi tiết một khách; không có thì 404. */
    public CustomerDetail findById(Long id) {
        CustomerRow row = customerMapper.findById(id);
        if (row == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy khách hàng");
        }
        return new CustomerDetail(row.getId(), row.getFullName(), row.getDateOfBirth(), row.getGender(),
                PersonalDataMasker.maskCccd(row.getCccd()), row.getIdIssueDate(), row.getIdIssuePlace(),
                PersonalDataMasker.maskPhone(row.getPhone()), PersonalDataMasker.maskEmail(row.getEmail()),
                row.getBranchName(), toVietnamDate(row.getCreatedAt()));
    }

    /** Tìm theo CCCD thì từ khóa phải đủ 12 chữ số (400); tìm theo họ tên thì từ khóa rỗng là liệt kê mọi khách. */
    private CustomerFilter toFilter(CustomerSearchRequest request) {
        CustomerFilter filter = new CustomerFilter();
        String keyword = normalizeKeyword(request.keyword());
        if (request.searchBy() == CustomerSearchBy.CCCD) {
            if (keyword == null || !CCCD_PATTERN.matcher(keyword).matches()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Số CCCD phải đủ 12 chữ số");
            }
            filter.setCccd(keyword);
        } else {
            filter.setNameKeyword(keyword);
        }
        int page = 1;
        if (request.page() != null && request.page() > 1) {
            page = request.page();
        }
        // Tính bằng long: trang gần Integer.MAX_VALUE nhân 10 sẽ tràn int thành số âm.
        filter.setOffset((page - 1L) * PAGE_SIZE);
        filter.setSize(PAGE_SIZE);
        return filter;
    }

    /**
     * Bỏ khoảng trắng hai đầu, gộp nhiều khoảng trắng thành một, chuẩn hóa Unicode NFC (chữ có dấu gõ từ bàn phím
     * khác nhau có thể khác byte, NFC đưa về một dạng). Rỗng thì null, tức không lọc.
     */
    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = Normalizer.normalize(keyword, Normalizer.Form.NFC).trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }

    /**
     * Câu thứ hai của danh sách: trạng thái tài khoản của đúng các khách trong trang, gom theo id khách.
     * Trang không có khách nào thì không cần hỏi database.
     */
    private Map<Long, List<String>> accountStatusesOf(List<CustomerRow> rows) {
        Map<Long, List<String>> statusesByCustomer = new HashMap<>();
        if (rows.isEmpty()) {
            return statusesByCustomer;
        }
        Long[] customerIds = new Long[rows.size()];
        for (int index = 0; index < rows.size(); index++) {
            customerIds[index] = rows.get(index).getId();
        }
        for (CustomerAccountRow account : customerMapper.findAccountStatuses(customerIds)) {
            statusesByCustomer.computeIfAbsent(account.getCustomerId(), customerId -> new ArrayList<>())
                    .add(account.getStatus());
        }
        return statusesByCustomer;
    }

    private LocalDate toVietnamDate(OffsetDateTime createdAt) {
        return createdAt.atZoneSameInstant(VIETNAM).toLocalDate();
    }
}