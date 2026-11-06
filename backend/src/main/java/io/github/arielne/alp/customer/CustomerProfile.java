package io.github.arielne.alp.customer;

import java.time.LocalDate;

/**
 * Hồ sơ khách mới nhập ở phiếu Mở tài khoản (CN-11). Đi từ ticket qua account tới customer, nơi duy nhất ghi
 * bảng app.customer. Dữ liệu đầy đủ (CCCD, điện thoại, email) nên không bao giờ trả về cho React để xem (QT-06).
 * gender: Nam, Nữ hoặc null.
 */
public record CustomerProfile(String fullName, LocalDate dateOfBirth, String gender, String cccd,
                              LocalDate idIssueDate, String idIssuePlace, String phone, String email,
                              String address) {
}