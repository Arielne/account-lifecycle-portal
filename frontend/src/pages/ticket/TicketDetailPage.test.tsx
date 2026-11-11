// Trang chi tiết phiếu (CN-15, spec phiếu mục 10.5): nội dung yêu cầu với CCCD đã che (QT-06), giấy tờ chỉ có Xem và
// Tải qua backend (QT-10), lịch sử xử lý; phiếu Bị từ chối có lý do từ chối. Test 28: nút Duyệt, Từ chối theo cờ của
// backend (QT-01 khóa kèm Tooltip, D11 khác chi nhánh). vi.mock thay hàm gọi API bằng hàm giả.
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { CurrentUserContext, type AppRole, type CurrentUser } from '../auth/currentUser.ts'
import { approveTicket, cancelTicket, fetchTicket, type TicketDetail } from './ticketApi.ts'
import TicketDetailPage from './TicketDetailPage.tsx'

vi.mock('./ticketApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('./ticketApi.ts')>()
  return { ...original, fetchTicket: vi.fn(), approveTicket: vi.fn(), cancelTicket: vi.fn() }
})

function viewer(roles: AppRole[]): CurrentUser {
  return {
    keycloakId: 'k-khoa',
    fullName: 'Trần Minh Khoa',
    email: 'khoa.tm@nhdemo.local',
    roles,
    branch: { id: 4, code: 'CN-TP', name: 'Chi nhánh Tân Phú' },
  }
}

const PENDING_TICKET: TicketDetail = {
  id: 7,
  code: 'PYC-2026-0007',
  type: 'GO_PHONG_TOA',
  status: 'PENDING_APPROVAL',
  accountId: 47,
  accountNumber: '1020000050',
  customerId: 21,
  customerName: 'Đỗ Thị Bình',
  customerCccd: '0991****0002',
  accountTypeName: 'Thanh toán cá nhân',
  fromStatus: 'DORMANT',
  toStatus: 'ACTIVE',
  newCustomer: false,
  createdByName: 'Nguyễn Thị Lan',
  branchName: 'Chi nhánh Tân Phú',
  reviewedByName: null,
  reason: 'Khách muốn dùng lại tài khoản',
  rejectReason: null,
  submitCount: 1,
  createdAt: '2026-11-05T02:12:00Z',
  lastSubmittedAt: '2026-11-05T02:12:00Z',
  attachments: [{ id: 3, originalName: 'don-de-nghi.pdf', contentType: 'application/pdf', sizeBytes: 421888 }],
  history: [{ action: 'SUBMITTED', fromStatus: null, toStatus: 'PENDING_APPROVAL', actorName: 'Nguyễn Thị Lan',
    note: null, createdAt: '2026-11-05T02:12:00Z' }],
  canApprove: false,
  canReject: false,
  blockedReason: null,
  canEdit: false,
  canCancel: false,
  cancelForLockedCreator: false,
}

function renderTicket(ticket: TicketDetail, roles: AppRole[] = ['MAKER']) {
  vi.mocked(fetchTicket).mockResolvedValue(ticket)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AntApp>
        <CurrentUserContext.Provider value={viewer(roles)}>
          <MemoryRouter initialEntries={[`/phieu/${ticket.id}`]}>
            <Routes>
              <Route path="/phieu/:id" element={<TicketDetailPage />} />
              <Route path="/phieu/:id/sua" element={<p>Trang sửa phiếu</p>} />
            </Routes>
          </MemoryRouter>
        </CurrentUserContext.Provider>
      </AntApp>
    </QueryClientProvider>,
  )
}

