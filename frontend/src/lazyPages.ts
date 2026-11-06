// Các màn đã làm thật, mỗi màn tải bằng React.lazy (router.tsx dùng).
// React.lazy: mỗi màn đóng gói thành một file JS riêng, chỉ tải khi mở màn đó lần đầu, nên trang đầu tải ít hơn.
// Trong lúc tải, Suspense ở AppLayout hiện Spin. Mỗi người chỉ vào được vài màn theo role, nên không tải màn của role khác.
// Để riêng file này vì router.tsx còn export routes, router: file vừa có component vừa có thứ khác thì Vite
// không cập nhật nóng (Fast Refresh) được, oxlint báo only-export-components.
import { lazy } from 'react'

export const AccountDetailPage = lazy(() => import('./pages/account/AccountDetailPage.tsx'))
export const AccountListPage = lazy(() => import('./pages/account/AccountListPage.tsx'))
export const AuditLogPage = lazy(() => import('./pages/audit/AuditLogPage.tsx'))
export const CustomerDetailPage = lazy(() => import('./pages/customer/CustomerDetailPage.tsx'))
export const CustomerListPage = lazy(() => import('./pages/customer/CustomerListPage.tsx'))
export const AccountTypePage = lazy(() => import('./pages/catalog/AccountTypePage.tsx'))
export const BranchPage = lazy(() => import('./pages/catalog/BranchPage.tsx'))
export const EmployeePage = lazy(() => import('./pages/employee/EmployeePage.tsx'))
export const CreateTicketPage = lazy(() => import('./pages/ticket/CreateTicketPage.tsx'))
export const TicketDetailPage = lazy(() => import('./pages/ticket/TicketDetailPage.tsx'))
export const PendingReviewPage = lazy(() => import('./pages/ticket/PendingReviewPage.tsx'))