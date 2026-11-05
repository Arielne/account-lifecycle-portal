// Tag trạng thái tài khoản theo bản thiết kế: nhãn tiếng Việt, mỗi trạng thái một màu.
import { Tag } from 'antd'
import { ACCOUNT_STATUS_COLORS, ACCOUNT_STATUS_LABELS, type AccountStatus } from './accountStatus.ts'

function AccountStatusTag({ status }: { status: AccountStatus }) {
  return <Tag color={ACCOUNT_STATUS_COLORS[status]}>{ACCOUNT_STATUS_LABELS[status]}</Tag>
}

export default AccountStatusTag