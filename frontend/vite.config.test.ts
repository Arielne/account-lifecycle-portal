// @vitest-environment node
// Test cấu hình Vite proxy: 4 đường dẫn của backend phải giữ nguyên Host (localhost:5173),
// để backend tạo đúng địa chỉ quay về sau khi đăng nhập (http://localhost:5173/login/oauth2/code/keycloak).
// Tái hiện lỗi: viết rút gọn '/api/': 'http://localhost:8081' thì Vite tự bật changeOrigin
// và đổi Host thành localhost:8081, KeyCloak từ chối vì địa chỉ quay về không có trong danh sách.
import { describe, expect, it } from 'vitest'
import config from './vite.config.ts'

describe('Vite proxy', () => {
  it.each(['/api/', '/oauth2/', '/login/oauth2/', '/logout'])(
    '%s chuyển sang backend và giữ nguyên Host (changeOrigin: false)',
    (path) => {
      // Chuẩn bị
      const proxy = config.server?.proxy ?? {}

      // Chạy
      const proxyOptions = proxy[path]

      // Kiểm tra kết quả
      expect(proxyOptions).toMatchObject({ target: 'http://localhost:8081', changeOrigin: false })
    },
  )
})