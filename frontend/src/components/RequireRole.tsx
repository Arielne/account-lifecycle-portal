// Chặn route theo role (QT-07): gõ thẳng URL trái quyền thì hiện trang 403.
// Đây chỉ là lớp hiển thị; backend vẫn tự kiểm quyền ở mọi API.
import type { ReactNode } from 'react'
import type { AppRole } from '../pages/auth/currentUser.ts'
import { useCurrentUser } from '../pages/auth/useCurrentUser.ts'
import ForbiddenPage from './ForbiddenPage.tsx'

interface RequireRoleProps {
  /** Role được vào; người có ít nhất 1 role trong danh sách là được. */
  roles: AppRole[]
  children: ReactNode
}

function RequireRole({ roles, children }: RequireRoleProps) {
  const currentUser = useCurrentUser()
  const isAllowed = roles.some((role) => currentUser.roles.includes(role))
  if (!isAllowed) {
    return <ForbiddenPage />
  }
  return children
}

export default RequireRole