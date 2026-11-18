// Nút Xem, Tải giấy tờ (CN-15, QT-10): tải file qua API như mọi API khác, nên hết phiên hay giấy tờ không có thì báo
// ngay trong app, trang đang mở không bị thay bằng chữ JSON của backend. jsdom (trình duyệt giả của test) không có
// URL.createObjectURL và window.open, nên test thay bằng hàm giả và ghi lại link được bấm.
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { App as AntApp } from 'antd'
import { AxiosError, AxiosHeaders } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TicketAttachments from './TicketAttachments.tsx'
import { fetchAttachment } from './ticketApi.ts'

vi.mock('./ticketApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('./ticketApi.ts')>()
  return { ...original, fetchAttachment: vi.fn() }
})

const ATTACHMENT = { id: 3, originalName: 'đơn đề nghị.pdf', contentType: 'application/pdf', sizeBytes: 421888 }
const PDF_FILE = new Blob(['%PDF-1.7 giay to gia'], { type: 'application/pdf' })

/** Lỗi dạng chung của backend như axios ném ra. */
function apiError(status: number, message: string): AxiosError {
  const headers = new AxiosHeaders()
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', { headers }, null, {
    status,
    statusText: 'Error',
    headers: {},
    config: { headers },
    data: { status, message, fieldErrors: [] },
  })
}

/** Tab giả mà window.open trả về: ghi lại địa chỉ được mở và việc tab bị đóng. */
function fakeTab() {
  return { opener: {} as unknown, location: { href: '' }, close: vi.fn() }
}

function renderAttachments() {
  render(
    <AntApp>
      <TicketAttachments ticketId={7} attachments={[ATTACHMENT]} />
    </AntApp>,
  )
}

describe('TicketAttachments', () => {
  let clickedLinks: { href: string; download: string }[]

  beforeEach(() => {
    vi.resetAllMocks()
    clickedLinks = []
    URL.createObjectURL = vi.fn(() => 'blob:giay-to')
    URL.revokeObjectURL = vi.fn()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      clickedLinks.push({ href: this.href, download: this.download })
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('Tải: lấy file qua API rồi lưu về máy bằng tên gốc', async () => {
    // Chuẩn bị
    vi.mocked(fetchAttachment).mockResolvedValue(PDF_FILE)
    renderAttachments()

    // Chạy
    fireEvent.click(screen.getByRole('button', { name: /Tải/ }))

    // Kiểm tra kết quả
    await waitFor(() => expect(clickedLinks).toEqual([{ href: 'blob:giay-to', download: 'đơn đề nghị.pdf' }]))
    expect(fetchAttachment).toHaveBeenCalledWith(7, 3)
    expect(URL.createObjectURL).toHaveBeenCalledWith(PDF_FILE)
  })

  it('Xem: mở tab mới ngay lúc bấm rồi hiện file trong tab đó', async () => {
    // Chuẩn bị
    const tab = fakeTab()
    vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window)
    vi.mocked(fetchAttachment).mockResolvedValue(PDF_FILE)
    renderAttachments()

    // Chạy
    fireEvent.click(screen.getByRole('button', { name: /Xem/ }))

    // Kiểm tra kết quả
    await waitFor(() => expect(tab.location.href).toBe('blob:giay-to'))
    expect(window.open).toHaveBeenCalledWith('', '_blank')
    expect(tab.opener).toBeNull()
    expect(tab.close).not.toHaveBeenCalled()
  })

  it('Hết phiên lúc bấm Xem: đóng tab vừa mở, không hiện thêm thông báo lỗi (hộp thoại hết phiên do httpClient mở)',
    async () => {
      // Chuẩn bị
      const tab = fakeTab()
      vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window)
      vi.mocked(fetchAttachment).mockRejectedValue(apiError(401, 'Bạn chưa đăng nhập hoặc phiên đăng nhập đã hết hạn.'))
      renderAttachments()

      // Chạy
      fireEvent.click(screen.getByRole('button', { name: /Xem/ }))

      // Kiểm tra kết quả
      await waitFor(() => expect(tab.close).toHaveBeenCalled())
      expect(screen.queryByText(/phiên đăng nhập đã hết hạn/)).not.toBeInTheDocument()
    })

  it('Giấy tờ không có (404) khi bấm Tải: báo câu của backend ngay trong app, không lưu file nào', async () => {
    // Chuẩn bị
    vi.mocked(fetchAttachment).mockRejectedValue(apiError(404, 'Không tìm thấy giấy tờ'))
    renderAttachments()

    // Chạy
    fireEvent.click(screen.getByRole('button', { name: /Tải/ }))

    // Kiểm tra kết quả
    expect(await screen.findByText('Không tìm thấy giấy tờ')).toBeInTheDocument()
    expect(clickedLinks).toEqual([])
  })

  it('Trình duyệt chặn cửa sổ mới khi bấm Xem: nhắc dùng nút Tải, không gọi API', async () => {
    // Chuẩn bị
    vi.spyOn(window, 'open').mockReturnValue(null)
    renderAttachments()

    // Chạy
    fireEvent.click(screen.getByRole('button', { name: /Xem/ }))

    // Kiểm tra kết quả
    expect(await screen.findByText(/Trình duyệt đang chặn cửa sổ mới/)).toBeInTheDocument()
    expect(fetchAttachment).not.toHaveBeenCalled()
  })
})