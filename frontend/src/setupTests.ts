// Chạy trước mọi file test của Vitest (khai báo ở vite.config.ts, mục test.setupFiles).
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

// jsdom (trình duyệt giả của Vitest) thiếu 2 API mà Table, Row/Col, Input của AntD dùng để đo kích thước màn hình.
// vi.stubGlobal gắn bản giả tối thiểu vào window: không bao giờ phát sự kiện, đủ để component chạy trong test.
vi.stubGlobal('matchMedia', (query: string) => ({
  matches: false,
  media: query,
  onchange: null,
  addListener: () => {},
  removeListener: () => {},
  addEventListener: () => {},
  removeEventListener: () => {},
  dispatchEvent: () => false,
}))

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
vi.stubGlobal('ResizeObserver', ResizeObserverStub)

// Import jest-dom ở trên thêm các hàm kiểm tra như toBeInTheDocument() cho expect.
// Sau mỗi test, gỡ component đã render để test sau bắt đầu với trang trống.
afterEach(() => {
  cleanup()
})