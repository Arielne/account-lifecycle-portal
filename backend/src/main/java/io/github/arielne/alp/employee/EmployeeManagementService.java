package io.github.arielne.alp.employee;

import io.github.arielne.alp.common.AuditNote;
import io.github.arielne.alp.common.Audited;
import io.github.arielne.alp.common.BusinessException;
import io.github.arielne.alp.notification.AccountActivationMailer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ màn Nhân viên (CN-22, spec admin mục 6): danh sách kèm role, thêm, sửa hồ sơ, gán chi nhánh,
 * gán role, khóa, mở khóa. Hồ sơ nằm ở bảng employee; tài khoản và role nằm ở KeyCloak (qua KeycloakUserClient).
 * Không có transaction chung giữa KeyCloak và database: mỗi thao tác ghi database trong transaction,
 * KeyCloak lỗi thì ném lỗi để transaction hủy; tạo user xong mà lưu hồ sơ lỗi thì xóa user để bù lại.
 * Mỗi thao tác ghi audit log (CN-26) qua @Audited và AuditNote.
 */
@Service
@RequiredArgsConstructor
public class EmployeeManagementService {

    private static final String ACTIVE = "ACTIVE";
    private static final String LOCKED = "LOCKED";

    private final EmployeeMapper employeeMapper;
    private final KeycloakUserClient keycloakUserClient;
    private final AccountActivationMailer accountActivationMailer;
    private final AuditNote auditNote;

    /** Mọi nhân viên kèm role: 3 lần gọi KeyCloak (mỗi role một lần) rồi ghép theo keycloak_id. Không lưu bản sao role. */
    public List<EmployeeListItem> findAll() {
        Map<String, Set<String>> userIdsByRole = new HashMap<>();
        for (String role : AppRoles.ALL) {
            userIdsByRole.put(role, keycloakUserClient.findUserIdsWithRole(role));
        }
        List<EmployeeListItem> items = new ArrayList<>();
        for (Employee employee : employeeMapper.findAll()) {
            items.add(toListItem(employee, rolesOf(employee.getKeycloakId(), userIdsByRole)));
        }
        return items;
    }

    /** Tạo user trên KeyCloak, lưu hồ sơ, gán role, rồi KeyCloak gửi mail để người đó tự đặt mật khẩu. */
    @Transactional
    @Audited(action = "CREATE", objectType = "EMPLOYEE")
    public void create(EmployeeCreateRequest request) {
        String email = normalizeEmail(request.email());
        NameParts name = NameParts.fromFullName(request.fullName());
        List<String> roles = normalizeRoles(request.roles());
        checkEmailNotUsed(email, null);
        // Gọi để kiểm tra chi nhánh đã chọn có thật (không có thì 404); tên chi nhánh không dùng ở đây.
        branchNameOf(request.branchId());
        checkBranchForRoles(request.branchId(), roles);

        String keycloakId = keycloakUserClient.createUser(email, name);
        try {
            insertProfile(keycloakId, email, name.fullName(), request.branchId());
            keycloakUserClient.addRoles(keycloakId, roles);
            keycloakUserClient.sendSetPasswordEmail(keycloakId);
        } catch (RuntimeException exception) {
            // Transaction tự hủy dòng employee; user vừa tạo trên KeyCloak thì xóa ở đây để hai bên không lệch.
            keycloakUserClient.deleteUser(keycloakId);
            throw exception;
        }
        auditNote.setObjectId(email);
        auditNote.setDescription(name.fullName());
    }

