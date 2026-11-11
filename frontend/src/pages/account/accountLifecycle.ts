// Chữ của Vòng đời tài khoản (CN-10, spec phiếu mục 10.6): tên bước theo cặp trạng thái trước → sau, và người làm.
import type { AccountStatusHistoryItem } from './accountApi.ts'

/** Tên bước, ví dụ "Phong tỏa", "Tạo phiếu mở tài khoản", "Tự chuyển trạng thái". */
export function lifecycleStepName(item: AccountStatusHistoryItem): string {
  const { fromStatus, toStatus } = item
  if (fromStatus === null && toStatus === 'PENDING_OPEN') {
    return 'Tạo phiếu mở tài khoản'
  }
  if (fromStatus === null) {
    // Có mã phiếu là phiếu lô; không có mã phiếu là tài khoản mẫu (người dùng chốt 07/11/2026).
    return item.ticketId === null ? 'Mở tài khoản' : 'Mở tài khoản theo lô'
  }
  if (fromStatus === 'PENDING_OPEN') {
    return toStatus === 'ACTIVE' ? 'Mở tài khoản' : 'Hủy phiếu mở tài khoản'
  }
  if (toStatus === 'BLOCKED') {
    return 'Phong tỏa'
  }
  if (toStatus === 'DORMANT') {
    return 'Tự chuyển trạng thái'
  }
  if (toStatus === 'CLOSED') {
    return 'Đóng tài khoản'
  }
  return fromStatus === 'DORMANT' ? 'Kích hoạt lại' : 'Gỡ phong tỏa'
}

/** "Nguyễn Thị Lan tạo", "Trần Minh Khoa duyệt", "Nguyễn Thị Lan hủy" hoặc "Hệ thống". */
export function lifecycleActorText(item: AccountStatusHistoryItem): string {
  if (item.actorName === null) {
    return 'Hệ thống'
  }
  if (item.fromStatus === null) {
    return `${item.actorName} tạo`
  }
  if (item.fromStatus === 'PENDING_OPEN' && item.toStatus === 'CLOSED') {
    return `${item.actorName} hủy`
  }
  return `${item.actorName} duyệt`
}

/** Ghi chú của bước: lý do của phiếu, hoặc "Không phát sinh giao dịch trong <n> tháng" khi tự chuyển Ngủ đông. */
export function lifecycleNote(item: AccountStatusHistoryItem): string | null {
  if (item.toStatus === 'DORMANT' && item.ticketId === null) {
    return `Không phát sinh giao dịch trong ${item.dormantAfterMonths} tháng`
  }
  return item.reason === null ? null : `Lý do: ${item.reason}`
}