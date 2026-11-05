// Màn Tài khoản (CN-09, màn 3.8): xem tài khoản của mọi chi nhánh (QT-04), lọc theo số, trạng thái, loại, chi nhánh.
// Bộ lọc và số trang nằm trên URL (useSearchParams): Tổng quan mở thẳng danh sách đã lọc, nút Back giữ bộ lọc.
// Các tham số này không có thông tin cá nhân (spec khách hàng tài khoản, C7). Backend lọc và phân trang 10 dòng.
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Button, Card, Flex, Form, Input, Select, Table, Tooltip, Typography, theme } from 'antd'
import type { TableColumnsType } from 'antd'
import { ExclamationCircleFilled } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import AccountStatusTag from '../../components/AccountStatusTag.tsx'
import {
  ACCOUNT_STATUS_LABELS,
  ACCOUNT_STATUSES,
  isAccountStatus,
  type AccountStatus,
} from '../../components/accountStatus.ts'
import CodeText from '../../components/CodeText.tsx'
import PageHeading from '../../components/PageHeading.tsx'
import { ACCOUNT_TYPES_QUERY_KEY, fetchAccountTypes, type AccountType } from '../catalog/accountTypeApi.ts'
import { BRANCHES_QUERY_KEY, fetchBranches, type Branch } from '../catalog/branchApi.ts'
import { formatLocalDate } from '../catalog/format.ts'
import { openTicketState } from './accountActions.ts'
import { ACCOUNTS_QUERY_KEY, fetchAccounts, type AccountFilters, type AccountItem } from './accountApi.ts'

const PAGE_SIZE = 10
const ACCOUNT_NUMBER_PATTERN = /^\d{1,10}$/

// Tên tham số trên URL, ví dụ /tai-khoan?trang-thai=DORMANT&trang-thai=BLOCKED&chi-nhanh=2&trang=3.
const STATUS_PARAM = 'trang-thai'
const ACCOUNT_TYPE_PARAM = 'loai'
const BRANCH_PARAM = 'chi-nhanh'
const ACCOUNT_NUMBER_PARAM = 'so-tk'
const PAGE_PARAM = 'trang'

const STATUS_OPTIONS = ACCOUNT_STATUSES.map((status) => ({ value: status, label: ACCOUNT_STATUS_LABELS[status] }))

function AccountListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const filters = readFilters(searchParams)
  const page = readPage(searchParams)

  // Khóa cache gồm cả điều kiện lọc và số trang: đổi một trong hai thì TanStack Query tự gọi lại API.
  const accountsQuery = useQuery({
    queryKey: [ACCOUNTS_QUERY_KEY, filters, page],
    queryFn: () => fetchAccounts(filters, page),
  })
  const accountTypesQuery = useQuery({ queryKey: ACCOUNT_TYPES_QUERY_KEY, queryFn: fetchAccountTypes })
  const branchesQuery = useQuery({ queryKey: BRANCHES_QUERY_KEY, queryFn: fetchBranches })

  /** Đổi bộ lọc thì ghi lại URL và về trang 1, để không rơi vào trang trống. */
  function changeFilters(newFilters: AccountFilters) {
    setSearchParams(toSearchParams(newFilters, 1))
  }

  function changePage(newPage: number) {
    setSearchParams(toSearchParams(filters, newPage))
  }

  return (
    <Flex vertical gap={16}>
      <PageHeading
        breadcrumb={['Tài khoản']}
        title="Tài khoản"
        description="Mọi thay đổi trạng thái đi qua phiếu yêu cầu — mở chi tiết tài khoản để tạo phiếu"
      />
      <Card>
        <FilterBar
          filters={filters}
          accountTypes={accountTypesQuery.data ?? []}
          branches={branchesQuery.data ?? []}
          onChange={changeFilters}
        />
        <Table
          rowKey="id"
          size="middle"
          loading={accountsQuery.isPending}
          columns={COLUMNS}
          dataSource={accountsQuery.data?.items ?? []}
          pagination={{
            current: page,
            pageSize: PAGE_SIZE,
            total: accountsQuery.data?.total ?? 0,
            showSizeChanger: false,
            showTotal: (total, range) => `${range[0]}–${range[1]} trên ${total.toLocaleString('vi-VN')}`,
            onChange: changePage,
          }}
        />
      </Card>
    </Flex>
  )
}

/**
 * Đọc bộ lọc từ URL. Giá trị lạ (người dùng sửa tay URL) thì bỏ qua, không gửi lên API,
 * để không gặp lỗi 400 chỉ vì một tham số gõ sai.
 */
function readFilters(searchParams: URLSearchParams): AccountFilters {
  const accountNumber = searchParams.get(ACCOUNT_NUMBER_PARAM)
  return {
    accountNumber: accountNumber !== null && ACCOUNT_NUMBER_PATTERN.test(accountNumber) ? accountNumber : undefined,
    statuses: searchParams.getAll(STATUS_PARAM).filter(isAccountStatus),
    accountTypeId: readPositiveNumber(searchParams.get(ACCOUNT_TYPE_PARAM)),
    branchId: readPositiveNumber(searchParams.get(BRANCH_PARAM)),
  }
}

function readPage(searchParams: URLSearchParams): number {
  return readPositiveNumber(searchParams.get(PAGE_PARAM)) ?? 1
}

/** "3" thành 3; rỗng, có chữ hay bằng 0 thì undefined. */
function readPositiveNumber(text: string | null): number | undefined {
  if (text === null || !/^\d+$/.test(text) || Number(text) === 0) {
    return undefined
  }
  return Number(text)
}

