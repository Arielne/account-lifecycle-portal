package io.github.arielne.alp.customer;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Mapper MyBatis của bảng app.customer (CN-07, CN-08, CN-11). Câu SQL nằm trong CustomerMapper.xml.
 * Khách hàng chỉ được tạo qua phiếu Mở tài khoản: insert và updateProfile chỉ chạy khi account gọi CustomerService.
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

    /** Thêm hồ sơ mới; id do database sinh được gán lại vào record. */
    int insert(CustomerRecord record);

    /** Ghi đè toàn bộ hồ sơ theo id (hồ sơ chưa từng mở được tài khoản, D6); giữ nguyên id và ngày tạo. */
    int updateProfile(CustomerRecord record);
}