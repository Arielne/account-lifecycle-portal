// Chọn khung cho người đang đăng nhập (CN-05): chưa có role nào thì chỉ hiện màn Chờ cấp quyền,
// không có layout và menu; có role thì vào layout chung.
import { useCurrentUser } from '../pages/auth/useCurrentUser.ts'
import WaitingForRolePage from '../pages/auth/WaitingForRolePage.tsx'
import AppLayout from './AppLayout.tsx'

function AppShell() {
  const currentUser = useCurrentUser()
  if (currentUser.roles.length === 0) {
    return <WaitingForRolePage />
  }
  return <AppLayout />
}

export default AppShell