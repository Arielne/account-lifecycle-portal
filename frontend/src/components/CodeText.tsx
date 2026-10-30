// Mã nghiệp vụ (mã loại, mã chi nhánh, điện thoại...) hiện bằng font mono như bản thiết kế.
// Font lấy từ token fontFamilyCode của theme (theme.useToken), không ghi cứng tên font ở đây.
// nowrap: bảng hẹp thì cột khác xuống dòng, không ngắt đôi một mã hay một số điện thoại.
import type { ReactNode } from 'react'
import { theme } from 'antd'

function CodeText({ children }: { children: ReactNode }) {
  const { token } = theme.useToken()
  return <span style={{ fontFamily: token.fontFamilyCode, whiteSpace: 'nowrap' }}>{children}</span>
}

export default CodeText