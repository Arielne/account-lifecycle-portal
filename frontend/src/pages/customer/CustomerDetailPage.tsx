// Màn Chi tiết khách hàng (CN-08, màn 3.7): thông tin khách (CCCD, điện thoại, email đã che từ backend, QT-06),
// tab Tài khoản (lấy từ API của module tài khoản) và tab Phiếu yêu cầu. Giao dịch viên có nút "Mở thêm tài khoản".
// Tab Phiếu yêu cầu làm cùng module phiếu (spec khách hàng tài khoản, C3); không hiện địa chỉ (C5).
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { Breadcrumb, Button, Card, Descriptions, Empty, Flex, Result, Table, Tabs, Typography } from 'antd'
import type { TableColumnsType } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { readApiError } from '../../api/apiError.ts'
import AccountStatusTag from '../../components/AccountStatusTag.tsx'
import type { AccountStatus } from '../../components/accountStatus.ts'
import CodeText from '../../components/CodeText.tsx'
import { openTicketState, ticketTypeLabel } from '../account/accountActions.ts'
import { ACCOUNTS_QUERY_KEY, fetchAccounts, type AccountFilters, type AccountItem } from '../account/accountApi.ts'
import { useCurrentUser } from '../auth/useCurrentUser.ts'
import { formatLocalDate } from '../catalog/format.ts'
import { CUSTOMER_QUERY_KEY, fetchCustomer, type CustomerDetail } from './customerApi.ts'

const PAGE_SIZE = 10

function CustomerDetailPage() {
  const { id } = useParams()
  const customerId = Number(id)
  // id không phải số (người dùng sửa tay URL) thì không gọi API, hiện luôn trang không tìm thấy.
  const isValidId = Number.isInteger(customerId) && customerId > 0
  const customerQuery = useQuery({
    queryKey: [CUSTOMER_QUERY_KEY, customerId],
    queryFn: () => fetchCustomer(customerId),
    enabled: isValidId,
  })

  if (!isValidId || readApiError(customerQuery.error)?.status === 404) {
    return <CustomerNotFound />
  }
  if (customerQuery.isError) {
    return <Result status="error" title="Không tải được khách hàng. Vui lòng thử lại sau." />
  }
  if (customerQuery.isPending) {
    return <Card loading />
  }
  const customer = customerQuery.data
  return (
    <Flex vertical gap={16}>
      <CustomerHeading customer={customer} />
      <Card>
        <CustomerDescriptions customer={customer} />
      </Card>
      <Card>
        <Tabs
          items={[
            { key: 'accounts', label: 'Tài khoản', children: <CustomerAccounts customerId={customer.id} /> },
            { key: 'tickets', label: 'Phiếu yêu cầu', children: <Empty description="Chưa có phiếu" /> },
          ]}
        />
      </Card>
    </Flex>
  )
}

/** Breadcrumb, họ tên, dòng phụ "Khách hàng từ ... · ... quản lý"; nút "Mở thêm tài khoản" chỉ Giao dịch viên thấy. */
function CustomerHeading({ customer }: { customer: CustomerDetail }) {
  const currentUser = useCurrentUser()
  const navigate = useNavigate()
  const isMaker = currentUser.roles.includes('MAKER')
  return (
    <Flex vertical gap={8}>
      <Breadcrumb items={[{ title: <Link to="/khach-hang">Khách hàng</Link> }, { title: customer.fullName }]} />
      <Flex wrap justify="space-between" align="flex-end" gap={12}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {customer.fullName}
          </Typography.Title>
          <Typography.Text type="secondary">
            Khách hàng từ {formatLocalDate(customer.createdDate)} · {customer.branchName} quản lý
          </Typography.Text>
        </div>
        {isMaker && (
          // Sang Tạo phiếu, bước 2 điền sẵn khách này (CN-11); truyền id chứ không truyền CCCD lên URL.
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => navigate(`/phieu/tao-moi?loai=MO_TAI_KHOAN&khachHang=${customer.id}`)}
          >
            Mở thêm tài khoản
          </Button>
        )}
      </Flex>
    </Flex>
  )
}

