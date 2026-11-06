// Bước 2 của phiếu Mở tài khoản cho khách mới (CN-11): hồ sơ khách theo bản thiết kế, quy tắc giống backend.
// Ô CCCD dùng CccdInput (che khi rời ô, QT-05); đủ 12 số thì gọi customer-check: đã có hồ sơ hoặc hồ sơ đang chờ
// duyệt thì báo dưới ô (D5); hồ sơ chưa từng mở được tài khoản thì báo sẽ ghi đè (D6) mà vẫn cho đi tiếp.
import { Alert, Col, DatePicker, Form, Input, Row, Select, Typography } from 'antd'
import type { FormRule } from 'antd'
import type { Dayjs } from 'dayjs'
import { readApiError } from '../../api/apiError.ts'
import CccdInput from '../../components/CccdInput.tsx'
import { REUSABLE_PROFILE_NOTE } from './createTicketForm.ts'
import { checkCustomer, type CustomerCheckResult } from './ticketApi.ts'

interface NewCustomerFieldsProps {
  branchName: string
  accountTypeOptions: { value: number; label: string }[]
  customerCheck: CustomerCheckResult | null
  onCustomerCheckChange: (result: CustomerCheckResult | null) => void
}

/** Ngày sau hôm nay không chọn được (ngày sinh, ngày cấp). */
function isFutureDate(date: Dayjs): boolean {
  return date.isAfter(new Date(), 'day')
}

function NewCustomerFields({ branchName, accountTypeOptions, customerCheck, onCustomerCheckChange }:
  NewCustomerFieldsProps) {
  const cccdRule: FormRule = {
    validator: async (_rule, value?: string) => {
      if (!value || !/^\d{12}$/.test(value)) {
        onCustomerCheckChange(null)
        return
      }
      let result: CustomerCheckResult
      try {
        result = await checkCustomer(value)
      } catch (error) {
        throw new Error(readApiError(error)?.message ?? 'Không kiểm tra được số CCCD. Vui lòng thử lại.')
      }
      onCustomerCheckChange(result)
      if (result.state === 'EXISTS') {
        throw new Error('Số CCCD đã có hồ sơ khách hàng. Hãy chọn Khách hàng đã có')
      }
      if (result.state === 'PENDING') {
        throw new Error(`Hồ sơ khách đang chờ duyệt ở phiếu ${result.ticketCode}`)
      }
    },
  }
  // Ngày cấp không trước ngày sinh: rule dạng hàm đọc ô ngày sinh lúc kiểm.
  const issueDateRule: FormRule = ({ getFieldValue }) => ({
    validator(_rule, value?: Dayjs) {
      const dateOfBirth: Dayjs | undefined = getFieldValue(['newCustomer', 'dateOfBirth'])
      if (value && dateOfBirth && value.isBefore(dateOfBirth, 'day')) {
        return Promise.reject(new Error('Ngày cấp không được trước ngày sinh'))
      }
      return Promise.resolve()
    },
  })

  return (
    <>
      <Row gutter={24}>
        <Col xs={24} md={8}>
          <Form.Item name={['newCustomer', 'fullName']} label="Họ và tên" rules={[
            { required: true, whitespace: true, message: 'Vui lòng nhập họ tên' },
            { max: 100, message: 'Họ tên tối đa 100 ký tự' },
            { pattern: /^\s*\S+(\s+\S+)+\s*$/, message: 'Họ tên phải có ít nhất 2 từ' },
          ]}>
            <Input placeholder="VD: Nguyễn Văn An" />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name={['newCustomer', 'dateOfBirth']} label="Ngày sinh"
            rules={[{ required: true, message: 'Vui lòng nhập ngày sinh' }]}>
            <DatePicker format="DD/MM/YYYY" disabledDate={isFutureDate} style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name={['newCustomer', 'gender']} label="Giới tính">
            <Select allowClear placeholder="Chọn" options={[{ value: 'Nam', label: 'Nam' }, { value: 'Nữ', label: 'Nữ' }]} />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item
            name={['newCustomer', 'cccd']}
            label="Số CCCD"
            validateFirst
            validateTrigger="onBlur"
            extra={customerCheck?.state === 'NEW' ? 'Chưa có hồ sơ nào dùng số CCCD này' : undefined}
            rules={[
              { required: true, message: 'Vui lòng nhập số CCCD' },
              { pattern: /^\d{12}$/, message: 'Số CCCD phải đủ 12 chữ số' },
              cccdRule,
            ]}
          >
            <CccdInput />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name={['newCustomer', 'idIssueDate']} label="Ngày cấp" dependencies={[['newCustomer', 'dateOfBirth']]}
            rules={[{ required: true, message: 'Vui lòng nhập ngày cấp' }, issueDateRule]}>
            <DatePicker format="DD/MM/YYYY" disabledDate={isFutureDate} style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name={['newCustomer', 'idIssuePlace']} label="Nơi cấp" rules={[
            { required: true, whitespace: true, message: 'Vui lòng nhập nơi cấp' },
            { max: 100, message: 'Nơi cấp tối đa 100 ký tự' },
          ]}>
            <Input placeholder="Cục CS QLHC về TTXH" />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name={['newCustomer', 'phone']} label="Điện thoại" rules={[
            { required: true, message: 'Vui lòng nhập số điện thoại' },
            { pattern: /^\d{10,11}$/, message: 'Số điện thoại gồm 10 đến 11 chữ số' },
          ]}>
            <Input inputMode="numeric" maxLength={11} />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name={['newCustomer', 'email']} label="Email nhận thông báo" rules={[
            { required: true, message: 'Vui lòng nhập email' },
            { type: 'email', message: 'Email chưa đúng dạng' },
            { max: 150, message: 'Email tối đa 150 ký tự' },
          ]}>
            <Input />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name="accountTypeId" label="Loại tài khoản" rules={[{ required: true, message: 'Chọn loại tài khoản' }]}>
            <Select placeholder="Chọn loại tài khoản" options={accountTypeOptions} />
          </Form.Item>
        </Col>
        <Col span={24}>
          <Form.Item name={['newCustomer', 'address']} label="Địa chỉ thường trú" rules={[
            { required: true, whitespace: true, message: 'Vui lòng nhập địa chỉ thường trú' },
            { max: 255, message: 'Địa chỉ tối đa 255 ký tự' },
          ]}>
            <Input />
          </Form.Item>
        </Col>
      </Row>
      {customerCheck?.state === 'REUSABLE' && (
        <Alert type="warning" showIcon title={REUSABLE_PROFILE_NOTE} style={{ marginBottom: 16 }} />
      )}
      <Typography.Text type="secondary">
        Chi nhánh mở là chi nhánh của bạn ({branchName}), không chọn. Số CCCD hiện đủ khi đang gõ, che lại khi rời ô.
      </Typography.Text>
    </>
  )
}

export default NewCustomerFields