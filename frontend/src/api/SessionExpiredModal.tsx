// Hộp thoại "Phiên đăng nhập đã hết hạn" (màn 3.13), hiện khi một API trả 401 lúc đang dùng app.
// Component không vẽ gì; nó chỉ đăng ký hàm mở hộp thoại cho interceptor trong httpClient.
// Lấy modal qua App.useApp() để hộp thoại dùng đúng theme và tiếng Việt của ConfigProvider.
import { useEffect, useRef } from 'react'
import { App } from 'antd'
import { redirectToLogin, setSessionExpiredHandler } from './httpClient.ts'

function SessionExpiredModal() {
  const { modal } = App.useApp()
  // Nhiều API cùng trả 401 một lúc thì chỉ mở một hộp thoại.
  const isModalOpen = useRef(false)

  useEffect(() => {
    setSessionExpiredHandler(() => {
      if (isModalOpen.current) {
        return
      }
      isModalOpen.current = true
      modal.warning({
        title: 'Phiên đăng nhập đã hết hạn',
        content: 'Vui lòng đăng nhập lại. Dữ liệu đang nhập trên form chưa được lưu.',
        okText: 'Đăng nhập lại',
        onOk: redirectToLogin,
      })
    })
  }, [modal])

  return null
}

export default SessionExpiredModal