// Test 27 của spec phiếu: màn Chờ duyệt tự chọn phiếu đầu (hoặc phiếu ở ?phieu=); duyệt xong tải lại và mở phiếu kế;
// nút khóa trong lúc gửi; nhận 409 thì hiện cảnh báo và tải lại; nhãn "Gửi lại lần N" theo D1.
// vi.mock thay các hàm gọi API bằng hàm giả. Màn hiện 2 cột từ breakpoint md, nên test giả lập màn rộng.
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { AxiosError, AxiosHeaders } from 'axios'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CurrentUserContext, type CurrentUser } from '../auth/currentUser.ts'
import PendingReviewPage from './PendingReviewPage.tsx'
import {
  approveTicket,
  fetchPendingReview,
  fetchTicket,
  type PendingTicket,
  type TicketDetail,
} from './ticketApi.ts'

vi.mock('./ticketApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('./ticketApi.ts')>()
  return { ...original, fetchPendingReview: vi.fn(), fetchTicket: vi.fn(), approveTicket: vi.fn() }
})

const KHOA: CurrentUser = {
  keycloakId: 'k-khoa',
  fullName: 'Trần Minh Khoa',
  email: 'khoa.tm@nhdemo.local',
  roles: ['CHECKER'],
  branch: { id: 4, code: 'CN-TP', name: 'Chi nhánh Tân Phú' },
}

function pending(id: number, submitCount: number, overdue: boolean): PendingTicket {
  return {
    id,
    code: `PYC-2026-000${id}`,
    type: 'PHONG_TOA',
    fromStatus: 'ACTIVE',
    submitCount,
    newCustomer: false,
    lastSubmittedAt: '2026-11-05T02:12:00Z',
    waitingMinutes: overdue ? 1500 : 20,
    overdue,
    accountNumber: `102000005${id}`,
    customerName: 'Đỗ Thị Bình',
  }
}

function detail(id: number): TicketDetail {
  return {
    id,
    code: `PYC-2026-000${id}`,
    type: 'PHONG_TOA',
    status: 'PENDING_APPROVAL',
    accountId: 40 + id,
    accountNumber: `102000005${id}`,
    customerId: 21,
    customerName: 'Đỗ Thị Bình',
    customerCccd: '0991****0002',
    accountTypeName: 'Thanh toán cá nhân',
    fromStatus: 'ACTIVE',
    toStatus: 'BLOCKED',
    newCustomer: false,
    createdByName: 'Nguyễn Thị Lan',
    branchName: 'Chi nhánh Tân Phú',
    reviewedByName: null,
    reason: 'Khách báo mất thẻ',
    rejectReason: null,
    submitCount: 1,
    createdAt: '2026-11-05T02:12:00Z',
    lastSubmittedAt: '2026-11-05T02:12:00Z',
    attachments: [],
    history: [],
    canApprove: true,
    canReject: true,
    blockedReason: null,
  }
}

/** Lỗi 409 dạng chung của backend như axios ném ra. */
function conflictError(message: string): AxiosError {
  const headers = new AxiosHeaders()
  return new AxiosError('Conflict', 'ERR_BAD_REQUEST', { headers }, null, {
    status: 409,
    statusText: 'Conflict',
    headers: {},
    config: { headers },
    data: { status: 409, message, fieldErrors: [] },
  })
}

function renderAt(path: string) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AntApp>
        <CurrentUserContext.Provider value={KHOA}>
          <MemoryRouter initialEntries={[path]}>
            <PendingReviewPage />
          </MemoryRouter>
        </CurrentUserContext.Provider>
      </AntApp>
    </QueryClientProvider>,
  )
}

/** Trang giả đứng ở route chi tiết: in ra đường dẫn và state nhận được để test kiểm. */
function OpenedTicketProbe() {
  const location = useLocation()
  const state = location.state as { from?: string } | null
  return <p>Mở phiếu {location.pathname} từ {state?.from ?? 'không rõ'}</p>
}

/** Bấm Duyệt ở cột phải rồi bấm Duyệt trong hộp xác nhận. */
async function approveOpenedTicket() {
  fireEvent.click(await screen.findByRole('button', { name: 'Duyệt' }))
  const dialog = await screen.findByRole('dialog')
  fireEvent.click(within(dialog).getByRole('button', { name: 'Duyệt' }))
}

