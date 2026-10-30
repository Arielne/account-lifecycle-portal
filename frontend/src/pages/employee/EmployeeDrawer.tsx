// Drawer dùng chung cho Thêm nhân viên, Sửa hồ sơ, Gán role (CN-22, màn 3.4).
// Form kiểm tra trước cùng quy tắc với backend; backend vẫn kiểm lại, lỗi trả về hiện dưới đúng ô (QT-07).
import { Alert, App, Badge, Button, Checkbox, Drawer, Flex, Form, Input, Select, Typography } from 'antd'
import type { FormRule } from 'antd'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { showApiError } from '../../api/apiError.ts'
import { ROLE_LABELS, type AppRole } from '../auth/currentUser.ts'
import type { Branch } from '../catalog/branchApi.ts'
import { formatDate } from '../catalog/format.ts'
import {
  assignEmployeeRoles,
  createEmployee,
  EMPLOYEES_QUERY_KEY,
  updateEmployee,
  type Employee,
  type EmployeeFormValues,
} from './employeeApi.ts'
import { EMPLOYEE_STATE_BADGES, EMPLOYEE_STATE_LABELS, employeeState } from './employeeState.ts'

export type DrawerMode = 'create' | 'edit' | 'assign'

const DRAWER_TITLES: Record<DrawerMode, string> = {
  create: 'Thêm nhân viên',
  edit: 'Sửa nhân viên',
  assign: 'Gán role',
}

const SUCCESS_MESSAGES: Record<DrawerMode, string> = {
  create: 'Đã tạo nhân viên và gửi mail đặt mật khẩu',
  edit: 'Đã lưu hồ sơ nhân viên',
  assign: 'Đã gán role. Nhân viên cần đăng nhập lại để nhận quyền mới.',
}

const ROLE_OPTIONS: { role: AppRole; description: string }[] = [
  { role: 'MAKER', description: 'Tạo phiếu yêu cầu, xem khách hàng và tài khoản' },
  { role: 'CHECKER', description: 'Duyệt hoặc từ chối phiếu; không duyệt phiếu do mình tạo' },
  { role: 'ADMIN', description: 'Quản lý danh mục, nhân viên, xem audit log' },
]

interface EmployeeDrawerProps {
  mode: DrawerMode
  /** Nhân viên đang sửa hoặc gán role; null khi thêm mới. */
  employee: Employee | null
  branches: Branch[]
  onClose: () => void
}

function EmployeeDrawer({ mode, employee, branches, onClose }: EmployeeDrawerProps) {
  const [form] = Form.useForm<EmployeeFormValues>()
  const { message } = App.useApp()
  const queryClient = useQueryClient()

  const saveMutation = useMutation({
    mutationFn: (values: EmployeeFormValues) => saveEmployee(mode, employee, values),
    onSuccess: () => {
      message.success(SUCCESS_MESSAGES[mode])
      queryClient.invalidateQueries({ queryKey: EMPLOYEES_QUERY_KEY })
      onClose()
    },
    onError: (error) => showApiError(error, message, form),
  })

  const footer = (
    <Flex justify="flex-end" gap={8}>
      <Button onClick={onClose}>Hủy</Button>
      <Button type="primary" loading={saveMutation.isPending} onClick={() => form.submit()}>
        {okLabel(mode, employee)}
      </Button>
    </Flex>
  )

  return (
    <Drawer open title={DRAWER_TITLES[mode]} size={480} onClose={onClose} footer={footer}>
      <Form
        form={form}
        layout="vertical"
        initialValues={employee ?? { roles: [] }}
        onFinish={(values) => saveMutation.mutate(values)}
      >
        {mode === 'assign' && employee !== null && <EmployeeSummary employee={employee} />}
        {mode !== 'assign' && <ProfileFields isCreate={mode === 'create'} />}
        <BranchField branches={branches} fixedRoles={mode === 'edit' && employee !== null ? employee.roles : null} />
        {mode !== 'edit' && <RoleField />}
      </Form>
    </Drawer>
  )
}

/** Gọi API theo chế độ của Drawer. Gán role mà đổi chi nhánh thì lưu chi nhánh trước rồi mới gán role (spec mục 6). */
async function saveEmployee(mode: DrawerMode, employee: Employee | null, values: EmployeeFormValues) {
  if (mode === 'create' || employee === null) {
    await createEmployee(values)
    return
  }
  const branchId = values.branchId ?? null
  if (mode === 'edit') {
    await updateEmployee(employee.id, { fullName: values.fullName, email: values.email, branchId })
    return
  }
  if (branchId !== employee.branchId) {
    await updateEmployee(employee.id, { fullName: employee.fullName, email: employee.email, branchId })
  }
  await assignEmployeeRoles(employee.id, values.roles)
}

