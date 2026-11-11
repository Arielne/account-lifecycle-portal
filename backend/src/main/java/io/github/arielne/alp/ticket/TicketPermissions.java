package io.github.arielne.alp.ticket;

import io.github.arielne.alp.auth.CurrentEmployee;

/**
 * Luật ai được sửa, hủy phiếu (spec phiếu mục 7.3, 7.4, D12), dùng chung cho API (TicketRevisionService) và cờ nút ở
 * chi tiết phiếu (TicketQueryService) để hai nơi không lệch nhau. Role MAKER đã kiểm ở SecurityConfig (API) hoặc ở
 * nơi gọi (cờ nút).
 */
final class TicketPermissions {

    private static final String LOCKED = "LOCKED";

    private TicketPermissions() {
    }

    /** Người xem là người tạo phiếu: so keycloak_id, không so tên hay role. */
    static boolean isCreator(String creatorKeycloakId, CurrentEmployee viewer) {
        return viewer.keycloakId().equals(creatorKeycloakId);
    }

    /** Sửa và gửi lại: chỉ người tạo, chỉ khi phiếu Bị từ chối. */
    static boolean canEdit(String status, String creatorKeycloakId, CurrentEmployee viewer) {
        return TicketStatus.REJECTED.name().equals(status) && isCreator(creatorKeycloakId, viewer);
    }

    /**
     * Hủy thay (D12): người tạo đã bị khóa thì Giao dịch viên cùng chi nhánh với phiếu được hủy, để phiếu không kẹt
     * mãi ở trạng thái mở (QT-03).
     */
    static boolean canCancelOnBehalf(String creatorStatus, Long ticketBranchId, CurrentEmployee viewer) {
        return LOCKED.equals(creatorStatus) && ticketBranchId.equals(viewer.branchId());
    }

    /** Hủy: phiếu Chờ duyệt hoặc Bị từ chối, người tạo hoặc người hủy thay hợp lệ. */
    static boolean canCancel(String status, String creatorKeycloakId, String creatorStatus, Long ticketBranchId,
                             CurrentEmployee viewer) {
        boolean open = TicketStatus.PENDING_APPROVAL.name().equals(status) || TicketStatus.REJECTED.name().equals(status);
        if (!open) {
            return false;
        }
        return isCreator(creatorKeycloakId, viewer) || canCancelOnBehalf(creatorStatus, ticketBranchId, viewer);
    }
}