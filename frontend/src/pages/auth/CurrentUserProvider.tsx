// Gọi GET /api/me một lần khi mở app (TanStack Query), rồi để kết quả trong React Context (CN-01, CN-05).
// Chưa đăng nhập (401): chuyển thẳng trình duyệt sang trang đăng nhập của KeyCloak.
import { useEffect, type ReactNode } from 'react'
import { Button, Result, Spin } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { isUnauthorizedError, redirectToLogin } from '../../api/httpClient.ts'
import { CurrentUserContext, fetchCurrentUser } from './currentUser.ts'

interface CurrentUserProviderProps {
  children: ReactNode
}

function CurrentUserProvider({ children }: CurrentUserProviderProps) {
  // retry: false để 401 chuyển sang trang đăng nhập ngay, không thử lại 3 lần.
  const { data, error, isPending } = useQuery({
    queryKey: ['current-user'],
    queryFn: fetchCurrentUser,
    retry: false,
  })
  const isNotLoggedIn = isUnauthorizedError(error)

  useEffect(() => {
    if (isNotLoggedIn) {
      redirectToLogin()
    }
  }, [isNotLoggedIn])

  // Đang tải, hoặc đang chuyển sang trang đăng nhập: Spin giữa trang.
  if (isPending || isNotLoggedIn) {
    return <Spin fullscreen />
  }

  if (error) {
    return (
      <Result
        status="500"
        title="Không kết nối được máy chủ"
        subTitle="Dữ liệu chưa bị thay đổi. Vui lòng thử lại sau ít phút."
        extra={<Button onClick={() => window.location.reload()}>Thử lại</Button>}
      />
    )
  }

  return <CurrentUserContext.Provider value={data}>{children}</CurrentUserContext.Provider>
}

export default CurrentUserProvider