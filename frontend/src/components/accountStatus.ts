// 5 trạng thái tài khoản (docs/01-chuc-nang.md mục 2.1): mã backend trả về và nhãn tiếng Việt.
// Dùng chung cho màn Tài khoản, Khách hàng và sau này Tổng quan.

export type AccountStatus = 'PENDING_OPEN' | 'ACTIVE' | 'BLOCKED' | 'DORMANT' | 'CLOSED'

export const ACCOUNT_STATUSES: AccountStatus[] = ['PENDING_OPEN', 'ACTIVE', 'BLOCKED', 'DORMANT', 'CLOSED']

export const ACCOUNT_STATUS_LABELS: Record<AccountStatus, string> = {
  PENDING_OPEN: 'Chờ mở',
  ACTIVE: 'Hoạt động',
  BLOCKED: 'Phong tỏa',
  DORMANT: 'Ngủ đông',
  CLOSED: 'Đã đóng',
}

/** Màu Tag của từng trạng thái: màu có sẵn của AntD, không ghi đè class. */
export const ACCOUNT_STATUS_COLORS: Record<AccountStatus, string> = {
  PENDING_OPEN: 'blue',
  ACTIVE: 'green',
  BLOCKED: 'red',
  DORMANT: 'orange',
  CLOSED: 'default',
}

/** Chữ có phải 1 trong 5 mã không; dùng khi đọc mã từ URL, vì người dùng có thể sửa tay URL. */
export function isAccountStatus(value: string): value is AccountStatus {
  return ACCOUNT_STATUSES.some((status) => status === value)
}