// Màn Khách hàng (CN-07, màn 3.6): tìm khách của mọi chi nhánh (QT-04) theo họ tên (gần đúng, gõ không dấu cũng
// được) hoặc đủ 12 số CCCD (khớp tuyệt đối). Từ khóa chỉ giữ trong state của trang, không ghi lên URL vì có thể là
// số CCCD (spec khách hàng tài khoản, C7). CCCD, điện thoại đã che từ backend (QT-06); backend phân trang 10 dòng.
import { useState } from 'react'
import { Link } from 'react-router'
import { Alert, Card, Flex, Form, Input, Select, Space, Table, Typography } from 'antd'
import type { TableColumnsType } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { readApiError } from '../../api/apiError.ts'
import AccountStatusTag from '../../components/AccountStatusTag.tsx'
import type { AccountStatus } from '../../components/accountStatus.ts'
import CodeText from '../../components/CodeText.tsx'
import PageHeading from '../../components/PageHeading.tsx'
import { formatLocalDate } from '../catalog/format.ts'
import {
  CUSTOMERS_QUERY_KEY,
  searchCustomers,
  type CustomerListItem,
  type CustomerSearchBy,
} from './customerApi.ts'

const PAGE_SIZE = 10
const CCCD_PATTERN = /^\d{12}$/

const SEARCH_BY_OPTIONS = [
  { value: 'NAME', label: 'Họ tên' },
  { value: 'CCCD', label: 'Số CCCD' },
]

/** Lần tìm đang hiện trên bảng: tìm theo gì và từ khóa nào. Từ khóa rỗng là liệt kê mọi khách. */
interface ExecutedSearch {
  searchBy: CustomerSearchBy
  keyword: string
}

function CustomerListPage() {
  const [search, setSearch] = useState<ExecutedSearch>({ searchBy: 'NAME', keyword: '' })
  const [page, setPage] = useState(1)

  // Khóa cache gồm cả lần tìm và số trang; khóa chỉ nằm trong bộ nhớ của trang, không ghi đi đâu.
  const customersQuery = useQuery({
    queryKey: [CUSTOMERS_QUERY_KEY, search, page],
    queryFn: () => searchCustomers(search.searchBy, search.keyword, page),
  })

  /** Tìm mới thì về trang 1. */
  function runSearch(newSearch: ExecutedSearch) {
    setSearch(newSearch)
    setPage(1)
  }

  return (
    <Flex vertical gap={16}>
      <PageHeading
        breadcrumb={['Khách hàng']}
        title="Khách hàng"
        description="Khách hàng cá nhân của mọi chi nhánh; tạo mới qua phiếu Mở tài khoản"
      />
      <Card>
        <SearchBar onSearch={runSearch} />
        {customersQuery.isError && (
          <Alert
            type="error"
            showIcon
            style={{ marginBottom: 16 }}
            title={readApiError(customersQuery.error)?.message ?? 'Lỗi hệ thống. Vui lòng thử lại sau.'}
          />
        )}
        <Table
          rowKey="id"
          size="middle"
          loading={customersQuery.isPending}
          columns={COLUMNS}
          dataSource={customersQuery.data?.items ?? []}
          pagination={{
            current: page,
            pageSize: PAGE_SIZE,
            total: customersQuery.data?.total ?? 0,
            showSizeChanger: false,
            showTotal: (total, range) => `${range[0]}–${range[1]} trên ${total.toLocaleString('vi-VN')}`,
            onChange: setPage,
          }}
        />
      </Card>
    </Flex>
  )
}

/**
 * Chọn tìm theo Họ tên hoặc Số CCCD ghép với ô tìm. Đổi lựa chọn thì xóa ô tìm.
 * Chế độ CCCD kiểm đủ 12 số trước khi tìm; backend vẫn kiểm lại (QT-07). Ô trống thì liệt kê mọi khách.
 */
function SearchBar({ onSearch }: { onSearch: (search: ExecutedSearch) => void }) {
  const [searchBy, setSearchBy] = useState<CustomerSearchBy>('NAME')
  const [inputValue, setInputValue] = useState('')
  const [hasError, setHasError] = useState(false)

  function changeSearchBy(newSearchBy: CustomerSearchBy) {
    setSearchBy(newSearchBy)
    setInputValue('')
    setHasError(false)
  }

  function search(text: string) {
    const keyword = text.trim()
    if (keyword === '') {
      setHasError(false)
      onSearch({ searchBy: 'NAME', keyword: '' })
      return
    }
    if (searchBy === 'CCCD' && !CCCD_PATTERN.test(keyword)) {
      setHasError(true)
      return
    }
    setHasError(false)
    onSearch({ searchBy, keyword })
  }

  return (
    <Form style={{ marginBottom: 16 }}>
      <Form.Item
        style={{ marginBottom: 4 }}
        validateStatus={hasError ? 'error' : undefined}
        help={hasError ? 'Số CCCD phải đủ 12 chữ số' : undefined}
      >
        <Space.Compact>
          <Select
            aria-label="Tìm theo"
            style={{ width: 120 }}
            options={SEARCH_BY_OPTIONS}
            value={searchBy}
            onChange={changeSearchBy}
          />
          <Input.Search
            allowClear
            placeholder={searchBy === 'NAME' ? 'Nhập họ tên' : 'Nhập đủ 12 số CCCD'}
            style={{ width: 320 }}
            value={inputValue}
            onChange={(event) => setInputValue(event.target.value)}
            onSearch={search}
          />
        </Space.Compact>
      </Form.Item>
      <Typography.Text type="secondary">
        Họ tên: tìm gần đúng, không dấu cũng được. Số CCCD: nhập đủ 12 số, khớp tuyệt đối.
      </Typography.Text>
    </Form>
  )
}

const COLUMNS: TableColumnsType<CustomerListItem> = [
  { title: 'Họ tên', dataIndex: 'fullName' },
  { title: 'Số CCCD', dataIndex: 'cccd', render: (cccd: string) => <CodeText>{cccd}</CodeText> },
  { title: 'Ngày sinh', dataIndex: 'dateOfBirth', render: (dateOfBirth: string) => formatLocalDate(dateOfBirth) },
  { title: 'Điện thoại', dataIndex: 'phone', render: (phone: string) => <CodeText>{phone}</CodeText> },
  { title: 'Chi nhánh quản lý', dataIndex: 'branchName' },
  {
    title: 'Tài khoản',
    dataIndex: 'accountStatuses',
    render: (accountStatuses: AccountStatus[]) => <AccountStatusTags statuses={accountStatuses} />,
  },
  { title: 'Tạo hồ sơ', dataIndex: 'createdDate', render: (createdDate: string) => formatLocalDate(createdDate) },
  {
    title: 'Thao tác',
    key: 'actions',
    render: (_value, customer) => <Link to={`/khach-hang/${customer.id}`}>Xem</Link>,
  },
]

/** Mỗi tài khoản của khách một Tag trạng thái; khách chưa có tài khoản thì "—". */
function AccountStatusTags({ statuses }: { statuses: AccountStatus[] }) {
  if (statuses.length === 0) {
    return '—'
  }
  return (
    <Flex wrap gap={4}>
      {statuses.map((status, index) => (
        // Một khách có thể có 2 tài khoản cùng trạng thái, nên khóa ghép thêm vị trí.
        <AccountStatusTag key={`${status}-${index}`} status={status} />
      ))}
    </Flex>
  )
}

export default CustomerListPage