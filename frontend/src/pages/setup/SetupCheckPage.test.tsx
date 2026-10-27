// Test mẫu của frontend (Vitest + React Testing Library) cho trang tạm SetupCheckPage.
import { fireEvent, render, screen } from '@testing-library/react'
import { App as AntApp } from 'antd'
import { describe, expect, it } from 'vitest'
import SetupCheckPage from './SetupCheckPage.tsx'

describe('SetupCheckPage', () => {
  it('bấm nút "Bấm thử" thì hiện thông báo thành công', async () => {
    // Chuẩn bị: bọc trang trong AntApp giống main.tsx, vì trang lấy message từ App.useApp().
    render(
      <AntApp>
        <SetupCheckPage />
      </AntApp>,
    )

    // Chạy
    fireEvent.click(screen.getByRole('button', { name: /Bấm thử/ }))

    // Kiểm tra kết quả: AntD hiện thông báo sau một nhịp nên dùng findByText (có chờ).
    expect(await screen.findByText('Ant Design đã nhận theme và chạy được.')).toBeInTheDocument()
  })
})