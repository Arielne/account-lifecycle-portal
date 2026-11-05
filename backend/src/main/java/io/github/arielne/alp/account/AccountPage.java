package io.github.arielne.alp.account;

import java.util.List;

/** Một trang của màn Tài khoản (CN-09): tối đa 10 tài khoản, kèm tổng số tài khoản khớp điều kiện lọc. */
public record AccountPage(List<AccountItem> items, long total) {
}