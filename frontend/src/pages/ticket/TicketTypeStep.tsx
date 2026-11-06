// Bước 1 của màn Tạo phiếu (CN-11): chọn 1 trong 4 loại phiếu lẻ, mỗi loại một thẻ kèm thay đổi trạng thái tài khoản.
import { Card, Col, Flex, Form, Radio, Row, theme, Typography } from 'antd'
import AccountStatusTag from '../../components/AccountStatusTag.tsx'
import type { AccountStatus } from '../../components/accountStatus.ts'
import type { TicketType } from '../../components/ticketType.ts'

interface TypeOption {
  type: TicketType
  title: string
  description: string
  /** Trạng thái tài khoản trước khi duyệt; mảng vì có loại nhận nhiều trạng thái. */
  from: AccountStatus[]
  to: AccountStatus
}

const TYPE_OPTIONS: TypeOption[] = [
  { type: 'MO_TAI_KHOAN', title: 'Mở tài khoản', description: 'Cho khách hàng mới hoặc khách đã có hồ sơ',
    from: ['PENDING_OPEN'], to: 'ACTIVE' },
  { type: 'PHONG_TOA', title: 'Phong tỏa', description: 'Tạm khóa tài khoản theo yêu cầu', from: ['ACTIVE'],
    to: 'BLOCKED' },
  { type: 'GO_PHONG_TOA', title: 'Gỡ phong tỏa / Kích hoạt lại', description: 'Đưa tài khoản về trạng thái hoạt động',
    from: ['BLOCKED', 'DORMANT'], to: 'ACTIVE' },
  { type: 'DONG_TAI_KHOAN', title: 'Đóng tài khoản', description: 'Trạng thái cuối, không mở lại được',
    from: ['ACTIVE', 'BLOCKED', 'DORMANT'], to: 'CLOSED' },
]

function TicketTypeStep() {
  return (
    <Form.Item name="type" label="Chọn loại phiếu" rules={[{ required: true, message: 'Chọn loại phiếu' }]}>
      <TypeCards />
    </Form.Item>
  )
}

/** 4 thẻ có Radio; Form.Item truyền value, onChange vào như một ô nhập thường. */
function TypeCards({ value, onChange }: { value?: TicketType; onChange?: (type: TicketType) => void }) {
  const { token } = theme.useToken()
  return (
    <Radio.Group value={value} onChange={(event) => onChange?.(event.target.value)} style={{ width: '100%' }}>
      <Row gutter={[16, 16]}>
        {TYPE_OPTIONS.map((option) => (
          <Col key={option.type} xs={24} md={12}>
            <Card
              hoverable
              size="small"
              onClick={() => onChange?.(option.type)}
              style={{ borderColor: value === option.type ? token.colorPrimary : undefined }}
            >
              <Radio value={option.type}>
                <Typography.Text strong>{option.title}</Typography.Text>
              </Radio>
              <Flex vertical gap={8} style={{ marginTop: 8 }}>
                <Typography.Text type="secondary">{option.description}</Typography.Text>
                <Flex wrap align="center" gap={4}>
                  {option.from.map((status) => (
                    <AccountStatusTag key={status} status={status} />
                  ))}
                  →
                  <AccountStatusTag status={option.to} />
                </Flex>
              </Flex>
            </Card>
          </Col>
        ))}
      </Row>
    </Radio.Group>
  )
}

export default TicketTypeStep