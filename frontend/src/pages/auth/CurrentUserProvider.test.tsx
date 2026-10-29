// Test 11 (spec auth mục 8): mở app mà /api/me trả 401 thì chuyển sang /oauth2/authorization/keycloak.
// vi.mock thay 2 hàm bằng hàm giả: fetchCurrentUser giả trả lỗi 401; redirectToLogin giả chỉ ghi lại là đã được gọi
// (jsdom không chuyển trang thật được). Các phần còn lại của 2 module giữ nguyên (importOriginal).
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios'
import { describe, expect, it, vi } from 'vitest'
import { redirectToLogin } from '../../api/httpClient.ts'
import CurrentUserProvider from './CurrentUserProvider.tsx'
import { fetchCurrentUser } from './currentUser.ts'

vi.mock('../../api/httpClient.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../api/httpClient.ts')>()
  return { ...original, redirectToLogin: vi.fn() }
})

vi.mock('./currentUser.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('./currentUser.ts')>()
  return { ...original, fetchCurrentUser: vi.fn() }
})

function unauthorizedError(): AxiosError {
  const response: AxiosResponse = {
    status: 401,
    statusText: 'Unauthorized',
    headers: {},
    config: { headers: new AxiosHeaders() },
    data: null,
  }
  return new AxiosError('Request failed with status code 401', 'ERR_BAD_REQUEST', undefined, undefined, response)
}

describe('CurrentUserProvider', () => {
  it('/api/me trả 401 thì chuyển trình duyệt sang trang đăng nhập của KeyCloak, không hiện nội dung app', async () => {
    // Chuẩn bị: chưa đăng nhập
    vi.mocked(fetchCurrentUser).mockRejectedValue(unauthorizedError())

    // Chạy
    render(
      <QueryClientProvider client={new QueryClient()}>
        <AntApp>
          <CurrentUserProvider>
            <div>Nội dung app</div>
          </CurrentUserProvider>
        </AntApp>
      </QueryClientProvider>,
    )

    // Kiểm tra kết quả
    await waitFor(() => expect(redirectToLogin).toHaveBeenCalled())
    expect(screen.queryByText('Nội dung app')).not.toBeInTheDocument()
  })
})