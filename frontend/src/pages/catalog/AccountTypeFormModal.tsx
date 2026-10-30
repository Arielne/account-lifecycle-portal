// Modal thêm, sửa loại tài khoản (CN-20, màn 3.2).
// Form kiểm tra trước cùng quy tắc với backend; backend vẫn kiểm lại, lỗi trả về thì hiện dưới đúng ô (QT-07).
import { App, Form, Input, InputNumber, Modal } from 'antd'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { showApiError } from '../../api/apiError.ts'
import {
  ACCOUNT_TYPES_QUERY_KEY,
  createAccountType,
  updateAccountType,
  type AccountType,
  type AccountTypeFormValues,
} from './accountTypeApi.ts'

interface AccountTypeFormModalProps {
  /** Loại đang sửa; null khi thêm mới. */
  accountType: AccountType | null
  onClose: () => void
}

function AccountTypeFormModal({ accountType, onClose }: AccountTypeFormModalProps) {
  const [form] = Form.useForm<AccountTypeFormValues>()
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const isEdit = accountType !== null

  // useMutation: gọi API ghi, kèm việc làm khi thành công (làm mới danh sách, đóng Modal) hoặc khi lỗi.
  const saveMutation = useMutation({
    mutationFn: (values: AccountTypeFormValues) =>
      accountType ? updateAccountType(accountType.id, values) : createAccountType(values),
    onSuccess: () => {
      message.success(isEdit ? 'Đã lưu loại tài khoản' : 'Đã thêm loại tài khoản')
      queryClient.invalidateQueries({ queryKey: ACCOUNT_TYPES_QUERY_KEY })
      onClose()
    },
    onError: (error) => showApiError(error, message, form),
  })

  return (
    <Modal
      open
      title={isEdit ? 'Sửa loại tài khoản' : 'Thêm loại tài khoản'}
      width={520}
      okText="Lưu"
      cancelText="Hủy"
      confirmLoading={saveMutation.isPending}
      onOk={() => form.submit()}
      onCancel={onClose}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={accountType ?? { dormantAfterMonths: 12 }}
        onFinish={(values) => saveMutation.mutate(values)}
      >
        <AccountTypeFields isEdit={isEdit} />
      </Form>
    </Modal>
  )
}

/** Các ô của Form; quy tắc giống Bean Validation ở AccountTypeCreateRequest. */
function AccountTypeFields({ isEdit }: { isEdit: boolean }) {
  return (
    <>
      <Form.Item
        name="code"
        label="Mã loại"
        extra="2–10 ký tự in hoa hoặc số, không trùng. Không sửa được sau khi tạo."
        normalize={(value: string) => value.toUpperCase()}
        rules={[
          { required: true, message: 'Vui lòng nhập mã loại' },
          { pattern: /^[A-Z0-9]{2,10}$/, message: 'Mã gồm 2–10 ký tự in hoa hoặc số' },
        ]}
      >
        <Input placeholder="VD: TTCN" disabled={isEdit} />
      </Form.Item>
      <Form.Item
        name="name"
        label="Tên loại tài khoản"
        rules={[
          { required: true, whitespace: true, message: 'Vui lòng nhập tên loại tài khoản' },
          { max: 100, message: 'Tên tối đa 100 ký tự' },
        ]}
      >
        <Input placeholder="VD: Thanh toán cá nhân" />
      </Form.Item>
      <Form.Item
        name="dormantAfterMonths"
        label="Chuyển ngủ đông sau (tháng không giao dịch)"
        extra="Hệ thống tự chuyển Ngủ đông khi tài khoản không giao dịch quá số tháng này. Giảm số tháng thì ngay đêm đó nhiều tài khoản có thể bị chuyển cùng lúc."
        rules={[
          { required: true, message: 'Vui lòng nhập số tháng' },
          { type: 'number', min: 1, max: 120, message: 'Số tháng từ 1 đến 120' },
        ]}
      >
        <InputNumber min={1} max={120} precision={0} suffix="tháng" style={{ width: 200 }} />
      </Form.Item>
      <Form.Item name="description" label="Mô tả" rules={[{ max: 255, message: 'Mô tả tối đa 255 ký tự' }]}>
        <Input.TextArea rows={3} />
      </Form.Item>
    </>
  )
}

export default AccountTypeFormModal