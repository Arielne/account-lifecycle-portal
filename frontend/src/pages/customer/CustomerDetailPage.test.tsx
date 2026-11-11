// Test màn Chi tiết khách hàng (spec khách hàng tài khoản mục 4): thông tin đã che, nút "Mở thêm tài khoản" chỉ
// Giao dịch viên thấy và sang Tạo phiếu điền sẵn khách; tab Tài khoản gọi API tài khoản theo khách, hiện phiếu đang mở;
// tab Phiếu yêu cầu gọi API phiếu theo khách (spec phiếu test 30).
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { fetchAccounts, type AccountItem } from '../account/accountApi.ts'
import { CurrentUserContext, type AppRole, type CurrentUser } from '../auth/currentUser.ts'
import { fetchTickets, type TicketListItem } from '../ticket/ticketApi.ts'
import CustomerDetailPage from './CustomerDetailPage.tsx'
import { fetchCustomer, type CustomerDetail } from './customerApi.ts'

vi.mock('./customerApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('./customerApi.ts')>()
  return { ...original, fetchCustomer: vi.fn() }
})

vi.mock('../account/accountApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('../account/accountApi.ts')>()
  return { ...original, fetchAccounts: vi.fn() }
})

vi.mock('../ticket/ticketApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('../ticket/ticketApi.ts')>()
  return { ...original, fetchTickets: vi.fn() }
})

const CUSTOMER_TICKET: TicketListItem = {
  id: 9,
  code: 'PYC-2026-0418',
  type: 'PHONG_TOA',
  status: 'PENDING_APPROVAL',
  fromStatus: 'ACTIVE',
  rejectReason: null,
  newCustomer: false,
  accountNumber: '1099000001',
  customerName: 'Đỗ Thị Bình',
  branchName: 'Chi nhánh Tân Phú',
  createdByName: 'Nguyễn Thị Lan',
  reviewedByName: null,
  createdAt: '2026-11-05T02:12:00Z',
  updatedAt: '2026-11-05T02:12:00Z',
}

const CUSTOMER: CustomerDetail = {
  id: 1,
  fullName: 'Đỗ Thị Bình',
  dateOfBirth: '1990-03-15',
  gender: 'Nữ',
  cccd: '0991****0002',
  idIssueDate: '2023-11-07',
  idIssuePlace: 'Nơi cấp thử',
  phone: '090****002',
  email: 'bin***@nhdemo.local',
  branchName: 'Chi nhánh Tân Phú',
  createdDate: '2024-01-10',
}

const BLOCKING_ACCOUNT: AccountItem = {
  id: 7,
  accountNumber: '1099000001',
  customerId: 1,
  customerName: 'Đỗ Thị Bình',
  customerCccd: '0991****0002',
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

function user(roles: AppRole[]): CurrentUser {
  return { keycloakId: 'k1', fullName: 'Nguyễn Thị Lan', email: 'lan.nt@nhdemo.local', roles, branch: null }
}

/** Trang Tạo phiếu giả: in ra đường dẫn đã chuyển tới để test đọc. */
function CreateTicketStub() {
  const location = useLocation()
  return <p>Đã sang {`${location.pathname}${location.search}`}</p>
}

function renderPage(roles: AppRole[]) {
  vi.mocked(fetchCustomer).mockResolvedValue(CUSTOMER)
  vi.mocked(fetchAccounts).mockResolvedValue({ items: [BLOCKING_ACCOUNT], total: 1 })
  vi.mocked(fetchTickets).mockResolvedValue({
    items: [CUSTOMER_TICKET],
    total: 1,
    statusCounts: { PENDING_APPROVAL: 1, REJECTED: 0, APPROVED: 0, COMPLETED: 0, CANCELLED: 0 },
  })
  const router = createMemoryRouter(
    [
      { path: '/khach-hang/:id', element: <CustomerDetailPage /> },
      { path: '/phieu/tao-moi', element: <CreateTicketStub /> },
    ],
    { initialEntries: ['/khach-hang/1'] },
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

describe('CustomerDetailPage', () => {
  it('Giao dịch viên bấm "Mở thêm tài khoản": sang Tạo phiếu với id khách, không đưa CCCD lên URL', async () => {
    // Chuẩn bị
    renderPage(['MAKER'])

    // Chạy
    fireEvent.click(await screen.findByRole('button', { name: /Mở thêm tài khoản/ }))

    // Kiểm tra kết quả
    expect(await screen.findByText('Đã sang /phieu/tao-moi?loai=MO_TAI_KHOAN&khachHang=1')).toBeInTheDocument()
  })

  it('Kiểm soát viên không thấy nút "Mở thêm tài khoản"; thông tin khách hiện bản đã che', async () => {
    // Chuẩn bị, chạy
    renderPage(['CHECKER'])

    // Kiểm tra kết quả
    expect(await screen.findByText('bin***@nhdemo.local')).toBeInTheDocument()
    expect(screen.getAllByText('0991****0002').length).toBeGreaterThan(0)
    expect(screen.queryByRole('button', { name: /Mở thêm tài khoản/ })).not.toBeInTheDocument()
  })

  it('Tab Tài khoản gọi API tài khoản theo khách; tài khoản có phiếu đang mở hiện mã, loại và trạng thái phiếu', async () => {
    // Chuẩn bị, chạy
    renderPage(['MAKER'])

    // Kiểm tra kết quả
    expect(await screen.findByText('PYC-2026-0418')).toBeInTheDocument()
    expect(screen.getByText('Phong tỏa · đang chờ duyệt')).toBeInTheDocument()
    await waitFor(() => expect(fetchAccounts).toHaveBeenCalledWith({ statuses: [], customerId: 1 }, 1))
  })

  it('Tab Phiếu yêu cầu: nhãn tab có số phiếu; gọi API phiếu theo khách; mã phiếu là link sang chi tiết phiếu', async () => {
    // Chuẩn bị
    renderPage(['CHECKER'])

    // Chạy
    fireEvent.click(await screen.findByRole('tab', { name: 'Phiếu yêu cầu (1)' }))

    // Kiểm tra kết quả
    await waitFor(() => expect(fetchTickets).toHaveBeenCalledWith({ statuses: [], customerId: 1 }, 1))
    const links = await screen.findAllByRole('link', { name: 'PYC-2026-0418' })
    expect(links[0]).toHaveAttribute('href', '/phieu/9')
  })
})