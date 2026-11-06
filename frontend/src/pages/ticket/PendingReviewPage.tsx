// Màn Phiếu chờ duyệt (CN-14, màn 3.10, spec phiếu mục 10.3). Trái: ô lọc và danh sách phiếu Chờ duyệt của chi nhánh
// người xem, trừ phiếu người xem tự tạo, gửi sớm nhất ở trên (backend lọc, QT-01, QT-04). Phải: chi tiết phiếu đang
// chọn và nút Từ chối, Duyệt. Duyệt hoặc từ chối xong thì tải lại danh sách và mở phiếu đầu. Màn hẹp chỉ có danh sách,
// bấm vào mở trang chi tiết.
import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { App, Button, Card, Col, Empty, Flex, Grid, Input, List, Row, Select, Spin, Tag, theme, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import CodeText from '../../components/CodeText.tsx'
import PageHeading from '../../components/PageHeading.tsx'
import { SINGLE_TICKET_TYPES, ticketTypeLabel, type TicketType } from '../../components/ticketType.ts'
import { useCurrentUser } from '../auth/useCurrentUser.ts'
import ReviewActions, { type ReviewDecision } from './ReviewActions.tsx'
import {
  fetchPendingReview,
  fetchTicket,
  PENDING_REVIEW_QUERY_KEY,
  TICKET_QUERY_KEY,
  type PendingTicket,
  type TicketDetail,
} from './ticketApi.ts'
import TicketDetailBody from './TicketDetailBody.tsx'
import { formatWaitingTime, resubmitLabel } from './ticketFormat.ts'
import { FROM_PENDING_REVIEW_STATE } from './ticketRoutes.ts'

const TYPE_OPTIONS = [...SINGLE_TICKET_TYPES, 'MO_TK_THEO_LO' as const].map((type) => ({
  value: type,
  label: ticketTypeLabel(type),
}))

/**
 * Phiếu đang mở ở cột phải: phiếu người dùng đã chọn nếu còn trong danh sách; không thì phiếu ở ?phieu= (nút "Mở để
 * duyệt" ở Tổng quan) nếu có trong danh sách; không thì phiếu đầu. Phiếu vừa duyệt rời danh sách nên tự sang phiếu đầu.
 */
function pickActiveId(items: PendingTicket[], selectedId: number | null, requestedId: number): number | null {
  if (items.some((item) => item.id === selectedId)) {
    return selectedId
  }
  if (items.some((item) => item.id === requestedId)) {
    return requestedId
  }
  return items[0]?.id ?? null
}

function PendingReviewPage() {
  const branchName = useCurrentUser().branch?.name ?? 'của bạn'
  const { message } = App.useApp()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [requestedId] = useState(() => Number(searchParams.get('phieu')))
  // useBreakpoint: màn hẹp hơn breakpoint md của AntD thì md là false.
  const narrow = Grid.useBreakpoint().md === false
  const [codeFilter, setCodeFilter] = useState('')
  const [typeFilter, setTypeFilter] = useState<TicketType | undefined>()
  const [selectedId, setSelectedId] = useState<number | null>(null)

  const listQuery = useQuery({
    queryKey: [PENDING_REVIEW_QUERY_KEY, codeFilter, typeFilter],
    queryFn: () => fetchPendingReview(codeFilter, typeFilter),
  })
  const items = listQuery.data ?? []
  const activeId = pickActiveId(items, selectedId, requestedId)
  const detailQuery = useQuery({
    queryKey: [TICKET_QUERY_KEY, activeId],
    queryFn: () => fetchTicket(activeId as number),
    enabled: activeId !== null && !narrow,
  })

  /** Duyệt, từ chối xong: tải lại danh sách theo bộ lọc đang bật, mở phiếu đầu, báo mã phiếu kế. */
  async function afterReview(decision: ReviewDecision) {
    const reviewedCode = detailQuery.data?.code ?? ''
    const reloaded = await listQuery.refetch()
    const next = reloaded.data?.[0]
    setSelectedId(next?.id ?? null)
    const done = decision === 'APPROVED' ? 'Đã duyệt' : 'Đã từ chối'
    message.success(next ? `${done} ${reviewedCode} · đang mở ${next.code}` : `${done} ${reviewedCode}`)
  }

  function openTicket(id: number) {
    if (narrow) {
      navigate(`/phieu/${id}`, { state: FROM_PENDING_REVIEW_STATE })
      return
    }
    setSelectedId(id)
  }

  return (
    <Flex vertical gap={16}>
      <PageHeading
        breadcrumb={['Phiếu yêu cầu', 'Chờ duyệt']}
        title="Phiếu chờ duyệt"
        description={`${items.length} phiếu của ${branchName} · không gồm phiếu do bạn tạo · gửi sớm nhất lên trên`}
      />
      <Row gutter={16}>
        <Col xs={24} md={9}>
          <Card>
            <Flex vertical gap={12}>
              <Flex gap={8}>
                <Input.Search
                  placeholder="Mã phiếu"
                  allowClear
                  onSearch={(value) => setCodeFilter(value)}
                />
                <Select
                  allowClear
                  placeholder="Loại phiếu"
                  options={TYPE_OPTIONS}
                  value={typeFilter}
                  onChange={(value: TicketType | undefined) => setTypeFilter(value)}
                  style={{ minWidth: 160 }}
                />
              </Flex>
              <PendingList
                items={items}
                loading={listQuery.isPending}
                activeId={narrow ? null : activeId}
                onOpen={openTicket}
              />
            </Flex>
          </Card>
        </Col>
        {!narrow && activeId !== null && (
          <Col md={15}>
            <DetailColumn
              ticket={detailQuery.data}
              busy={listQuery.isFetching || detailQuery.isFetching}
              onReviewed={(decision) => void afterReview(decision)}
              onConflict={() => void listQuery.refetch()}
            />
          </Col>
        )}
      </Row>
    </Flex>
  )
}

interface PendingListProps {
  items: PendingTicket[]
  loading: boolean
  activeId: number | null
  onOpen: (id: number) => void
}

/** Danh sách bên trái; không có nút duyệt nhanh ở đây (CN-14). Thời gian chờ quá 24 giờ tô màu cảnh báo. */
function PendingList({ items, loading, activeId, onOpen }: PendingListProps) {
  // Màu nền dòng đang chọn lấy từ theme token của AntD (cùng màu mục đang chọn của Menu), không ghi mã màu cứng.
  const { token } = theme.useToken()
  if (!loading && items.length === 0) {
    return <Empty description="Không còn phiếu nào chờ bạn duyệt" />
  }
  return (
    <List
      loading={loading}
      dataSource={items}
      renderItem={(item) => {
        const resubmit = resubmitLabel(item.submitCount)
        return (
          <List.Item
            onClick={() => onOpen(item.id)}
            style={{
              cursor: 'pointer',
              paddingInline: 8,
              background: item.id === activeId ? token.controlItemBgActive : undefined,
            }}
            aria-current={item.id === activeId}
          >
            <Flex vertical gap={4} style={{ width: '100%' }}>
              <Flex justify="space-between">
                <CodeText>{item.code}</CodeText>
                <Typography.Text type={item.overdue ? 'danger' : 'secondary'}>
                  {formatWaitingTime(item.waitingMinutes)}
                </Typography.Text>
              </Flex>
              <Flex wrap gap={4} align="center">
                <Typography.Text strong>{ticketTypeLabel(item.type, item.fromStatus)}</Typography.Text>
                {resubmit && <Tag color="orange">{resubmit}</Tag>}
                {item.newCustomer && <Tag color="blue">Khách hàng mới</Tag>}
              </Flex>
              <Typography.Text type="secondary">
                {item.customerName ?? '—'} · {item.accountNumber ?? '—'}
              </Typography.Text>
            </Flex>
          </List.Item>
        )
      }}
    />
  )
}

interface DetailColumnProps {
  /** undefined khi đang tải chi tiết phiếu. */
  ticket: TicketDetail | undefined
  busy: boolean
  onReviewed: (decision: ReviewDecision) => void
  onConflict: () => void
}

/** Cột phải: chi tiết phiếu đang chọn và nút Từ chối, Duyệt; phiếu lô chỉ có nút mở trang đầy đủ (CN-14). */
function DetailColumn({ ticket, busy, onReviewed, onConflict }: DetailColumnProps) {
  const navigate = useNavigate()
  if (!ticket) {
    return (
      <Flex justify="center" style={{ padding: 48 }}>
        <Spin />
      </Flex>
    )
  }
  if (ticket.type === 'MO_TK_THEO_LO') {
    return (
      <Card title={ticket.code}>
        <Button type="primary" onClick={() => navigate(`/phieu/${ticket.id}`)}>Mở trang đầy đủ</Button>
      </Card>
    )
  }
  return (
    <Flex vertical gap={16}>
      <Card>
        <Flex wrap justify="space-between" align="center" gap={12}>
          <Typography.Title level={5} style={{ margin: 0 }}>
            {ticket.code} · {ticketTypeLabel(ticket.type, ticket.fromStatus)}
          </Typography.Title>
          <ReviewActions ticket={ticket} busy={busy} onReviewed={onReviewed} onConflict={onConflict} />
        </Flex>
      </Card>
      <TicketDetailBody ticket={ticket} compact />
    </Flex>
  )
}

export default PendingReviewPage