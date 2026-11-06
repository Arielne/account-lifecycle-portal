// Nút Duyệt, Từ chối của một phiếu (CN-16, CN-17, spec phiếu mục 8.3), dùng ở trang 2.4 và cột phải màn Chờ duyệt.
// Hiện theo cờ backend tính cho người đang xem: được duyệt thì 2 nút bấm được; người tạo phiếu (QT-01) thấy nút bị khóa
// kèm Tooltip; kiểm soát viên khác chi nhánh không thấy nút mà thấy dòng "Phiếu thuộc ..." (D11). Khóa nút chỉ là lớp
// chặn thứ hai: backend kiểm lại mọi trường hợp khi bấm.
import { useState } from 'react'
import { App, Button, Flex, Form, Input, Modal, Tooltip, Typography } from 'antd'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { readApiError, showApiError } from '../../api/apiError.ts'
import { ACCOUNT_STATUS_LABELS } from '../../components/accountStatus.ts'
import { useCurrentUser } from '../auth/useCurrentUser.ts'
import {
  approveTicket,
  PENDING_REVIEW_SUMMARY_QUERY_KEY,
  rejectTicket,
  type TicketDetail,
} from './ticketApi.ts'

export type ReviewDecision = 'APPROVED' | 'REJECTED'

interface ReviewActionsProps {
  ticket: TicketDetail
  /** Khóa nút từ bên ngoài: màn Chờ duyệt khóa tới khi phiếu kế tải xong. */
  busy?: boolean
  /** Duyệt hoặc từ chối xong. */
  onReviewed: (decision: ReviewDecision) => void
  /** Backend trả 409 (phiếu vừa được người khác xử lý, trạng thái tài khoản đã đổi): màn cần tải lại. */
  onConflict: () => void
}

interface ReviewRequest {
  decision: ReviewDecision
  reason: string
}

function ReviewActions({ ticket, busy = false, onReviewed, onConflict }: ReviewActionsProps) {
  const isChecker = useCurrentUser().roles.includes('CHECKER')
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [openModal, setOpenModal] = useState<ReviewDecision | null>(null)
  const [rejectForm] = Form.useForm<{ reason: string }>()

  const reviewMutation = useMutation({
    mutationFn: (request: ReviewRequest) =>
      request.decision === 'APPROVED' ? approveTicket(ticket.id) : rejectTicket(ticket.id, request.reason),
    onSuccess: (_result, request) => {
      setOpenModal(null)
      // Badge số phiếu chờ ở menu đổi sau mỗi lần duyệt (D9).
      void queryClient.invalidateQueries({ queryKey: [PENDING_REVIEW_SUMMARY_QUERY_KEY] })
      onReviewed(request.decision)
    },
    onError: (error) => {
      setOpenModal(null)
      const apiError = readApiError(error)
      if (apiError?.status === 409) {
        message.warning(apiError.message)
        onConflict()
        return
      }
      showApiError(error, message)
    },
  })

  if (!isChecker || ticket.status !== 'PENDING_APPROVAL') {
    return null
  }
  if (!ticket.canApprove && ticket.blockedReason === null) {
    return <Typography.Text type="secondary">Phiếu thuộc {ticket.branchName}</Typography.Text>
  }
  const disabled = !ticket.canApprove || busy || reviewMutation.isPending
  // Nút bị khóa không nhận sự kiện chuột, nên bọc trong span để Tooltip vẫn hiện khi rê chuột vào.
  return (
    <Flex gap={8}>
      <Tooltip title={ticket.blockedReason}>
        <span>
          <Button danger disabled={disabled} onClick={() => setOpenModal('REJECTED')}>
            Từ chối
          </Button>
        </span>
      </Tooltip>
      <Tooltip title={ticket.blockedReason}>
        <span>
          <Button type="primary" disabled={disabled} onClick={() => setOpenModal('APPROVED')}>
            Duyệt
          </Button>
        </span>
      </Tooltip>
      <Modal
        open={openModal === 'APPROVED'}
        title={`Duyệt phiếu ${ticket.code}?`}
        okText="Duyệt"
        cancelText="Hủy"
        confirmLoading={reviewMutation.isPending}
        onOk={() => reviewMutation.mutate({ decision: 'APPROVED', reason: '' })}
        onCancel={() => setOpenModal(null)}
      >
        <Typography.Paragraph>{approveSummary(ticket)}</Typography.Paragraph>
      </Modal>
      <Modal
        open={openModal === 'REJECTED'}
        title={`Từ chối phiếu ${ticket.code}?`}
        okText="Từ chối"
        okButtonProps={{ danger: true }}
        cancelText="Hủy"
        confirmLoading={reviewMutation.isPending}
        destroyOnHidden
        onOk={() => rejectForm.submit()}
        onCancel={() => setOpenModal(null)}
      >
        <Typography.Paragraph>
          Phiếu sẽ trả về {ticket.createdByName} để sửa và gửi lại. Hệ thống gửi mail kèm lý do bên dưới.
        </Typography.Paragraph>
        <Form
          form={rejectForm}
          layout="vertical"
          preserve={false}
          onFinish={(values) => reviewMutation.mutate({ decision: 'REJECTED', reason: values.reason })}
        >
          <Form.Item
            name="reason"
            label="Lý do từ chối"
            rules={[{ required: true, whitespace: true, message: 'Vui lòng nhập lý do từ chối' }]}
          >
            <Input.TextArea rows={4} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Flex>
  )
}

/** "Tài khoản 1020000050: Hoạt động → Phong tỏa. Hãy chắc bạn đã đối chiếu giấy tờ đính kèm." (CN-16) */
function approveSummary(ticket: TicketDetail): string {
  const reminder = 'Hãy chắc bạn đã đối chiếu giấy tờ đính kèm.'
  if (!ticket.accountNumber || !ticket.fromStatus || !ticket.toStatus) {
    return reminder
  }
  const change = `${ACCOUNT_STATUS_LABELS[ticket.fromStatus]} → ${ACCOUNT_STATUS_LABELS[ticket.toStatus]}`
  return `Tài khoản ${ticket.accountNumber}: ${change}. ${reminder}`
}

export default ReviewActions