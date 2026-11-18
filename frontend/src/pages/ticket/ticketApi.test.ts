// Hàm tải giấy tờ (QT-10): axios đọc thân response thành Blob, kể cả khi backend trả lỗi dạng JSON; hàm phải đổi thân
// lỗi lại thành JSON để readApiError đọc được câu báo lỗi. vi.mock thay httpClient bằng hàm giả.
import { AxiosError, AxiosHeaders } from 'axios'
import { describe, expect, it, vi } from 'vitest'
import { readApiError } from '../../api/apiError.ts'
import { httpClient } from '../../api/httpClient.ts'
import { fetchAttachment } from './ticketApi.ts'

vi.mock('../../api/httpClient.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../api/httpClient.ts')>()
  return { ...original, httpClient: { get: vi.fn() } }
})

/** Lỗi axios mà thân lỗi là Blob, như khi gọi với responseType 'blob'. */
function blobError(status: number, body: string, type: string): AxiosError {
  const headers = new AxiosHeaders()
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', { headers }, null, {
    status,
    statusText: 'Error',
    headers: {},
    config: { headers },
    data: new Blob([body], { type }),
  })
}

describe('fetchAttachment', () => {
  it('Lỗi trả về dạng Blob chứa JSON thì đổi thành JSON: đọc được câu "Không tìm thấy giấy tờ"', async () => {
    // Chuẩn bị
    const body = JSON.stringify({ status: 404, message: 'Không tìm thấy giấy tờ', fieldErrors: [] })
    vi.mocked(httpClient.get).mockRejectedValue(blobError(404, body, 'application/json'))

    // Chạy
    const error = await fetchAttachment(7, 99).catch((caught: unknown) => caught)

    // Kiểm tra kết quả
    expect(readApiError(error)?.message).toBe('Không tìm thấy giấy tờ')
  })

  it('Thân lỗi không phải JSON (trang lỗi HTML) thì giữ nguyên, readApiError trả null để màn hiện câu chung',
    async () => {
      // Chuẩn bị
      vi.mocked(httpClient.get).mockRejectedValue(blobError(502, '<html>Bad Gateway</html>', 'text/html'))

      // Chạy
      const error = await fetchAttachment(7, 3).catch((caught: unknown) => caught)

      // Kiểm tra kết quả
      expect(readApiError(error)).toBeNull()
    })

  it('Thành công thì trả đúng Blob của file, gọi API với responseType blob', async () => {
    // Chuẩn bị
    const file = new Blob(['%PDF-1.7'], { type: 'application/pdf' })
    vi.mocked(httpClient.get).mockResolvedValue({ data: file })

    // Chạy
    const result = await fetchAttachment(7, 3)

    // Kiểm tra kết quả
    expect(result).toBe(file)
    expect(httpClient.get).toHaveBeenCalledWith('/api/tickets/7/attachments/3', { responseType: 'blob' })
  })
})