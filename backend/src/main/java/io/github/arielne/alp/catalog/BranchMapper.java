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

    /** Lấy mọi chi nhánh, xếp theo mã. */
    List<Branch> findAll();
}