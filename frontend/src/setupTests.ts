// Chạy trước mọi file test của Vitest (khai báo ở vite.config.ts, mục test.setupFiles).
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// Import jest-dom ở trên thêm các hàm kiểm tra như toBeInTheDocument() cho expect.
// Sau mỗi test, gỡ component đã render để test sau bắt đầu với trang trống.
afterEach(() => {
  cleanup()
})