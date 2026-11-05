package io.github.arielne.alp.customer;

import java.util.List;

/** Một trang của màn Khách hàng (CN-07): tối đa 10 khách, kèm tổng số khách khớp điều kiện tìm. */
public record CustomerPage(List<CustomerListItem> items, long total) {
}