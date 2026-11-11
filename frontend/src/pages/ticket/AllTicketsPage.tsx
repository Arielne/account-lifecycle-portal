// Màn Tất cả phiếu (CN-14, màn 3.10, spec phiếu mục 10.4): phiếu của mọi chi nhánh (QT-04), lọc theo trạng thái
// (Segmented kèm số phiếu, D8), mã phiếu, loại, chi nhánh, khoảng ngày tạo. Bộ lọc và số trang nằm trên URL như màn Tài
// khoản: nút Back giữ bộ lọc; các tham số không có thông tin cá nhân. Backend lọc, đếm và phân trang 10 dòng.
import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { Button, Card, DatePicker, Flex, Form, Input, Segmented, Select } from 'antd'
import dayjs from 'dayjs'
import type { Dayjs } from 'dayjs'
import { useQuery } from '@tanstack/react-query'
import PageHeading from '../../components/PageHeading.tsx'
import { TICKET_STATUS_LABELS, type TicketStatus } from '../../components/ticketStatus.ts'
import { SINGLE_TICKET_TYPES, ticketTypeLabel, type TicketType } from '../../components/ticketType.ts'
import { BRANCHES_QUERY_KEY, fetchBranches, type Branch } from '../catalog/branchApi.ts'
import { fetchTickets, TICKETS_QUERY_KEY, type TicketListFilters } from './ticketApi.ts'
import TicketTable from './TicketTable.tsx'

// Tên tham số trên URL, ví dụ /phieu?trang-thai=REJECTED&ma=PYC-2026&chi-nhanh=2&tu=2026-11-01&den=2026-11-07&trang=2.
const STATUS_PARAM = 'trang-thai'
const CODE_PARAM = 'ma'
const TYPE_PARAM = 'loai'
const BRANCH_PARAM = 'chi-nhanh'
const FROM_PARAM = 'tu'
const TO_PARAM = 'den'
const PAGE_PARAM = 'trang'

const CODE_PATTERN = /^[A-Za-z0-9-]{1,14}$/
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/
const ALL = 'ALL'
const STATUS_ORDER: TicketStatus[] = ['PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED']
const TICKET_TYPES: TicketType[] = [...SINGLE_TICKET_TYPES, 'MO_TK_THEO_LO']
const TYPE_OPTIONS = TICKET_TYPES.map((type) => ({ value: type, label: ticketTypeLabel(type) }))

function AllTicketsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const filters = readFilters(searchParams)
  const page = readPage(searchParams)
  const ticketsQuery = useQuery({
    queryKey: [TICKETS_QUERY_KEY, filters, page],
    queryFn: () => fetchTickets(filters, page),
  })
  const branchesQuery = useQuery({ queryKey: BRANCHES_QUERY_KEY, queryFn: fetchBranches })

  /** Đổi bộ lọc thì ghi lại URL và về trang 1, để không rơi vào trang trống. */
  function changeFilters(newFilters: TicketListFilters) {
    setSearchParams(toSearchParams(newFilters, 1))
  }

  const counts = ticketsQuery.data?.statusCounts
  const allCount = counts ? STATUS_ORDER.reduce((sum, status) => sum + counts[status], 0) : undefined
  const statusOptions = [
    { value: ALL, label: withCount('Tất cả', allCount) },
    ...STATUS_ORDER.map((status) => ({ value: status, label: withCount(TICKET_STATUS_LABELS[status], counts?.[status]) })),
  ]
  return (
    <Flex vertical gap={16}>
      <PageHeading
        breadcrumb={['Phiếu yêu cầu', 'Tất cả phiếu']}
        title="Tất cả phiếu"
        description="Phiếu của mọi chi nhánh, mới cập nhật ở trên"
      />
      <Card>
        <Flex vertical gap={16}>
          <Segmented
            options={statusOptions}
            value={filters.statuses[0] ?? ALL}
            onChange={(value) => changeFilters({ ...filters, statuses: value === ALL ? [] : [value as TicketStatus] })}
          />
          <FilterBar filters={filters} branches={branchesQuery.data ?? []} onChange={changeFilters} />
          <TicketTable
            variant="all"
            items={ticketsQuery.data?.items ?? []}
            loading={ticketsQuery.isPending}
            pagination={{
              current: page,
              total: ticketsQuery.data?.total ?? 0,
              showTotal: (total, range) => `${range[0]}–${range[1]} trên ${total.toLocaleString('vi-VN')}`,
              onChange: (newPage) => setSearchParams(toSearchParams(filters, newPage)),
            }}
          />
        </Flex>
      </Card>
    </Flex>
  )
}

/** "Chờ duyệt (12)"; chưa có số đếm (đang tải) thì chỉ có chữ. */
function withCount(label: string, count: number | undefined): string {
  return count === undefined ? label : `${label} (${count})`
}

