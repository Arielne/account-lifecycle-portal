// Màn Chờ cấp quyền (CN-05, màn 3.13): đăng nhập thành công nhưng chưa có role nào.
// Không có layout, không có menu; chỉ có nút Đăng xuất.
import { App, Button, Result } from 'antd'
import { logout } from './logout.ts'
import styles from './WaitingForRolePage.module.scss'

function WaitingForRolePage() {
  const { message } = App.useApp()

  async function handleLogout() {
    try {
      await logout()
    } catch {
      message.error('Không đăng xuất được. Vui lòng thử lại.')
    }
  }

  return (
    <div className={styles.page}>
      <Result
        status="info"
        title="Tài khoản đang chờ cấp quyền"
        subTitle="Quản trị viên sẽ gán role cho bạn. Hệ thống gửi mail khi tài khoản được kích hoạt."
        extra={<Button onClick={handleLogout}>Đăng xuất</Button>}
      />
    </div>
  )
}

export default WaitingForRolePage