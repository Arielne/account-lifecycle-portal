// Gọi API Chi nhánh (CN-21). Mọi lời gọi đi qua httpClient dùng chung (tự gửi CSRF token, bắt 401).
import { httpClient } from '../../api/httpClient.ts'

/** Một chi nhánh GET /api/branches trả về. Điện thoại chỉ gồm chữ số. */
export interface Branch {
  id: number
  code: string
  name: string
  address: string
  phone: string
  employeeCount: number
  accountCount: number
}

/** Giá trị Form thêm, sửa; mã chi nhánh sửa được. */
export interface BranchFormValues {
  code: string
  name: string
  address: string
  phone: string
}

/** Khóa cache của TanStack Query cho danh sách; thêm, sửa, xóa xong thì làm mới theo khóa này. */
export const BRANCHES_QUERY_KEY = ['branches']

const BRANCHES_URL = '/api/branches'

export async function fetchBranches(): Promise<Branch[]> {
  const response = await httpClient.get<Branch[]>(BRANCHES_URL)
  return response.data
}

/** Mã chi nhánh còn trống không (gọi khi người dùng rời ô Mã). */
export async function isBranchCodeAvailable(code: string): Promise<boolean> {
  const response = await httpClient.get<{ available: boolean }>(`${BRANCHES_URL}/code-available`, {
    params: { code },
  })
  return response.data.available
}

export async function createBranch(values: BranchFormValues): Promise<Branch> {
  const response = await httpClient.post<Branch>(BRANCHES_URL, values)
  return response.data
}

export async function updateBranch(id: number, values: BranchFormValues): Promise<Branch> {
  const response = await httpClient.put<Branch>(`${BRANCHES_URL}/${id}`, values)
  return response.data
}

export async function deleteBranch(id: number): Promise<void> {
  await httpClient.delete(`${BRANCHES_URL}/${id}`)
}