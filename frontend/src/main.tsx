// Điểm vào của React: bọc toàn app bằng các provider dùng chung rồi hiển thị router.
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App as AntApp, ConfigProvider } from 'antd'
import viVN from 'antd/locale/vi_VN'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router/dom'
import 'antd/dist/reset.css'
import { router } from './router.tsx'
import { theme } from './theme/theme.ts'

// Nơi TanStack Query giữ cache kết quả gọi API, dùng chung cho toàn app.
const queryClient = new QueryClient()

// ConfigProvider: theme token và chữ tiếng Việt cho mọi component AntD.
// AntApp: cho phép gọi message, notification, modal qua App.useApp() trong component.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ConfigProvider theme={theme} locale={viVN}>
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>
  </StrictMode>,
)