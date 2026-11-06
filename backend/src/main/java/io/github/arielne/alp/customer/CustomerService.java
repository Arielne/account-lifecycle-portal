package io.github.arielne.alp.customer;

import io.github.arielne.alp.common.BusinessException;
import io.github.arielne.alp.common.PersonalDataMasker;
import io.github.arielne.alp.common.TextNormalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Tìm và xem khách hàng cho màn Khách hàng (CN-07) và Chi tiết khách hàng (CN-08). Tìm, xem không ghi bảng nào
 * nên không có @Audited, kể cả khi tìm theo CCCD (spec khách hàng tài khoản, C8). Xem được khách của mọi chi nhánh
 * (QT-04). CCCD, điện thoại, email được che trước khi trả về (QT-06).
 * Tạo, ghi đè hồ sơ (create, overwrite) chỉ chạy khi account mở tài khoản cho phiếu Mở tài khoản (CN-11);
 * audit nằm ở dòng "Tạo phiếu" của ticket.
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

    /**
     * Tạo hồ sơ khách mới từ phiếu Mở tài khoản (CN-11); chỉ account gọi, trong transaction tạo phiếu.
     * branchId: chi nhánh của người tạo phiếu, thành chi nhánh quản lý hồ sơ. Trả id hồ sơ vừa tạo.
     */
    public Long create(CustomerProfile profile, Long branchId) {
        CustomerRecord record = toRecord(profile, branchId);
        customerMapper.insert(record);
        return record.getId();
    }

    /**
     * Ghi đè hồ sơ chưa từng mở được tài khoản bằng dữ liệu của phiếu mới (D6): giữ id, nên các tài khoản Đã đóng
     * cũ vẫn gắn đúng hồ sơ. Chỉ account gọi; ticket đã kiểm hồ sơ đúng là chưa từng mở được tài khoản.
     */
    public void overwrite(Long customerId, CustomerProfile profile, Long branchId) {
        CustomerRecord record = toRecord(profile, branchId);
        record.setId(customerId);
        customerMapper.updateProfile(record);
    }

    /**
     * Họ tên, nơi cấp, địa chỉ chuẩn hóa bằng TextNormalizer (NFC, gộp khoảng trắng) để tìm tên không dấu tìm ra
     * (spec phiếu, mục 7.2). Email đổi về chữ thường; giới tính rỗng thì lưu null.
     */
    private CustomerRecord toRecord(CustomerProfile profile, Long branchId) {
        CustomerRecord record = new CustomerRecord();
        record.setCccd(profile.cccd());
        record.setFullName(TextNormalizer.normalize(profile.fullName()));
        record.setDateOfBirth(profile.dateOfBirth());
        record.setGender(emptyToNull(profile.gender()));
        record.setIdIssueDate(profile.idIssueDate());
        record.setIdIssuePlace(TextNormalizer.normalize(profile.idIssuePlace()));
        record.setPhone(profile.phone());
        record.setEmail(profile.email().trim().toLowerCase(Locale.ROOT));
        record.setAddress(TextNormalizer.normalize(profile.address()));
        record.setBranchId(branchId);
        return record;
    }

    private String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
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

    /** Chuẩn hóa từ khóa như khi lưu hồ sơ (TextNormalizer), để so được với nhau. Rỗng thì null, tức không lọc. */
    private String normalizeKeyword(String keyword) {
        String normalized = TextNormalizer.normalize(keyword);
        if (normalized == null || normalized.isEmpty()) {
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