package io.github.arielne.alp.catalog;

import io.github.arielne.alp.common.AuditNote;
import io.github.arielne.alp.common.Audited;
import io.github.arielne.alp.common.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ loại tài khoản (CN-20): mã không trùng, không sửa mã, không xóa khi còn tài khoản dùng (QT-08).
 * Mỗi lần thêm, sửa, xóa ghi audit log (CN-26): method gắn @Audited và ghi nội dung vào AuditNote.
 */
@Service
@RequiredArgsConstructor
public class AccountTypeService {

    private final AccountTypeMapper accountTypeMapper;
    private final AuditNote auditNote;

    public List<AccountType> findAll() {
        return accountTypeMapper.findAll();
    }

    @Transactional
    @Audited(action = "CREATE", objectType = "ACCOUNT_TYPE")
    public AccountType create(AccountTypeCreateRequest request) {
        if (accountTypeMapper.existsByCode(request.code())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Mã loại " + request.code() + " đã tồn tại");
        }
        AccountType accountType = new AccountType();
        accountType.setCode(request.code());
        accountType.setName(request.name().trim());
        accountType.setDescription(emptyToNull(request.description()));
        accountType.setDormantAfterMonths(request.dormantAfterMonths());
        accountTypeMapper.insert(accountType);

        auditNote.setObjectId(accountType.getCode());
        auditNote.setDescription(accountType.getName());
        return accountTypeMapper.findById(accountType.getId());
    }

    /** Sửa tên, mô tả, số tháng; mã giữ nguyên. Giảm số tháng vẫn lưu bình thường, audit ghi trước và sau. */
    @Transactional
    @Audited(action = "UPDATE", objectType = "ACCOUNT_TYPE")
    public AccountType update(Long id, AccountTypeUpdateRequest request) {
        AccountType accountType = findExisting(id);
        String newName = request.name().trim();
        String newDescription = emptyToNull(request.description());

        auditNote.setObjectId(accountType.getCode());
        auditNote.addChangeIfDifferent("Tên loại tài khoản", accountType.getName(), newName);
        auditNote.addChangeIfDifferent("Mô tả", accountType.getDescription(), newDescription);
        auditNote.addChangeIfDifferent("Chuyển ngủ đông sau",
                monthsText(accountType.getDormantAfterMonths()), monthsText(request.dormantAfterMonths()));
        auditNote.describeChanges();

        accountType.setName(newName);
        accountType.setDescription(newDescription);
        accountType.setDormantAfterMonths(request.dormantAfterMonths());
        accountTypeMapper.update(accountType);
        return accountTypeMapper.findById(id);
    }

    @Transactional
    @Audited(action = "DELETE", objectType = "ACCOUNT_TYPE")
    public void delete(Long id) {
        AccountType accountType = findExisting(id);
        if (accountType.getAccountCount() > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "Không xóa được " + accountType.getName()
                    + ": còn " + accountType.getAccountCount() + " tài khoản dùng loại này.");
        }
        accountTypeMapper.deleteById(id);

        auditNote.setObjectId(accountType.getCode());
        auditNote.setDescription(accountType.getName());
    }

    private AccountType findExisting(Long id) {
        AccountType accountType = accountTypeMapper.findById(id);
        if (accountType == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy loại tài khoản");
        }
        return accountType;
    }

    private String monthsText(Integer months) {
        return months + " tháng";
    }

    /** Mô tả không bắt buộc: để trống thì lưu null, có chữ thì bỏ khoảng trắng hai đầu. */
    private String emptyToNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }
}