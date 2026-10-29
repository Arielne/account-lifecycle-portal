// Danh sách route của React (React Router, dạng data router: khai báo route bằng mảng object).
// Không đặt route /login, /logout, /oauth2 vì trùng đường dẫn của Spring Security.
import { createBrowserRouter, type RouteObject } from 'react-router'
import ComingSoonPage from './components/ComingSoonPage.tsx'
import NotFoundPage from './components/NotFoundPage.tsx'
import RequireRole from './components/RequireRole.tsx'
import AppShell from './layout/AppShell.tsx'
import { PAGES, type PageEntry } from './layout/menuItems.tsx'

/** Route của một mục menu: bọc bằng RequireRole, dùng chung danh sách role với menu. */
function toPageRoute(page: PageEntry): RouteObject {
  const element = (
    <RequireRole roles={page.roles}>
      <ComingSoonPage title={page.label} week={page.week} />
    </RequireRole>
  )
  // Trang chủ "/" là route index của AppShell.
  if (page.route === '/') {
    return { index: true, element }
  }
  return { path: page.route, element }
}

export const routes: RouteObject[] = [
  {
    path: '/',
    element: <AppShell />,
    children: [...PAGES.map(toPageRoute), { path: '*', element: <NotFoundPage /> }],
  },
]

export const router = createBrowserRouter(routes)