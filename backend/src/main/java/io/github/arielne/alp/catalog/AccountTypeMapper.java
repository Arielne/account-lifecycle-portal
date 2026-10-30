package io.github.arielne.alp.catalog;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper MyBatis cho bảng app.account_type (CN-20 Quản lý loại tài khoản). Câu SQL nằm trong AccountTypeMapper.xml.
 */
@Mapper
public interface AccountTypeMapper {

    /** Mọi loại tài khoản kèm số tài khoản đang dùng, xếp theo mã. */
    List<AccountType> findAll();

    /** Một loại tài khoản kèm số tài khoản đang dùng; null khi không có. */
    AccountType findById(Long id);

    /** Đã có loại tài khoản mang mã này chưa. */
    boolean existsByCode(String code);

    /** Thêm mới; id do database sinh được gán lại vào accountType.getId(). */
    void insert(AccountType accountType);

    /** Sửa tên, mô tả, số tháng chuyển Ngủ đông. Không sửa mã. */
    void update(AccountType accountType);

    void deleteById(Long id);
}