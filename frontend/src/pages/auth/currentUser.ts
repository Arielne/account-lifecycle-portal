// Người đang đăng nhập, lấy từ GET /api/me (CN-01 đến CN-05).
// React không cầm token: chỉ biết họ tên, role, chi nhánh qua API này; cookie phiên do trình duyệt tự gửi.
import { createContext } from 'react'
import { CURRENT_USER_URL, httpClient } from '../../api/httpClient.ts'

/** 3 role của app; role chỉ lưu ở KeyCloak. */
export type AppRole = 'MAKER' | 'CHECKER' | 'ADMIN'

export interface BranchSummary {
  id: number
  code: string
  name: string
}

/** Dữ liệu GET /api/me trả về. branch là null khi chưa được gán chi nhánh (ví dụ Admin). */
export interface CurrentUser {
  keycloakId: string
  fullName: string
  email: string
  roles: AppRole[]
  branch: BranchSummary | null
}

/** Nhãn tiếng Việt của từng role, dùng cho Tag ở header. */
export const ROLE_LABELS: Record<AppRole, string> = {
  MAKER: 'Giao dịch viên',
  CHECKER: 'Kiểm soát viên',
  ADMIN: 'Admin',
}

export async function fetchCurrentUser(): Promise<CurrentUser> {
  const response = await httpClient.get<CurrentUser>(CURRENT_USER_URL)
  return response.data
}

// Context giữ người đang đăng nhập cho cả app; CurrentUserProvider đặt giá trị, useCurrentUser đọc ra.
export const CurrentUserContext = createContext<CurrentUser | null>(null)