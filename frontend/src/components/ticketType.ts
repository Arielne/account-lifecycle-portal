// 5 loại phiếu (docs/01-chuc-nang.md mục 2.2): mã, tên, giấy tờ cần có, trạng thái tài khoản trước → sau khi duyệt.
// Dùng chung cho màn tài khoản, khách hàng và các màn phiếu.
import type { AccountStatus } from './accountStatus.ts'

export type TicketType = 'MO_TAI_KHOAN' | 'PHONG_TOA' | 'GO_PHONG_TOA' | 'DONG_TAI_KHOAN' | 'MO_TK_THEO_LO'

/** 4 loại phiếu lẻ tạo ở màn Tạo phiếu; phiếu lô tạo ở màn Import. */
export const SINGLE_TICKET_TYPES: TicketType[] = ['MO_TAI_KHOAN', 'PHONG_TOA', 'GO_PHONG_TOA', 'DONG_TAI_KHOAN']

const TICKET_TYPE_LABELS: Record<TicketType, string> = {
  MO_TAI_KHOAN: 'Mở tài khoản',
  PHONG_TOA: 'Phong tỏa',
  GO_PHONG_TOA: 'Gỡ phong tỏa',
  DONG_TAI_KHOAN: 'Đóng tài khoản',
  MO_TK_THEO_LO: 'Mở tài khoản theo lô',
}

/** Giấy tờ cần có, hiện ở bước Lý do & giấy tờ ("Cần có: ..."). */
export const REQUIRED_DOCUMENTS: Record<TicketType, string> = {
  MO_TAI_KHOAN: 'CCCD 2 mặt, đơn đề nghị mở tài khoản có chữ ký',
  PHONG_TOA: 'Đơn đề nghị phong tỏa hoặc văn bản yêu cầu',
  GO_PHONG_TOA: 'Đơn đề nghị gỡ phong tỏa, CCCD',
  DONG_TAI_KHOAN: 'Đơn đề nghị đóng tài khoản có chữ ký',
  MO_TK_THEO_LO: 'Không cần, dữ liệu nằm trong file',
}

/**
 * Tên loại phiếu. GO_PHONG_TOA hiện "Kích hoạt lại" khi tài khoản đang Ngủ đông (mục 2.2). Ở chi tiết phiếu truyền
 * trạng thái lúc tạo phiếu (fromStatus); ở chi tiết tài khoản truyền trạng thái hiện tại.
 */
export function ticketTypeLabel(ticketType: TicketType, accountStatus?: AccountStatus | null): string {
  if (ticketType === 'GO_PHONG_TOA' && accountStatus === 'DORMANT') {
    return 'Kích hoạt lại'
  }
  return TICKET_TYPE_LABELS[ticketType]
}

/** Chữ có phải 1 trong 4 loại phiếu lẻ không; dùng khi đọc ?loai= trên URL (người dùng có thể sửa tay). */
export function isSingleTicketType(value: string | null): value is TicketType {
  return SINGLE_TICKET_TYPES.some((type) => type === value)
}