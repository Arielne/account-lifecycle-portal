package io.github.arielne.alp.catalog;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper MyBatis cho bảng app.branch (CN-21 Quản lý chi nhánh).
 * Chỉ khai báo hàm; câu SQL của từng hàm nằm trong BranchMapper.xml
 * (src/main/resources, cùng đường dẫn package), nối với nhau qua namespace và id.
 * @Mapper: MyBatis tự tạo class cài đặt interface này và đăng ký thành bean của Spring.
 */
@Mapper
public interface BranchMapper {

    /** Lấy mọi chi nhánh kèm số nhân viên và số tài khoản, xếp theo mã. */
    List<Branch> findAll();

    /** Một chi nhánh kèm số nhân viên và số tài khoản; null khi không có. */
    Branch findById(Long id);

    /** Đã có chi nhánh mang mã này chưa. */
    boolean existsByCode(String code);

    /** Những gì còn thuộc chi nhánh (nhân viên, khách hàng, tài khoản, phiếu), để chặn xóa (QT-08). */
    BranchUsage findUsage(Long id);

    /** Thêm mới; id do database sinh được gán lại vào branch.getId(). */
    void insert(Branch branch);

    /** Sửa mã, tên, địa chỉ, điện thoại. */
    void update(Branch branch);

    void deleteById(Long id);
}