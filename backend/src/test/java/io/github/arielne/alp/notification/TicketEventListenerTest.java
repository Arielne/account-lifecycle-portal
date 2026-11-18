package io.github.arielne.alp.notification;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.arielne.alp.common.BusinessException;
import io.github.arielne.alp.ticket.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;

/**
 * Test nhanh cho TicketEventListener (spec phiếu mục 9 bước 3, mục 9.1): duyệt thì gửi 2 mail rồi mới Hoàn tất phiếu,
 * từ chối thì gửi 1 mail, sự kiện tới lần hai không gửi lại, gửi lỗi thì ném lỗi để Spring Kafka thử lại.
 * NotificationMapper, TicketResultMailer, TicketService là mock; OutboxMessageReader chạy thật, đọc đúng dạng message.
 */
class TicketEventListenerTest {

    private static final String APPROVED_MESSAGE = """
            {"eventType": "TICKET_APPROVED", "payload": {"ticketId": 5, "ticketCode": "PYC-2026-0005"}}""";
    private static final String REJECTED_MESSAGE = """
            {"eventType": "TICKET_REJECTED", "payload": {"ticketId": 5, "ticketCode": "PYC-2026-0005"}}""";

    private NotificationMapper notificationMapper;
    private TicketResultMailer ticketResultMailer;
    private TicketService ticketService;
    private TicketEventListener listener;

    @BeforeEach
    void setUp() {
        notificationMapper = mock(NotificationMapper.class);
        ticketResultMailer = mock(TicketResultMailer.class);
        ticketService = mock(TicketService.class);
        listener = new TicketEventListener(new OutboxMessageReader(new ObjectMapper()), notificationMapper,
                ticketResultMailer, ticketService);
    }

    @Test
    @DisplayName("Phiếu được duyệt: gửi mail cho khách, cho người tạo, rồi mới chuyển phiếu sang Hoàn tất")
    void approvedEventSendsTwoMailsThenCompletesTicket() {
        // Chuẩn bị
        TicketMailData ticket = ticket("APPROVED");
        when(notificationMapper.findTicketMail(5L)).thenReturn(ticket);

        // Chạy
        listener.onTicketEvent(APPROVED_MESSAGE);

        // Kiểm tra kết quả
        InOrder inOrder = inOrder(ticketResultMailer, ticketService);
        inOrder.verify(ticketResultMailer).sendApprovedToCustomer(ticket);
        inOrder.verify(ticketResultMailer).sendApprovedToCreator(ticket);
        inOrder.verify(ticketService).markMailSent(5L);
    }

    @Test
    @DisplayName("Sự kiện duyệt tới lần hai, phiếu đã Hoàn tất: không gửi mail lần nữa")
    void approvedEventForCompletedTicketIsSkipped() {
        // Chuẩn bị
        when(notificationMapper.findTicketMail(5L)).thenReturn(ticket("COMPLETED"));

        // Chạy
        listener.onTicketEvent(APPROVED_MESSAGE);

        // Kiểm tra kết quả
        verifyNoInteractions(ticketResultMailer, ticketService);
    }

    @Test
    @DisplayName("Phiếu vừa đổi trạng thái nên Hoàn tất báo 409: bỏ qua, không ném lỗi để Kafka thử lại")
    void conflictWhenCompletingIsIgnored() {
        // Chuẩn bị
        when(notificationMapper.findTicketMail(5L)).thenReturn(ticket("APPROVED"));
        doThrow(new BusinessException(HttpStatus.CONFLICT, "Phiếu PYC-2026-0005 không còn Đã duyệt"))
                .when(ticketService).markMailSent(5L);

        // Chạy
        listener.onTicketEvent(APPROVED_MESSAGE);

        // Kiểm tra kết quả: 2 mail đã gửi, lỗi không đi ra ngoài
        verify(ticketResultMailer).sendApprovedToCustomer(any());
        verify(ticketResultMailer).sendApprovedToCreator(any());
    }

    @Test
    @DisplayName("Gửi mail lỗi (Mailpit tắt): ném lỗi ra để Spring Kafka thử lại, phiếu chưa sang Hoàn tất")
    void mailErrorIsThrownSoKafkaRetries() {
        // Chuẩn bị
        TicketMailData ticket = ticket("APPROVED");
        when(notificationMapper.findTicketMail(5L)).thenReturn(ticket);
        doThrow(new MailSendException("Mailpit tắt")).when(ticketResultMailer).sendApprovedToCustomer(ticket);

        // Chạy
        assertThrows(MailSendException.class, () -> listener.onTicketEvent(APPROVED_MESSAGE));

        // Kiểm tra kết quả
        verify(ticketService, never()).markMailSent(anyLong());
    }

    @Test
    @DisplayName("Phiếu bị từ chối: chỉ gửi mail cho người tạo, phiếu giữ Bị từ chối")
    void rejectedEventSendsMailToCreator() {
        // Chuẩn bị
        TicketMailData ticket = ticket("REJECTED");
        when(notificationMapper.findTicketMail(5L)).thenReturn(ticket);

        // Chạy
        listener.onTicketEvent(REJECTED_MESSAGE);

        // Kiểm tra kết quả
        verify(ticketResultMailer).sendRejectedToCreator(ticket);
        verify(ticketResultMailer, never()).sendApprovedToCustomer(any());
        verifyNoInteractions(ticketService);
    }

    @Test
    @DisplayName("Phiếu lô (chưa gắn tài khoản) thì bỏ qua: mail của phiếu lô làm ở phần import theo lô")
    void batchTicketIsSkipped() {
        // Chuẩn bị
        TicketMailData batchTicket = ticket("APPROVED");
        batchTicket.setAccountNumber(null);
        when(notificationMapper.findTicketMail(5L)).thenReturn(batchTicket);

        // Chạy
        listener.onTicketEvent(APPROVED_MESSAGE);

        // Kiểm tra kết quả
        verifyNoInteractions(ticketResultMailer, ticketService);
    }

    @Test
    @DisplayName("Message không phải JSON đúng dạng, hoặc trỏ tới phiếu không có: bỏ qua, không ném lỗi")
    void brokenMessageOrMissingTicketIsSkipped() {
        // Chuẩn bị
        when(notificationMapper.findTicketMail(5L)).thenReturn(null);

        // Chạy
        listener.onTicketEvent("không phải JSON");
        listener.onTicketEvent("{\"eventType\": \"TICKET_APPROVED\"}");
        listener.onTicketEvent(APPROVED_MESSAGE);

        // Kiểm tra kết quả: chỉ message đúng dạng mới đi tới bước đọc phiếu
        verify(notificationMapper).findTicketMail(5L);
        verifyNoInteractions(ticketResultMailer, ticketService);
    }

    private TicketMailData ticket(String status) {
        TicketMailData ticket = new TicketMailData();
        ticket.setId(5L);
        ticket.setCode("PYC-2026-0005");
        ticket.setType("PHONG_TOA");
        ticket.setStatus(status);
        ticket.setFromStatus("ACTIVE");
        ticket.setToStatus("BLOCKED");
        ticket.setAccountNumber("1020000050");
        return ticket;
    }
}