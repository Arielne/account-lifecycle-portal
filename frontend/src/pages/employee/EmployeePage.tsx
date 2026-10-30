// Màn Nhân viên (CN-22, màn 3.4): xem, tìm, lọc theo chi nhánh, role, trạng thái; thêm, sửa, gán role, khóa, mở khóa.
// Chỉ Admin vào được (RequireRole ở router). Tìm, lọc, phân trang 10 dòng làm ngay ở React; backend vẫn kiểm quyền (QT-07).
import { useState } from 'react'
import { App, Badge, Button, Card, Flex, Input, Select, Space, Table, Tag, Typography } from 'antd'
import type { TableColumnsType } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { showApiError } from '../../api/apiError.ts'
import PageHeading from '../../components/PageHeading.tsx'
import { ROLE_LABELS, ROLE_TAG_COLORS, type AppRole } from '../auth/currentUser.ts'
import { useCurrentUser } from '../auth/useCurrentUser.ts'
import { BRANCHES_QUERY_KEY, fetchBranches } from '../catalog/branchApi.ts'
import EmployeeDrawer, { type DrawerMode } from './EmployeeDrawer.tsx'
import { EMPLOYEES_QUERY_KEY, fetchEmployees, lockEmployee, unlockEmployee, type Employee } from './employeeApi.ts'
import {
  EMPLOYEE_STATE_BADGES,
  EMPLOYEE_STATE_LABELS,
  employeeState,
  sortEmployees,
  type EmployeeState,
} from './employeeState.ts'

interface EmployeeFilters {
  keyword: string
  branchId?: number
  role?: AppRole
  state?: EmployeeState
}

interface DrawerState {
  mode: DrawerMode
  employee: Employee | null
}

function EmployeePage() {
  const currentUser = useCurrentUser()
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState<EmployeeFilters>({ keyword: '' })
  const [drawer, setDrawer] = useState<DrawerState | null>(null)

  const employeesQuery = useQuery({ queryKey: EMPLOYEES_QUERY_KEY, queryFn: fetchEmployees })
  const branchesQuery = useQuery({ queryKey: BRANCHES_QUERY_KEY, queryFn: fetchBranches })
  const lockMutation = useMutation({
    mutationFn: (target: { employee: Employee; lock: boolean }) =>
      target.lock ? lockEmployee(target.employee.id) : unlockEmployee(target.employee.id),
    onSuccess: (_result, target) => {
      message.success(target.lock ? 'Đã khóa nhân viên' : 'Đã mở khóa nhân viên')
      queryClient.invalidateQueries({ queryKey: EMPLOYEES_QUERY_KEY })
    },
    onError: (error) => showApiError(error, message),
  })

  /** Khóa, mở khóa đều hỏi xác nhận (màn 3.13); nút xác nhận ghi đúng động từ. */
  function confirmLock(employee: Employee, lock: boolean) {
    modal.confirm({
      title: `${lock ? 'Khóa' : 'Mở khóa'} nhân viên ${employee.fullName}?`,
      content: lock
        ? 'Người này bị đăng xuất ngay và không đăng nhập được nữa. Phiếu họ đã tạo vẫn giữ nguyên.'
        : 'Người này đăng nhập lại được với các role hiện có.',
      okText: lock ? 'Khóa' : 'Mở khóa',
      okButtonProps: { danger: lock },
      cancelText: 'Hủy',
      onOk: () => lockMutation.mutate({ employee, lock }),
    })
  }

  const employees = sortEmployees(employeesQuery.data ?? [])
  const actions: RowActions = {
    onEdit: (employee) => setDrawer({ mode: 'edit', employee }),
    onAssign: (employee) => setDrawer({ mode: 'assign', employee }),
    onLock: (employee) => confirmLock(employee, true),
    onUnlock: (employee) => confirmLock(employee, false),
  }

  return (
    <Flex vertical gap={16}>
      <PageHeading
        breadcrumb={['Nhân viên']}
        title="Nhân viên"
        description="Tài khoản đăng nhập do KeyCloak quản lý; hồ sơ và role đồng bộ về hệ thống"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setDrawer({ mode: 'create', employee: null })}>
            Thêm nhân viên
          </Button>
        }
      />
      <Card>
        <FilterBar filters={filters} branches={branchesQuery.data ?? []} onChange={setFilters} />
        <Table
          rowKey="id"
          size="middle"
          loading={employeesQuery.isPending}
          columns={buildColumns(currentUser.keycloakId, actions)}
          dataSource={filterEmployees(employees, filters)}
          pagination={{ pageSize: 10, showTotal: (total, range) => `${range[0]}–${range[1]} trên ${total}` }}
        />
        <Typography.Text type="secondary">Nhân viên chờ cấp quyền luôn nằm đầu danh sách.</Typography.Text>
      </Card>
      {drawer !== null && (
        <EmployeeDrawer
          mode={drawer.mode}
          employee={drawer.employee}
          branches={branchesQuery.data ?? []}
          onClose={() => setDrawer(null)}
        />
      )}
    </Flex>
  )
}

interface FilterBarProps {
  filters: EmployeeFilters
  branches: { id: number; name: string }[]
  onChange: (filters: EmployeeFilters) => void
}

