// Màn Chi tiết tài khoản (CN-10, màn 2.3): thông tin tài khoản và nút tạo phiếu theo trạng thái cho Giao dịch viên.
// Tài khoản đang có phiếu Chờ duyệt hoặc Bị từ chối thì khóa mọi nút (QT-03); khóa nút chỉ là lớp giao diện,
// API tạo phiếu kiểm lại. Lịch sử phiếu lấy từ API của module phiếu, Vòng đời và Mở bởi, Duyệt mở bởi từ API tài
// khoản (spec phiếu mục 10.6).
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { Alert, Breadcrumb, Button, Card, Descriptions, Flex, Result, Space, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { readApiError } from '../../api/apiError.ts'
import AccountStatusTag from '../../components/AccountStatusTag.tsx'
import CodeText from '../../components/CodeText.tsx'
import { useCurrentUser } from '../auth/useCurrentUser.ts'
import { formatLocalDate } from '../catalog/format.ts'
import { fetchTickets, TICKETS_QUERY_KEY } from '../ticket/ticketApi.ts'
import TicketTable from '../ticket/TicketTable.tsx'
import { ACCOUNT_ACTIONS, createTicketUrl, openTicketState } from './accountActions.ts'
import { ACCOUNT_QUERY_KEY, fetchAccount, type AccountItem } from './accountApi.ts'
import AccountLifecycle from './AccountLifecycle.tsx'

function AccountDetailPage() {
  const { id } = useParams()
  const accountId = Number(id)
  // id không phải số (người dùng sửa tay URL) thì không gọi API, hiện luôn trang không tìm thấy.
  const isValidId = Number.isInteger(accountId) && accountId > 0
  const accountQuery = useQuery({
    queryKey: [ACCOUNT_QUERY_KEY, accountId],
    queryFn: () => fetchAccount(accountId),
    enabled: isValidId,
  })

  if (!isValidId || readApiError(accountQuery.error)?.status === 404) {
    return <AccountNotFound />
  }
  if (accountQuery.isError) {
    return <Result status="error" title="Không tải được tài khoản. Vui lòng thử lại sau." />
  }
  if (accountQuery.isPending) {
    return <Card loading />
  }
  const account = accountQuery.data
  return (
    <Flex vertical gap={16}>
      <AccountHeading account={account} />
      <AccountActions account={account} />
      <Card title="Thông tin tài khoản">
        <AccountDescriptions account={account} />
      </Card>
      <AccountTickets accountId={account.id} />
      <Card title="Vòng đời tài khoản">
        <AccountLifecycle accountId={account.id} />
      </Card>
    </Flex>
  )
}

/** Lịch sử phiếu của tài khoản, gọi API Tất cả phiếu lọc theo tài khoản; số phiếu ở tiêu đề Card. */
function AccountTickets({ accountId }: { accountId: number }) {
  const [page, setPage] = useState(1)
  const filters = { statuses: [], accountId }
  const ticketsQuery = useQuery({
    queryKey: [TICKETS_QUERY_KEY, filters, page],
    queryFn: () => fetchTickets(filters, page),
  })
  const total = ticketsQuery.data?.total
  return (
    <Card title={total === undefined ? 'Lịch sử phiếu' : `Lịch sử phiếu (${total})`}>
      <TicketTable
        variant="account"
        items={ticketsQuery.data?.items ?? []}
        loading={ticketsQuery.isPending}
        pagination={{ current: page, total: total ?? 0, onChange: setPage }}
      />
    </Card>
  )
}

/** Breadcrumb, số tài khoản sao chép được, Tag trạng thái, dòng phụ chủ tài khoản · loại · chi nhánh. */
function AccountHeading({ account }: { account: AccountItem }) {
  return (
    <Flex vertical gap={8}>
      <Breadcrumb items={[{ title: <Link to="/tai-khoan">Tài khoản</Link> }, { title: account.accountNumber }]} />
      <Flex align="center" gap={12}>
        <Typography.Title level={4} style={{ margin: 0 }} copyable={{ text: account.accountNumber }}>
          Tài khoản {account.accountNumber}
        </Typography.Title>
        <AccountStatusTag status={account.status} />
      </Flex>
      <Typography.Text type="secondary">
        {account.customerName} · {account.accountTypeName} · {account.branchName}
      </Typography.Text>
    </Flex>
  )
}

/**
 * Thao tác theo trạng thái. Chờ mở và Đã đóng chỉ có một dòng giải thích cho mọi role.
 * Còn lại: Giao dịch viên thấy nút (khóa khi có phiếu đang mở), Kiểm soát viên thấy dòng nhắc.
 */
function AccountActions({ account }: { account: AccountItem }) {
  const currentUser = useCurrentUser()
  const navigate = useNavigate()

  if (account.status === 'PENDING_OPEN') {
    // Tài khoản Chờ mở luôn có phiếu Mở tài khoản đang mở; dòng này đã nói rõ nên không hiện thêm Alert.
    let pendingText = 'Chưa mở'
    if (account.openTicket !== null) {
      pendingText = `Chưa mở — phiếu ${account.openTicket.code} ${openTicketState(account.openTicket)}`
    }
    return <Alert type="info" showIcon title={pendingText} />
  }
  if (account.status === 'CLOSED') {
    return (
      <Alert
        type="info"
        showIcon
        title={`Tài khoản đã đóng ngày ${formatLocalDate(account.closedAt)} — không còn thao tác.`}
      />
    )
  }
  const isMaker = currentUser.roles.includes('MAKER')
  const isLocked = account.openTicket !== null
  return (
    <Card>
      <Flex vertical gap={12}>
        {account.openTicket !== null && (
          <Alert
            type="warning"
            showIcon
            title={
              account.openTicket.status === 'REJECTED'
                ? `Tài khoản đang có phiếu ${account.openTicket.code} bị từ chối, cần sửa hoặc hủy`
                : `Tài khoản đang có phiếu ${account.openTicket.code} chờ duyệt`
            }
          />
        )}
        {isMaker ? (
          <Flex vertical gap={8}>
            <Space wrap>
              {ACCOUNT_ACTIONS[account.status].map((action) => (
                <Button
                  key={action.label}
                  type="primary"
                  danger={action.danger}
                  disabled={isLocked}
                  onClick={() => navigate(createTicketUrl(action.ticketType, account.accountNumber))}
                >
                  {action.label}
                </Button>
              ))}
            </Space>
            <Typography.Text type="secondary">Mỗi thao tác tạo một phiếu, chờ kiểm soát viên duyệt</Typography.Text>
          </Flex>
        ) : (
          <Typography.Text type="secondary">Chỉ giao dịch viên tạo được phiếu thay đổi trạng thái.</Typography.Text>
        )}
      </Flex>
    </Card>
  )
}

function AccountDescriptions({ account }: { account: AccountItem }) {
  return (
    <Descriptions bordered column={2} size="middle">
      <Descriptions.Item label="Số tài khoản">
        <CodeText>{account.accountNumber}</CodeText>
      </Descriptions.Item>
      <Descriptions.Item label="Loại tài khoản">{account.accountTypeName}</Descriptions.Item>
      <Descriptions.Item label="Chủ tài khoản">
        <Link to={`/khach-hang/${account.customerId}`}>{account.customerName}</Link>
      </Descriptions.Item>
      <Descriptions.Item label="Số CCCD">
        <CodeText>{account.customerCccd}</CodeText>
      </Descriptions.Item>
      <Descriptions.Item label="Chi nhánh mở">{account.branchName}</Descriptions.Item>
      <Descriptions.Item label="Trạng thái">
        <AccountStatusTag status={account.status} />
      </Descriptions.Item>
      <Descriptions.Item label="Ngày mở">{formatLocalDate(account.openedAt)}</Descriptions.Item>
      <Descriptions.Item label="Giao dịch gần nhất">{formatLocalDate(account.lastTransactionAt)}</Descriptions.Item>
      <Descriptions.Item label="Mở bởi">{account.openedByName ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Duyệt mở bởi">{account.openApprovedByName ?? '—'}</Descriptions.Item>
    </Descriptions>
  )
}

function AccountNotFound() {
  return (
    <Result
      status="404"
      title="Không tìm thấy tài khoản"
      extra={
        <Link to="/tai-khoan">
          <Button type="primary">Về danh sách tài khoản</Button>
        </Link>
      }
    />
  )
}

export default AccountDetailPage