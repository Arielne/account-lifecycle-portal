// Màn Sửa và gửi lại (CN-12, D2, D3, D10): điền sẵn từ edit-data, hiện lý do từ chối; số CCCD của chính hồ sơ phiếu
// báo "đang chờ duyệt" thì không chặn; bỏ giấy tờ cũ, thêm giấy tờ mới rồi gửi đúng dữ liệu; người khác (403) thấy
// trang 403; phiếu không còn Bị từ chối (409) thì báo. vi.mock thay các hàm gọi API bằng hàm giả.
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { AxiosError, AxiosHeaders } from 'axios'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CurrentUserContext, type CurrentUser } from '../auth/currentUser.ts'
import { fetchAccountTypes } from '../catalog/accountTypeApi.ts'
import EditTicketPage from './EditTicketPage.tsx'
import { checkCustomer, fetchEditData, resubmitTicket, type TicketEditData } from './ticketApi.ts'

vi.mock('./ticketApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('./ticketApi.ts')>()
  return { ...original, fetchEditData: vi.fn(), resubmitTicket: vi.fn(), checkCustomer: vi.fn() }
})

vi.mock('../catalog/accountTypeApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('../catalog/accountTypeApi.ts')>()
  return { ...original, fetchAccountTypes: vi.fn() }
})

const LAN: CurrentUser = {
  keycloakId: 'k-lan',
  fullName: 'Nguyễn Thị Lan',
  email: 'lan.nt@nhdemo.local',
  roles: ['MAKER'],
  branch: { id: 4, code: 'CN-TP', name: 'Chi nhánh Tân Phú' },
}

const NEW_CUSTOMER_TICKET: TicketEditData = {
  id: 7,
  code: 'PYC-2026-0007',
  type: 'MO_TAI_KHOAN',
  fromStatus: 'PENDING_OPEN',
  toStatus: 'ACTIVE',
  accountNumber: '1030000001',
  accountTypeId: 3,
  customerId: 21,
  newCustomer: {
    fullName: 'Lê Minh Thử',
    dateOfBirth: '1995-03-15',
    gender: 'Nam',
    cccd: '099399000101',
    idIssueDate: '2021-04-20',
    idIssuePlace: 'Cục CS QLHC về TTXH',
    phone: '0909000101',
    email: 'khach.thu@nhdemo.local',
    address: '12 Đường Số 1, Phường Thử',
  },
  reason: 'Khách mở tài khoản',
  rejectReason: 'Đơn chưa có chữ ký',
  submitCount: 1,
  attachments: [{ id: 3, originalName: 'don-cu.pdf', contentType: 'application/pdf', sizeBytes: 2048 }],
}

function apiError(status: number, message: string): AxiosError {
  const headers = new AxiosHeaders()
  return new AxiosError(message, 'ERR_BAD_REQUEST', { headers }, null, {
    status,
    statusText: message,
    headers: {},
    config: { headers },
    data: { status, message, fieldErrors: [] },
  })
}

function renderEdit() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AntApp>
        <CurrentUserContext.Provider value={LAN}>
          <MemoryRouter initialEntries={['/phieu/7/sua']}>
            <Routes>
              <Route path="/phieu/:id/sua" element={<EditTicketPage />} />
              <Route path="/phieu/:id" element={<p>Trang chi tiết phiếu</p>} />
            </Routes>
          </MemoryRouter>
        </CurrentUserContext.Provider>
      </AntApp>
    </QueryClientProvider>,
  )
}

function nextButton() {
  return screen.getByRole('button', { name: /Tiếp tục|Gửi lại/ })
}