describe('PendingReviewPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    // Giả lập màn rộng: mọi câu hỏi kích thước màn hình đều đúng, nên breakpoint md bật và màn hiện 2 cột.
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: true,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }))
    vi.mocked(fetchTicket).mockImplementation((id) => Promise.resolve(detail(id)))
  })

  it('Tự mở phiếu đầu; nhãn "Gửi lại lần N" chỉ có khi đã gửi lại; dòng phụ có số phiếu và chi nhánh', async () => {
    // Chuẩn bị
    vi.mocked(fetchPendingReview).mockResolvedValue([pending(1, 1, true), pending(2, 2, false)])

    // Chạy
    renderAt('/phieu/cho-duyet')

    // Kiểm tra kết quả
    expect(await screen.findByText('PYC-2026-0001 · Phong tỏa')).toBeInTheDocument()
    expect(fetchTicket).toHaveBeenCalledWith(1)
    expect(screen.getByText('Gửi lại lần 1')).toBeInTheDocument()
    expect(screen.queryByText('Gửi lại lần 0')).not.toBeInTheDocument()
    expect(screen.getByText('1 ngày 1 giờ')).toBeInTheDocument()
    expect(screen.getByText(
      '2 phiếu của Chi nhánh Tân Phú · không gồm phiếu do bạn tạo · gửi sớm nhất lên trên',
    )).toBeInTheDocument()
  })

  it('?phieu= mở đúng phiếu đó nếu có trong danh sách', async () => {
    // Chuẩn bị
    vi.mocked(fetchPendingReview).mockResolvedValue([pending(1, 1, false), pending(2, 1, false)])

    // Chạy
    renderAt('/phieu/cho-duyet?phieu=2')

    // Kiểm tra kết quả
    expect(await screen.findByText('PYC-2026-0002 · Phong tỏa')).toBeInTheDocument()
  })

  it('Duyệt xong: nút khóa trong lúc gửi, rồi tải lại danh sách, mở phiếu kế và báo mã phiếu kế', async () => {
    // Chuẩn bị: lần tải đầu có 2 phiếu, sau khi duyệt chỉ còn phiếu 2; API duyệt chờ tới khi test cho trả lời
    vi.mocked(fetchPendingReview)
      .mockResolvedValueOnce([pending(1, 1, false), pending(2, 1, false)])
      .mockResolvedValue([pending(2, 1, false)])
    let finishApproval: () => void = () => {}
    vi.mocked(approveTicket).mockImplementation(() => new Promise((resolve) => {
      finishApproval = () => resolve()
    }))
    renderAt('/phieu/cho-duyet')
    await screen.findByText('PYC-2026-0001 · Phong tỏa')

    // Chạy: bấm duyệt, kiểm nút khóa khi đang gửi, rồi cho API trả lời
    await approveOpenedTicket()
    await waitFor(() => expect(screen.getAllByRole('button', { name: 'Duyệt' })[0]).toBeDisabled())
    finishApproval()

    // Kiểm tra kết quả
    expect(await screen.findByText('Đã duyệt PYC-2026-0001 · đang mở PYC-2026-0002')).toBeInTheDocument()
    expect(await screen.findByText('PYC-2026-0002 · Phong tỏa')).toBeInTheDocument()
    expect(approveTicket).toHaveBeenCalledWith(1)
  })

  it('Nhận 409 (phiếu vừa được người khác xử lý) thì hiện cảnh báo của backend và tải lại danh sách', async () => {
    // Chuẩn bị
    vi.mocked(fetchPendingReview).mockResolvedValue([pending(1, 1, false)])
    vi.mocked(approveTicket).mockRejectedValue(conflictError('Phiếu này vừa được người khác xử lý'))
    renderAt('/phieu/cho-duyet')
    await screen.findByText('PYC-2026-0001 · Phong tỏa')

    // Chạy
    await approveOpenedTicket()

    // Kiểm tra kết quả
    expect(await screen.findByText('Phiếu này vừa được người khác xử lý')).toBeInTheDocument()
    await waitFor(() => expect(fetchPendingReview).toHaveBeenCalledTimes(2))
  })

  it('Màn hẹp: chỉ có danh sách; bấm phiếu thì mở trang chi tiết, trang đó biết đi tới từ Chờ duyệt', async () => {
    // Chuẩn bị: màn hẹp, mọi câu hỏi kích thước màn hình đều sai (giống mặc định của setupTests.ts)
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }))
    vi.mocked(fetchPendingReview).mockResolvedValue([pending(1, 1, false)])
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <AntApp>
          <CurrentUserContext.Provider value={KHOA}>
            <MemoryRouter initialEntries={['/phieu/cho-duyet']}>
              <Routes>
                <Route path="/phieu/cho-duyet" element={<PendingReviewPage />} />
                <Route path="/phieu/:id" element={<OpenedTicketProbe />} />
              </Routes>
            </MemoryRouter>
          </CurrentUserContext.Provider>
        </AntApp>
      </QueryClientProvider>,
    )

    // Chạy
    fireEvent.click(await screen.findByText('PYC-2026-0001'))

    // Kiểm tra kết quả: không tải chi tiết ở màn hẹp; sang trang chi tiết kèm state
    expect(await screen.findByText('Mở phiếu /phieu/1 từ cho-duyet')).toBeInTheDocument()
    expect(fetchTicket).not.toHaveBeenCalled()
  })

  it('Không còn phiếu nào thì hiện Empty', async () => {
    // Chuẩn bị
    vi.mocked(fetchPendingReview).mockResolvedValue([])

    // Chạy
    renderAt('/phieu/cho-duyet')

    // Kiểm tra kết quả
    expect(await screen.findByText('Không còn phiếu nào chờ bạn duyệt')).toBeInTheDocument()
  })
})