function okLabel(mode: DrawerMode, employee: Employee | null): string {
  if (mode === 'create') {
    return 'Tạo và gửi mail'
  }
  if (mode === 'assign' && employee !== null && employeeState(employee) === 'PENDING') {
    return 'Lưu và kích hoạt'
  }
  return 'Lưu'
}

/** Đầu Drawer Gán role: họ tên, email, lúc tự đăng ký và trạng thái. */
function EmployeeSummary({ employee }: { employee: Employee }) {
  const state = employeeState(employee)
  const createdTime = new Date(employee.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
  return (
    <Flex vertical gap={2} style={{ marginBottom: 24 }}>
      <Typography.Text strong>{employee.fullName}</Typography.Text>
      <Typography.Text type="secondary">{employee.email}</Typography.Text>
      <Typography.Text type="secondary">
        {state === 'PENDING' && `Tự đăng ký lúc ${formatDate(employee.createdAt)} ${createdTime} · `}
        <Badge status={EMPLOYEE_STATE_BADGES[state]} text={EMPLOYEE_STATE_LABELS[state]} />
      </Typography.Text>
    </Flex>
  )
}

/** Họ tên và email; quy tắc giống Bean Validation ở EmployeeCreateRequest. */
function ProfileFields({ isCreate }: { isCreate: boolean }) {
  return (
    <>
      <Form.Item
        name="fullName"
        label="Họ tên"
        rules={[
          { required: true, whitespace: true, message: 'Vui lòng nhập họ tên' },
          { max: 100, message: 'Họ tên tối đa 100 ký tự' },
          { pattern: /^\s*\S+(\s+\S+)+\s*$/, message: 'Họ tên phải có ít nhất 2 từ' },
        ]}
      >
        <Input placeholder="VD: Nguyễn Văn An" />
      </Form.Item>
      <Form.Item
        name="email"
        label="Email công việc"
        extra={isCreate ? 'Hệ thống tạo tài khoản trên KeyCloak và gửi mail đặt mật khẩu tới địa chỉ này.' : undefined}
        rules={[
          { required: true, message: 'Vui lòng nhập email' },
          { type: 'email', message: 'Email chưa đúng dạng' },
          { max: 150, message: 'Email tối đa 150 ký tự' },
        ]}
      >
        <Input placeholder="ten.ho@nhdemo.local" />
      </Form.Item>
    </>
  )
}

/**
 * Chi nhánh làm việc: bắt buộc khi có role Giao dịch viên hoặc Kiểm soát viên; Admin không cần.
 * fixedRoles: role cố định (Drawer Sửa không có ô Role); null thì đọc ô Role của Form.
 */
function BranchField({ branches, fixedRoles }: { branches: Branch[]; fixedRoles: AppRole[] | null }) {
  // Rule dạng hàm: AntD truyền vào getFieldValue để đọc giá trị ô khác (ô Role) lúc kiểm tra.
  const branchRule: FormRule = ({ getFieldValue }) => ({
    validator(_rule, value: number | null | undefined) {
      const roles: AppRole[] = fixedRoles ?? getFieldValue('roles') ?? []
      const needsBranch = roles.includes('MAKER') || roles.includes('CHECKER')
      if (needsBranch && value == null) {
        return Promise.reject(new Error('Giao dịch viên, Kiểm soát viên phải có chi nhánh'))
      }
      return Promise.resolve()
    },
  })
  return (
    // dependencies: ô Role đổi thì kiểm tra lại ô này.
    <Form.Item name="branchId" label="Chi nhánh làm việc" dependencies={['roles']} rules={[branchRule]}>
      <Select
        allowClear
        placeholder="Chọn chi nhánh"
        options={branches.map((branch) => ({ value: branch.id, label: branch.name }))}
      />
    </Form.Item>
  )
}

function RoleField() {
  return (
    <>
      <Form.Item name="roles" label="Role" rules={[{ required: true, message: 'Chọn ít nhất một role' }]}>
        <Checkbox.Group>
          <Flex vertical gap={12}>
            {ROLE_OPTIONS.map((option) => (
              <Checkbox key={option.role} value={option.role}>
                <Flex vertical>
                  <Typography.Text>{ROLE_LABELS[option.role]}</Typography.Text>
                  <Typography.Text type="secondary">{option.description}</Typography.Text>
                </Flex>
              </Checkbox>
            ))}
          </Flex>
        </Checkbox.Group>
      </Form.Item>
      <Alert
        type="info"
        showIcon
        title="Role được ghi vào KeyCloak (realm role). Nhân viên cần đăng nhập lại để nhận quyền mới."
      />
    </>
  )
}

export default EmployeeDrawer