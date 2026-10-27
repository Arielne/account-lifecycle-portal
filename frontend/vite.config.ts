/// <reference types="vitest/config" />
// Dòng trên cho TypeScript biết mục `test` (cấu hình của Vitest) bên dưới.
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Backend Spring Boot chạy trên máy (IDE hoặc mvnw) ở cổng 8081.
const backendUrl = 'http://localhost:8081'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Vite proxy: request tới 4 đường dẫn này được chuyển sang backend, nên trình duyệt thấy React
    // và backend cùng một origin (localhost:5173), giống như khi đi qua Nginx ở cổng 80.
    // Không bật changeOrigin: giữ nguyên Host localhost:5173 để backend tạo đúng địa chỉ quay về khi đăng nhập.
    proxy: {
      '/api/': backendUrl,
      '/oauth2/': backendUrl,
      '/login/oauth2/': backendUrl,
      '/logout': backendUrl,
    },
  },
  // Vitest: chạy test trong jsdom (trình duyệt giả bằng JavaScript), không cần mở trình duyệt thật.
  test: {
    environment: 'jsdom',
    setupFiles: './src/setupTests.ts',
  },
})