describe('TicketDetailPage', () => {
  it('Phiếu chờ duyệt: tên loại theo trạng thái lúc tạo, CCCD đã che, giấy tờ có Xem và Tải, lịch sử có bước hiện tại', async () => {
    // Chuẩn bị, chạy
    renderTicket(PENDING_TICKET)

    // Kiểm tra kết quả
    expect(await screen.findByText('PYC-2026-0007 · Kích hoạt lại')).toBeInTheDocument()
    expect(screen.getByText('0991****0002')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Đỗ Thị Bình' })).toHaveAttribute('href', '/khach-hang/21')
    expect(screen.getByRole('link', { name: /Xem/ })).toHaveAttribute('href', '/api/tickets/7/attachments/3')
    expect(screen.getByRole('link', { name: /Tải/ }))
      .toHaveAttribute('href', '/api/tickets/7/attachments/3?download=true')
    expect(screen.getByText('412 KB')).toBeInTheDocument()
    expect(screen.getByText('Chờ kiểm soát viên duyệt')).toBeInTheDocument()
    expect(screen.getByText('Nguyễn Thị Lan gửi phiếu')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /xóa/i })).not.toBeInTheDocument()
  })

  it('Phiếu bị từ chối hiện lý do từ chối', async () => {
    // Chuẩn bị, chạy
    renderTicket({ ...PENDING_TICKET, status: 'REJECTED', rejectReason: 'Đơn chưa có chữ ký' })

    // Kiểm tra kết quả
    const content = (await screen.findByText('Lý do từ chối')).closest('table') as HTMLElement
    expect(within(content).getByText('Đơn chưa có chữ ký')).toBeInTheDocument()
  })

  it('Phiếu Đã hủy: nội dung ghi "không chuyển ... vì phiếu đã hủy", không còn "khi phiếu được duyệt"', async () => {
    // Chuẩn bị, chạy
    renderTicket({ ...PENDING_TICKET, status: 'CANCELLED' })

    // Kiểm tra kết quả
    expect(await screen.findByText('vì phiếu đã hủy')).toBeInTheDocument()
    expect(screen.getByText(/không chuyển/)).toBeInTheDocument()
    expect(screen.queryByText('khi phiếu được duyệt')).not.toBeInTheDocument()
  })

  it('Giao dịch viên không thấy nút Duyệt, Từ chối', async () => {
    // Chuẩn bị, chạy
    renderTicket(PENDING_TICKET, ['MAKER'])

    // Kiểm tra kết quả
    await screen.findByText('PYC-2026-0007 · Kích hoạt lại')
    expect(screen.queryByRole('button', { name: 'Duyệt' })).not.toBeInTheDocument()
  })

  it('QT-01: người tạo phiếu giữ cả CHECKER thấy nút Duyệt bị khóa kèm Tooltip lý do', async () => {
    // Chuẩn bị
    renderTicket({ ...PENDING_TICKET, blockedReason: 'Bạn không thể duyệt phiếu do chính mình tạo' },
      ['MAKER', 'CHECKER'])

    // Chạy: rê chuột vào nút bị khóa (AntD bọc nút bị khóa trong thẻ span để vẫn hiện Tooltip)
    const approveButton = await screen.findByRole('button', { name: 'Duyệt' })
    fireEvent.mouseEnter(approveButton.parentElement as HTMLElement)

    // Kiểm tra kết quả
    expect(approveButton).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Từ chối' })).toBeDisabled()
    expect(await screen.findByText('Bạn không thể duyệt phiếu do chính mình tạo')).toBeInTheDocument()
  })

  it('D11: kiểm soát viên khác chi nhánh không thấy nút, thấy dòng "Phiếu thuộc ..."', async () => {
    // Chuẩn bị, chạy: backend trả cả 2 cờ false và không có lý do khóa
    renderTicket({ ...PENDING_TICKET, branchName: 'Chi nhánh Quận 1' }, ['CHECKER'])

    // Kiểm tra kết quả
    expect(await screen.findByText('Phiếu thuộc Chi nhánh Quận 1')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Duyệt' })).not.toBeInTheDocument()
  })

  it('Test 28: người tạo thấy "Sửa và gửi lại", "Hủy phiếu" khi phiếu Bị từ chối; bấm Sửa thì sang trang sửa', async () => {
    // Chuẩn bị
    renderTicket({ ...PENDING_TICKET, status: 'REJECTED', rejectReason: 'Thiếu chữ ký', canEdit: true, canCancel: true },
      ['MAKER'])

    // Chạy
    expect(await screen.findByRole('button', { name: 'Hủy phiếu' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Sửa và gửi lại' }))

    // Kiểm tra kết quả
    expect(await screen.findByText('Trang sửa phiếu')).toBeInTheDocument()
  })

  it('Hủy phiếu Mở tài khoản cho khách mới: hộp nói rõ hậu quả, nhắc sửa thay vì hủy (D6), bắt nhập lý do (D4)', async () => {
    // Chuẩn bị
    vi.mocked(cancelTicket).mockResolvedValue()
    renderTicket({ ...PENDING_TICKET, type: 'MO_TAI_KHOAN', fromStatus: 'PENDING_OPEN', newCustomer: true,
      canCancel: true }, ['MAKER'])

    // Chạy: mở hộp, bấm xác nhận khi chưa nhập lý do
    fireEvent.click(await screen.findByRole('button', { name: 'Hủy phiếu' }))
    const dialog = await screen.findByRole('dialog')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận hủy phiếu' }))

    // Kiểm tra kết quả
    expect(within(dialog).getByText('Tài khoản đang Chờ mở sẽ chuyển sang Đã đóng.')).toBeInTheDocument()
    expect(within(dialog).getByText(/Sai thông tin khách thì Sửa và gửi lại, đừng hủy/)).toBeInTheDocument()
    expect(await within(dialog).findByText('Vui lòng nhập lý do hủy')).toBeInTheDocument()
    expect(cancelTicket).not.toHaveBeenCalled()

    // Chạy: nhập lý do rồi xác nhận
    fireEvent.change(within(dialog).getByRole('textbox', { name: 'Lý do hủy' }), { target: { value: ' Khách đổi ý ' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận hủy phiếu' }))

    // Kiểm tra kết quả
    await waitFor(() => expect(cancelTicket).toHaveBeenCalledWith(7, 'Khách đổi ý'))
  })

  it('D12: hủy thay người tạo đã bị khóa thì hộp có dòng "bạn hủy thay"; không có nút Sửa', async () => {
    // Chuẩn bị, chạy
    renderTicket({ ...PENDING_TICKET, canCancel: true, cancelForLockedCreator: true }, ['MAKER'])
    fireEvent.click(await screen.findByRole('button', { name: 'Hủy phiếu' }))

    // Kiểm tra kết quả
    expect(await screen.findByText('Người tạo Nguyễn Thị Lan đã bị khóa; bạn hủy thay.')).toBeInTheDocument()
    expect(screen.getByText('Tài khoản 1020000050 giữ nguyên trạng thái Ngủ đông.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Sửa và gửi lại' })).not.toBeInTheDocument()
  })

  it('Kiểm soát viên bấm Duyệt: hộp xác nhận nêu thay đổi và nhắc đối chiếu giấy tờ; xác nhận thì gọi API duyệt', async () => {
    // Chuẩn bị
    vi.mocked(approveTicket).mockResolvedValue()
    renderTicket({ ...PENDING_TICKET, canApprove: true, canReject: true }, ['CHECKER'])

    // Chạy
    fireEvent.click(await screen.findByRole('button', { name: 'Duyệt' }))

    // Kiểm tra kết quả
    expect(await screen.findByText('Duyệt phiếu PYC-2026-0007?')).toBeInTheDocument()
    expect(screen.getByText(
      'Tài khoản 1020000050: Ngủ đông → Hoạt động. Hãy chắc bạn đã đối chiếu giấy tờ đính kèm.',
    )).toBeInTheDocument()
    const dialog = screen.getByRole('dialog')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Duyệt' }))
    await waitFor(() => expect(approveTicket).toHaveBeenCalledWith(7))
  })
})