/** Đọc bộ lọc từ URL; giá trị lạ (người dùng sửa tay URL) thì bỏ qua, không gửi lên API. */
function readFilters(searchParams: URLSearchParams): TicketListFilters {
  const status = searchParams.get(STATUS_PARAM)
  const code = searchParams.get(CODE_PARAM)
  const type = searchParams.get(TYPE_PARAM)
  return {
    statuses: STATUS_ORDER.filter((value) => value === status),
    code: code !== null && CODE_PATTERN.test(code) ? code : undefined,
    type: TICKET_TYPES.find((value) => value === type),
    branchId: readPositiveNumber(searchParams.get(BRANCH_PARAM)),
    createdFrom: readDate(searchParams.get(FROM_PARAM)),
    createdTo: readDate(searchParams.get(TO_PARAM)),
  }
}

function readPage(searchParams: URLSearchParams): number {
  return readPositiveNumber(searchParams.get(PAGE_PARAM)) ?? 1
}

function readPositiveNumber(text: string | null): number | undefined {
  if (text === null || !/^\d+$/.test(text) || Number(text) === 0) {
    return undefined
  }
  return Number(text)
}

/**
 * Ngày dạng 2026-11-05 có thật. dayjs không báo lỗi với ngày như 2026-13-45 mà tự lăn sang ngày khác, nên đổi lại
 * thành chữ rồi so với chữ ban đầu: khác nhau là ngày không có thật.
 */
function readDate(text: string | null): string | undefined {
  if (text === null || !DATE_PATTERN.test(text) || dayjs(text).format('YYYY-MM-DD') !== text) {
    return undefined
  }
  return text
}

/** Bộ lọc và số trang thành tham số URL; ô trống và trang 1 thì không ghi cho URL gọn. */
function toSearchParams(filters: TicketListFilters, page: number): URLSearchParams {
  const params = new URLSearchParams()
  const values: [string, string | number | undefined][] = [
    [STATUS_PARAM, filters.statuses[0]],
    [CODE_PARAM, filters.code],
    [TYPE_PARAM, filters.type],
    [BRANCH_PARAM, filters.branchId],
    [FROM_PARAM, filters.createdFrom],
    [TO_PARAM, filters.createdTo],
  ]
  for (const [name, value] of values) {
    if (value !== undefined) {
      params.set(name, String(value))
    }
  }
  if (page > 1) {
    params.set(PAGE_PARAM, String(page))
  }
  return params
}

interface FilterBarProps {
  filters: TicketListFilters
  branches: Branch[]
  onChange: (filters: TicketListFilters) => void
}

/** Ô mã phiếu, loại phiếu, chi nhánh, khoảng ngày tạo; lấy giá trị từ URL, đổi ô nào thì báo lên trang. */
function FilterBar({ filters, branches, onChange }: FilterBarProps) {
  const range: [Dayjs, Dayjs] | null = filters.createdFrom && filters.createdTo
    ? [dayjs(filters.createdFrom), dayjs(filters.createdTo)]
    : null
  return (
    <Form layout="inline" style={{ rowGap: 12 }}>
      {/* key theo giá trị trên URL: "Xóa lọc" hay nút Back đổi URL thì ô tìm dựng lại với giá trị mới. */}
      <CodeSearch key={filters.code ?? ''} initialValue={filters.code} onSearch={(code) => onChange({ ...filters, code })} />
      <Form.Item label="Loại phiếu">
        <Select
          allowClear
          placeholder="Tất cả"
          style={{ width: 200 }}
          options={TYPE_OPTIONS}
          value={filters.type}
          onChange={(type?: TicketType) => onChange({ ...filters, type })}
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
      <Form.Item label="Ngày tạo">
        <DatePicker.RangePicker
          format="DD/MM/YYYY"
          value={range}
          onChange={(dates) => onChange({
            ...filters,
            createdFrom: dates?.[0]?.format('YYYY-MM-DD'),
            createdTo: dates?.[1]?.format('YYYY-MM-DD'),
          })}
        />
      </Form.Item>
      <Button type="link" onClick={() => onChange({ statuses: filters.statuses })}>
        Xóa lọc
      </Button>
    </Form>
  )
}

interface CodeSearchProps {
  initialValue?: string
  onSearch: (code?: string) => void
}

/** Ô mã phiếu: khớp từ đầu mã, chỉ nhận chữ, số và dấu "-"; backend vẫn kiểm lại. */
function CodeSearch({ initialValue, onSearch }: CodeSearchProps) {
  const [hasError, setHasError] = useState(false)

  function search(text: string) {
    const code = text.trim()
    if (code !== '' && !CODE_PATTERN.test(code)) {
      setHasError(true)
      return
    }
    setHasError(false)
    onSearch(code === '' ? undefined : code)
  }

  return (
    <Form.Item
      validateStatus={hasError ? 'error' : undefined}
      help={hasError ? 'Mã phiếu chỉ gồm chữ, số và dấu -' : undefined}
    >
      <Input.Search allowClear placeholder="Mã phiếu" defaultValue={initialValue} style={{ width: 200 }} onSearch={search} />
    </Form.Item>
  )
}

export default AllTicketsPage