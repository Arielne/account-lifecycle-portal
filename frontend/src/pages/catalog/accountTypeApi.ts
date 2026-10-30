// Gọi API Loại tài khoản (CN-20). Mọi lời gọi đi qua httpClient dùng chung (tự gửi CSRF token, bắt 401).
import { httpClient } from '../../api/httpClient.ts'

/** Một loại tài khoản GET /api/account-types trả về. */
export interface AccountType {
  id: number
  code: string
  name: string
  description: string | null
  dormantAfterMonths: number
  /** Số tài khoản đang dùng loại này; khác 0 thì không xóa được. */
  accountCount: number
  updatedAt: string
}

/** Giá trị Form thêm, sửa. */
export interface AccountTypeFormValues {
  code: string
  name: string
  description?: string
  dormantAfterMonths: number
}

/** Khóa cache của TanStack Query cho danh sách; thêm, sửa, xóa xong thì làm mới theo khóa này. */
export const ACCOUNT_TYPES_QUERY_KEY = ['account-types']

const ACCOUNT_TYPES_URL = '/api/account-types'

export async function fetchAccountTypes(): Promise<AccountType[]> {
  const response = await httpClient.get<AccountType[]>(ACCOUNT_TYPES_URL)
  return response.data
}

export async function createAccountType(values: AccountTypeFormValues): Promise<AccountType> {
  const response = await httpClient.post<AccountType>(ACCOUNT_TYPES_URL, values)
  return response.data
}

/** Sửa không gửi mã: mã loại không sửa được sau khi tạo. */
export async function updateAccountType(id: number, values: AccountTypeFormValues): Promise<AccountType> {
  const body = {
    name: values.name,
    description: values.description,
    dormantAfterMonths: values.dormantAfterMonths,
  }
  const response = await httpClient.put<AccountType>(`${ACCOUNT_TYPES_URL}/${id}`, body)
  return response.data
}

export async function deleteAccountType(id: number): Promise<void> {
  await httpClient.delete(`${ACCOUNT_TYPES_URL}/${id}`)
}