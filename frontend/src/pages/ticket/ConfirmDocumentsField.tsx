// Ô xác nhận đã đối chiếu giấy tờ gốc, bắt buộc ở bước cuối của màn Tạo phiếu (CN-11) và màn Sửa phiếu (CN-12).
// Backend cũng bắt confirmed phải là true.
import { Checkbox, Form } from 'antd'

function ConfirmDocumentsField() {
  return (
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
  )
}

export default ConfirmDocumentsField