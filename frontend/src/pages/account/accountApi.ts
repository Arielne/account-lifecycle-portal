// Gọi API tài khoản (CN-09, CN-10): chỉ đọc, kể cả Vòng đời tài khoản. Backend tự kiểm quyền (QT-07) và che CCCD
// trước khi trả về (QT-06).
import { httpClient } from '../../api/httpClient.ts'
import type { AccountStatus } from '../../components/accountStatus.ts'
import type { TicketType } from '../../components/ticketType.ts'

/** Phiếu đang Chờ duyệt hoặc Bị từ chối của tài khoản (QT-03). */
export interface OpenTicket {
  code: string
  type: TicketType
  status: 'PENDING_APPROVAL' | 'REJECTED'
}

/** Một tài khoản; customerCccd đã che. Ngày dạng 2026-10-31. */
export interface AccountItem {
  id: number
  accountNumber: string
  customerId: number
  customerName: string
  customerCccd: string
  accountTypeName: string
  branchName: string
  status: AccountStatus
  /** null khi tài khoản còn Chờ mở. */
  openedAt: string | null
  /** null khi tài khoản chưa đóng. */
  closedAt: string | null
  lastTransactionAt: string
  /** null khi không có phiếu đang mở. */
  openTicket: OpenTicket | null
  /** Ô "Mở bởi", "Duyệt mở bởi": chỉ có ở chi tiết tài khoản; null khi tài khoản không mở qua phiếu (tài khoản mẫu). */
  openedByName: string | null
  openApprovedByName: string | null
}

/** Một mục của Vòng đời tài khoản (CN-10); fromStatus null: vừa được tạo; actorName null: Hệ thống. */
export interface AccountStatusHistoryItem {
  id: number
  changedAt: string
  fromStatus: AccountStatus | null
  toStatus: AccountStatus
  actorName: string | null
  ticketId: number | null
  ticketCode: string | null
  ticketType: TicketType | null
  ticketFromStatus: AccountStatus | null
  reason: string | null
  dormantAfterMonths: number
}

export interface AccountPage {
  items: AccountItem[]
  total: number
}

/** Điều kiện lọc trên màn Tài khoản; để trống là không lọc. */
export interface AccountFilters {
  /** 1–10 chữ số, khớp từ đầu số tài khoản. */
  accountNumber?: string
  statuses: AccountStatus[]
  accountTypeId?: number
  branchId?: number
  /** Chỉ tài khoản của khách này (tab Tài khoản ở chi tiết khách hàng). */
  customerId?: number
}

export const ACCOUNTS_QUERY_KEY = 'accounts'
export const ACCOUNT_QUERY_KEY = 'account'

/** Một trang 10 tài khoản. Nhiều trạng thái gửi thành một chuỗi cách nhau dấu phẩy; Spring tự tách thành danh sách. */
export async function fetchAccounts(filters: AccountFilters, page: number): Promise<AccountPage> {
  const params = {
    accountNumber: filters.accountNumber,
    statuses: filters.statuses.length > 0 ? filters.statuses.join(',') : undefined,
    accountTypeId: filters.accountTypeId,
    branchId: filters.branchId,
    customerId: filters.customerId,
    page,
  }
  const response = await httpClient.get<AccountPage>('/api/accounts', { params })
  return response.data
}

export async function fetchAccount(id: number): Promise<AccountItem> {
  const response = await httpClient.get<AccountItem>(`/api/accounts/${id}`)
  return response.data
}

export const ACCOUNT_STATUS_HISTORY_QUERY_KEY = 'accountStatusHistory'

/** Vòng đời tài khoản, mới nhất ở trên. */
export async function fetchStatusHistory(id: number): Promise<AccountStatusHistoryItem[]> {
  const response = await httpClient.get<AccountStatusHistoryItem[]>(`/api/accounts/${id}/status-history`)
  return response.data
}