// Màn Audit log (CN-23, màn 3.5): chỉ đọc. Lọc theo người thực hiện, khoảng thời gian, hành động, đối tượng;
// backend lọc và phân trang 10 dòng, mới nhất ở trên. Chỉ dòng sửa từ 2 trường mở rộng được để xem Trước, Sau.
// Mã request và tên method lưu trong database để tra lỗi, không hiện ở đây.
import { useState } from 'react'
import { Button, Card, DatePicker, Flex, Form, Select, Table, Typography } from 'antd'
import type { TableColumnsType } from 'antd'
import { useQuery } from '@tanstack/react-query'
import CodeText from '../../components/CodeText.tsx'
import PageHeading from '../../components/PageHeading.tsx'
import { formatDate } from '../catalog/format.ts'
import { EMPLOYEES_QUERY_KEY, fetchEmployees, type Employee } from '../employee/employeeApi.ts'
import {
  ACTION_FILTER_OPTIONS,
  actionLabel,
  OBJECT_TYPE_FILTER_OPTIONS,
  OBJECT_TYPE_LABELS,
  SCREEN_LABELS,
} from './auditLabels.ts'
import {
  AUDIT_LOGS_QUERY_KEY,
  fetchAuditLogs,
  type AuditChange,
  type AuditLogFilters,
  type AuditLogItem,
} from './auditLogApi.ts'

const PAGE_SIZE = 10

function AuditLogPage() {
  const [filters, setFilters] = useState<AuditLogFilters>({})
  const [page, setPage] = useState(1)
  // Tăng số này để dựng lại thanh lọc từ đầu, nhờ vậy nút "Xóa lọc" làm trống mọi ô.
  const [filterBarKey, setFilterBarKey] = useState(0)

  // Khóa cache gồm cả điều kiện lọc và số trang: đổi một trong hai thì TanStack Query tự gọi lại API.
  const auditLogsQuery = useQuery({
    queryKey: [AUDIT_LOGS_QUERY_KEY, filters, page],
    queryFn: () => fetchAuditLogs(filters, page),
  })
  const employeesQuery = useQuery({ queryKey: EMPLOYEES_QUERY_KEY, queryFn: fetchEmployees })

  /** Đổi điều kiện lọc thì về trang 1. */
  function changeFilters(newFilters: AuditLogFilters) {
    setFilters(newFilters)
    setPage(1)
  }

  function clearFilters() {
    changeFilters({})
    setFilterBarKey(filterBarKey + 1)
  }

  return (
    <Flex vertical gap={16}>
      <PageHeading
        breadcrumb={['Audit log']}
        title="Audit log"
        description="Mọi thao tác ghi dữ liệu, chỉ đọc — không sửa, không xóa"
      />
      <Card>
        <FilterBar
          key={filterBarKey}
          filters={filters}
          employees={employeesQuery.data ?? []}
          onChange={changeFilters}
          onClear={clearFilters}
        />
        <Table
          rowKey="id"
          size="middle"
          loading={auditLogsQuery.isPending}
          columns={COLUMNS}
          dataSource={auditLogsQuery.data?.items ?? []}
          expandable={{
            rowExpandable: (item) => item.changes.length >= 2,
            expandedRowRender: (item) => <ChangeDetails item={item} />,
          }}
          pagination={{
            current: page,
            pageSize: PAGE_SIZE,
            total: auditLogsQuery.data?.total ?? 0,
            showSizeChanger: false,
            showTotal: (total, range) => `${range[0]}–${range[1]} trên ${total.toLocaleString('vi-VN')}`,
            onChange: setPage,
          }}
        />
      </Card>
    </Flex>
  )
}

interface FilterBarProps {
  filters: AuditLogFilters
  employees: Employee[]
  onChange: (filters: AuditLogFilters) => void
  onClear: () => void
}

