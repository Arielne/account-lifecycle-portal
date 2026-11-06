// 5 trạng thái phiếu (docs/01-chuc-nang.md mục 2.3): mã backend trả về và nhãn tiếng Việt.

export type TicketStatus = 'PENDING_APPROVAL' | 'REJECTED' | 'APPROVED' | 'COMPLETED' | 'CANCELLED'

export const TICKET_STATUS_LABELS: Record<TicketStatus, string> = {
  PENDING_APPROVAL: 'Chờ duyệt',
  REJECTED: 'Bị từ chối',
  APPROVED: 'Đã duyệt',
  COMPLETED: 'Hoàn tất',
  CANCELLED: 'Đã hủy',
}