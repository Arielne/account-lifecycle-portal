// Drawer Gán role (CN-22): chặn Admin đi cùng Giao dịch viên, Kiểm soát viên; lưu chi nhánh xong mà gán role lỗi
// thì bấm Lưu lần nữa chỉ gán role, và bảng được tải lại. vi.mock thay các hàm gọi API bằng hàm giả.
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { describe, expect, it, vi } from 'vitest'
import type { Branch } from '../catalog/branchApi.ts'
import EmployeeDrawer from './EmployeeDrawer.tsx'
import { assignEmployeeRoles, EMPLOYEES_QUERY_KEY, updateEmployee, type Employee } from './employeeApi.ts'

vi.mock('./employeeApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('./employeeApi.ts')>()
  return { ...original, updateEmployee: vi.fn(), assignEmployeeRoles: vi.fn() }
})

const THU: Employee = {
  id: 9,
  keycloakId: '0a1b2c3d-0000-4000-8000-0000000000d1',
  fullName: 'Bắc Đẩu Thu',
  email: 'thu.dangky@nhdemo.local',
  branchId: 4,
  branchName: 'Chi nhánh Tân Phú',
  roles: ['MAKER'],
  status: 'ACTIVE',
  createdAt: '2026-10-19T01:05:00Z',
}

function branch(id: number, name: string): Branch {
  return { id, code: `CN-${id}`, name, address: 'Địa chỉ giả', phone: '0280000000', employeeCount: 0, accountCount: 0 }
}

const BRANCHES = [branch(4, 'Chi nhánh Tân Phú'), branch(5, 'Chi nhánh Thủ Đức')]

/** Mở Drawer Gán role cho Thu; trả về QueryClient để kiểm tra bảng có được tải lại không. */
function renderAssignDrawer(onClose: () => void = vi.fn()) {
  vi.mocked(updateEmployee).mockReset()
  vi.mocked(assignEmployeeRoles).mockReset()
  const queryClient = new QueryClient()
  render(
    <QueryClientProvider client={queryClient}>
      <AntApp>
        <EmployeeDrawer mode="assign" employee={THU} branches={BRANCHES} onClose={onClose} />
      </AntApp>
    </QueryClientProvider>,
  )
  return queryClient
}

function clickSave() {
  fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
}

describe('EmployeeDrawer, chế độ Gán role', () => {
  it('Chọn Admin cùng Kiểm soát viên: báo lỗi dưới ô Role, không gọi API', async () => {
    // Chuẩn bị
    renderAssignDrawer()

    // Chạy: bỏ Giao dịch viên, chọn Kiểm soát viên và Admin
    fireEvent.click(screen.getByRole('checkbox', { name: /^Giao dịch viên/ }))
    fireEvent.click(screen.getByRole('checkbox', { name: /^Kiểm soát viên/ }))
    fireEvent.click(screen.getByRole('checkbox', { name: /^Admin/ }))
    clickSave()

    // Kiểm tra kết quả
    expect(await screen.findByText('Admin không giữ thêm role Giao dịch viên hoặc Kiểm soát viên')).toBeInTheDocument()
    expect(updateEmployee).not.toHaveBeenCalled()
    expect(assignEmployeeRoles).not.toHaveBeenCalled()
  })

  it('Đổi chi nhánh, gán role lỗi: tải lại bảng; bấm Lưu lần nữa chỉ gán role, không lưu lại chi nhánh', async () => {
    // Chuẩn bị: lưu chi nhánh được, gán role lỗi lần đầu, lần sau được
    const onClose = vi.fn()
    const queryClient = renderAssignDrawer(onClose)
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    vi.mocked(updateEmployee).mockResolvedValue()
    vi.mocked(assignEmployeeRoles).mockRejectedValueOnce(new Error('KeyCloak lỗi')).mockResolvedValueOnce()
    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Chi nhánh làm việc' }))
    fireEvent.click(await screen.findByTitle('Chi nhánh Thủ Đức'))

    // Chạy: lần 1 lỗi ở bước gán role
    clickSave()
    await waitFor(() => expect(assignEmployeeRoles).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: EMPLOYEES_QUERY_KEY }))
    // Chạy: lần 2
    clickSave()

    // Kiểm tra kết quả: chi nhánh chỉ lưu 1 lần, role gán 2 lần, lần 2 xong thì đóng Drawer
    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(updateEmployee).toHaveBeenCalledTimes(1)
    expect(updateEmployee).toHaveBeenCalledWith(9, {
      fullName: 'Bắc Đẩu Thu',
      email: 'thu.dangky@nhdemo.local',
      branchId: 5,
    })
    expect(assignEmployeeRoles).toHaveBeenCalledTimes(2)
  })
})