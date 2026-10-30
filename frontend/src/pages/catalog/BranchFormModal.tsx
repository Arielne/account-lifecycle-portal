// Modal thêm, sửa chi nhánh (CN-21, màn 3.3): Form 2 cột, kiểm tra mã trùng ngay khi rời ô Mã.
// Form kiểm tra trước cùng quy tắc với backend; backend vẫn kiểm lại, lỗi trả về thì hiện dưới đúng ô (QT-07).
import { App, Col, Form, Input, Modal, Row } from 'antd'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { showApiError } from '../../api/apiError.ts'
import {
  BRANCHES_QUERY_KEY,
  createBranch,
  isBranchCodeAvailable,
  updateBranch,
  type Branch,
  type BranchFormValues,
} from './branchApi.ts'

const BRANCH_CODE_PATTERN = /^[A-Z0-9-]{2,10}$/

interface BranchFormModalProps {
  /** Chi nhánh đang sửa; null khi thêm mới. */
  branch: Branch | null
  onClose: () => void
}

function BranchFormModal({ branch, onClose }: BranchFormModalProps) {
  const [form] = Form.useForm<BranchFormValues>()
  const { message } = App.useApp()
  const queryClient = useQueryClient()

  const saveMutation = useMutation({
    mutationFn: (values: BranchFormValues) => (branch ? updateBranch(branch.id, values) : createBranch(values)),
    onSuccess: () => {
      message.success(branch ? 'Đã lưu chi nhánh' : 'Đã thêm chi nhánh')
      queryClient.invalidateQueries({ queryKey: BRANCHES_QUERY_KEY })
      onClose()
    },
    onError: (error) => showApiError(error, message, form),
  })

  /** Hỏi backend mã còn trống không. Mã sai dạng để rule pattern báo; mã giữ nguyên khi sửa thì không cần hỏi. */
  async function validateCodeAvailable(code: string | undefined) {
    if (!code || !BRANCH_CODE_PATTERN.test(code) || code === branch?.code) {
      return
    }
    let isAvailable = true
    try {
      isAvailable = await isBranchCodeAvailable(code)
    } catch {
      // Không hỏi được backend thì bỏ qua; lúc Lưu backend vẫn kiểm tra trùng.
      return
    }
    if (!isAvailable) {
      throw new Error('Mã chi nhánh đã tồn tại')
    }
  }

  return (
    <Modal
      open
      title={branch ? 'Sửa chi nhánh' : 'Thêm chi nhánh'}
      width={720}
      okText="Lưu"
      cancelText="Hủy"
      confirmLoading={saveMutation.isPending}
      onOk={() => form.submit()}
      onCancel={onClose}
    >
      <Form form={form} layout="vertical" initialValues={branch ?? {}} onFinish={(values) => saveMutation.mutate(values)}>
        <Row gutter={24}>
          <Col span={12}>
            <Form.Item
              name="code"
              label="Mã chi nhánh"
              normalize={(value: string) => value.toUpperCase()}
              // Rule thường kiểm khi gõ; rule hỏi backend chỉ chạy khi rời ô (và khi bấm Lưu).
              validateTrigger={['onChange', 'onBlur']}
              rules={[
                { required: true, message: 'Vui lòng nhập mã chi nhánh' },
                { pattern: BRANCH_CODE_PATTERN, message: 'Mã gồm 2–10 ký tự in hoa, số hoặc dấu -' },
                { validator: (_rule, value: string | undefined) => validateCodeAvailable(value), validateTrigger: 'onBlur' },
              ]}
            >
              <Input placeholder="VD: CN-Q1" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <BranchNameField />
          </Col>
          <Col span={24}>
            <BranchAddressField />
          </Col>
          <Col span={12}>
            <BranchPhoneField />
          </Col>
        </Row>
      </Form>
    </Modal>
  )
}

function BranchNameField() {
  return (
    <Form.Item
      name="name"
      label="Tên chi nhánh"
      rules={[
        { required: true, whitespace: true, message: 'Vui lòng nhập tên chi nhánh' },
        { max: 100, message: 'Tên tối đa 100 ký tự' },
      ]}
    >
      <Input placeholder="VD: Chi nhánh Gò Vấp" />
    </Form.Item>
  )
}

function BranchAddressField() {
  return (
    <Form.Item
      name="address"
      label="Địa chỉ"
      rules={[
        { required: true, whitespace: true, message: 'Vui lòng nhập địa chỉ' },
        { max: 255, message: 'Địa chỉ tối đa 255 ký tự' },
      ]}
    >
      <Input placeholder="Số nhà, đường, phường, tỉnh/thành" />
    </Form.Item>
  )
}

/**
 * Điện thoại: normalize bỏ mọi ký tự không phải chữ số ngay khi gõ, nên giá trị gửi lên chỉ còn chữ số.
 * Không đặt maxLength: trình duyệt cắt chuỗi dán vào (ví dụ "028 3872 0011") theo số ký tự tính cả dấu cách,
 * trước khi normalize chạy, nên mất chữ số cuối. Độ dài đã có rule pattern kiểm tra.
 */
function BranchPhoneField() {
  return (
    <Form.Item
      name="phone"
      label="Số điện thoại"
      normalize={(value: string) => value.replace(/\D/g, '')}
      rules={[
        { required: true, message: 'Vui lòng nhập số điện thoại' },
        { pattern: /^\d{10,11}$/, message: 'Số điện thoại phải có 10–11 chữ số' },
      ]}
    >
      <Input placeholder="VD: 02839112200" />
    </Form.Item>
  )
}

export default BranchFormModal