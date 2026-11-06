// Tag trạng thái phiếu theo bản thiết kế (spec phiếu mục 10.1): Chờ duyệt xanh dương, Đã duyệt xanh lá, Bị từ chối đỏ,
// Hoàn tất nền xanh lá đậm chữ trắng, Đã hủy xám. Dùng ở mọi màn có phiếu.
// Hoàn tất dùng màu colorSuccess của theme: Tag nhận mã màu thì tự tô nền đặc, chữ trắng.
import { Tag, theme } from 'antd'
import { TICKET_STATUS_LABELS, type TicketStatus } from './ticketStatus.ts'

const PRESET_COLORS: Record<Exclude<TicketStatus, 'COMPLETED'>, string> = {
  PENDING_APPROVAL: 'blue',
  APPROVED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
}

function TicketStatusTag({ status }: { status: TicketStatus }) {
  const { token } = theme.useToken()
  const color = status === 'COMPLETED' ? token.colorSuccess : PRESET_COLORS[status]
  return <Tag color={color}>{TICKET_STATUS_LABELS[status]}</Tag>
}

export default TicketStatusTag