// Danh sách route của React (React Router, dạng data router: khai báo route bằng mảng object).
// Không đặt route /login, /logout, /oauth2 vì trùng đường dẫn của Spring Security.
import type { ReactNode } from 'react'
import { createBrowserRouter, type RouteObject } from 'react-router'
import ComingSoonPage from './components/ComingSoonPage.tsx'
import NotFoundPage from './components/NotFoundPage.tsx'
import RequireRole from './components/RequireRole.tsx'
import AppShell from './layout/AppShell.tsx'
import { PAGES, type PageEntry } from './layout/menuItems.tsx'
import AccountTypePage from './pages/catalog/AccountTypePage.tsx'
import BranchPage from './pages/catalog/BranchPage.tsx'

// Màn đã làm thật, theo route. Route không có ở đây hiện trang chờ ComingSoonPage.
const PAGE_ELEMENTS: Record<string, ReactNode> = {
  '/danh-muc/loai-tai-khoan': <AccountTypePage />,
  '/danh-muc/chi-nhanh': <BranchPage />,
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

export const routes: RouteObject[] = [
  {
    path: '/',
    element: <AppShell />,
    children: [...PAGES.map(toPageRoute), { path: '*', element: <NotFoundPage /> }],
  },
]

export const router = createBrowserRouter(routes)