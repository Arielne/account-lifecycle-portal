package io.github.arielne.alp.notification;

/** Phần chung của mọi mail app gửi (CN-05, CN-24): địa chỉ người gửi và dòng ký tên cuối mail. */
final class NotificationMail {

    static final String FROM_ADDRESS = "no-reply@nhdemo.local";
    static final String SIGNATURE = "Ngân hàng Demo";

    private NotificationMail() {
    }
}