/** Thanh lọc theo bản thiết kế (Form layout inline). Các ô không giữ giá trị riêng: đổi ô nào thì báo lên trang. */
function FilterBar({ filters, employees, onChange, onClear }: FilterBarProps) {
  const actorOptions = [
    { value: 'SYSTEM', label: 'Hệ thống' },
    ...employees.map((employee) => ({ value: employee.id, label: employee.fullName })),
  ]
  return (
    <Form layout="inline" style={{ marginBottom: 16, rowGap: 12 }}>
      <Form.Item label="Người thực hiện">
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="Tất cả · gõ để tìm"
          style={{ width: 220 }}
          options={actorOptions}
          onChange={(actor?: number | 'SYSTEM') => onChange({ ...filters, actor })}
        />
      </Form.Item>
      <Form.Item label="Khoảng thời gian">
        <DatePicker.RangePicker
          format="DD/MM/YYYY"
          onChange={(_dates, dateTexts) =>
            onChange({ ...filters, from: toIsoDate(dateTexts[0]), to: toIsoDate(dateTexts[1]) })
          }
        />
      </Form.Item>
      <Form.Item label="Hành động">
        <Select
          allowClear
          placeholder="Tất cả"
          style={{ width: 150 }}
          options={ACTION_FILTER_OPTIONS}
          onChange={(action?: string) => onChange({ ...filters, action })}
        />
      </Form.Item>
      <Form.Item label="Đối tượng">
        <Select
          allowClear
          placeholder="Tất cả"
          style={{ width: 150 }}
          options={OBJECT_TYPE_FILTER_OPTIONS}
          onChange={(objectType?: string) => onChange({ ...filters, objectType })}
        />
      </Form.Item>
      <Button onClick={onClear}>Xóa lọc</Button>
    </Form>
  )
}

/** "31/10/2026" (cách RangePicker hiện ngày) thành "2026-10-31" (cách backend nhận); ô trống thì undefined. */
function toIsoDate(displayDate: string): string | undefined {
  if (displayDate === '') {
    return undefined
  }
  const [day, month, year] = displayDate.split('/')
  return `${year}-${month}-${day}`
}

/** "18/10/2026 16:58:40" theo giờ của máy người xem. */
function formatDateTime(isoDateTime: string): string {
  const time = new Date(isoDateTime).toLocaleTimeString('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
  return `${formatDate(isoDateTime)} ${time}`
}

const COLUMNS: TableColumnsType<AuditLogItem> = [
  {
    title: 'Thời gian',
    dataIndex: 'occurredAt',
    render: (occurredAt: string) => <CodeText>{formatDateTime(occurredAt)}</CodeText>,
  },
  {
    title: 'Người thực hiện',
    dataIndex: 'employeeName',
    render: (employeeName: string | null) =>
      employeeName ?? <Typography.Text type="secondary">Hệ thống</Typography.Text>,
  },
  { title: 'Hành động', key: 'action', render: (_value, item) => actionLabel(item.objectType, item.action) },
  { title: 'Đối tượng', key: 'object', render: (_value, item) => <ObjectCell item={item} /> },
  {
    title: 'Mô tả',
    dataIndex: 'description',
    render: (description: string) => <Typography.Text type="secondary">{description}</Typography.Text>,
  },
  {
    title: 'IP',
    dataIndex: 'ipAddress',
    render: (ipAddress: string | null) => (ipAddress === null ? '—' : <CodeText>{ipAddress}</CodeText>),
  },
]

/** Nhân viên hiện họ tên; danh mục hiện mã (ví dụ "Chi nhánh CN-Q3"). */
function ObjectCell({ item }: { item: AuditLogItem }) {
  const typeLabel = OBJECT_TYPE_LABELS[item.objectType] ?? item.objectType
  if (item.objectName !== null) {
    return <>{`${typeLabel} ${item.objectName}`}</>
  }
  return (
    <>
      {typeLabel} <CodeText>{item.objectId}</CodeText>
    </>
  )
}

/** Phần mở rộng của dòng sửa từ 2 trường: bảng Thông tin, Trước, Sau; giá trị mới được tô nổi. */
function ChangeDetails({ item }: { item: AuditLogItem }) {
  const changeColumns: TableColumnsType<AuditChange> = [
    { title: 'Thông tin', dataIndex: 'label' },
    { title: 'Trước', dataIndex: 'before' },
    { title: 'Sau', dataIndex: 'after', render: (after: string) => <Typography.Text mark>{after}</Typography.Text> },
  ]
  return (
    <Flex vertical gap={8}>
      <Table rowKey="label" size="small" pagination={false} columns={changeColumns} dataSource={item.changes} />
      {SCREEN_LABELS[item.objectType] && (
        <Typography.Text type="secondary">Thực hiện tại màn {SCREEN_LABELS[item.objectType]}</Typography.Text>
      )}
    </Flex>
  )
}

export default AuditLogPage