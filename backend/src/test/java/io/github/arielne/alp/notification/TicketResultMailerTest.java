package io.github.arielne.alp.notification;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Test nhanh cho nội dung mail kết quả phiếu (CN-24, spec phiếu mục 9 bước 3): đúng người nhận, tiêu đề, tên loại
 * phiếu và trạng thái bằng tiếng Việt, có mã phiếu. JavaMailSender là mock: CI không có Mailpit.
 */
class TicketResultMailerTest {

    private JavaMailSender mailSender;
    private TicketResultMailer mailer;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        mailer = new TicketResultMailer(mailSender);
    }

    @Test
    @DisplayName("Mail cho khách: gửi tới email của khách, có tên loại phiếu, số tài khoản, trạng thái mới, mã phiếu")
    void approvedMailToCustomer() {
        // Chuẩn bị
        TicketMailData ticket = blockTicket();

        // Chạy
        mailer.sendApprovedToCustomer(ticket);

        // Kiểm tra kết quả
        SimpleMailMessage mail = sentMail();
        assertEquals("no-reply@nhdemo.local", mail.getFrom());
        assertArrayEquals(new String[] {"khach.binh@nhdemo.local"}, mail.getTo());
        assertEquals("Kết quả yêu cầu Phong tỏa", mail.getSubject());
        assertTrue(mail.getText().startsWith("Kính gửi Quý khách Đỗ Thị Bình,"));
        assertTrue(mail.getText().contains("Tài khoản 1020000050 hiện ở trạng thái Phong tỏa."));
        assertTrue(mail.getText().contains("Mã yêu cầu: PYC-2026-0005"));
    }

    @Test
    @DisplayName("Mail duyệt cho người tạo: có mã phiếu, trạng thái trước → sau, báo khách đã nhận mail")
    void approvedMailToCreator() {
        // Chuẩn bị
        TicketMailData ticket = blockTicket();

        // Chạy
        mailer.sendApprovedToCreator(ticket);

        // Kiểm tra kết quả
        SimpleMailMessage mail = sentMail();
        assertArrayEquals(new String[] {"lan.nt@nhdemo.local"}, mail.getTo());
        assertEquals("Phiếu PYC-2026-0005 đã được duyệt", mail.getSubject());
        assertTrue(mail.getText().contains("Tài khoản đã chuyển Hoạt động → Phong tỏa."));
        assertTrue(mail.getText().contains("Hệ thống đã gửi mail báo kết quả cho khách hàng."));
    }

    @Test
    @DisplayName("Mail từ chối cho người tạo: có lý do từ chối, nhắc sửa và gửi lại hoặc hủy phiếu")
    void rejectedMailToCreator() {
        // Chuẩn bị
        TicketMailData ticket = blockTicket();
        ticket.setRejectReason("Thiếu ảnh CCCD mặt sau");

        // Chạy
        mailer.sendRejectedToCreator(ticket);

        // Kiểm tra kết quả
        SimpleMailMessage mail = sentMail();
        assertArrayEquals(new String[] {"lan.nt@nhdemo.local"}, mail.getTo());
        assertEquals("Phiếu PYC-2026-0005 bị từ chối", mail.getSubject());
        assertTrue(mail.getText().contains("bị từ chối với lý do:\nThiếu ảnh CCCD mặt sau"));
        assertTrue(mail.getText().contains("Vui lòng sửa và gửi lại phiếu, hoặc hủy phiếu trên Admin Portal."));
    }

    @Test
    @DisplayName("Gỡ phong tỏa cho tài khoản Ngủ đông: mail gọi là Kích hoạt lại, như trên màn hình")
    void reactivationUsesItsOwnName() {
        // Chuẩn bị
        TicketMailData ticket = blockTicket();
        ticket.setType("GO_PHONG_TOA");
        ticket.setFromStatus("DORMANT");
        ticket.setToStatus("ACTIVE");

        // Chạy
        mailer.sendApprovedToCustomer(ticket);

        // Kiểm tra kết quả
        SimpleMailMessage mail = sentMail();
        assertEquals("Kết quả yêu cầu Kích hoạt lại", mail.getSubject());
        assertTrue(mail.getText().contains("hiện ở trạng thái Hoạt động."));
    }

    private SimpleMailMessage sentMail() {
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        return mail.getValue();
    }

    private TicketMailData blockTicket() {
        TicketMailData ticket = new TicketMailData();
        ticket.setId(5L);
        ticket.setCode("PYC-2026-0005");
        ticket.setType("PHONG_TOA");
        ticket.setStatus("APPROVED");
        ticket.setFromStatus("ACTIVE");
        ticket.setToStatus("BLOCKED");
        ticket.setAccountNumber("1020000050");
        ticket.setCustomerName("Đỗ Thị Bình");
        ticket.setCustomerEmail("khach.binh@nhdemo.local");
        ticket.setCreatorName("Nguyễn Thị Lan");
        ticket.setCreatorEmail("lan.nt@nhdemo.local");
        return ticket;
    }
}