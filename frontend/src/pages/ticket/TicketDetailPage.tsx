// Trang chi tiết phiếu (CN-15, trang 2.4), kèm nút Duyệt, Từ chối cho kiểm soát viên (CN-16, CN-17) và Sửa và gửi
// lại, Hủy phiếu cho giao dịch viên (CN-12, CN-13). Giao dịch viên, kiểm soát viên xem được phiếu của mọi chi nhánh
// (QT-04). Đi tới từ màn Chờ duyệt (màn hẹp) thì Breadcrumb ghi "Chờ duyệt", duyệt xong quay về màn đó.
import { useLocation, useNavigate, useParams } from 'react-router'
import { Alert, Flex, Spin } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { readApiError } from '../../api/apiError.ts'
import PageHeading from '../../components/PageHeading.tsx'
import TicketStatusTag from '../../components/TicketStatusTag.tsx'
import { ticketTypeLabel } from '../../components/ticketType.ts'
import { formatDateTime } from '../catalog/format.ts'
import ReviewActions from './ReviewActions.tsx'
import RevisionActions from './RevisionActions.tsx'
import { fetchTicket, TICKET_QUERY_KEY } from './ticketApi.ts'
import TicketDetailBody from './TicketDetailBody.tsx'
import { FROM_PENDING_REVIEW_STATE, PENDING_REVIEW_ROUTE, type TicketDetailState } from './ticketRoutes.ts'

function TicketDetailPage() {
  const id = Number(useParams().id)
  const location = useLocation()
  const navigate = useNavigate()
  const routeState = location.state as TicketDetailState | null
  const fromPendingReview = routeState?.from === FROM_PENDING_REVIEW_STATE.from
  const ticketQuery = useQuery({
    queryKey: [TICKET_QUERY_KEY, id],
    queryFn: () => fetchTicket(id),
    // id không phải số (người dùng sửa tay URL) thì không gọi API.
    enabled: Number.isInteger(id),
  })

  /** Duyệt, từ chối xong: về màn Chờ duyệt nếu đi tới từ đó, không thì tải lại phiếu để thấy trạng thái mới. */
  function afterReview() {
    if (fromPendingReview) {
      navigate(PENDING_REVIEW_ROUTE)
      return
    }
    void ticketQuery.refetch()
  }

  if (!Number.isInteger(id) || ticketQuery.isError) {
    const message = readApiError(ticketQuery.error)?.message ?? 'Không tìm thấy phiếu'
    return <Alert type="error" showIcon title={message} />
  }
  if (ticketQuery.isPending) {
    return (
      <Flex justify="center" style={{ padding: 48 }}>
        <Spin />
      </Flex>
    )
  }
  const ticket = ticketQuery.data
  const breadcrumb = fromPendingReview ? ['Phiếu yêu cầu', 'Chờ duyệt', ticket.code] : ['Phiếu yêu cầu', ticket.code]
  return (
    <Flex vertical gap={16}>
      <PageHeading
        breadcrumb={breadcrumb}
        title={`${ticket.code} · ${ticketTypeLabel(ticket.type, ticket.fromStatus)}`}
        description={`Tạo bởi ${ticket.createdByName} · ${formatDateTime(ticket.createdAt)} · Gửi lần ${ticket.submitCount}`}
        extra={
          <Flex align="center" gap={12}>
            <TicketStatusTag status={ticket.status} />
            <RevisionActions ticket={ticket} onChanged={() => void ticketQuery.refetch()} />
            <ReviewActions
              ticket={ticket}
              busy={ticketQuery.isFetching}
              onReviewed={afterReview}
              onConflict={() => void ticketQuery.refetch()}
            />
          </Flex>
        }
      />
      <TicketDetailBody ticket={ticket} />
    </Flex>
  )
}

export default TicketDetailPage