/** Ô tìm theo họ tên, email và 3 ô lọc; ô lọc để trống là không lọc. */
function FilterBar({ filters, branches, onChange }: FilterBarProps) {
  return (
    <Flex wrap gap={12} style={{ marginBottom: 16 }}>
      <Input.Search
        allowClear
        placeholder="Tìm theo họ tên hoặc email"
        aria-label="Tìm nhân viên"
        style={{ width: 280, maxWidth: '100%' }}
        onChange={(event) => onChange({ ...filters, keyword: event.target.value })}
      />
      <Select
        allowClear
        placeholder="Chi nhánh"
        aria-label="Lọc chi nhánh"
        style={{ width: 200 }}
        options={branches.map((branch) => ({ value: branch.id, label: branch.name }))}
        onChange={(branchId?: number) => onChange({ ...filters, branchId })}
      />
      <Select
        allowClear
        placeholder="Role"
        aria-label="Lọc role"
        style={{ width: 170 }}
        options={(Object.keys(ROLE_LABELS) as AppRole[]).map((role) => ({ value: role, label: ROLE_LABELS[role] }))}
        onChange={(role?: AppRole) => onChange({ ...filters, role })}
      />
      <Select
        allowClear
        placeholder="Trạng thái"
        aria-label="Lọc trạng thái"
        style={{ width: 170 }}
        options={(Object.keys(EMPLOYEE_STATE_LABELS) as EmployeeState[]).map((state) => ({
          value: state,
          label: EMPLOYEE_STATE_LABELS[state],
        }))}
        onChange={(state?: EmployeeState) => onChange({ ...filters, state })}
      />
    </Flex>
  )
}

function filterEmployees(employees: Employee[], filters: EmployeeFilters): Employee[] {
  const keyword = filters.keyword.trim().toLowerCase()
  return employees.filter(
    (employee) =>
      (keyword === '' ||
        employee.fullName.toLowerCase().includes(keyword) ||
        employee.email.toLowerCase().includes(keyword)) &&
      (filters.branchId === undefined || employee.branchId === filters.branchId) &&
      (filters.role === undefined || employee.roles.includes(filters.role)) &&
      (filters.state === undefined || employeeState(employee) === filters.state),
  )
}

interface RowActions {
  onEdit: (employee: Employee) => void
  onAssign: (employee: Employee) => void
  onLock: (employee: Employee) => void
  onUnlock: (employee: Employee) => void
}

function buildColumns(currentKeycloakId: string, actions: RowActions): TableColumnsType<Employee> {
  return [
    {
      title: 'Họ tên',
      dataIndex: 'fullName',
      render: (fullName: string, employee) => (
        <>
          {fullName}
          {employee.keycloakId === currentKeycloakId && <Typography.Text type="secondary"> (bạn)</Typography.Text>}
        </>
      ),
    },
    { title: 'Email', dataIndex: 'email' },
    { title: 'Chi nhánh', dataIndex: 'branchName', render: (branchName: string | null) => branchName ?? '—' },
    { title: 'Role', dataIndex: 'roles', render: (roles: AppRole[]) => <RoleTags roles={roles} /> },
    {
      title: 'Trạng thái',
      key: 'state',
      render: (_value, employee) => {
        const state = employeeState(employee)
        return <Badge status={EMPLOYEE_STATE_BADGES[state]} text={EMPLOYEE_STATE_LABELS[state]} />
      },
    },
    {
      title: 'Thao tác',
      key: 'actions',
      render: (_value, employee) => (
        <EmployeeActions employee={employee} isSelf={employee.keycloakId === currentKeycloakId} actions={actions} />
      ),
    },
  ]
}

function RoleTags({ roles }: { roles: AppRole[] }) {
  if (roles.length === 0) {
    return <Typography.Text type="secondary">Chưa có</Typography.Text>
  }
  return (
    <Space size={4} wrap>
      {roles.map((role) => (
        <Tag key={role} color={ROLE_TAG_COLORS[role]}>
          {ROLE_LABELS[role]}
        </Tag>
      ))}
    </Space>
  )
}

/**
 * Nút theo trạng thái, đúng như bản thiết kế: dòng của chính Admin chỉ có Sửa; Chờ cấp quyền chỉ có Gán role;
 * Hoạt động có Sửa, Gán role, Khóa; Đã khóa có Sửa, Mở khóa. Backend vẫn chặn tự khóa, tự đổi role.
 */
function EmployeeActions({ employee, isSelf, actions }: { employee: Employee; isSelf: boolean; actions: RowActions }) {
  const state = employeeState(employee)
  const showEdit = isSelf || state !== 'PENDING'
  const showAssign = !isSelf && state !== 'LOCKED'
  return (
    <Space size={4}>
      {showEdit && (
        <Button type="link" size="small" onClick={() => actions.onEdit(employee)}>
          Sửa
        </Button>
      )}
      {showAssign && (
        <Button type="link" size="small" onClick={() => actions.onAssign(employee)}>
          Gán role
        </Button>
      )}
      {!isSelf && state === 'ACTIVE' && (
        <Button type="link" size="small" danger onClick={() => actions.onLock(employee)}>
          Khóa
        </Button>
      )}
      {!isSelf && state === 'LOCKED' && (
        <Button type="link" size="small" onClick={() => actions.onUnlock(employee)}>
          Mở khóa
        </Button>
      )}
    </Space>
  )
}

export default EmployeePage