// Bảng phiếu dùng chung (CN-14, spec phiếu mục 10.4, 10.6): màn Tất cả phiếu, Lịch sử phiếu ở chi tiết tài khoản, tab
// Phiếu yêu cầu ở chi tiết khách hàng. Mỗi nơi một bộ cột theo bản thiết kế (variant). Phân trang ở backend.
import { Link } from 'react-router'
import { Flex, Table, Typography } from 'antd'
import type { TableColumnsType, TablePaginationConfig } from 'antd'
import CodeText from '../../components/CodeText.tsx'
import TicketStatusTag from '../../components/TicketStatusTag.tsx'
import { ticketTypeLabel } from '../../components/ticketType.ts'
import { formatDate, formatDateTime } from '../catalog/format.ts'
import type { TicketListItem } from './ticketApi.ts'

/** all: Tất cả phiếu; account: Lịch sử phiếu của tài khoản; customer: tab Phiếu yêu cầu của khách hàng. */
export type TicketTableVariant = 'all' | 'account' | 'customer'

interface TicketTableProps {
  variant: TicketTableVariant
  items: TicketListItem[]
  loading: boolean
  pagination: TablePaginationConfig
}

function TicketTable({ variant, items, loading, pagination }: TicketTableProps) {
  return (
    <Table
      rowKey="id"
      size="middle"
      loading={loading}
      columns={columnsFor(variant)}
      dataSource={items}
      locale={{ emptyText: 'Chưa có phiếu' }}
      pagination={{ pageSize: 10, showSizeChanger: false, hideOnSinglePage: variant !== 'all', ...pagination }}
    />
  )
}

const CODE_COLUMN = {
  title: 'Mã phiếu',
  key: 'code',
  render: (_value: unknown, ticket: TicketListItem) => (
    <Link to={`/phieu/${ticket.id}`}>
      <CodeText>{ticket.code}</CodeText>
    </Link>
  ),
}

/** Mã phiếu kèm ngày tạo ở dòng dưới (Lịch sử phiếu của tài khoản). */
const CODE_WITH_DATE_COLUMN = {
  title: 'Mã phiếu',
  key: 'code',
  render: (_value: unknown, ticket: TicketListItem) => (
    <Flex vertical>
      <Link to={`/phieu/${ticket.id}`}>
        <CodeText>{ticket.code}</CodeText>
      </Link>
      <Typography.Text type="secondary">{formatDate(ticket.createdAt)}</Typography.Text>
    </Flex>
  ),
}

const TYPE_COLUMN = {
  title: 'Loại phiếu',
  key: 'type',
  render: (_value: unknown, ticket: TicketListItem) => ticketTypeLabel(ticket.type, ticket.fromStatus),
}

/** Trạng thái; dưới là lý do từ chối khi Bị từ chối, "Đang gửi mail" khi Đã duyệt (chưa Hoàn tất). */
const STATUS_COLUMN = {
  title: 'Trạng thái',
  key: 'status',
  render: (_value: unknown, ticket: TicketListItem) => (
    <Flex vertical gap={4} align="flex-start">
      <TicketStatusTag status={ticket.status} />
      {ticket.status === 'REJECTED' && ticket.rejectReason && (
        <Typography.Text type="danger" style={{ fontSize: 12 }}>{ticket.rejectReason}</Typography.Text>
      )}
      {ticket.status === 'APPROVED' && (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>Đang gửi mail</Typography.Text>
      )}
    </Flex>
  ),
}

const CREATOR_COLUMN = { title: 'Người tạo', dataIndex: 'createdByName' }

const REVIEWER_COLUMN = {
  title: 'Người duyệt',
  key: 'reviewer',
  render: (_value: unknown, ticket: TicketListItem) => ticket.reviewedByName ?? '—',
}

function columnsFor(variant: TicketTableVariant): TableColumnsType<TicketListItem> {
  if (variant === 'account') {
    return [CODE_WITH_DATE_COLUMN, TYPE_COLUMN, STATUS_COLUMN, CREATOR_COLUMN, REVIEWER_COLUMN]
  }
  if (variant === 'customer') {
    return [
      CODE_COLUMN,
      TYPE_COLUMN,
      {
        title: 'Tài khoản',
        key: 'account',
        render: (_value: unknown, ticket: TicketListItem) => <CodeText>{ticket.accountNumber ?? '—'}</CodeText>,
      },
      STATUS_COLUMN,
      CREATOR_COLUMN,
      { title: 'Ngày tạo', key: 'createdAt', render: (_value: unknown, ticket: TicketListItem) => formatDate(ticket.createdAt) },
    ]
  }
  return [
    CODE_COLUMN,
    TYPE_COLUMN,
    {
      title: 'Đối tượng',
      key: 'subject',
      render: (_value: unknown, ticket: TicketListItem) => (
        <Flex vertical>
          <span>{ticket.customerName ?? '—'}</span>
          <Typography.Text type="secondary">
            <CodeText>{ticket.accountNumber ?? ''}</CodeText>
          </Typography.Text>
        </Flex>
      ),
    },
    STATUS_COLUMN,
    CREATOR_COLUMN,
    REVIEWER_COLUMN,
    { title: 'Cập nhật', key: 'updatedAt', render: (_value: unknown, ticket: TicketListItem) => formatDateTime(ticket.updatedAt) },
  ]
}

export default TicketTable