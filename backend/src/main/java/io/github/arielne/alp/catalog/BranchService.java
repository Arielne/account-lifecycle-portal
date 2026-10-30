package io.github.arielne.alp.catalog;

import io.github.arielne.alp.common.AuditNote;
import io.github.arielne.alp.common.Audited;
import io.github.arielne.alp.common.BusinessException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ chi nhánh (CN-21): mã không trùng (sửa được mã, vẫn kiểm tra trùng); không xóa khi còn nhân viên,
 * khách hàng, tài khoản hoặc phiếu thuộc chi nhánh (QT-08). Mỗi lần thêm, sửa, xóa ghi audit log (CN-26).
 */
@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchMapper branchMapper;
    private final AuditNote auditNote;

    public List<Branch> findAll() {
        return branchMapper.findAll();
    }

    /** Mã còn trống không; màn Chi nhánh gọi khi người dùng rời ô Mã. */
    public boolean isCodeAvailable(String code) {
        return !branchMapper.existsByCode(code);
    }

    @Transactional
    @Audited(action = "CREATE", objectType = "BRANCH")
    public Branch create(BranchRequest request) {
        checkCodeNotUsed(request.code());
        Branch branch = new Branch();
        copyRequest(request, branch);
        branchMapper.insert(branch);

        auditNote.setObjectId(branch.getCode());
        auditNote.setDescription(branch.getName());
        return branchMapper.findById(branch.getId());
    }

    @Transactional
    @Audited(action = "UPDATE", objectType = "BRANCH")
    public Branch update(Long id, BranchRequest request) {
        Branch branch = findExisting(id);
        if (!branch.getCode().equals(request.code())) {
            checkCodeNotUsed(request.code());
        }

        auditNote.setObjectId(request.code());
        auditNote.addChangeIfDifferent("Mã chi nhánh", branch.getCode(), request.code());
        auditNote.addChangeIfDifferent("Tên chi nhánh", branch.getName(), request.name().trim());
        auditNote.addChangeIfDifferent("Địa chỉ", branch.getAddress(), request.address().trim());
        auditNote.addChangeIfDifferent("Số điện thoại", formatPhone(branch.getPhone()), formatPhone(request.phone()));
        auditNote.describeChanges();

        copyRequest(request, branch);
        branchMapper.update(branch);
        return branchMapper.findById(id);
    }

    @Transactional
    @Audited(action = "DELETE", objectType = "BRANCH")
    public void delete(Long id) {
        Branch branch = findExisting(id);
        String usage = describeUsage(branchMapper.findUsage(id));
        if (!usage.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Không xóa được " + branch.getName() + ": còn " + usage + " thuộc chi nhánh này.");
        }
        branchMapper.deleteById(id);

        auditNote.setObjectId(branch.getCode());
        auditNote.setDescription(branch.getName());
    }

    private Branch findExisting(Long id) {
        Branch branch = branchMapper.findById(id);
        if (branch == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy chi nhánh");
        }
        return branch;
    }

    private void checkCodeNotUsed(String code) {
        if (branchMapper.existsByCode(code)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Mã chi nhánh " + code + " đã tồn tại");
        }
    }

    private void copyRequest(BranchRequest request, Branch branch) {
        branch.setCode(request.code());
        branch.setName(request.name().trim());
        branch.setAddress(request.address().trim());
        branch.setPhone(request.phone());
    }

    /** Ví dụ "14 nhân viên, 812 tài khoản"; chuỗi rỗng khi không còn gì thuộc chi nhánh. */
    private String describeUsage(BranchUsage usage) {
        List<String> parts = new ArrayList<>();
        addUsagePart(parts, usage.getEmployeeCount(), "nhân viên");
        addUsagePart(parts, usage.getCustomerCount(), "khách hàng");
        addUsagePart(parts, usage.getAccountCount(), "tài khoản");
        addUsagePart(parts, usage.getTicketCount(), "phiếu");
        return String.join(", ", parts);
    }

    private void addUsagePart(List<String> parts, long count, String label) {
        if (count > 0) {
            parts.add(count + " " + label);
        }
    }

    /** Điện thoại hiện theo nhóm số như trên màn: 11 số nhóm 3-4-4 (028 3911 2200), 10 số nhóm 4-3-3 (0901 234 567). */
    private String formatPhone(String digits) {
        if (digits.length() == 11) {
            return digits.substring(0, 3) + " " + digits.substring(3, 7) + " " + digits.substring(7);
        }
        if (digits.length() == 10) {
            return digits.substring(0, 4) + " " + digits.substring(4, 7) + " " + digits.substring(7);
        }
        return digits;
    }
}