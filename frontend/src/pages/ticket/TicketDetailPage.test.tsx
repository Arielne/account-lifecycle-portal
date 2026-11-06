// Trang chi tiết phiếu (CN-15, spec phiếu mục 10.5): nội dung yêu cầu với CCCD đã che (QT-06), giấy tờ chỉ có Xem và
// Tải qua backend (QT-10), lịch sử xử lý; phiếu Bị từ chối có lý do từ chối. vi.mock thay hàm gọi API bằng hàm giả.
import { render, screen, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { fetchTicket, type TicketDetail } from './ticketApi.ts'
import TicketDetailPage from './TicketDetailPage.tsx'

vi.mock('./ticketApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('./ticketApi.ts')>()
  return { ...original, fetchTicket: vi.fn() }
})

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
}

function renderTicket(ticket: TicketDetail) {
  vi.mocked(fetchTicket).mockResolvedValue(ticket)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AntApp>
        <MemoryRouter initialEntries={[`/phieu/${ticket.id}`]}>
          <Routes>
            <Route path="/phieu/:id" element={<TicketDetailPage />} />
          </Routes>
        </MemoryRouter>
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
})