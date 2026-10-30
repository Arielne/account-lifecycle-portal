// Trang lỗi đăng nhập (spec admin mục 6): email của tài khoản KeyCloak vừa đăng nhập đã gắn với hồ sơ nhân viên khác.
// Backend đã xóa session và đăng xuất khỏi KeyCloak trước khi chuyển tới đây, nên trang này nằm ngoài
// phần cần đăng nhập (không gọi /api/me) và người dùng đăng nhập được ngay bằng tài khoản khác.
import { Button, Result } from 'antd'
import { redirectToLogin } from '../../api/httpClient.ts'
import styles from './LoginErrorPage.module.scss'

export const LOGIN_ERROR_PATH = '/loi-dang-nhap'

function LoginErrorPage() {
  return (
    <div className={styles.page}>
      <Result
        status="warning"
        title="Không đăng nhập được"
        subTitle="Email của tài khoản bạn vừa đăng nhập đã gắn với một hồ sơ nhân viên khác. Liên hệ Admin để được hỗ trợ. Phiên đăng nhập trên KeyCloak đã được kết thúc, bạn có thể đăng nhập bằng tài khoản khác."
        extra={
          <Button type="primary" onClick={redirectToLogin}>
            Đăng nhập bằng tài khoản khác
          </Button>
        }
      />
    </div>
  )
}

export default LoginErrorPage