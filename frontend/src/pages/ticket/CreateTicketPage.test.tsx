// Test 26 của spec phiếu: màn Tạo phiếu. Tham số URL mở thẳng bước 2 đã điền sẵn; tài khoản không hợp lệ thì báo
// dưới ô và khóa Tiếp tục; ô CCCD che khi rời ô (QT-05); customer-check EXISTS khóa Tiếp tục, REUSABLE hiện Alert mà
// vẫn đi tiếp; chưa tick xác nhận thì không gửi. vi.mock thay các hàm gọi API bằng hàm giả.
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { CurrentUserContext, type CurrentUser } from '../auth/currentUser.ts'
import { fetchAccountTypes } from '../catalog/accountTypeApi.ts'
import CreateTicketPage from './CreateTicketPage.tsx'
import { checkCustomer, checkEligibility, createTicket, type TicketEligibility } from './ticketApi.ts'

vi.mock('./ticketApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('./ticketApi.ts')>()
  return { ...original, checkEligibility: vi.fn(), checkCustomer: vi.fn(), createTicket: vi.fn() }
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

function eligibility(allowed: boolean): TicketEligibility {
  return {
    accountId: 47,
    accountNumber: '1020000050',
    customerId: 21,
    customerName: 'Đỗ Thị Bình',
    customerCccd: '0991****0002',
    accountTypeName: 'Thanh toán cá nhân',
    branchName: 'Chi nhánh Tân Phú',
    currentStatus: 'ACTIVE',
    targetStatus: 'BLOCKED',
    allowed,
    message: allowed ? null : 'Tài khoản đang có phiếu PYC-2026-0418 chờ duyệt',
  }
}

function renderAt(path: string) {
  vi.mocked(fetchAccountTypes).mockResolvedValue([
    { id: 3, code: 'TTCN', name: 'Thanh toán cá nhân', description: null, dormantAfterMonths: 12, accountCount: 0,
      updatedAt: '2026-10-01T00:00:00Z' },
  ])
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AntApp>
        <CurrentUserContext.Provider value={LAN}>
          <MemoryRouter initialEntries={[path]}>
            <CreateTicketPage />
          </MemoryRouter>
        </CurrentUserContext.Provider>
      </AntApp>
    </QueryClientProvider>,
  )
}

function nextButton() {
  return screen.getByRole('button', { name: /Tiếp tục|Gửi phiếu/ })
}

