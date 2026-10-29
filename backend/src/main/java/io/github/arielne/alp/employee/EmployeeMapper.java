package io.github.arielne.alp.employee;

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
}