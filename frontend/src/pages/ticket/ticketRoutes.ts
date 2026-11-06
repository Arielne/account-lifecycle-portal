// Đường dẫn và state dùng chung giữa màn Chờ duyệt và trang chi tiết phiếu (spec phiếu mục 10.3, 10.5).

export const PENDING_REVIEW_ROUTE = '/phieu/cho-duyet'

/** State của React Router khi màn Chờ duyệt mở trang chi tiết (màn hẹp): Breadcrumb ghi "Chờ duyệt", duyệt xong quay về. */
export interface TicketDetailState {
  from: 'cho-duyet'
}

export const FROM_PENDING_REVIEW_STATE: TicketDetailState = { from: 'cho-duyet' }