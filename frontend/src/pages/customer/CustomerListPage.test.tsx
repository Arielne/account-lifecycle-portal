// Test 12 (spec khách hàng tài khoản mục 7): màn Khách hàng. Đổi chế độ tìm thì xóa ô; CCCD chưa đủ 12 số báo lỗi,
// không gọi API; bảng hiện Tag trạng thái và CCCD đã che. vi.mock thay hàm gọi API bằng hàm giả.
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import CustomerListPage from './CustomerListPage.tsx'
import { searchCustomers, type CustomerListItem } from './customerApi.ts'

vi.mock('./customerApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('./customerApi.ts')>()
  return { ...original, searchCustomers: vi.fn() }
})

const BINH: CustomerListItem = {
  id: 1,
  fullName: 'Đỗ Thị Bình',
  cccd: '0991****0002',
  dateOfBirth: '1990-03-15',
  phone: '090****002',
  branchName: 'Chi nhánh Tân Phú',
  createdDate: '2024-01-10',
  accountStatuses: ['ACTIVE', 'DORMANT'],
}

function renderPage() {
  vi.mocked(searchCustomers).mockReset()
  vi.mocked(searchCustomers).mockResolvedValue({ items: [BINH], total: 1 })
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AntApp>
        <MemoryRouter>
          <CustomerListPage />
        </MemoryRouter>
      </AntApp>
    </QueryClientProvider>,
  )
}

/** Mở ô "Tìm theo" rồi chọn "Số CCCD". */
async function chooseCccdMode() {
  fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Tìm theo' }))
  fireEvent.click(await screen.findByTitle('Số CCCD'))
}

describe('CustomerListPage', () => {
  it('Mở màn: liệt kê mọi khách; bảng hiện CCCD đã che và mỗi tài khoản một Tag trạng thái', async () => {
    // Chuẩn bị, chạy
    renderPage()

    // Kiểm tra kết quả
    expect(await screen.findByText('Đỗ Thị Bình')).toBeInTheDocument()
    expect(searchCustomers).toHaveBeenCalledWith('NAME', '', 1)
    expect(screen.getByText('0991****0002')).toBeInTheDocument()
    expect(screen.getByText('Hoạt động')).toBeInTheDocument()
    expect(screen.getByText('Ngủ đông')).toBeInTheDocument()
  })

  it('Tìm họ tên: gọi API với đúng từ khóa đã gõ, về trang 1', async () => {
    // Chuẩn bị
    renderPage()
    const searchBox = await screen.findByPlaceholderText('Nhập họ tên')

    // Chạy
    fireEvent.change(searchBox, { target: { value: 'binh' } })
    fireEvent.keyDown(searchBox, { key: 'Enter', code: 'Enter', keyCode: 13 })

    // Kiểm tra kết quả
    await waitFor(() => expect(searchCustomers).toHaveBeenLastCalledWith('NAME', 'binh', 1))
  })

  it('Đổi sang tìm theo CCCD thì ô tìm bị xóa', async () => {
    // Chuẩn bị
    renderPage()
    fireEvent.change(await screen.findByPlaceholderText('Nhập họ tên'), { target: { value: 'binh' } })

    // Chạy
    await chooseCccdMode()

    // Kiểm tra kết quả
    expect(screen.getByPlaceholderText('Nhập đủ 12 số CCCD')).toHaveValue('')
  })

  it('CCCD chưa đủ 12 số: báo lỗi dưới ô, không gọi API tìm theo CCCD', async () => {
    // Chuẩn bị
    renderPage()
    await screen.findByText('Đỗ Thị Bình')
    await chooseCccdMode()
    const searchBox = screen.getByPlaceholderText('Nhập đủ 12 số CCCD')

    // Chạy
    fireEvent.change(searchBox, { target: { value: '09900000001' } })
    fireEvent.keyDown(searchBox, { key: 'Enter', code: 'Enter', keyCode: 13 })

    // Kiểm tra kết quả
    expect(await screen.findByText('Số CCCD phải đủ 12 chữ số')).toBeInTheDocument()
    expect(searchCustomers).not.toHaveBeenCalledWith('CCCD', expect.anything(), expect.anything())
  })
})