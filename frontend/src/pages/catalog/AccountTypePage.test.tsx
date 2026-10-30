// Test 12 (spec admin mục 8): nút Xóa loại tài khoản bị khóa khi còn tài khoản dùng; Form báo lỗi đúng ô
// khi backend trả fieldErrors. vi.mock thay các hàm gọi API bằng hàm giả, phần còn lại của module giữ nguyên.
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios'
import { describe, expect, it, vi } from 'vitest'
import type { ApiErrorResponse } from '../../api/apiError.ts'
import AccountTypePage from './AccountTypePage.tsx'
import { createAccountType, fetchAccountTypes, type AccountType } from './accountTypeApi.ts'

vi.mock('./accountTypeApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('./accountTypeApi.ts')>()
  return { ...original, fetchAccountTypes: vi.fn(), createAccountType: vi.fn() }
})

function accountType(id: number, code: string, name: string, accountCount: number): AccountType {
  return { id, code, name, description: null, dormantAfterMonths: 12, accountCount, updatedAt: '2026-10-18T10:00:00Z' }
}

function badRequest(body: ApiErrorResponse): AxiosError {
  const response: AxiosResponse = {
    status: 400,
    statusText: 'Bad Request',
    headers: {},
    config: { headers: new AxiosHeaders() },
    data: body,
  }
  return new AxiosError('Request failed with status code 400', 'ERR_BAD_REQUEST', undefined, undefined, response)
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <AntApp>
        <AccountTypePage />
      </AntApp>
    </QueryClientProvider>,
  )
}

/** Khối Form.Item (nhãn, ô nhập, chỗ hiện lỗi) chứa ô có nhãn này. */
function formItemOf(label: string): HTMLElement {
  return screen.getByLabelText(label).closest('.ant-form-item') as HTMLElement
}

describe('Màn Loại tài khoản', () => {
  it('Nút Xóa bị khóa khi còn tài khoản dùng loại đó; loại chưa ai dùng thì bấm được', async () => {
    // Chuẩn bị: TKL có 612 tài khoản, TKSV chưa có
    vi.mocked(fetchAccountTypes).mockResolvedValue([
      accountType(2, 'TKL', 'Tài khoản lương', 612),
      accountType(4, 'TKSV', 'Tài khoản sinh viên', 0),
    ])

    // Chạy
    renderPage()

    // Kiểm tra kết quả
    const salaryRow = (await screen.findByText('TKL')).closest('tr') as HTMLElement
    expect(within(salaryRow).getByRole('button', { name: 'Xóa' })).toBeDisabled()
    const studentRow = screen.getByText('TKSV').closest('tr') as HTMLElement
    expect(within(studentRow).getByRole('button', { name: 'Xóa' })).toBeEnabled()
  })

  it('Backend trả fieldErrors thì lỗi hiện dưới đúng ô của Form', async () => {
    // Chuẩn bị: backend từ chối ô tên
    vi.mocked(fetchAccountTypes).mockResolvedValue([])
    vi.mocked(createAccountType).mockRejectedValue(
      badRequest({
        status: 400,
        message: 'Dữ liệu chưa hợp lệ',
        fieldErrors: [{ field: 'name', message: 'Tên do backend báo lỗi' }],
      }),
    )
    renderPage()

    // Chạy: mở Modal thêm, nhập đúng quy tắc phía React rồi bấm Lưu
    fireEvent.click(screen.getByRole('button', { name: /Thêm loại tài khoản/ }))
    fireEvent.change(screen.getByLabelText('Mã loại'), { target: { value: 'tkmoi' } })
    fireEvent.change(screen.getByLabelText('Tên loại tài khoản'), { target: { value: 'Tài khoản mới' } })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))

    // Kiểm tra kết quả: mã đã được đổi sang chữ in hoa trước khi gửi; lỗi nằm ở ô tên, không ở ô mã
    await waitFor(() => expect(within(formItemOf('Tên loại tài khoản')).getByText('Tên do backend báo lỗi')).toBeInTheDocument())
    expect(createAccountType).toHaveBeenCalledWith(expect.objectContaining({ code: 'TKMOI', dormantAfterMonths: 12 }))
    expect(within(formItemOf('Mã loại')).queryByText('Tên do backend báo lỗi')).not.toBeInTheDocument()
  })
})