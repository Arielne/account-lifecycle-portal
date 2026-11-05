package io.github.arielne.alp.customer;

/** Tìm khách hàng theo gì (CN-07): họ tên gần đúng, không dấu cũng được; hoặc đủ 12 số CCCD, khớp tuyệt đối. */
public enum CustomerSearchBy {
    NAME,
    CCCD
}