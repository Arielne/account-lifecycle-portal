// Bước 2 của phiếu Phong tỏa, Gỡ phong tỏa, Đóng tài khoản (CN-11): tìm số tài khoản, backend kiểm trạng thái hợp lệ
// và không có phiếu đang mở (QT-03). Không hợp lệ thì báo dưới ô; màn Tạo phiếu khóa nút Tiếp tục.
import { useEffect } from 'react'
import { Card, Flex, Form, Input, Typography } from 'antd'
import type { FormRule } from 'antd'
import { readApiError } from '../../api/apiError.ts'
import AccountStatusTag from '../../components/AccountStatusTag.tsx'
import CodeText from '../../components/CodeText.tsx'
import type { TicketType } from '../../components/ticketType.ts'
import { checkEligibility, type TicketEligibility } from './ticketApi.ts'

interface AccountTargetFieldsProps {
  type: TicketType
  eligibility: TicketEligibility | null
  onEligibilityChange: (eligibility: TicketEligibility | null) => void
}

function AccountTargetFields({ type, eligibility, onEligibilityChange }: AccountTargetFieldsProps) {
  const form = Form.useFormInstance()

  // Số tài khoản điền sẵn (từ chi tiết tài khoản, ?taiKhoan=) hoặc loại phiếu vừa đổi: kiểm lại ngay.
  useEffect(() => {
    if (form.getFieldValue('accountNumber')) {
      form.validateFields(['accountNumber']).catch(() => undefined)
    }
  }, [form, type])

  /** Kiểm bằng API; tài khoản không có (404) hay không tạo được phiếu thì lỗi hiện dưới ô. */
  const eligibilityRule: FormRule = {
    validator: async (_rule, value?: string) => {
      if (!value || !/^\d{10}$/.test(value)) {
        return
      }
      let result: TicketEligibility
      try {
        result = await checkEligibility(type, value)
      } catch (error) {
        onEligibilityChange(null)
        throw new Error(readApiError(error)?.message ?? 'Không kiểm tra được tài khoản. Vui lòng thử lại.')
      }
      onEligibilityChange(result)
      if (!result.allowed) {
        throw new Error(result.message ?? 'Tài khoản này không tạo được phiếu')
      }
    },
  }

  return (
    <>
      <Form.Item
        name="accountNumber"
        label="Số tài khoản"
        validateFirst
        validateTrigger="onBlur"
        rules={[
          { required: true, message: 'Nhập số tài khoản' },
          { pattern: /^\d{10}$/, message: 'Số tài khoản gồm 10 chữ số' },
          eligibilityRule,
        ]}
      >
        <Input.Search
          placeholder="Nhập 10 số tài khoản"
          maxLength={10}
          enterButton="Kiểm tra"
          style={{ maxWidth: 420 }}
          onChange={() => onEligibilityChange(null)}
          onSearch={() => form.validateFields(['accountNumber']).catch(() => undefined)}
        />
      </Form.Item>
      {eligibility && <AccountCard eligibility={eligibility} />}
    </>
  )
}

/**
 * Thẻ tài khoản: số, loại, chủ tài khoản (CCCD đã che), trạng thái trước → sau khi duyệt. Không tạo được phiếu thì
 * không có trạng thái sau, chỉ hiện trạng thái hiện tại (tránh hiện kiểu "Hoạt động → Hoạt động").
 */
function AccountCard({ eligibility }: { eligibility: TicketEligibility }) {
  return (
    <Card size="small" style={{ maxWidth: 560, marginBottom: 16 }}>
      <Flex vertical gap={6}>
        <Typography.Text strong>
          <CodeText>{eligibility.accountNumber}</CodeText> · {eligibility.accountTypeName}
        </Typography.Text>
        <Typography.Text type="secondary">
          {eligibility.customerName} · <CodeText>{eligibility.customerCccd}</CodeText> · {eligibility.branchName}
        </Typography.Text>
        <Flex align="center" gap={4}>
          <AccountStatusTag status={eligibility.currentStatus} />
          {eligibility.allowed && <>→<AccountStatusTag status={eligibility.targetStatus} /></>}
        </Flex>
        {eligibility.allowed && (
          <Typography.Text type="success">
            Hợp lệ: trạng thái cho phép, không có phiếu nào đang Chờ duyệt hoặc Bị từ chối
          </Typography.Text>
        )}
      </Flex>
    </Card>
  )
}

export default AccountTargetFields