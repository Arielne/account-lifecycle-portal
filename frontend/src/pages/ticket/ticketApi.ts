// Gọi API phiếu (CN-11, CN-14, CN-15, CN-16, CN-17). Backend tự kiểm quyền (QT-07), lấy chi nhánh từ hồ sơ người
// đang đăng nhập (QT-04)
// và che CCCD trước khi trả về (QT-06).
import { httpClient } from '../../api/httpClient.ts'
import type { AccountStatus } from '../../components/accountStatus.ts'
import type { TicketStatus } from '../../components/ticketStatus.ts'
import type { TicketType } from '../../components/ticketType.ts'

/** Hồ sơ khách mới trong phiếu Mở tài khoản. Ngày dạng 2026-10-31; gender là Nam, Nữ hoặc null. */
export interface NewCustomerData {
  fullName: string
  dateOfBirth: string
  gender: string | null
  cccd: string
  idIssueDate: string
  idIssuePlace: string
  phone: string
  email: string
  address: string
}

/** Phần "data" của POST /api/tickets: không có chi nhánh hay người tạo, backend tự lấy (QT-04). */
export interface TicketCreateData {
  type: TicketType
  customerId: number | null
  newCustomer: NewCustomerData | null
  accountTypeId: number | null
  accountNumber: string | null
  reason: string
  confirmed: boolean
}

export interface TicketCreated {
  id: number
  code: string
}

/** Tài khoản có tạo được phiếu loại này không (bước 2); customerCccd đã che. */
export interface TicketEligibility {
  accountId: number
  accountNumber: string
  customerId: number
  customerName: string
  customerCccd: string
  accountTypeName: string
  branchName: string
  currentStatus: AccountStatus
  targetStatus: AccountStatus
  allowed: boolean
  /** Lý do không tạo được; null khi allowed. */
  message: string | null
}

/** NEW: chưa có hồ sơ; EXISTS: đã có; REUSABLE: hồ sơ chưa từng mở được tài khoản (D6); PENDING: đang chờ duyệt (D5). */
export type CustomerCccdState = 'NEW' | 'EXISTS' | 'REUSABLE' | 'PENDING'

export interface CustomerCheckResult {
  state: CustomerCccdState
  customerId: number | null
  /** Mã phiếu đang giữ hồ sơ khi state là PENDING. */
  ticketCode: string | null
}

export interface TicketAttachment {
  id: number
  originalName: string
  contentType: string
  sizeBytes: number
}

/** Mã hành động trong lịch sử xử lý phiếu. */
export type TicketAction = 'SUBMITTED' | 'RESUBMITTED' | 'REJECTED' | 'APPROVED' | 'CANCELLED' | 'COMPLETED'

export interface TicketHistoryEntry {
  action: TicketAction
  fromStatus: TicketStatus | null
  toStatus: TicketStatus | null
  /** null là Hệ thống. */
  actorName: string | null
  note: string | null
  createdAt: string
}

/** Chi tiết phiếu (CN-15); customerCccd đã che, không có điện thoại, email của khách. */
export interface TicketDetail {
  id: number
  code: string
  type: TicketType
  status: TicketStatus
  accountId: number | null
  accountNumber: string | null
  customerId: number | null
  customerName: string | null
  customerCccd: string | null
  accountTypeName: string | null
  fromStatus: AccountStatus | null
  toStatus: AccountStatus | null
  newCustomer: boolean
  createdByName: string
  branchName: string
  reviewedByName: string | null
  reason: string
  rejectReason: string | null
  submitCount: number
  createdAt: string
  lastSubmittedAt: string
  attachments: TicketAttachment[]
  history: TicketHistoryEntry[]
  /** Người đang xem được bấm Duyệt, Từ chối (backend tính, vẫn kiểm lại khi bấm). */
  canApprove: boolean
  canReject: boolean
  /** Nút hiện nhưng bị khóa vì lý do này (người tạo phiếu, QT-01); null khi không khóa. */
  blockedReason: string | null
}

/** Một dòng của màn Chờ duyệt (CN-14); waitingMinutes, overdue do backend tính từ lần gửi gần nhất. */
export interface PendingTicket {
  id: number
  code: string
  type: TicketType
  fromStatus: AccountStatus | null
  submitCount: number
  newCustomer: boolean
  lastSubmittedAt: string
  waitingMinutes: number
  overdue: boolean
  /** null với phiếu lô. */
  accountNumber: string | null
  customerName: string | null
}

/** Số phiếu đang chờ người xem duyệt (badge menu, D9) và số phiếu quá 24 giờ. */
export interface PendingReviewSummary {
  pendingCount: number
  overdueCount: number
}

export const TICKET_QUERY_KEY = 'ticket'
export const PENDING_REVIEW_QUERY_KEY = 'pendingReview'
export const PENDING_REVIEW_SUMMARY_QUERY_KEY = 'pendingReviewSummary'

/**
 * Gửi phiếu dạng multipart: phần "data" là JSON (Blob có kiểu application/json để Spring đọc thành
 * TicketCreateData), phần "files" lặp lại cho từng giấy tờ.
 */
export async function createTicket(data: TicketCreateData, files: File[]): Promise<TicketCreated> {
  const formData = new FormData()
  formData.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
  for (const file of files) {
    formData.append('files', file)
  }
  const response = await httpClient.post<TicketCreated>('/api/tickets', formData)
  return response.data
}

export async function checkEligibility(type: TicketType, accountNumber: string): Promise<TicketEligibility> {
  const response = await httpClient.get<TicketEligibility>('/api/tickets/eligibility', {
    params: { type, accountNumber },
  })
  return response.data
}

/** Gửi bằng POST để số CCCD nằm trong thân request, không lên URL (QT-05). */
export async function checkCustomer(cccd: string): Promise<CustomerCheckResult> {
  const response = await httpClient.post<CustomerCheckResult>('/api/tickets/customer-check', { cccd })
  return response.data
}

export async function fetchTicket(id: number): Promise<TicketDetail> {
  const response = await httpClient.get<TicketDetail>(`/api/tickets/${id}`)
  return response.data
}

/** Ô lọc rỗng thì không gửi tham số đó. */
export async function fetchPendingReview(code: string, type: TicketType | undefined): Promise<PendingTicket[]> {
  const response = await httpClient.get<PendingTicket[]>('/api/tickets/pending-review', {
    params: { code: code.trim() === '' ? undefined : code.trim(), type },
  })
  return response.data
}

export async function fetchPendingReviewSummary(): Promise<PendingReviewSummary> {
  const response = await httpClient.get<PendingReviewSummary>('/api/tickets/pending-review/summary')
  return response.data
}

export async function approveTicket(id: number): Promise<void> {
  await httpClient.post(`/api/tickets/${id}/approve`)
}

export async function rejectTicket(id: number, reason: string): Promise<void> {
  await httpClient.post(`/api/tickets/${id}/reject`, { reason })
}

/** Đường dẫn xem (mở tab mới) hoặc tải một giấy tờ; file chỉ đi qua backend (QT-10). */
export function attachmentUrl(ticketId: number, attachmentId: number, download: boolean): string {
  const url = `/api/tickets/${ticketId}/attachments/${attachmentId}`
  return download ? `${url}?download=true` : url
}