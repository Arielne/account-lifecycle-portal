// Bước 4 của màn Tạo phiếu (CN-11): xem lại toàn bộ trước khi gửi và xác nhận đã đối chiếu giấy tờ gốc (bắt buộc).
import { Alert, Checkbox, Descriptions, Flex, Form, Typography } from 'antd'
import AccountStatusTag from '../../components/AccountStatusTag.tsx'
import type { AccountStatus } from '../../components/accountStatus.ts'
import CodeText from '../../components/CodeText.tsx'
import { ticketTypeLabel } from '../../components/ticketType.ts'
import { REUSABLE_PROFILE_NOTE, type CreateTicketFormValues } from './createTicketForm.ts'
import type { CustomerCheckResult, TicketEligibility } from './ticketApi.ts'

interface ReviewStepProps {
  values: CreateTicketFormValues
  eligibility: TicketEligibility | null
  customerCheck: CustomerCheckResult | null
  /** Họ tên khách đã có (phiếu Mở tài khoản cho khách đã có hồ sơ). */
  existingCustomerName: string | null
  accountTypeName: string | null
  branchName: string
}

function ReviewStep({ values, eligibility, customerCheck, existingCustomerName, accountTypeName, branchName }:
  ReviewStepProps) {
  const isOpening = values.type === 'MO_TAI_KHOAN'
  const fromStatus: AccountStatus | undefined = isOpening ? 'PENDING_OPEN' : eligibility?.currentStatus
  const toStatus: AccountStatus | undefined = isOpening ? 'ACTIVE' : eligibility?.targetStatus
  const items = [
    { key: 'type', label: 'Loại phiếu', children: ticketTypeLabel(values.type, fromStatus) },
    { key: 'subject', label: 'Đối tượng', children: subjectText(values, eligibility, existingCustomerName, accountTypeName) },
    {
      key: 'change',
      label: 'Thay đổi khi được duyệt',
      children: fromStatus && toStatus && (
        <Flex align="center" gap={4}>
          <AccountStatusTag status={fromStatus} />→<AccountStatusTag status={toStatus} />
        </Flex>
      ),
    },
    { key: 'reason', label: 'Lý do', children: values.reason },
    { key: 'files', label: 'Giấy tờ', children: values.files.map((file) => file.name).join(', ') },
    {
      key: 'reviewer',
      label: 'Người duyệt',
      children: (
        <>
          Kiểm soát viên {branchName} <Typography.Text type="secondary">(trừ bạn)</Typography.Text>
        </>
      ),
    },
  ]
  return (
    <Flex vertical gap={16}>
      <Typography.Title level={5} style={{ margin: 0 }}>
        Kiểm tra lại trước khi gửi
      </Typography.Title>
      <Descriptions bordered size="small" column={1} items={items} />
      {isOpening && values.customerMode === 'NEW' && customerCheck?.state === 'REUSABLE' && (
        <Alert type="warning" showIcon title={REUSABLE_PROFILE_NOTE} />
      )}
      <Form.Item
        name="confirmed"
        valuePropName="checked"
        rules={[{ validator: (_rule, checked?: boolean) => checked
          ? Promise.resolve()
          : Promise.reject(new Error('Cần xác nhận đã đối chiếu thông tin với giấy tờ gốc')) }]}
        style={{ marginBottom: 0 }}
      >
        <Checkbox>Tôi đã đối chiếu thông tin với giấy tờ gốc của khách hàng</Checkbox>
      </Form.Item>
      <Typography.Text type="secondary">
        Sau khi gửi, phiếu không sửa được cho tới khi kiểm soát viên trả về.
      </Typography.Text>
    </Flex>
  )
}

/** Khách mới: họ tên và "khách hàng mới"; khách đã có: họ tên; phiếu tài khoản: số tài khoản và chủ tài khoản. */
function subjectText(values: CreateTicketFormValues, eligibility: TicketEligibility | null,
                     existingCustomerName: string | null, accountTypeName: string | null) {
  if (values.type !== 'MO_TAI_KHOAN') {
    return (
      <>
        TK <CodeText>{eligibility?.accountNumber}</CodeText> · {eligibility?.customerName}
      </>
    )
  }
  const customerName = values.customerMode === 'NEW' ? `${values.newCustomer?.fullName} (khách hàng mới)` : existingCustomerName
  return `${customerName} · ${accountTypeName ?? ''}`
}

export default ReviewStep