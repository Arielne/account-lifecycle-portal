// Đăng xuất (CN-02): POST /logout (axios tự kèm CSRF token). Backend xóa session rồi trả địa chỉ đăng xuất
// của KeyCloak; React chuyển trình duyệt tới đó để KeyCloak kết thúc luôn phiên SSO.
// Thiếu bước này thì bấm đăng nhập lại sẽ vào thẳng mà không phải nhập mật khẩu.
import { httpClient } from '../../api/httpClient.ts'

interface LogoutResponse {
  logoutUrl: string
}

export async function logout() {
  const response = await httpClient.post<LogoutResponse>('/logout')
  window.location.assign(response.data.logoutUrl)
}