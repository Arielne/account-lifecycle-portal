// Test 9, 10 (spec auth mục 8): chặn route theo role và màn Chờ cấp quyền; trang /loi-dang-nhap không cần đăng nhập.
// Dùng đúng danh sách route của app, chỉ thay trình duyệt thật bằng MemoryRouter và thay /api/me bằng hàm giả.
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { fetchCurrentUser, type AppRole, type CurrentUser } from './pages/auth/currentUser.ts'
import { routes } from './router.tsx'

vi.mock('./pages/auth/currentUser.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('./pages/auth/currentUser.ts')>()
  return { ...original, fetchCurrentUser: vi.fn() }
})

function renderAt(path: string, roles: AppRole[]) {
  const currentUser: CurrentUser = {
    keycloakId: '5b30439a-4e15-42ac-bf7d-c4e8dba67784',
    fullName: 'Nguyễn Thị Lan',
    email: 'lan.nt@nhdemo.local',
    roles,
    branch: { id: 4, code: 'CN-TP', name: 'Chi nhánh Tân Phú' },
  }
  vi.mocked(fetchCurrentUser).mockResolvedValue(currentUser)
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(
    <QueryClientProvider client={new QueryClient()}>
      <AntApp>
        <RouterProvider router={router} />
      </AntApp>
    </QueryClientProvider>,
  )
}

describe('Chặn route theo role', () => {
  it('Giao dịch viên gõ thẳng /nhan-vien thì thấy trang 403', async () => {
    // Chuẩn bị, chạy
    renderAt('/nhan-vien', ['MAKER'])

    // Kiểm tra kết quả
    expect(await screen.findByText('Bạn không có quyền truy cập trang này.')).toBeInTheDocument()
  })

  it('Admin vào /audit-log thì qua được, thấy trang chờ của màn chưa làm', async () => {
    // Chuẩn bị, chạy
    renderAt('/audit-log', ['ADMIN'])

    // Kiểm tra kết quả
    expect(await screen.findByText('Màn này làm ở tuần 4.')).toBeInTheDocument()
    expect(screen.queryByText('Bạn không có quyền truy cập trang này.')).not.toBeInTheDocument()
  })

  it('Người chưa có role chỉ thấy màn Chờ cấp quyền, không có menu', async () => {
    // Chuẩn bị, chạy
    renderAt('/', [])

    // Kiểm tra kết quả
    expect(await screen.findByText('Tài khoản đang chờ cấp quyền')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Đăng xuất' })).toBeInTheDocument()
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })
})

describe('Trang lỗi đăng nhập', () => {
  it('/loi-dang-nhap hiện thông báo email trùng mà không gọi /api/me', async () => {
    // Chuẩn bị
    vi.mocked(fetchCurrentUser).mockClear()
    const router = createMemoryRouter(routes, { initialEntries: ['/loi-dang-nhap'] })

    // Chạy
    render(
      <QueryClientProvider client={new QueryClient()}>
        <AntApp>
          <RouterProvider router={router} />
        </AntApp>
      </QueryClientProvider>,
    )

    // Kiểm tra kết quả
    expect(await screen.findByText('Không đăng nhập được')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Đăng nhập bằng tài khoản khác' })).toBeInTheDocument()
    expect(fetchCurrentUser).not.toHaveBeenCalled()
  })
})