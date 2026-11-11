// Test 13 (spec khách hàng tài khoản mục 7): Chi tiết tài khoản. Nút đúng theo trạng thái và role; có phiếu đang mở
// thì khóa nút và hiện Alert (QT-03); Kiểm soát viên không thấy nút. Test 30 (spec phiếu): Lịch sử phiếu, Vòng đời,
// Mở bởi, Duyệt mở bởi. vi.mock thay hàm gọi API bằng hàm giả;
// người đang đăng nhập đặt sẵn qua CurrentUserContext; MemoryRouter thay trình duyệt để kiểm trang chuyển tới.
import { fireEvent, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { CurrentUserContext, type AppRole, type CurrentUser } from '../auth/currentUser.ts'
import { fetchTickets } from '../ticket/ticketApi.ts'
import AccountDetailPage from './AccountDetailPage.tsx'
import { fetchAccount, fetchStatusHistory, type AccountItem, type AccountStatusHistoryItem } from './accountApi.ts'

vi.mock('./accountApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('./accountApi.ts')>()
  return { ...original, fetchAccount: vi.fn(), fetchStatusHistory: vi.fn() }
})

vi.mock('../ticket/ticketApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('../ticket/ticketApi.ts')>()
  return { ...original, fetchTickets: vi.fn() }
})

function historyItem(values: Partial<AccountStatusHistoryItem>): AccountStatusHistoryItem {
  return {
    id: 1,
    changedAt: '2026-11-05T02:12:00Z',
    fromStatus: null,
    toStatus: 'ACTIVE',
    actorName: null,
    ticketId: null,
    ticketCode: null,
    ticketType: null,
    ticketFromStatus: null,
    reason: null,
    dormantAfterMonths: 12,
    ...values,
  }
}

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
    openedByName: null,
    openApprovedByName: null,
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

function renderPage(accountItem: AccountItem, roles: AppRole[], history: AccountStatusHistoryItem[] = []) {
  vi.mocked(fetchAccount).mockResolvedValue(accountItem)
  vi.mocked(fetchStatusHistory).mockResolvedValue(history)
  vi.mocked(fetchTickets).mockResolvedValue({
    items: [],
    total: 3,
    statusCounts: { PENDING_APPROVAL: 0, REJECTED: 0, APPROVED: 0, COMPLETED: 3, CANCELLED: 0 },
  })
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

  it('Chủ tài khoản là link sang chi tiết khách hàng', async () => {
    // Chuẩn bị, chạy
    renderPage(account({}), ['CHECKER'])

    // Kiểm tra kết quả
    expect(await screen.findByRole('link', { name: 'Phạm Văn An' })).toHaveAttribute('href', '/khach-hang/1')
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

  it('Test 30: Mở bởi, Duyệt mở bởi lấy từ API; Lịch sử phiếu gọi API phiếu theo tài khoản, số phiếu ở tiêu đề', async () => {
    // Chuẩn bị, chạy
    renderPage(account({ openedByName: 'Nguyễn Thị Lan', openApprovedByName: 'Trần Minh Khoa' }), ['CHECKER'])

    // Kiểm tra kết quả
    expect(await screen.findByText('Trần Minh Khoa')).toBeInTheDocument()
    expect(screen.getByText('Nguyễn Thị Lan')).toBeInTheDocument()
    expect(await screen.findByText('Lịch sử phiếu (3)')).toBeInTheDocument()
    expect(fetchTickets).toHaveBeenCalledWith({ statuses: [], accountId: 1 }, 1)
  })

  it('Vòng đời: tên bước, người làm, mã phiếu là link; tài khoản mẫu ghi "Mở tài khoản" do Hệ thống', async () => {
    // Chuẩn bị: mới nhất ở trên, như API trả về
    const history = [
      historyItem({ id: 3, fromStatus: 'ACTIVE', toStatus: 'BLOCKED', actorName: 'Trần Minh Khoa', ticketId: 7,
        ticketCode: 'PYC-2026-0007', ticketType: 'PHONG_TOA', reason: 'Khách báo mất thẻ' }),
      historyItem({ id: 2, fromStatus: 'ACTIVE', toStatus: 'DORMANT' }),
      historyItem({ id: 1 }),
    ]

    // Chạy
    renderPage(account({}), ['MAKER'], history)

    // Kiểm tra kết quả
    expect(await screen.findByText(/Trần Minh Khoa duyệt/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'PYC-2026-0007' })).toHaveAttribute('href', '/phieu/7')
    expect(screen.getByText('Lý do: Khách báo mất thẻ')).toBeInTheDocument()
    expect(screen.getByText('Tự chuyển trạng thái')).toBeInTheDocument()
    expect(screen.getByText('Không phát sinh giao dịch trong 12 tháng')).toBeInTheDocument()
    expect(screen.getByText('Mở tài khoản')).toBeInTheDocument()
    expect(screen.getAllByText('Hệ thống')).toHaveLength(2)
  })

  it('Tài khoản Đã đóng: dòng ghi ngày đóng, không còn nút nào', async () => {
    // Chuẩn bị, chạy
    renderPage(account({ status: 'CLOSED', closedAt: '2026-10-18' }), ['MAKER'])

    // Kiểm tra kết quả
    expect(await screen.findByText('Tài khoản đã đóng ngày 18/10/2026 — không còn thao tác.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Đóng tài khoản' })).not.toBeInTheDocument()
  })
})