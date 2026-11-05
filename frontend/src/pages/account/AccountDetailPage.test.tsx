// Test 13 (spec khách hàng tài khoản mục 7): Chi tiết tài khoản. Nút đúng theo trạng thái và role; có phiếu đang mở
// thì khóa nút và hiện Alert (QT-03); Kiểm soát viên không thấy nút. vi.mock thay hàm gọi API bằng hàm giả;
// người đang đăng nhập đặt sẵn qua CurrentUserContext; MemoryRouter thay trình duyệt để kiểm trang chuyển tới.
import { fireEvent, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { CurrentUserContext, type AppRole, type CurrentUser } from '../auth/currentUser.ts'
import AccountDetailPage from './AccountDetailPage.tsx'
import { fetchAccount, type AccountItem } from './accountApi.ts'

vi.mock('./accountApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('./accountApi.ts')>()
  return { ...original, fetchAccount: vi.fn() }
})

function account(values: Partial<AccountItem>): AccountItem {
  return {
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
    openTicket: null,
    ...values,
  }
}

function user(roles: AppRole[]): CurrentUser {
  return { keycloakId: 'k1', fullName: 'Nguyễn Thị Lan', email: 'lan.nt@nhdemo.local', roles, branch: null }
}

/** Trang Tạo phiếu giả: in ra đường dẫn đã chuyển tới để test đọc. */
function CreateTicketStub() {
  const location = useLocation()
  return <p>Đã sang {`${location.pathname}${location.search}`}</p>
}

function renderPage(accountItem: AccountItem, roles: AppRole[]) {
  vi.mocked(fetchAccount).mockResolvedValue(accountItem)
  const router = createMemoryRouter(
    [
      { path: '/tai-khoan/:id', element: <AccountDetailPage /> },
      { path: '/phieu/tao-moi', element: <CreateTicketStub /> },
    ],
    { initialEntries: ['/tai-khoan/1'] },
  )
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AntApp>
        <CurrentUserContext.Provider value={user(roles)}>
          <RouterProvider router={router} />
        </CurrentUserContext.Provider>
      </AntApp>
    </QueryClientProvider>,
  )
}

describe('AccountDetailPage', () => {
  it('Giao dịch viên xem tài khoản Hoạt động: có nút Phong tỏa, Đóng tài khoản; bấm thì sang Tạo phiếu điền sẵn', async () => {
    // Chuẩn bị
    renderPage(account({}), ['MAKER'])

    // Chạy
    fireEvent.click(await screen.findByRole('button', { name: 'Phong tỏa' }))

    // Kiểm tra kết quả
    expect(await screen.findByText('Đã sang /phieu/tao-moi?loai=PHONG_TOA&taiKhoan=1099000001')).toBeInTheDocument()
  })

  it('Tài khoản Ngủ đông có nút Kích hoạt lại và Đóng tài khoản, không có nút Phong tỏa', async () => {
    // Chuẩn bị, chạy
    renderPage(account({ status: 'DORMANT' }), ['MAKER'])

    // Kiểm tra kết quả
    expect(await screen.findByRole('button', { name: 'Kích hoạt lại' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Đóng tài khoản' })).toBeEnabled()
    expect(screen.queryByRole('button', { name: 'Phong tỏa' })).not.toBeInTheDocument()
  })

  it('Có phiếu đang chờ duyệt: hiện Alert ghi mã phiếu và khóa mọi nút (QT-03)', async () => {
    // Chuẩn bị, chạy
    renderPage(
      account({ openTicket: { code: 'PYC-2026-0418', type: 'PHONG_TOA', status: 'PENDING_APPROVAL' } }),
      ['MAKER'],
    )

    // Kiểm tra kết quả
    expect(await screen.findByText('Tài khoản đang có phiếu PYC-2026-0418 chờ duyệt')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Phong tỏa' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Đóng tài khoản' })).toBeDisabled()
  })

  it('Kiểm soát viên không thấy nút tạo phiếu, chỉ thấy dòng nhắc', async () => {
    // Chuẩn bị, chạy
    renderPage(account({}), ['CHECKER'])

    // Kiểm tra kết quả
    expect(await screen.findByText('Chỉ giao dịch viên tạo được phiếu thay đổi trạng thái.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Phong tỏa' })).not.toBeInTheDocument()
  })

  it('Tài khoản Chờ mở: chỉ một dòng ghi mã phiếu mở tài khoản, không có nút, không hiện thêm Alert phiếu đang mở', async () => {
    // Chuẩn bị, chạy
    renderPage(
      account({
        status: 'PENDING_OPEN',
        openedAt: null,
        openTicket: { code: 'PYC-2026-0420', type: 'MO_TAI_KHOAN', status: 'PENDING_APPROVAL' },
      }),
      ['MAKER'],
    )

    // Kiểm tra kết quả
    expect(await screen.findByText('Chưa mở — phiếu PYC-2026-0420 đang chờ duyệt')).toBeInTheDocument()
    expect(screen.queryByText(/Tài khoản đang có phiếu/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Đóng tài khoản' })).not.toBeInTheDocument()
  })

  it('Tài khoản Đã đóng: dòng ghi ngày đóng, không còn nút nào', async () => {
    // Chuẩn bị, chạy
    renderPage(account({ status: 'CLOSED', closedAt: '2026-10-18' }), ['MAKER'])

    // Kiểm tra kết quả
    expect(await screen.findByText('Tài khoản đã đóng ngày 18/10/2026 — không còn thao tác.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Đóng tài khoản' })).not.toBeInTheDocument()
  })
})