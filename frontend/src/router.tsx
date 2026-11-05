// Danh sách route của React (React Router, dạng data router: khai báo route bằng mảng object).
// Không đặt route /login, /logout, /oauth2 vì trùng đường dẫn của Spring Security.
import type { ReactNode } from 'react'
import { createBrowserRouter, type RouteObject } from 'react-router'
import ComingSoonPage from './components/ComingSoonPage.tsx'
import NotFoundPage from './components/NotFoundPage.tsx'
import RequireRole from './components/RequireRole.tsx'
import AppShell from './layout/AppShell.tsx'
import { PAGES, type PageEntry } from './layout/menuItems.tsx'
import CurrentUserProvider from './pages/auth/CurrentUserProvider.tsx'
import LoginErrorPage, { LOGIN_ERROR_PATH } from './pages/auth/LoginErrorPage.tsx'
import AccountDetailPage from './pages/account/AccountDetailPage.tsx'
import AccountListPage from './pages/account/AccountListPage.tsx'
import AuditLogPage from './pages/audit/AuditLogPage.tsx'
import AccountTypePage from './pages/catalog/AccountTypePage.tsx'
import BranchPage from './pages/catalog/BranchPage.tsx'
import EmployeePage from './pages/employee/EmployeePage.tsx'

// Màn đã làm thật, theo route. Route không có ở đây hiện trang chờ ComingSoonPage.
const PAGE_ELEMENTS: Record<string, ReactNode> = {
  '/tai-khoan': <AccountListPage />,
  '/danh-muc/loai-tai-khoan': <AccountTypePage />,
  '/danh-muc/chi-nhanh': <BranchPage />,
  '/nhan-vien': <EmployeePage />,
  '/audit-log': <AuditLogPage />,
}

/** Route của một mục menu: bọc bằng RequireRole, dùng chung danh sách role với menu. */
function toPageRoute(page: PageEntry): RouteObject {
  const pageElement = PAGE_ELEMENTS[page.route] ?? <ComingSoonPage title={page.label} week={page.week} />
  const element = <RequireRole roles={page.roles}>{pageElement}</RequireRole>
  // Trang chủ "/" là route index của AppShell.
  if (page.route === '/') {
    return { index: true, element }
  }
  return { path: page.route, element }
}

/** Route chi tiết không có trong menu: tự bọc RequireRole, dùng đúng role của màn danh sách đi kèm. */
function toDetailRoute(path: string, listRoute: string, detailElement: ReactNode): RouteObject {
  const listPage = PAGES.find((page) => page.route === listRoute)
  // Không tìm thấy màn danh sách thì danh sách role rỗng, tức không ai vào được: an toàn hơn là mở cho mọi người.
  const roles = listPage?.roles ?? []
  return { path, element: <RequireRole roles={roles}>{detailElement}</RequireRole> }
}

export const routes: RouteObject[] = [
  // Trang lỗi đăng nhập nằm ngoài phần cần đăng nhập: không đi qua CurrentUserProvider nên không gọi /api/me.
  { path: LOGIN_ERROR_PATH, element: <LoginErrorPage /> },
  {
    path: '/',
    // CurrentUserProvider gọi /api/me trước khi hiện mọi trang cần đăng nhập.
    element: (
      <CurrentUserProvider>
        <AppShell />
      </CurrentUserProvider>
    ),
    children: [
      ...PAGES.map(toPageRoute),
      toDetailRoute('/tai-khoan/:id', '/tai-khoan', <AccountDetailPage />),
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]

export const router = createBrowserRouter(routes)