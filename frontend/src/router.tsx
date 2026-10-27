// Danh sách route của React (React Router, dạng data router: khai báo route bằng mảng object).
// Không đặt route /login, /logout, /oauth2 vì trùng đường dẫn của Spring Security.
import { createBrowserRouter } from 'react-router'
import SetupCheckPage from './pages/setup/SetupCheckPage.tsx'

export const router = createBrowserRouter([
  { path: '/', element: <SetupCheckPage /> },
])