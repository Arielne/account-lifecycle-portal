// Lỗi tìm thấy khi thử bằng tay: dán "028 3872 0011" vào ô điện thoại thì chỉ còn 9 chữ số, vì ô có maxLength 11
// và trình duyệt cắt chuỗi dán vào (tính cả dấu cách) trước khi normalize bỏ dấu cách.
// jsdom không tự cắt theo maxLength, nên test kiểm 2 điều: ô không có maxlength, và normalize giữ đủ chữ số.
import { fireEvent, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { describe, expect, it } from 'vitest'
import BranchFormModal from './BranchFormModal.tsx'

describe('Modal thêm chi nhánh', () => {
  it('Dán số điện thoại có dấu cách thì ô giữ đủ 11 chữ số', () => {
    // Chuẩn bị
    render(
      <QueryClientProvider client={new QueryClient()}>
        <AntApp>
          <BranchFormModal branch={null} onClose={() => {}} />
        </AntApp>
      </QueryClientProvider>,
    )
    const phoneInput = screen.getByLabelText('Số điện thoại')

    // Chạy
    fireEvent.change(phoneInput, { target: { value: '028 3872 0011' } })

    // Kiểm tra kết quả
    expect(phoneInput).not.toHaveAttribute('maxlength')
    expect(phoneInput).toHaveValue('02838720011')
  })
})