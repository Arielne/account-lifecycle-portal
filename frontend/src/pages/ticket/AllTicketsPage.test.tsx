// Test 30 của spec phiếu: Tất cả phiếu đọc bộ lọc từ URL (giá trị lạ thì bỏ qua), Segmented trạng thái có số phiếu
// (D8), đổi bộ lọc thì về trang 1. vi.mock thay các hàm gọi API bằng hàm giả.
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchBranches } from '../catalog/branchApi.ts'
import AllTicketsPage from './AllTicketsPage.tsx'
import { fetchTickets, type TicketListItem } from './ticketApi.ts'

vi.mock('./ticketApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('./ticketApi.ts')>()
  return { ...original, fetchTickets: vi.fn() }
})

vi.mock('../catalog/branchApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('../catalog/branchApi.ts')>()
  return { ...original, fetchBranches: vi.fn() }
})

const REJECTED_TICKET: TicketListItem = {
  id: 9,
  code: 'PYC-2026-0009',
  type: 'GO_PHONG_TOA',
  status: 'REJECTED',
  fromStatus: 'DORMANT',
  rejectReason: 'Đơn chưa có chữ ký',
  newCustomer: false,
  accountNumber: '1020000004',
  customerName: 'Đỗ Thị Bình',
  branchName: 'Chi nhánh Tân Phú',
  createdByName: 'Nguyễn Thị Lan',
  reviewedByName: 'Trần Minh Khoa',
  createdAt: '2026-11-05T02:12:00Z',
  updatedAt: '2026-11-06T03:00:00Z',
}

function renderAt(path: string) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AntApp>
        <MemoryRouter initialEntries={[path]}>
          <AllTicketsPage />
        </MemoryRouter>
      </AntApp>
    </QueryClientProvider>,
  )
}

describe('AllTicketsPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(fetchBranches).mockResolvedValue([])
    vi.mocked(fetchTickets).mockResolvedValue({
      items: [REJECTED_TICKET],
      total: 1,
      statusCounts: { PENDING_APPROVAL: 3, REJECTED: 2, APPROVED: 0, COMPLETED: 4, CANCELLED: 1 },
    })
  })

  it('Đọc bộ lọc và số trang từ URL; Segmented có số phiếu từng trạng thái; dòng Bị từ chối có lý do', async () => {
    // Chuẩn bị, chạy
    renderAt('/phieu?trang-thai=REJECTED&ma=PYC-2026&tu=2026-11-01&den=2026-11-07&trang=2')

    // Kiểm tra kết quả
    await waitFor(() => expect(fetchTickets).toHaveBeenCalledWith({
      statuses: ['REJECTED'], code: 'PYC-2026', createdFrom: '2026-11-01', createdTo: '2026-11-07',
    }, 2))
    expect(await screen.findByText('Tất cả (10)')).toBeInTheDocument()
    expect(screen.getByText('Bị từ chối (2)')).toBeInTheDocument()
    expect(screen.getByText('Đơn chưa có chữ ký')).toBeInTheDocument()
    expect(screen.getByText('Kích hoạt lại')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'PYC-2026-0009' })).toHaveAttribute('href', '/phieu/9')
  })

  it('Giá trị lạ trên URL (người dùng sửa tay) thì bỏ qua, không gửi lên API', async () => {
    // Chuẩn bị, chạy
    renderAt('/phieu?trang-thai=XYZ&ma=PYC%25&loai=XOA&tu=2026-13-45&trang=abc')

    // Kiểm tra kết quả
    await waitFor(() => expect(fetchTickets).toHaveBeenCalledWith({ statuses: [] }, 1))
  })

  it('Chọn trạng thái ở Segmented thì lọc theo trạng thái đó và về trang 1', async () => {
    // Chuẩn bị
    renderAt('/phieu?trang=3')
    await screen.findByText('Chờ duyệt (3)')

    // Chạy
    fireEvent.click(screen.getByText('Chờ duyệt (3)'))

    // Kiểm tra kết quả
    await waitFor(() => expect(fetchTickets).toHaveBeenLastCalledWith({ statuses: ['PENDING_APPROVAL'] }, 1))
  })
})