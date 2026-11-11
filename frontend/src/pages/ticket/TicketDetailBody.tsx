// Thân chi tiết phiếu (CN-15, spec phiếu mục 10.5): Steps, nội dung yêu cầu, giấy tờ đính kèm, lịch sử xử lý.
// Dùng ở trang 2.4 và cột phải của màn Chờ duyệt. CCCD đã che từ backend (QT-06); giấy tờ chỉ xem và tải qua
// backend, không có nút xóa (QT-10).
import { Link } from 'react-router'
import { Button, Card, Descriptions, Flex, List, Steps, Timeline, Typography } from 'antd'
import type { StepsProps } from 'antd'
import { DownloadOutlined, EyeOutlined, FileOutlined } from '@ant-design/icons'
import AccountStatusTag from '../../components/AccountStatusTag.tsx'
import CodeText from '../../components/CodeText.tsx'
import type { TicketStatus } from '../../components/ticketStatus.ts'
import { ticketTypeLabel } from '../../components/ticketType.ts'
import { formatDateTime } from '../catalog/format.ts'
import { attachmentUrl, type TicketDetail } from './ticketApi.ts'
import { formatFileSize, HISTORY_ACTION_TEXTS, historyNoteText } from './ticketFormat.ts'

interface TicketDetailBodyProps {
  ticket: TicketDetail
  /** Cột hẹp (cột phải màn Chờ duyệt): Steps nhỏ, chữ nằm dưới biểu tượng để 4 bước không bị ngắt dòng. */
  compact?: boolean
}

function TicketDetailBody({ ticket, compact = false }: TicketDetailBodyProps) {
  return (
    <Flex vertical gap={16}>
      <Card>
        <TicketSteps ticket={ticket} compact={compact} />
      </Card>
      <RequestContent ticket={ticket} />
      <Attachments ticket={ticket} />
      <History ticket={ticket} />
    </Flex>
  )
}

/** Tạo phiếu → Kiểm soát viên duyệt → Gửi mail → Hoàn tất; Bị từ chối, Đã hủy thì bước đang dừng báo lỗi. */
function TicketSteps({ ticket, compact }: { ticket: TicketDetail; compact: boolean }) {
  let current = 1
  let status: StepsProps['status'] = 'process'
  let reviewDescription = 'Đang chờ duyệt'
  if (ticket.status === 'REJECTED') {
    status = 'error'
    reviewDescription = 'Bị từ chối'
  } else if (ticket.status === 'CANCELLED') {
    status = 'error'
    reviewDescription = 'Đã hủy'
  } else if (ticket.status === 'APPROVED') {
    current = 2
    reviewDescription = 'Đã duyệt'
  } else if (ticket.status === 'COMPLETED') {
    current = 3
    status = 'finish'
    reviewDescription = 'Đã duyệt'
  }
  const items = [
    { title: 'Tạo phiếu', content: `${ticket.createdByName} · ${formatDateTime(ticket.createdAt)}` },
    { title: 'Kiểm soát viên duyệt', content: reviewDescription },
    { title: 'Gửi mail thông báo', content: 'Tự động sau khi duyệt' },
    { title: 'Hoàn tất', content: ticket.status === 'COMPLETED' ? 'Đã hoàn tất' : '' },
  ]
  if (compact) {
    return <Steps current={current} status={status} items={items} size="small" titlePlacement="vertical" />
  }
  return <Steps current={current} status={status} items={items} />
}

function RequestContent({ ticket }: { ticket: TicketDetail }) {
  const wording = changeWording(ticket.status)
  return (
    <Card title="Nội dung yêu cầu">
      {ticket.fromStatus && ticket.toStatus && (
        <Flex wrap align="center" gap={8} style={{ marginBottom: 16 }}>
          <Typography.Text>
            Tài khoản <CodeText>{ticket.accountNumber}</CodeText> {wording.before}
          </Typography.Text>
          <AccountStatusTag status={ticket.fromStatus} />→<AccountStatusTag status={ticket.toStatus} />
          {wording.after && <Typography.Text>{wording.after}</Typography.Text>}
        </Flex>
      )}
      <Descriptions bordered size="small" column={{ xs: 1, md: 2 }} items={contentItems(ticket)} />
    </Card>
  )
}

/**
 * Câu nói về thay đổi trạng thái tài khoản theo trạng thái phiếu (spec phiếu mục 10.5): đã duyệt thì "đã chuyển";
 * đã hủy thì "không chuyển ... vì phiếu đã hủy" (người dùng chốt 11/11/2026); còn lại "sẽ chuyển ... khi phiếu được duyệt".
 */
