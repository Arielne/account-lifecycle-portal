// Nút "Sửa và gửi lại", "Hủy phiếu" ở trang chi tiết phiếu (CN-12, CN-13, spec phiếu mục 10.5). Hiện theo cờ backend
// tính cho người đang xem: người tạo thấy Sửa khi phiếu Bị từ chối, thấy Hủy khi Chờ duyệt hoặc Bị từ chối; người tạo
// đã bị khóa thì Giao dịch viên cùng chi nhánh thấy Hủy (hủy thay, D12). Backend vẫn kiểm lại khi bấm.
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { Alert, App, Button, Flex, Form, Input, Modal, Typography } from 'antd'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { readApiError, showApiError } from '../../api/apiError.ts'
import { ACCOUNT_STATUS_LABELS } from '../../components/accountStatus.ts'
import { cancelTicket, TICKETS_QUERY_KEY, type TicketDetail } from './ticketApi.ts'

interface RevisionActionsProps {
  ticket: TicketDetail
  /** Hủy xong, hoặc phiếu vừa đổi trạng thái (409): trang tải lại phiếu. */
  onChanged: () => void
}

function RevisionActions({ ticket, onChanged }: RevisionActionsProps) {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [cancelOpen, setCancelOpen] = useState(false)
  const [cancelForm] = Form.useForm<{ reason: string }>()

  const cancelMutation = useMutation({
    mutationFn: (reason: string) => cancelTicket(ticket.id, reason),
    onSuccess: () => {
      setCancelOpen(false)
      message.success(`Đã hủy phiếu ${ticket.code}`)
      void queryClient.invalidateQueries({ queryKey: [TICKETS_QUERY_KEY] })
      onChanged()
    },
    onError: (error) => {
      setCancelOpen(false)
      const apiError = readApiError(error)
      if (apiError?.status === 409) {
        message.warning(apiError.message)
        onChanged()
        return
      }
      showApiError(error, message)
    },
  })

  if (!ticket.canEdit && !ticket.canCancel) {
    return null
  }
  return (
    <Flex gap={8}>
      {ticket.canCancel && (
        <Button danger onClick={() => setCancelOpen(true)}>
          Hủy phiếu
        </Button>
      )}
      {ticket.canEdit && (
        <Button type="primary" onClick={() => navigate(`/phieu/${ticket.id}/sua`)}>
          Sửa và gửi lại
        </Button>
      )}
      <Modal
        open={cancelOpen}
        title={`Hủy phiếu ${ticket.code}?`}
        okText="Xác nhận hủy phiếu"
        okButtonProps={{ danger: true }}
        cancelText="Đóng"
        confirmLoading={cancelMutation.isPending}
        destroyOnHidden
        onOk={() => cancelForm.submit()}
        onCancel={() => setCancelOpen(false)}
      >
        <Flex vertical gap={12}>
          <Typography.Text>{cancelConsequence(ticket)}</Typography.Text>
          {ticket.newCustomer && (
            <Alert
              type="info"
              showIcon
              title="Sai thông tin khách thì Sửa và gửi lại, đừng hủy: hồ sơ khách vẫn được giữ sau khi hủy."
            />
          )}
          {ticket.cancelForLockedCreator && (
            <Alert type="warning" showIcon title={`Người tạo ${ticket.createdByName} đã bị khóa; bạn hủy thay.`} />
          )}
          <Form
            form={cancelForm}
            layout="vertical"
            preserve={false}
            onFinish={(values) => cancelMutation.mutate(values.reason.trim())}
          >
            <Form.Item
              name="reason"
              label="Lý do hủy"
              rules={[{ required: true, whitespace: true, message: 'Vui lòng nhập lý do hủy' }]}
            >
              <Input.TextArea rows={3} maxLength={500} showCount />
            </Form.Item>
          </Form>
        </Flex>
      </Modal>
    </Flex>
  )
}

/** Hậu quả của việc hủy theo loại phiếu (spec phiếu mục 10.5). */
function cancelConsequence(ticket: TicketDetail): string {
  if (ticket.type === 'MO_TAI_KHOAN') {
    return 'Tài khoản đang Chờ mở sẽ chuyển sang Đã đóng.'
  }
  const status = ticket.fromStatus ? ACCOUNT_STATUS_LABELS[ticket.fromStatus] : ''
  return `Tài khoản ${ticket.accountNumber ?? ''} giữ nguyên trạng thái ${status}.`
}

export default RevisionActions