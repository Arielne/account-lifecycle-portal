// Trạng thái hiển thị của nhân viên trên màn Nhân viên (CN-22, spec admin mục 6):
// status LOCKED → Đã khóa; chưa có role nào → Chờ cấp quyền; còn lại → Hoạt động.
import type { Employee } from './employeeApi.ts'

export type EmployeeState = 'PENDING' | 'ACTIVE' | 'LOCKED'

export const EMPLOYEE_STATE_LABELS: Record<EmployeeState, string> = {
  PENDING: 'Chờ cấp quyền',
  ACTIVE: 'Hoạt động',
  LOCKED: 'Đã khóa',
}

/** Kiểu chấm màu của Badge (AntD) cho từng trạng thái. */
export const EMPLOYEE_STATE_BADGES: Record<EmployeeState, 'warning' | 'success' | 'error'> = {
  PENDING: 'warning',
  ACTIVE: 'success',
  LOCKED: 'error',
}

export function employeeState(employee: Employee): EmployeeState {
  if (employee.status === 'LOCKED') {
    return 'LOCKED'
  }
  if (employee.roles.length === 0) {
    return 'PENDING'
  }
  return 'ACTIVE'
}

/** Người Chờ cấp quyền luôn ở đầu, sau đó xếp theo họ tên theo thứ tự chữ cái tiếng Việt. */
export function sortEmployees(employees: Employee[]): Employee[] {
  return [...employees].sort((first, second) => {
    const isFirstPending = employeeState(first) === 'PENDING'
    const isSecondPending = employeeState(second) === 'PENDING'
    if (isFirstPending !== isSecondPending) {
      return isFirstPending ? -1 : 1
    }
    return first.fullName.localeCompare(second.fullName, 'vi')
  })
}