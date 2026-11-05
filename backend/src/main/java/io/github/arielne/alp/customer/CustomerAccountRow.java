package io.github.arielne.alp.customer;

import lombok.Getter;
import lombok.Setter;

/** Trạng thái một tài khoản của một khách, để vẽ Tag ở cột Tài khoản của màn Khách hàng (CN-07). */
@Getter
@Setter
public class CustomerAccountRow {
    private Long customerId;
    private String status;
}