function CustomerDescriptions({ customer }: { customer: CustomerDetail }) {
  return (
    <Descriptions bordered column={3} size="middle">
      <Descriptions.Item label="Họ tên">{customer.fullName}</Descriptions.Item>
      <Descriptions.Item label="Ngày sinh">{formatLocalDate(customer.dateOfBirth)}</Descriptions.Item>
      <Descriptions.Item label="Giới tính">{customer.gender ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Số CCCD">
        <CodeText>{customer.cccd}</CodeText>
      </Descriptions.Item>
      <Descriptions.Item label="Ngày cấp">{formatLocalDate(customer.idIssueDate)}</Descriptions.Item>
      <Descriptions.Item label="Nơi cấp">{customer.idIssuePlace}</Descriptions.Item>
      <Descriptions.Item label="Điện thoại">
        <CodeText>{customer.phone}</CodeText>
      </Descriptions.Item>
      <Descriptions.Item label="Email">{customer.email}</Descriptions.Item>
      <Descriptions.Item label="Chi nhánh quản lý">{customer.branchName}</Descriptions.Item>
    </Descriptions>
  )
}

/** Tab Tài khoản: React gọi API của module tài khoản (theo chiều gọi trong CLAUDE.md), 10 dòng mỗi trang. */
function CustomerAccounts({ customerId }: { customerId: number }) {
  const [page, setPage] = useState(1)
  const filters: AccountFilters = { statuses: [], customerId }
  const accountsQuery = useQuery({
    queryKey: [ACCOUNTS_QUERY_KEY, filters, page],
    queryFn: () => fetchAccounts(filters, page),
  })
  return (
    <Table
      rowKey="id"
      size="middle"
      loading={accountsQuery.isPending}
      columns={ACCOUNT_COLUMNS}
      dataSource={accountsQuery.data?.items ?? []}
      locale={{ emptyText: 'Khách chưa có tài khoản nào' }}
      pagination={{
        current: page,
        pageSize: PAGE_SIZE,
        total: accountsQuery.data?.total ?? 0,
        showSizeChanger: false,
        hideOnSinglePage: true,
        onChange: setPage,
      }}
    />
  )
}

const ACCOUNT_COLUMNS: TableColumnsType<AccountItem> = [
  { title: 'Số tài khoản', dataIndex: 'accountNumber', render: (number: string) => <CodeText>{number}</CodeText> },
  { title: 'Loại tài khoản', dataIndex: 'accountTypeName' },
  {
    title: 'Trạng thái',
    dataIndex: 'status',
    render: (status: AccountStatus) => <AccountStatusTag status={status} />,
  },
  { title: 'Chi nhánh mở', dataIndex: 'branchName' },
  { title: 'Ngày mở', dataIndex: 'openedAt', render: (openedAt: string | null) => formatLocalDate(openedAt) },
  { title: 'Phiếu đang xử lý', key: 'openTicket', render: (_value, account) => <OpenTicketCell account={account} /> },
  {
    title: 'Thao tác',
    key: 'actions',
    render: (_value, account) => <Link to={`/tai-khoan/${account.id}`}>Xem</Link>,
  },
]

/** Mã phiếu đang mở kèm loại và trạng thái, ví dụ "PYC-2026-0418 · Phong tỏa · đang chờ duyệt"; không có thì "—". */
function OpenTicketCell({ account }: { account: AccountItem }) {
  if (account.openTicket === null) {
    return '—'
  }
  return (
    <Flex vertical>
      <CodeText>{account.openTicket.code}</CodeText>
      <Typography.Text type="secondary">
        {ticketTypeLabel(account.openTicket.type, account.status)} · {openTicketState(account.openTicket)}
      </Typography.Text>
    </Flex>
  )
}

function CustomerNotFound() {
  return (
    <Result
      status="404"
      title="Không tìm thấy khách hàng"
      extra={
        <Link to="/khach-hang">
          <Button type="primary">Về danh sách khách hàng</Button>
        </Link>
      }
    />
  )
}

export default CustomerDetailPage