describe('CreateTicketPage', () => {
  it('?loai=PHONG_TOA&taiKhoan=... mở thẳng bước 2, số tài khoản điền sẵn và được kiểm ngay', async () => {
    // Chuẩn bị
    vi.mocked(checkEligibility).mockResolvedValue(eligibility(true))

    // Chạy
    renderAt('/phieu/tao-moi?loai=PHONG_TOA&taiKhoan=1020000050')

    // Kiểm tra kết quả: thẻ tài khoản hiện, CCCD đã che, nút Tiếp tục mở
    expect(await screen.findByText(/Hợp lệ: trạng thái cho phép/)).toBeInTheDocument()
    expect(checkEligibility).toHaveBeenCalledWith('PHONG_TOA', '1020000050')
    expect(screen.getByDisplayValue('1020000050')).toBeInTheDocument()
    expect(screen.getByText('0991****0002')).toBeInTheDocument()
    expect(nextButton()).toBeEnabled()
  })

  it('Tài khoản không hợp lệ: báo lý do dưới ô, khóa nút Tiếp tục, thẻ chỉ hiện trạng thái hiện tại', async () => {
    // Chuẩn bị
    vi.mocked(checkEligibility).mockResolvedValue(eligibility(false))

    // Chạy
    renderAt('/phieu/tao-moi?loai=PHONG_TOA&taiKhoan=1020000050')

    // Kiểm tra kết quả: không hiện trạng thái sau (Phong tỏa) vì phiếu không tạo được
    expect(await screen.findByText('Tài khoản đang có phiếu PYC-2026-0418 chờ duyệt')).toBeInTheDocument()
    await waitFor(() => expect(nextButton()).toBeDisabled())
    expect(screen.getByText('Hoạt động')).toBeInTheDocument()
    expect(screen.queryByText('Phong tỏa')).not.toBeInTheDocument()
  })

  it('QT-05: ô CCCD hiện đủ số khi đang gõ, che phần giữa khi rời ô; số đã có hồ sơ thì khóa Tiếp tục', async () => {
    // Chuẩn bị
    vi.mocked(checkCustomer).mockResolvedValue({ state: 'EXISTS', customerId: 21, ticketCode: null })
    renderAt('/phieu/tao-moi?loai=MO_TAI_KHOAN')
    const cccdInput = await screen.findByPlaceholderText('Nhập đủ 12 số CCCD')

    // Chạy: gõ đủ 12 số rồi rời ô
    fireEvent.focus(cccdInput)
    fireEvent.change(cccdInput, { target: { value: '099190000002' } })
    expect(cccdInput).toHaveValue('099190000002')
    fireEvent.blur(cccdInput)

    // Kiểm tra kết quả
    expect(cccdInput).toHaveValue('0991****0002')
    expect(await screen.findByText('Số CCCD đã có hồ sơ khách hàng. Hãy chọn Khách hàng đã có')).toBeInTheDocument()
    expect(checkCustomer).toHaveBeenCalledWith('099190000002')
    await waitFor(() => expect(nextButton()).toBeDisabled())
  })

  it('D6: số CCCD thuộc hồ sơ chưa từng mở tài khoản thì hiện cảnh báo ghi đè, vẫn đi tiếp được', async () => {
    // Chuẩn bị
    vi.mocked(checkCustomer).mockResolvedValue({ state: 'REUSABLE', customerId: 21, ticketCode: null })
    renderAt('/phieu/tao-moi?loai=MO_TAI_KHOAN')
    const cccdInput = await screen.findByPlaceholderText('Nhập đủ 12 số CCCD')

    // Chạy
    fireEvent.change(cccdInput, { target: { value: '099190000002' } })
    fireEvent.blur(cccdInput)

    // Kiểm tra kết quả
    expect(await screen.findByText(/có hồ sơ cũ chưa từng mở được tài khoản/)).toBeInTheDocument()
    expect(nextButton()).toBeEnabled()
  })

  it('Chưa tick xác nhận đối chiếu giấy tờ thì không gửi được; tick xong thì gửi đúng dữ liệu kèm file', async () => {
    // Chuẩn bị: đi tới bước 4 của phiếu Phong tỏa
    vi.mocked(checkEligibility).mockResolvedValue(eligibility(true))
    vi.mocked(createTicket).mockResolvedValue({ id: 7, code: 'PYC-2026-0007' })
    renderAt('/phieu/tao-moi?loai=PHONG_TOA&taiKhoan=1020000050')
    await screen.findByText(/Hợp lệ: trạng thái cho phép/)
    fireEvent.click(nextButton())
    fireEvent.change(await screen.findByRole('textbox', { name: 'Lý do' }), { target: { value: 'Khách báo mất thẻ' } })
    const pdf = new File(['%PDF-1.7'], 'don-de-nghi.pdf', { type: 'application/pdf' })
    fireEvent.change(document.querySelector('input[type="file"]') as HTMLInputElement, { target: { files: [pdf] } })
    expect(await screen.findByText('don-de-nghi.pdf')).toBeInTheDocument()
    fireEvent.click(nextButton())
    expect(await screen.findByText('Kiểm tra lại trước khi gửi')).toBeInTheDocument()

    // Chạy: bấm Gửi khi chưa tick
    fireEvent.click(nextButton())

    // Kiểm tra kết quả
    expect(await screen.findByText('Cần xác nhận đã đối chiếu thông tin với giấy tờ gốc')).toBeInTheDocument()
    expect(createTicket).not.toHaveBeenCalled()

    // Chạy: tick rồi gửi
    fireEvent.click(screen.getByRole('checkbox', { name: /Tôi đã đối chiếu/ }))
    fireEvent.click(nextButton())

    // Kiểm tra kết quả: dữ liệu không có chi nhánh (QT-04), kèm đúng file
    expect(await screen.findByText('PYC-2026-0007')).toBeInTheDocument()
    const [data, files] = vi.mocked(createTicket).mock.calls[0]
    expect(data).toEqual({
      type: 'PHONG_TOA',
      customerId: null,
      newCustomer: null,
      accountTypeId: null,
      accountNumber: '1020000050',
      reason: 'Khách báo mất thẻ',
      confirmed: true,
    })
    expect(files.map((file) => file.name)).toEqual(['don-de-nghi.pdf'])
  })
})