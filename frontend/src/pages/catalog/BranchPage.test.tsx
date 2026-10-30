// Màn Chi nhánh (spec admin mục 5): xóa phải xác nhận; chi nhánh còn được dùng thì hiện câu báo lỗi của backend.
import { fireEvent, render, screen, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios'
import { describe, expect, it, vi } from 'vitest'
import BranchPage from './BranchPage.tsx'
import { deleteBranch, fetchBranches } from './branchApi.ts'

vi.mock('./branchApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('./branchApi.ts')>()
  return { ...original, fetchBranches: vi.fn(), deleteBranch: vi.fn() }
})

function conflict(message: string): AxiosError {
  const response: AxiosResponse = {
    status: 409,
    statusText: 'Conflict',
    headers: {},
    config: { headers: new AxiosHeaders() },
    data: { status: 409, message, fieldErrors: [] },
  }
  return new AxiosError('Request failed with status code 409', 'ERR_BAD_REQUEST', undefined, undefined, response)
}

describe('Màn Chi nhánh', () => {
  it('Xóa chi nhánh đang được dùng: hỏi xác nhận, rồi hiện câu báo lỗi của backend', async () => {
    // Chuẩn bị: chi nhánh Quận 1 còn nhân viên và tài khoản, backend trả 409
    vi.mocked(fetchBranches).mockResolvedValue([
      {
        id: 2,
        code: 'CN-Q1',
        name: 'Chi nhánh Quận 1',
        address: '45 Lê Thánh Tôn',
        phone: '02838234510',
        employeeCount: 14,
        accountCount: 812,
      },
    ])
    const backendMessage = 'Không xóa được Chi nhánh Quận 1: còn 14 nhân viên, 812 tài khoản thuộc chi nhánh này.'
    vi.mocked(deleteBranch).mockRejectedValue(conflict(backendMessage))
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <AntApp>
          <BranchPage />
        </AntApp>
      </QueryClientProvider>,
    )

    // Chạy: bấm Xóa ở dòng chi nhánh, chưa gọi API cho tới khi xác nhận
    const row = (await screen.findByText('CN-Q1')).closest('tr') as HTMLElement
    expect(within(row).getByText('028 3823 4510')).toBeInTheDocument()
    fireEvent.click(within(row).getByRole('button', { name: 'Xóa' }))
    expect(deleteBranch).not.toHaveBeenCalled()
    const confirmDialog = await screen.findByRole('dialog')
    fireEvent.click(within(confirmDialog).getByRole('button', { name: 'Xóa' }))

    // Kiểm tra kết quả
    expect(await screen.findByText(backendMessage)).toBeInTheDocument()
    expect(vi.mocked(deleteBranch).mock.calls[0][0]).toBe(2)
  })
})