describe('EditTicketPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(fetchAccountTypes).mockResolvedValue([
      { id: 3, code: 'TTCN', name: 'Thanh toán cá nhân', description: null, dormantAfterMonths: 12, accountCount: 0,
        updatedAt: '2026-10-01T00:00:00Z' },
    ])
  })

  it('D2, D3: điền sẵn hồ sơ, không chặn CCCD của chính phiếu; bỏ giấy tờ cũ, thêm giấy tờ mới rồi gửi lại', async () => {
    // Chuẩn bị: CCCD báo đang chờ duyệt ở chính hồ sơ 21 của phiếu này
    vi.mocked(fetchEditData).mockResolvedValue(NEW_CUSTOMER_TICKET)
    vi.mocked(checkCustomer).mockResolvedValue({ state: 'PENDING', customerId: 21, ticketCode: 'PYC-2026-0007' })
    vi.mocked(resubmitTicket).mockResolvedValue()
    renderEdit()

    // Kiểm tra kết quả: lý do từ chối ở đầu, hồ sơ điền sẵn
    expect(await screen.findByText('Lý do từ chối: Đơn chưa có chữ ký')).toBeInTheDocument()
    expect(screen.getByDisplayValue('Lê Minh Thử')).toBeInTheDocument()

    // Chạy: bước 1 → bước 2, bỏ giấy tờ cũ, thêm giấy tờ mới, sang bước 3, tick xác nhận rồi gửi lại
    fireEvent.click(nextButton())
    fireEvent.click(await screen.findByRole('checkbox', { name: /Bỏ don-cu.pdf/ }))
    const pdf = new File(['%PDF-1.7'], 'don-moi.pdf', { type: 'application/pdf' })
    fireEvent.change(document.querySelector('input[type="file"]') as HTMLInputElement, { target: { files: [pdf] } })
    expect(await screen.findByText('don-moi.pdf')).toBeInTheDocument()
    fireEvent.click(nextButton())
    fireEvent.click(await screen.findByRole('checkbox', { name: /Tôi đã đối chiếu/ }))
    fireEvent.click(nextButton())

    // Kiểm tra kết quả: gửi đúng dữ liệu và file, rồi về chi tiết phiếu
    expect(await screen.findByText('Trang chi tiết phiếu')).toBeInTheDocument()
    const [ticketId, data, files] = vi.mocked(resubmitTicket).mock.calls[0]
    expect(ticketId).toBe(7)
    expect(data).toEqual({
      accountTypeId: 3,
      newCustomer: {
        fullName: 'Lê Minh Thử', dateOfBirth: '1995-03-15', gender: 'Nam', cccd: '099399000101',
        idIssueDate: '2021-04-20', idIssuePlace: 'Cục CS QLHC về TTXH', phone: '0909000101',
        email: 'khach.thu@nhdemo.local', address: '12 Đường Số 1, Phường Thử',
      },
      reason: 'Khách mở tài khoản',
      removedAttachmentIds: [3],
      confirmed: true,
    })
    expect(files.map((file) => file.name)).toEqual(['don-moi.pdf'])
  })

  it('Bỏ hết giấy tờ cũ mà không thêm file mới thì không sang được bước sau', async () => {
    // Chuẩn bị
    vi.mocked(fetchEditData).mockResolvedValue({ ...NEW_CUSTOMER_TICKET, type: 'PHONG_TOA', newCustomer: null,
      fromStatus: 'ACTIVE', toStatus: 'BLOCKED', accountNumber: '1020000001' })
    renderEdit()
    await screen.findByText(/Phiếu này chỉ sửa được lý do và giấy tờ/)

    // Chạy
    fireEvent.click(nextButton())
    fireEvent.click(await screen.findByRole('checkbox', { name: /Bỏ don-cu.pdf/ }))
    fireEvent.click(nextButton())

    // Kiểm tra kết quả
    expect(await screen.findByText('Cần đính kèm ít nhất 1 giấy tờ')).toBeInTheDocument()
  })

  it('Người khác người tạo (edit-data trả 403) thấy trang 403', async () => {
    // Chuẩn bị, chạy
    vi.mocked(fetchEditData).mockRejectedValue(apiError(403, 'Chỉ người tạo phiếu được sửa'))
    renderEdit()

    // Kiểm tra kết quả
    expect(await screen.findByText('Bạn không có quyền truy cập trang này.')).toBeInTheDocument()
  })

  it('Phiếu không còn Bị từ chối (409) thì báo và có nút về chi tiết phiếu', async () => {
    // Chuẩn bị
    vi.mocked(fetchEditData).mockRejectedValue(apiError(409, 'Chỉ sửa được phiếu đang Bị từ chối'))
    renderEdit()

    // Chạy
    fireEvent.click(await screen.findByRole('button', { name: 'Về chi tiết phiếu' }))

    // Kiểm tra kết quả
    await waitFor(() => expect(screen.getByText('Trang chi tiết phiếu')).toBeInTheDocument())
  })
})