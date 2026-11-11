package io.github.arielne.alp.ticket;

import java.time.LocalDate;
import java.util.List;

/**
 * JSON của GET /api/tickets/{id}/edit-data (CN-12): dữ liệu điền sẵn cho màn sửa và gửi lại. Chỉ người tạo, chỉ khi
 * phiếu Bị từ chối. newCustomer là hồ sơ khách đầy đủ (kể cả CCCD, điện thoại, email) và chỉ có khi phiếu Mở tài
 * khoản đã tạo hồ sơ khách mới (D2): QT-06 chỉ cấm gửi bản đầy đủ khi chỉ để xem; response có Cache-Control no-store.
 * customerId: hồ sơ của phiếu, để màn sửa biết kết quả kiểm CCCD "đang chờ duyệt" là của chính phiếu này.
 */
public record TicketEditData(Long id, String code, String type, String fromStatus, String toStatus,
                             String accountNumber, Long accountTypeId, Long customerId, CustomerData newCustomer,
                             String reason, String rejectReason, int submitCount,
                             List<TicketDetail.Attachment> attachments) {

    /** Hồ sơ khách đầy đủ, cùng tên trường với NewCustomerRequest để React điền thẳng vào Form. */
    public record CustomerData(String fullName, LocalDate dateOfBirth, String gender, String cccd,
                               LocalDate idIssueDate, String idIssuePlace, String phone, String email,
                               String address) {
    }
}