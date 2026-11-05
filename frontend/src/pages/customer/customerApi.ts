// Gọi API khách hàng (CN-07, CN-08): chỉ đọc. Backend tự kiểm quyền (QT-07) và che CCCD, điện thoại, email (QT-06).
import { httpClient } from '../../api/httpClient.ts'
import type { AccountStatus } from '../../components/accountStatus.ts'

/** Tìm theo họ tên (gần đúng, không dấu cũng được) hoặc đủ 12 số CCCD (khớp tuyệt đối). */
export type CustomerSearchBy = 'NAME' | 'CCCD'

/** Một dòng màn Khách hàng; cccd, phone đã che. Ngày dạng 2026-10-31. */
export interface CustomerListItem {
  id: number
  fullName: string
  cccd: string
  dateOfBirth: string
  phone: string
  branchName: string
  /** Ngày tạo hồ sơ theo giờ Việt Nam. */
  createdDate: string
  /** Trạng thái từng tài khoản của khách, mỗi cái một Tag. */
  accountStatuses: AccountStatus[]
}

export interface CustomerPage {
  items: CustomerListItem[]
  total: number
}

/** Chi tiết một khách; cccd, phone, email đã che. Không có địa chỉ (theo bản thiết kế). */
export interface CustomerDetail {
  id: number
  fullName: string
  dateOfBirth: string
  /** null khi khách không khai giới tính. */
  gender: string | null
  cccd: string
  idIssueDate: string
  idIssuePlace: string
  phone: string
  email: string
  branchName: string
  createdDate: string
}

export const CUSTOMERS_QUERY_KEY = 'customers'
export const CUSTOMER_QUERY_KEY = 'customer'

/** Một trang 10 khách. Gửi bằng POST để số CCCD nằm trong thân request, không nằm trên URL. */
export async function searchCustomers(searchBy: CustomerSearchBy, keyword: string, page: number): Promise<CustomerPage> {
  const response = await httpClient.post<CustomerPage>('/api/customers/search', { searchBy, keyword, page })
  return response.data
}

export async function fetchCustomer(id: number): Promise<CustomerDetail> {
  const response = await httpClient.get<CustomerDetail>(`/api/customers/${id}`)
  return response.data
}