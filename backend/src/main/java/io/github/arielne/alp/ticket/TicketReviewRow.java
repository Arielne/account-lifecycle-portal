package io.github.arielne.alp.ticket;

import lombok.Getter;
import lombok.Setter;

/**
 * Phiếu cho bước duyệt, từ chối (CN-16, CN-17), sửa và gửi lại, hủy (CN-12, CN-13) và cho các service task của
 * Camunda: đủ để kiểm QT-01, QT-04, QT-11, D12, đổi trạng thái tài khoản và ghi audit. Đọc chéo account (số, trạng
 * thái hiện tại, khách), employee (người tạo), branch. Không có dữ liệu cá nhân của khách.
 */
@Getter
@Setter
public class TicketReviewRow {

    private Long id;
    private String code;
    private String type;
    private String status;
    private Long accountId;
    private String accountNumber;
    private Long customerId;
    /** Trạng thái hiện tại của tài khoản (fromStatus là trạng thái lúc tạo phiếu). */
    private String accountStatus;
    private Long branchId;
    private String branchName;
    private Long createdBy;
    private String creatorKeycloakId;
    private String creatorName;
    /** ACTIVE hoặc LOCKED (bảng employee). */
    private String creatorStatus;
    private Long reviewedBy;
    private String processInstanceId;
    private String fromStatus;
    private String toStatus;
    private boolean newCustomer;
}