/** Bộ lọc và số trang thành tham số URL; ô trống và trang 1 thì không ghi cho URL gọn. */
function toSearchParams(filters: AccountFilters, page: number): URLSearchParams {
  const params = new URLSearchParams()
  if (filters.accountNumber !== undefined) {
    params.set(ACCOUNT_NUMBER_PARAM, filters.accountNumber)
  }
  for (const status of filters.statuses) {
    params.append(STATUS_PARAM, status)
  }
  if (filters.accountTypeId !== undefined) {
    params.set(ACCOUNT_TYPE_PARAM, String(filters.accountTypeId))
  }
  if (filters.branchId !== undefined) {
    params.set(BRANCH_PARAM, String(filters.branchId))
  }
  if (page > 1) {
    params.set(PAGE_PARAM, String(page))
  }
  return params
}

interface FilterBarProps {
  filters: AccountFilters
  accountTypes: AccountType[]
  branches: Branch[]
  onChange: (filters: AccountFilters) => void
}

/** Thanh lọc theo bản thiết kế. Các ô lấy giá trị từ URL, đổi ô nào thì báo lên trang để ghi lại URL. */
function FilterBar({ filters, accountTypes, branches, onChange }: FilterBarProps) {
  return (
    <Form layout="inline" style={{ marginBottom: 16, rowGap: 12 }}>
      {/* key theo giá trị trên URL: "Xóa lọc" hay nút Back đổi URL thì ô tìm dựng lại với giá trị mới. */}
      <AccountNumberSearch
        key={filters.accountNumber ?? ''}
        initialValue={filters.accountNumber}
        onSearch={(accountNumber) => onChange({ ...filters, accountNumber })}
      />
      <Form.Item label="Trạng thái">
        <Select
          mode="multiple"
          allowClear
          placeholder="Tất cả"
          style={{ minWidth: 200 }}
          options={STATUS_OPTIONS}
          value={filters.statuses}
          onChange={(statuses: AccountStatus[]) => onChange({ ...filters, statuses })}
        />
      </Form.Item>
      <Form.Item label="Loại tài khoản">
        <Select
          allowClear
          placeholder="Tất cả"
          style={{ width: 200 }}
          options={accountTypes.map((accountType) => ({ value: accountType.id, label: accountType.name }))}
          value={filters.accountTypeId}
          onChange={(accountTypeId?: number) => onChange({ ...filters, accountTypeId })}
        />
      </Form.Item>
      <Form.Item label="Chi nhánh">
        <Select
          allowClear
          placeholder="Tất cả"
          style={{ width: 200 }}
          options={branches.map((branch) => ({ value: branch.id, label: branch.name }))}
          value={filters.branchId}
          onChange={(branchId?: number) => onChange({ ...filters, branchId })}
        />
      </Form.Item>
      <Button type="link" onClick={() => onChange({ statuses: [] })}>
        Xóa lọc
      </Button>
    </Form>
  )
}

interface AccountNumberSearchProps {
  initialValue?: string
  onSearch: (accountNumber?: string) => void
}

/** Ô tìm số tài khoản: kiểm chỉ gồm chữ số trước khi tìm; backend vẫn kiểm lại (QT-07). */
function AccountNumberSearch({ initialValue, onSearch }: AccountNumberSearchProps) {
  const [hasError, setHasError] = useState(false)

  function search(text: string) {
    const accountNumber = text.trim()
    if (accountNumber !== '' && !ACCOUNT_NUMBER_PATTERN.test(accountNumber)) {
      setHasError(true)
      return
    }
    setHasError(false)
    onSearch(accountNumber === '' ? undefined : accountNumber)
  }

  return (
    <Form.Item
      validateStatus={hasError ? 'error' : undefined}
      help={hasError ? 'Số tài khoản chỉ gồm 1–10 chữ số' : undefined}
    >
      <Input.Search
        allowClear
        placeholder="Số tài khoản"
        defaultValue={initialValue}
        style={{ width: 220 }}
        onSearch={search}
      />
    </Form.Item>
  )
}

const COLUMNS: TableColumnsType<AccountItem> = [
  { title: 'Số tài khoản', key: 'accountNumber', render: (_value, account) => <AccountNumberCell account={account} /> },
  {
    title: 'Chủ tài khoản',
    key: 'customer',
    render: (_value, account) => (
      <Flex vertical>
        <span>{account.customerName}</span>
        <Typography.Text type="secondary">
          <CodeText>{account.customerCccd}</CodeText>
        </Typography.Text>
      </Flex>
    ),
  },
  { title: 'Loại tài khoản', dataIndex: 'accountTypeName' },
  { title: 'Chi nhánh', dataIndex: 'branchName' },
  {
    title: 'Trạng thái',
    dataIndex: 'status',
    render: (status: AccountStatus) => <AccountStatusTag status={status} />,
  },
  { title: 'Ngày mở', dataIndex: 'openedAt', render: (openedAt: string | null) => formatLocalDate(openedAt) },
  {
    title: 'Thao tác',
    key: 'actions',
    render: (_value, account) => <Link to={`/tai-khoan/${account.id}`}>Xem</Link>,
  },
]

/** Số tài khoản; có phiếu đang mở (QT-03) thì kèm biểu tượng cảnh báo, Tooltip ghi mã phiếu. */
function AccountNumberCell({ account }: { account: AccountItem }) {
  const { token } = theme.useToken()
  if (account.openTicket === null) {
    return <CodeText>{account.accountNumber}</CodeText>
  }
  return (
    <Flex gap={6} align="center">
      <CodeText>{account.accountNumber}</CodeText>
      <Tooltip title={`Có phiếu ${account.openTicket.code} ${openTicketState(account.openTicket)}`}>
        <ExclamationCircleFilled aria-label="Có phiếu đang mở" style={{ color: token.colorWarning }} />
      </Tooltip>
    </Flex>
  )
}

export default AccountListPage