function changeWording(status: TicketStatus): { before: string; after: string | null } {
  if (status === 'APPROVED' || status === 'COMPLETED') {
    return { before: 'đã chuyển', after: null }
  }
  if (status === 'CANCELLED') {
    return { before: 'không chuyển', after: 'vì phiếu đã hủy' }
  }
  return { before: 'sẽ chuyển', after: 'khi phiếu được duyệt' }
}

function contentItems(ticket: TicketDetail) {
  const customer = ticket.customerId ? (
    <Link to={`/khach-hang/${ticket.customerId}`}>{ticket.customerName}</Link>
  ) : (
    '—'
  )
  const items = [
    { key: 'type', label: 'Loại phiếu', children: ticketTypeLabel(ticket.type, ticket.fromStatus) },
    { key: 'accountType', label: 'Loại tài khoản', children: ticket.accountTypeName ?? '—' },
    { key: 'customer', label: 'Chủ tài khoản', children: customer },
    { key: 'cccd', label: 'Số CCCD', children: <CodeText>{ticket.customerCccd ?? '—'}</CodeText> },
    { key: 'creator', label: 'Người tạo', children: ticket.createdByName },
    { key: 'branch', label: 'Chi nhánh tạo', children: ticket.branchName },
    { key: 'createdAt', label: 'Ngày tạo', children: formatDateTime(ticket.createdAt) },
    { key: 'submitCount', label: 'Số lần gửi', children: ticket.submitCount },
    { key: 'reason', label: 'Lý do', span: 'filled' as const, children: ticket.reason },
  ]
  if (ticket.newCustomer) {
    items.splice(3, 0, { key: 'newCustomer', label: 'Hồ sơ khách', children: 'Khách hàng mới' })
  }
  if (ticket.status === 'REJECTED' && ticket.rejectReason) {
    items.push({ key: 'rejectReason', label: 'Lý do từ chối', span: 'filled' as const, children: ticket.rejectReason })
  }
  return items
}

/** Giấy tờ đang dùng: Xem mở tab mới, Tải tải về; không có nút xóa (QT-10). */
function Attachments({ ticket }: { ticket: TicketDetail }) {
  return (
    <Card title="Giấy tờ đính kèm" extra={<Typography.Text type="secondary">{ticket.attachments.length} tệp</Typography.Text>}>
      <List
        dataSource={ticket.attachments}
        locale={{ emptyText: 'Không có giấy tờ' }}
        renderItem={(attachment) => (
          <List.Item
            actions={[
              <Button
                key="view"
                type="link"
                icon={<EyeOutlined />}
                href={attachmentUrl(ticket.id, attachment.id, false)}
                target="_blank"
                rel="noopener noreferrer"
              >
                Xem
              </Button>,
              <Button key="download" type="link" icon={<DownloadOutlined />} href={attachmentUrl(ticket.id, attachment.id, true)}>
                Tải
              </Button>,
            ]}
          >
            <List.Item.Meta
              avatar={<FileOutlined />}
              title={attachment.originalName}
              description={formatFileSize(attachment.sizeBytes)}
            />
          </List.Item>
        )}
      />
    </Card>
  )
}

/** Lịch sử xử lý, mới nhất ở trên; phiếu đang chờ thì dòng đầu là bước hiện tại. */
function History({ ticket }: { ticket: TicketDetail }) {
  const items = ticket.history.map((entry, index) => ({
    key: `${entry.createdAt}-${index}`,
    children: (
      <Flex vertical>
        <Typography.Text type="secondary">{formatDateTime(entry.createdAt)}</Typography.Text>
        <Typography.Text>
          {entry.actorName ?? 'Hệ thống'} {HISTORY_ACTION_TEXTS[entry.action]}
        </Typography.Text>
        {entry.note && <Typography.Text type="secondary">{historyNoteText(entry.action, entry.note)}</Typography.Text>}
      </Flex>
    ),
  }))
  if (ticket.status === 'PENDING_APPROVAL') {
    items.unshift({
      key: 'current',
      children: (
        <Flex vertical>
          <Typography.Text type="secondary">Hiện tại</Typography.Text>
          <Typography.Text>Chờ kiểm soát viên duyệt</Typography.Text>
        </Flex>
      ),
    })
  }
  return (
    <Card title="Lịch sử xử lý">
      <Timeline items={items} />
    </Card>
  )
}

export default TicketDetailBody