// Test 14 (spec khách hàng tài khoản mục 7): màn Tài khoản đọc bộ lọc từ URL, bỏ qua giá trị lạ, đổi bộ lọc thì về
// trang 1; tài khoản có phiếu đang mở có dấu cảnh báo. vi.mock thay hàm gọi API bằng hàm giả; createMemoryRouter
// thay trình duyệt để đặt URL ban đầu và đọc URL sau khi đổi bộ lọc.
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import AccountListPage from './AccountListPage.tsx'
import { fetchAccounts, type AccountItem } from './accountApi.ts'

vi.mock('./accountApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('./accountApi.ts')>()
  return { ...original, fetchAccounts: vi.fn() }
})

vi.mock('../catalog/accountTypeApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('../catalog/accountTypeApi.ts')>()
  return { ...original, fetchAccountTypes: vi.fn().mockResolvedValue([]) }
})

vi.mock('../catalog/branchApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('../catalog/branchApi.ts')>()
  return { ...original, fetchBranches: vi.fn().mockResolvedValue([]) }
})

const ACCOUNT_WITH_TICKET: AccountItem = {
  id: 1,
  accountNumber: '1099000001',
  customerId: 1,
  customerName: 'Phạm Văn An',
  customerCccd: '0990****5678',
  accountTypeName: 'Thanh toán cá nhân',
  branchName: 'Chi nhánh Tân Phú',
  status: 'ACTIVE',
  openedAt: '2024-01-10',
  closedAt: null,
  lastTransactionAt: '2026-10-01',
  openTicket: { code: 'PYC-2026-0418', type: 'PHONG_TOA', status: 'PENDING_APPROVAL' },
  openedByName: null,
  openApprovedByName: null,
}

function renderPage(url: string, items: AccountItem[] = []) {
  vi.mocked(fetchAccounts).mockReset()
  vi.mocked(fetchAccounts).mockResolvedValue({ items, total: items.length })
  const router = createMemoryRouter([{ path: '/tai-khoan', element: <AccountListPage /> }], {
    initialEntries: [url],
  })
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AntApp>
        <RouterProvider router={router} />
      </AntApp>
    </QueryClientProvider>,
  )
  return router
}

describe('AccountListPage', () => {
  it('Đọc bộ lọc từ URL: gọi API với trạng thái Ngủ đông và trang 3; trạng thái lạ trên URL bị bỏ qua', async () => {
    // Chuẩn bị, chạy
    renderPage('/tai-khoan?trang-thai=DORMANT&trang-thai=KHONG_CO&trang=3')

    // Kiểm tra kết quả
    await waitFor(() =>
      expect(fetchAccounts).toHaveBeenCalledWith(
        { accountNumber: undefined, statuses: ['DORMANT'], accountTypeId: undefined, branchId: undefined },
        3,
      ),
    )
    expect(screen.getByTitle('Ngủ đông')).toBeInTheDocument()
  })

  it('Tìm số tài khoản khi đang ở trang 3: về trang 1, giữ bộ lọc trạng thái, URL ghi số đã tìm', async () => {
    // Chuẩn bị
    const router = renderPage('/tai-khoan?trang-thai=DORMANT&trang=3')

    // Chạy
    const searchBox = await screen.findByPlaceholderText('Số tài khoản')
    fireEvent.change(searchBox, { target: { value: '1099' } })
    fireEvent.keyDown(searchBox, { key: 'Enter', code: 'Enter', keyCode: 13 })

    // Kiểm tra kết quả
    await waitFor(() =>
      expect(fetchAccounts).toHaveBeenLastCalledWith(
        { accountNumber: '1099', statuses: ['DORMANT'], accountTypeId: undefined, branchId: undefined },
        1,
      ),
    )
    expect(router.state.location.search).toBe('?so-tk=1099&trang-thai=DORMANT')
  })

  it('Số tài khoản có chữ: báo lỗi dưới ô, không gọi API với số đó', async () => {
    // Chuẩn bị
    renderPage('/tai-khoan')

    // Chạy
    const searchBox = await screen.findByPlaceholderText('Số tài khoản')
    fireEvent.change(searchBox, { target: { value: '10a9' } })
    fireEvent.keyDown(searchBox, { key: 'Enter', code: 'Enter', keyCode: 13 })

    // Kiểm tra kết quả
    expect(await screen.findByText('Số tài khoản chỉ gồm 1–10 chữ số')).toBeInTheDocument()
    expect(fetchAccounts).not.toHaveBeenCalledWith(expect.objectContaining({ accountNumber: '10a9' }), 1)
  })

  it('Tài khoản có phiếu đang mở có dấu cảnh báo; CCCD hiện đúng bản đã che', async () => {
    // Chuẩn bị, chạy
    renderPage('/tai-khoan', [ACCOUNT_WITH_TICKET])

    // Kiểm tra kết quả
    expect(await screen.findByLabelText('Có phiếu đang mở')).toBeInTheDocument()
    expect(screen.getByText('0990****5678')).toBeInTheDocument()
  })
})