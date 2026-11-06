// Trang chi tiết phiếu (CN-15, trang 2.4). Lần này chỉ xem; nút Duyệt, Từ chối, Sửa và gửi lại, Hủy phiếu thêm ở các
// bước sau của module phiếu. Giao dịch viên, kiểm soát viên xem được phiếu của mọi chi nhánh (QT-04).
import { useParams } from 'react-router'
import { Alert, Flex, Spin } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { readApiError } from '../../api/apiError.ts'
import PageHeading from '../../components/PageHeading.tsx'
import TicketStatusTag from '../../components/TicketStatusTag.tsx'
import { ticketTypeLabel } from '../../components/ticketType.ts'
import { formatDateTime } from '../catalog/format.ts'
import { fetchTicket, TICKET_QUERY_KEY } from './ticketApi.ts'
import TicketDetailBody from './TicketDetailBody.tsx'

function TicketDetailPage() {
  const id = Number(useParams().id)
  const ticketQuery = useQuery({
    queryKey: [TICKET_QUERY_KEY, id],
    queryFn: () => fetchTicket(id),
    // id không phải số (người dùng sửa tay URL) thì không gọi API.
    enabled: Number.isInteger(id),
  })

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
  return (
    <Flex vertical gap={16}>
      <PageHeading
        breadcrumb={['Phiếu yêu cầu', ticket.code]}
        title={`${ticket.code} · ${ticketTypeLabel(ticket.type, ticket.fromStatus)}`}
        description={`Tạo bởi ${ticket.createdByName} · ${formatDateTime(ticket.createdAt)} · Gửi lần ${ticket.submitCount}`}
        extra={<TicketStatusTag status={ticket.status} />}
      />
      <TicketDetailBody ticket={ticket} />
    </Flex>
  )
}

export default TicketDetailPage