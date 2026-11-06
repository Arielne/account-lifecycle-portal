// Bước 2 của phiếu Mở tài khoản cho khách đã có hồ sơ (CN-11): tìm khách bằng đủ 12 số CCCD (hoặc khách chọn sẵn từ
// chi tiết khách hàng, ?khachHang=), hiện thẻ khách (CCCD đã che, QT-06) rồi chọn loại tài khoản mở thêm.
// Hồ sơ đang chờ duyệt ở phiếu Mở tài khoản khác thì không chọn được (D5).
import { Card, Flex, Form, Select, Spin, Typography } from 'antd'
import type { FormRule } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { readApiError } from '../../api/apiError.ts'
import AccountStatusTag from '../../components/AccountStatusTag.tsx'
import CccdInput from '../../components/CccdInput.tsx'
import CodeText from '../../components/CodeText.tsx'
import { fetchAccounts } from '../account/accountApi.ts'
import { formatLocalDate } from '../catalog/format.ts'
import { CUSTOMER_QUERY_KEY, fetchCustomer } from '../customer/customerApi.ts'
import { checkCustomer, type CustomerCheckResult } from './ticketApi.ts'

function ExistingCustomerFields({ accountTypeOptions }: { accountTypeOptions: { value: number; label: string }[] }) {
  const form = Form.useFormInstance()

  /** Đủ 12 số thì hỏi backend; tìm ra hồ sơ dùng được thì gán customerId cho Form. */
  const lookupRule: FormRule = {
    validator: async (_rule, value?: string) => {
      if (!value) {
        return
      }
      if (!/^\d{12}$/.test(value)) {
        throw new Error('Số CCCD phải đủ 12 chữ số')
      }
      let result: CustomerCheckResult
      try {
        result = await checkCustomer(value)
      } catch (error) {
        throw new Error(readApiError(error)?.message ?? 'Không tìm được khách hàng. Vui lòng thử lại.')
      }
      if (result.state === 'NEW' || result.customerId === null) {
        form.setFieldValue('customerId', undefined)
        throw new Error('Không có hồ sơ khách nào dùng số CCCD này')
      }
      if (result.state === 'PENDING') {
        form.setFieldValue('customerId', undefined)
        throw new Error(`Hồ sơ khách đang chờ duyệt ở phiếu ${result.ticketCode}`)
      }
      form.setFieldValue('customerId', result.customerId)
    },
  }

  return (
    <>
      <Form.Item name="existingCccd" label="Tìm khách hàng" validateTrigger="onBlur" rules={[lookupRule]}>
        <CccdInput />
      </Form.Item>
      <Form.Item name="customerId" rules={[{ required: true, message: 'Tìm khách hàng bằng số CCCD trước' }]}>
        <CustomerCard />
      </Form.Item>
      <Form.Item name="accountTypeId" label="Loại tài khoản mở thêm"
        rules={[{ required: true, message: 'Chọn loại tài khoản' }]}>
        <Select placeholder="Chọn loại tài khoản" options={accountTypeOptions} style={{ maxWidth: 420 }} />
      </Form.Item>
    </>
  )
}

/** Thẻ khách đã chọn: họ tên, CCCD đã che, ngày sinh, chi nhánh, Tag các tài khoản đang có. Form truyền value. */
function CustomerCard({ value }: { value?: number }) {
  const customerQuery = useQuery({
    queryKey: [CUSTOMER_QUERY_KEY, value],
    queryFn: () => fetchCustomer(value as number),
    enabled: value !== undefined,
  })
  const accountsQuery = useQuery({
    queryKey: ['customer-accounts', value],
    queryFn: () => fetchAccounts({ statuses: [], customerId: value }, 1),
    enabled: value !== undefined,
  })
  if (value === undefined) {
    return <Typography.Text type="secondary">Chưa chọn khách hàng</Typography.Text>
  }
  if (customerQuery.isPending) {
    return <Spin size="small" />
  }
  if (customerQuery.isError) {
    return <Typography.Text type="danger">Không tìm thấy khách hàng</Typography.Text>
  }
  const customer = customerQuery.data
  return (
    <Card size="small" style={{ maxWidth: 560 }}>
      <Flex vertical gap={6}>
        <Typography.Text strong>{customer.fullName}</Typography.Text>
        <Typography.Text type="secondary">
          <CodeText>{customer.cccd}</CodeText> · {formatLocalDate(customer.dateOfBirth)} · {customer.branchName}
        </Typography.Text>
        <Flex wrap align="center" gap={4}>
          <Typography.Text type="secondary">Đang có:</Typography.Text>
          {(accountsQuery.data?.items ?? []).map((account) => (
            <AccountStatusTag key={account.id} status={account.status} />
          ))}
        </Flex>
      </Flex>
    </Card>
  )
}

export default ExistingCustomerFields