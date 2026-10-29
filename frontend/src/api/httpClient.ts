// axios dùng chung cho mọi lời gọi API (module common).
// Đường dẫn tương đối /api/...: chạy dev đi qua Vite proxy, chạy demo đi qua Nginx, đều cùng origin với React.
// axios tự đọc cookie XSRF-TOKEN và gửi header X-XSRF-TOKEN cho request cùng origin (CSRF cho SPA),
// nên không cần viết thêm gì cho POST, PUT, DELETE.
import axios from 'axios'

export const LOGIN_URL = '/oauth2/authorization/keycloak'
export const CURRENT_USER_URL = '/api/me'

export const httpClient = axios.create()

/** Chuyển trình duyệt sang trang đăng nhập của KeyCloak (qua đường dẫn có sẵn của Spring Security). */
export function redirectToLogin() {
  window.location.assign(LOGIN_URL)
}

/** Lỗi do backend trả 401: chưa đăng nhập hoặc phiên đã hết hạn. */
export function isUnauthorizedError(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 401
}

// Việc cần làm khi phiên hết hạn lúc đang dùng app. Mặc định chuyển thẳng sang trang đăng nhập;
// SessionExpiredModal thay bằng hộp thoại "Phiên đăng nhập đã hết hạn" để dùng đúng theme của AntD.
let handleSessionExpired: () => void = redirectToLogin

export function setSessionExpiredHandler(handler: () => void) {
  handleSessionExpired = handler
}

// Interceptor: chạy với mọi response lỗi trước khi tới nơi gọi API.
// 401 của /api/me (lúc mở app) do CurrentUserProvider xử lý: chuyển thẳng sang trang đăng nhập.
// 401 của API khác nghĩa là phiên hết hạn khi đang dùng: hỏi người dùng trước khi chuyển.
httpClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    const isCurrentUserRequest = axios.isAxiosError(error) && error.config?.url === CURRENT_USER_URL
    if (isUnauthorizedError(error) && !isCurrentUserRequest) {
      handleSessionExpired()
    }
    return Promise.reject(error)
  },
)