    /** Sửa họ tên, email (trên KeyCloak trước, thành công mới lưu database) và chi nhánh. */
    @Transactional
    @Audited(action = "UPDATE", objectType = "EMPLOYEE")
    public void update(Long id, EmployeeUpdateRequest request) {
        Employee employee = findExisting(id);
        String email = normalizeEmail(request.email());
        NameParts name = NameParts.fromFullName(request.fullName());
        checkEmailNotUsed(email, id);
        String newBranchName = branchNameOf(request.branchId());
        if (employee.getBranchId() != null && request.branchId() == null) {
            checkBranchForRoles(null, keycloakUserClient.findAppRoles(employee.getKeycloakId()));
        }
        recordProfileChanges(employee, email, name.fullName(), newBranchName);

        boolean accountChanged = !email.equals(employee.getEmail()) || !name.fullName().equals(employee.getFullName());
        if (accountChanged) {
            keycloakUserClient.updateUser(employee.getKeycloakId(), email, name);
        }
        employee.setEmail(email);
        employee.setFullName(name.fullName());
        employee.setBranchId(request.branchId());
        employeeMapper.updateProfile(employee);
    }

    /**
     * Danh sách role mới thay cho danh sách cũ: so với role hiện có trên KeyCloak rồi thêm hoặc bỏ từng role.
     * Người Chờ cấp quyền được gán role đầu tiên thì gửi mail kích hoạt (CN-05).
     */
    @Transactional
    @Audited(action = "ASSIGN_ROLE", objectType = "EMPLOYEE")
    public void assignRoles(Long id, List<String> requestedRoles, String currentKeycloakId) {
        Employee employee = findExisting(id);
        checkNotSelf(employee, currentKeycloakId, "Không thể tự đổi role của chính mình");
        List<String> newRoles = normalizeRoles(requestedRoles);
        checkBranchForRoles(employee.getBranchId(), newRoles);

        List<String> currentRoles = keycloakUserClient.findAppRoles(employee.getKeycloakId());
        List<String> rolesToAdd = rolesNotIn(newRoles, currentRoles);
        List<String> rolesToRemove = rolesNotIn(currentRoles, newRoles);
        keycloakUserClient.addRoles(employee.getKeycloakId(), rolesToAdd);
        keycloakUserClient.removeRoles(employee.getKeycloakId(), rolesToRemove);

        auditNote.setObjectId(employee.getEmail());
        auditNote.addChangeIfDifferent("Role", AppRoles.labels(currentRoles), AppRoles.labels(newRoles));
        auditNote.setDescription(describeRoleChange(rolesToAdd, rolesToRemove));
        if (currentRoles.isEmpty()) {
            accountActivationMailer.sendAccountActivated(employee.getEmail(), employee.getFullName(),
                    AppRoles.labels(newRoles));
        }
    }

    /** Khóa: hồ sơ LOCKED, tắt tài khoản và đăng xuất mọi phiên trên KeyCloak. Phiếu họ đã tạo giữ nguyên. */
    @Transactional
    @Audited(action = "LOCK", objectType = "EMPLOYEE")
    public void lock(Long id, String currentKeycloakId) {
        Employee employee = findExisting(id);
        checkNotSelf(employee, currentKeycloakId, "Không thể tự khóa chính mình");
        employeeMapper.updateStatus(id, LOCKED);
        keycloakUserClient.setEnabled(employee.getKeycloakId(), false);
        keycloakUserClient.logout(employee.getKeycloakId());

        auditNote.setObjectId(employee.getEmail());
        auditNote.setDescription(employee.getFullName());
    }

    /** Mở khóa: hồ sơ ACTIVE, bật lại tài khoản trên KeyCloak. */
    @Transactional
    @Audited(action = "UNLOCK", objectType = "EMPLOYEE")
    public void unlock(Long id) {
        Employee employee = findExisting(id);
        employeeMapper.updateStatus(id, ACTIVE);
        keycloakUserClient.setEnabled(employee.getKeycloakId(), true);

        auditNote.setObjectId(employee.getEmail());
        auditNote.setDescription(employee.getFullName());
    }

    private Employee findExisting(Long id) {
        Employee employee = employeeMapper.findById(id);
        if (employee == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên");
        }
        return employee;
    }

    private void insertProfile(String keycloakId, String email, String fullName, Long branchId) {
        Employee employee = new Employee();
        employee.setKeycloakId(keycloakId);
        employee.setEmail(email);
        employee.setFullName(fullName);
        employee.setBranchId(branchId);
        employee.setStatus(ACTIVE);
        employeeMapper.insert(employee);
    }

