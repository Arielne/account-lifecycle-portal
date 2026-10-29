// Test 9, 10 (spec auth mục 8): chặn route theo role và màn Chờ cấp quyền.
// Dùng đúng danh sách route của app, chỉ thay trình duyệt thật bằng MemoryRouter và đặt sẵn người đang đăng nhập.
import { render, screen } from '@testing-library/react'
import { App as AntApp } from 'antd'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { CurrentUserContext, type AppRole, type CurrentUser } from './pages/auth/currentUser.ts'
import { routes } from './router.tsx'

function renderAt(path: string, roles: AppRole[]) {
  const currentUser: CurrentUser = {
    keycloakId: '5b30439a-4e15-42ac-bf7d-c4e8dba67784',
    fullName: 'Nguyễn Thị Lan',
    email: 'lan.nt@nhdemo.local',
    roles,
    branch: { id: 4, code: 'CN-TP', name: 'Chi nhánh Tân Phú' },
  }
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(
    <AntApp>
      <CurrentUserContext.Provider value={currentUser}>
        <RouterProvider router={router} />
      </CurrentUserContext.Provider>
    </AntApp>,
  )
}

describe('Chặn route theo role', () => {
  it('Giao dịch viên gõ thẳng /nhan-vien thì thấy trang 403', () => {
    // Chuẩn bị, chạy
    renderAt('/nhan-vien', ['MAKER'])

    // Kiểm tra kết quả
    expect(screen.getByText('Bạn không có quyền truy cập trang này.')).toBeInTheDocument()
  })

  it('Admin vào /nhan-vien thì qua được, thấy trang chờ của màn làm ở tuần 4', () => {
    // Chuẩn bị, chạy
    renderAt('/nhan-vien', ['ADMIN'])

    // Kiểm tra kết quả
    expect(screen.getByText('Màn này làm ở tuần 4.')).toBeInTheDocument()
    expect(screen.queryByText('Bạn không có quyền truy cập trang này.')).not.toBeInTheDocument()
  })

  it('Người chưa có role chỉ thấy màn Chờ cấp quyền, không có menu', () => {
    // Chuẩn bị, chạy
    renderAt('/', [])

    // Kiểm tra kết quả
    expect(screen.getByText('Tài khoản đang chờ cấp quyền')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Đăng xuất' })).toBeInTheDocument()
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })
})