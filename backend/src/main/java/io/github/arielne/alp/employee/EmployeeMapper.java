package io.github.arielne.alp.employee;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Mapper MyBatis cho bảng app.employee. Câu SQL nằm trong EmployeeMapper.xml.
 * Hàm có từ 2 tham số trở lên thì đặt @Param để file XML gọi được #{tên}.
 */
@Mapper
public interface EmployeeMapper {

    /**
     * Lần đăng nhập đầu: thêm hồ sơ với status ACTIVE, chưa có chi nhánh.
     * Các lần sau: chỉ cập nhật email và họ tên. Trả về số dòng bị thay đổi.
     */
    int upsertFromLogin(@Param("keycloakId") String keycloakId,
                        @Param("email") String email,
                        @Param("fullName") String fullName);

    /** Hồ sơ kèm chi nhánh; trả null khi chưa có hồ sơ. */
    Employee findByKeycloakId(@Param("keycloakId") String keycloakId);

    /** Mọi hồ sơ kèm chi nhánh (màn Nhân viên, CN-22). */
    List<Employee> findAll();

    /** Hồ sơ kèm chi nhánh theo id; null khi không có. */
    Employee findById(Long id);

    /** Hồ sơ đang dùng email này; null khi chưa ai dùng. */
    Employee findByEmail(String email);

    /** Trạng thái ACTIVE hoặc LOCKED theo keycloak_id; null khi chưa có hồ sơ. Bộ lọc nhân viên bị khóa gọi mỗi request. */
    String findStatusByKeycloakId(String keycloakId);

    /** Tên chi nhánh theo id (đọc bảng branch của module catalog); null khi không có chi nhánh này. */
    String findBranchNameById(Long branchId);

    /** Thêm hồ sơ do Admin tạo; id do database sinh được gán lại vào employee.getId(). */
    void insert(Employee employee);

    /** Sửa họ tên, email, chi nhánh. Không đổi status: việc đó dùng updateStatus. */
    void updateProfile(Employee employee);

    void updateStatus(@Param("id") Long id, @Param("status") String status);
}