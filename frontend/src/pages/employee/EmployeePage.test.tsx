// Test 13 (spec admin mục 8): màn Nhân viên. Người Chờ cấp quyền ở đầu; dòng "(bạn)" chỉ có nút Sửa; Khóa phải xác nhận.
// vi.mock thay các hàm gọi API bằng hàm giả; người đang đăng nhập (Admin Phúc) đặt sẵn qua CurrentUserContext.
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { describe, expect, it, vi } from 'vitest'
import { CurrentUserContext, type CurrentUser } from '../auth/currentUser.ts'
import EmployeePage from './EmployeePage.tsx'
import { fetchEmployees, lockEmployee, type Employee } from './employeeApi.ts'

vi.mock('./employeeApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('./employeeApi.ts')>()
  return { ...original, fetchEmployees: vi.fn(), lockEmployee: vi.fn() }
})

vi.mock('../catalog/branchApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('../catalog/branchApi.ts')>()
  return { ...original, fetchBranches: vi.fn().mockResolvedValue([]) }
})

const PHUC_KEYCLOAK_ID = '927c941a-3244-4299-8a41-7c78b44d08e6'

const admin: CurrentUser = {
  keycloakId: PHUC_KEYCLOAK_ID,
  fullName: 'Lê Hoàng Phúc',
  email: 'phuc.lh@nhdemo.local',
  roles: ['ADMIN'],
  branch: null,
}

function employee(id: number, fullName: string, keycloakId: string, roles: Employee['roles']): Employee {
  return {
    id,
    keycloakId,
    fullName,
    email: `nv${id}@nhdemo.local`,
    branchId: null,
    branchName: null,
    roles,
    status: 'ACTIVE',
    createdAt: '2026-10-19T01:05:00Z',
  }
}

function renderPage() {
  vi.mocked(fetchEmployees).mockResolvedValue([
    employee(1, 'Nguyễn Thị Lan', '5b30439a-4e15-42ac-bf7d-c4e8dba67784', ['MAKER']),
    employee(4, 'Lê Hoàng Phúc', PHUC_KEYCLOAK_ID, ['ADMIN']),
    employee(9, 'Bắc Đẩu Thu', '0a1b2c3d-0000-4000-8000-0000000000d1', []),
  ])
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AntApp>
        <CurrentUserContext.Provider value={admin}>
          <EmployeePage />
        </CurrentUserContext.Provider>
      </AntApp>
    </QueryClientProvider>,
  )
}

/** Tên các nút trong một dòng của bảng. */
function buttonNamesOf(row: HTMLElement): string[] {
  return within(row)
    .queryAllByRole('button')
    .map((button) => button.textContent ?? '')
}

describe('Màn Nhân viên', () => {
  it('Người Chờ cấp quyền nằm đầu danh sách, chỉ có nút Gán role', async () => {
    // Chuẩn bị, chạy
    renderPage()

    // Kiểm tra kết quả: dòng đầu là Thu (chưa có role), dù tên xếp sau Lan và Phúc
    await screen.findByText('Bắc Đẩu Thu')
    const firstRow = screen.getAllByRole('row')[1]
    expect(within(firstRow).getByText('Bắc Đẩu Thu')).toBeInTheDocument()
    expect(within(firstRow).getByText('Chờ cấp quyền')).toBeInTheDocument()
    expect(buttonNamesOf(firstRow)).toEqual(['Gán role'])
  })

  it('Dòng của chính Admin ghi "(bạn)" và chỉ có nút Sửa', async () => {
    // Chuẩn bị, chạy
    renderPage()

    // Kiểm tra kết quả
    const selfRow = (await screen.findByText('(bạn)', { exact: false })).closest('tr') as HTMLElement
    expect(within(selfRow).getByText('Lê Hoàng Phúc')).toBeInTheDocument()
    expect(buttonNamesOf(selfRow)).toEqual(['Sửa'])
  })

  it('Bấm Khóa thì hỏi xác nhận trước, xác nhận xong mới gọi API khóa', async () => {
    // Chuẩn bị
    vi.mocked(lockEmployee).mockResolvedValue()
    renderPage()
    const lanRow = (await screen.findByText('Nguyễn Thị Lan')).closest('tr') as HTMLElement

    // Chạy: bấm Khóa ở dòng Lan
    fireEvent.click(within(lanRow).getByRole('button', { name: 'Khóa' }))

    // Kiểm tra kết quả: chưa gọi API cho tới khi bấm nút Khóa trong hộp xác nhận
    expect(lockEmployee).not.toHaveBeenCalled()
    const dialog = await screen.findByRole('dialog')
    // AntD ghi tiêu đề 2 lần (một bản ẩn cho trình đọc màn hình), nên dùng getAllByText.
    expect(within(dialog).getAllByText('Khóa nhân viên Nguyễn Thị Lan?').length).toBeGreaterThan(0)
    fireEvent.click(within(dialog).getByRole('button', { name: 'Khóa' }))
    await waitFor(() => expect(lockEmployee).toHaveBeenCalled())
    expect(vi.mocked(lockEmployee).mock.calls[0][0]).toBe(1)
  })
})