package io.github.arielne.alp.notification;

import lombok.Getter;
import lombok.Setter;

/** Họ tên, email của nhân viên để gửi mail "Tài khoản đã được kích hoạt", đọc bằng NotificationMapper. */
@Getter
@Setter
public class EmployeeMailData {

    private String fullName;
    private String email;
}