package io.github.arielne.alp.ticket;

import lombok.Getter;
import lombok.Setter;

/**
 * Phiếu cho bước duyệt, từ chối (CN-16, CN-17) và cho các service task của Camunda: đủ để kiểm QT-01, QT-04, QT-11,
 * đổi trạng thái tài khoản và ghi audit. Đọc chéo account (số tài khoản), employee (keycloak_id người tạo), branch.
 * Không có dữ liệu cá nhân của khách.
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
    private Long branchId;
    private String branchName;
    private Long createdBy;
    private String creatorKeycloakId;
    private Long reviewedBy;
    private String processInstanceId;
    private String fromStatus;
    private String toStatus;
}