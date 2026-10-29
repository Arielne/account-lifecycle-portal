// Hook đọc người đang đăng nhập từ React Context. Dùng được ở mọi component nằm trong CurrentUserProvider.
import { useContext } from 'react'
import { CurrentUserContext, type CurrentUser } from './currentUser.ts'

export function useCurrentUser(): CurrentUser {
  const currentUser = useContext(CurrentUserContext)
  if (currentUser === null) {
    throw new Error('useCurrentUser phải được gọi bên trong CurrentUserProvider')
  }
  return currentUser
}