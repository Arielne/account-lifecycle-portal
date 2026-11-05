// Nút tạo phiếu theo trạng thái tài khoản (CN-10, bảng ở spec khách hàng tài khoản mục 5), tên loại phiếu
// và câu báo phiếu đang mở.
import type { AccountStatus } from '../../components/accountStatus.ts'
import type { OpenTicket, TicketType } from './accountApi.ts'

export interface AccountAction {
  label: string
  ticketType: TicketType
  /** Nút màu đỏ cho thao tác hạn chế tài khoản (Phong tỏa, Đóng). */
  danger: boolean
}

const CLOSE_ACTION: AccountAction = { label: 'Đóng tài khoản', ticketType: 'DONG_TAI_KHOAN', danger: true }

/** Chờ mở và Đã đóng không có nút. Gỡ phong tỏa và Kích hoạt lại cùng là phiếu GO_PHONG_TOA (mục 2.2). */
export const ACCOUNT_ACTIONS: Record<AccountStatus, AccountAction[]> = {
  PENDING_OPEN: [],
  ACTIVE: [{ label: 'Phong tỏa', ticketType: 'PHONG_TOA', danger: true }, CLOSE_ACTION],
  BLOCKED: [{ label: 'Gỡ phong tỏa', ticketType: 'GO_PHONG_TOA', danger: false }, CLOSE_ACTION],
  DORMANT: [{ label: 'Kích hoạt lại', ticketType: 'GO_PHONG_TOA', danger: false }, CLOSE_ACTION],
  CLOSED: [],
}

const TICKET_TYPE_LABELS: Record<TicketType, string> = {
  MO_TAI_KHOAN: 'Mở tài khoản',
  PHONG_TOA: 'Phong tỏa',
  GO_PHONG_TOA: 'Gỡ phong tỏa',
  DONG_TAI_KHOAN: 'Đóng tài khoản',
  MO_TK_THEO_LO: 'Mở tài khoản theo lô',
}

/** Tên loại phiếu. GO_PHONG_TOA hiện "Kích hoạt lại" khi tài khoản đang Ngủ đông (docs/01-chuc-nang.md mục 2.2). */
export function ticketTypeLabel(ticketType: TicketType, accountStatus: AccountStatus): string {
  if (ticketType === 'GO_PHONG_TOA' && accountStatus === 'DORMANT') {
    return 'Kích hoạt lại'
  }
  return TICKET_TYPE_LABELS[ticketType]
}

/** "đang chờ duyệt" hoặc "bị từ chối", ghép sau mã phiếu. */
export function openTicketState(ticket: OpenTicket): string {
  if (ticket.status === 'REJECTED') {
    return 'bị từ chối'
  }
  return 'đang chờ duyệt'
}

/** Đường dẫn sang Tạo phiếu với loại phiếu và số tài khoản điền sẵn (CN-11). */
export function createTicketUrl(ticketType: TicketType, accountNumber: string): string {
  return `/phieu/tao-moi?loai=${ticketType}&taiKhoan=${accountNumber}`
}