package io.github.arielne.alp.notification;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Test nhanh cho EmployeeEventListener và mail "Tài khoản đã được kích hoạt" (CN-05, CN-22, spec phiếu mục 9.1).
 * NotificationMapper và JavaMailSender là mock; AccountActivationMailer, OutboxMessageReader chạy thật.
 */
class EmployeeEventListenerTest {

    private static final String ACTIVATED_MESSAGE = """
            {"eventType": "EMPLOYEE_ACTIVATED", "payload": {"employeeId": 7, "roleLabels": "Giao dịch viên"}}""";

    private NotificationMapper notificationMapper;
    private JavaMailSender mailSender;
    private EmployeeEventListener listener;

    @BeforeEach
    void setUp() {
        notificationMapper = mock(NotificationMapper.class);
        mailSender = mock(JavaMailSender.class);
        listener = new EmployeeEventListener(new OutboxMessageReader(new ObjectMapper()), notificationMapper,
                new AccountActivationMailer(mailSender));
    }

    @Test
    @DisplayName("Nhân viên được gán role đầu tiên: mail tới email trong hồ sơ, có họ tên và tên role")
    void activatedEventSendsMailToEmployee() {
        // Chuẩn bị
        when(notificationMapper.findEmployeeMail(7L)).thenReturn(employee());

        // Chạy
        listener.onEmployeeEvent(ACTIVATED_MESSAGE);

        // Kiểm tra kết quả
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertEquals("no-reply@nhdemo.local", mail.getValue().getFrom());
        assertArrayEquals(new String[] {"thu.dangky@nhdemo.local"}, mail.getValue().getTo());
        assertEquals("Tài khoản đã được kích hoạt", mail.getValue().getSubject());
        assertTrue(mail.getValue().getText().startsWith("Chào Bắc Đẩu Thu,"));
        assertTrue(mail.getValue().getText().contains("đã được cấp quyền: Giao dịch viên."));
    }

    @Test
    @DisplayName("Gửi mail lỗi (Mailpit tắt): ném lỗi ra để Spring Kafka thử lại, mail không bị mất")
    void mailErrorIsThrownSoKafkaRetries() {
        // Chuẩn bị
        when(notificationMapper.findEmployeeMail(7L)).thenReturn(employee());
        doThrow(new MailSendException("Mailpit tắt")).when(mailSender).send(any(SimpleMailMessage.class));

        // Chạy và kiểm tra kết quả
        assertThrows(MailSendException.class, () -> listener.onEmployeeEvent(ACTIVATED_MESSAGE));
    }

    @Test
    @DisplayName("Sự kiện trỏ tới nhân viên không có, hoặc loại sự kiện lạ: bỏ qua, không gửi mail")
    void missingEmployeeOrUnknownEventIsSkipped() {
        // Chuẩn bị
        when(notificationMapper.findEmployeeMail(7L)).thenReturn(null);

        // Chạy
        listener.onEmployeeEvent(ACTIVATED_MESSAGE);
        listener.onEmployeeEvent("{\"eventType\": \"EMPLOYEE_LOCKED\", \"payload\": {\"employeeId\": 8}}");

        // Kiểm tra kết quả: loại sự kiện lạ không đi tới bước đọc hồ sơ
        verify(notificationMapper).findEmployeeMail(7L);
        verify(notificationMapper, never()).findEmployeeMail(8L);
        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("Message không phải JSON thì bỏ qua, không ném lỗi")
    void brokenMessageIsSkipped() {
        // Chạy
        listener.onEmployeeEvent("{không phải JSON");

        // Kiểm tra kết quả
        verify(notificationMapper, never()).findEmployeeMail(anyLong());
        verifyNoInteractions(mailSender);
    }

    private EmployeeMailData employee() {
        EmployeeMailData employee = new EmployeeMailData();
        employee.setFullName("Bắc Đẩu Thu");
        employee.setEmail("thu.dangky@nhdemo.local");
        return employee;
    }
}