    /** Email đã thuộc hồ sơ khác thì 409. currentId là id đang sửa (null khi thêm mới). */
    private void checkEmailNotUsed(String email, Long currentId) {
        Employee owner = employeeMapper.findByEmail(email);
        if (owner != null && !owner.getId().equals(currentId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Email đã có hồ sơ nhân viên");
        }
    }

    /** Tên chi nhánh để ghi audit; null khi không chọn chi nhánh. Chọn chi nhánh không có thì 404. */
    private String branchNameOf(Long branchId) {
        if (branchId == null) {
            return null;
        }
        String branchName = employeeMapper.findBranchNameById(branchId);
        if (branchName == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy chi nhánh");
        }
        return branchName;
    }

    /** MAKER, CHECKER làm việc theo chi nhánh: chưa có chi nhánh thì không gán được (409). */
    private void checkBranchForRoles(Long branchId, List<String> roles) {
        if (branchId != null) {
            return;
        }
        for (String role : roles) {
            if (AppRoles.needsBranch(role)) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "Giao dịch viên, Kiểm soát viên phải có chi nhánh. Vui lòng chọn chi nhánh trước.");
            }
        }
    }

    /** Admin không tự khóa, không tự đổi role của mình, để không ai lỡ tay làm mất quyền quản trị. */
    private void checkNotSelf(Employee employee, String currentKeycloakId, String message) {
        if (employee.getKeycloakId().equals(currentKeycloakId)) {
            throw new BusinessException(HttpStatus.CONFLICT, message);
        }
    }

    private void recordProfileChanges(Employee employee, String email, String fullName, String branchName) {
        auditNote.setObjectId(email);
        auditNote.addChangeIfDifferent("Họ tên", employee.getFullName(), fullName);
        auditNote.addChangeIfDifferent("Email", employee.getEmail(), email);
        auditNote.addChangeIfDifferent("Chi nhánh", employee.getBranchName(), branchName);
        auditNote.describeChanges();
    }

    /** Ví dụ "Thêm Kiểm soát viên, Bỏ Giao dịch viên". */
    private String describeRoleChange(List<String> rolesToAdd, List<String> rolesToRemove) {
        List<String> parts = new ArrayList<>();
        for (String role : rolesToAdd) {
            parts.add("Thêm " + AppRoles.label(role));
        }
        for (String role : rolesToRemove) {
            parts.add("Bỏ " + AppRoles.label(role));
        }
        if (parts.isEmpty()) {
            return "Không đổi role";
        }
        return String.join(", ", parts);
    }

    /** Các role có trong roles mà không có trong otherRoles. */
    private List<String> rolesNotIn(List<String> roles, List<String> otherRoles) {
        List<String> result = new ArrayList<>();
        for (String role : roles) {
            if (!otherRoles.contains(role)) {
                result.add(role);
            }
        }
        return result;
    }

    /** Bỏ role trùng, xếp theo thứ tự MAKER, CHECKER, ADMIN. */
    private List<String> normalizeRoles(List<String> roles) {
        List<String> result = new ArrayList<>();
        for (String role : AppRoles.ALL) {
            if (roles.contains(role)) {
                result.add(role);
            }
        }
        return result;
    }

    private List<String> rolesOf(String keycloakId, Map<String, Set<String>> userIdsByRole) {
        List<String> roles = new ArrayList<>();
        for (String role : AppRoles.ALL) {
            if (userIdsByRole.get(role).contains(keycloakId)) {
                roles.add(role);
            }
        }
        return roles;
    }

    private EmployeeListItem toListItem(Employee employee, List<String> roles) {
        return new EmployeeListItem(employee.getId(), employee.getKeycloakId(), employee.getFullName(),
                employee.getEmail(), employee.getBranchId(), employee.getBranchName(), roles, employee.getStatus(),
                employee.getCreatedAt());
    }

    /** KeyCloak lưu email chữ thường, nên database cũng lưu chữ thường để so trùng cho đúng. */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}