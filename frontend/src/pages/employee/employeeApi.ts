// Gọi API Nhân viên (CN-22). Chỉ Admin gọi được; backend vẫn tự kiểm quyền (QT-07).
import { httpClient } from '../../api/httpClient.ts'
import type { AppRole } from '../auth/currentUser.ts'

/** Một nhân viên GET /api/employees trả về: hồ sơ trong database ghép với role lấy từ KeyCloak. */
export interface Employee {
  id: number
  keycloakId: string
  fullName: string
  email: string
  branchId: number | null
  branchName: string | null
  /** Rỗng nghĩa là Chờ cấp quyền. */
  roles: AppRole[]
  status: 'ACTIVE' | 'LOCKED'
  createdAt: string
}

/** Giá trị của Drawer thêm, sửa, gán role; mỗi chế độ dùng một phần các ô. */
export interface EmployeeFormValues {
  fullName: string
  email: string
  branchId?: number | null
  roles: AppRole[]
}

/** Dữ liệu sửa hồ sơ, gán chi nhánh (PUT /api/employees/{id}). */
export interface EmployeeProfile {
  fullName: string
  email: string
  branchId: number | null
}

/** Khóa cache của TanStack Query cho danh sách; thêm, sửa, khóa xong thì làm mới theo khóa này. */
export const EMPLOYEES_QUERY_KEY = ['employees']

const EMPLOYEES_URL = '/api/employees'

export async function fetchEmployees(): Promise<Employee[]> {
  const response = await httpClient.get<Employee[]>(EMPLOYEES_URL)
  return response.data
}

/** Backend tạo user trên KeyCloak, lưu hồ sơ, gán role, rồi KeyCloak gửi mail đặt mật khẩu. */
export async function createEmployee(values: EmployeeFormValues): Promise<void> {
  await httpClient.post(EMPLOYEES_URL, { ...values, branchId: values.branchId ?? null })
}

export async function updateEmployee(id: number, profile: EmployeeProfile): Promise<void> {
  await httpClient.put(`${EMPLOYEES_URL}/${id}`, profile)
}

/** Danh sách role mới thay cho danh sách cũ. */
export async function assignEmployeeRoles(id: number, roles: AppRole[]): Promise<void> {
  await httpClient.put(`${EMPLOYEES_URL}/${id}/roles`, { roles })
}

export async function lockEmployee(id: number): Promise<void> {
  await httpClient.post(`${EMPLOYEES_URL}/${id}/lock`)
}

export async function unlockEmployee(id: number): Promise<void> {
  await httpClient.post(`${EMPLOYEES_URL}/${id}/unlock`)
}