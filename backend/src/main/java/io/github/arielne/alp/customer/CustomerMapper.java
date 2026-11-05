package io.github.arielne.alp.customer;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Mapper MyBatis đọc bảng app.customer (CN-07, CN-08). Câu SQL nằm trong CustomerMapper.xml.
 * Chỉ có hàm đọc: khách hàng chỉ được tạo qua phiếu (CN-11, CN-18), phần ghi làm cùng module phiếu.
 */
@Mapper
public interface CustomerMapper {

    /** Một trang khách khớp điều kiện tìm, hồ sơ mới tạo ở trên. */
    List<CustomerRow> findPage(CustomerFilter filter);

    /** Tổng số khách khớp điều kiện tìm (không tính phân trang). */
    long count(CustomerFilter filter);

    /** Trạng thái tài khoản của các khách có id trong danh sách, đọc chéo bảng account. */
    List<CustomerAccountRow> findAccountStatuses(@Param("customerIds") Long[] customerIds);

    /** Một khách theo id; không có thì trả null. */
    CustomerRow findById(Long id);
}