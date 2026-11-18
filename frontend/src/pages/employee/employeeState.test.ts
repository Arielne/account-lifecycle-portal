// Số nhân viên Chờ cấp quyền cho badge ở mục menu Nhân viên (CN-22): chỉ đếm người chưa có role và chưa bị khóa.
import { describe, expect, it } from 'vitest'
import type { Employee } from './employeeApi.ts'
import { countPendingEmployees } from './employeeState.ts'

function employee(id: number, roles: Employee['roles'], status: Employee['status']): Employee {
  return {
    id,
    keycloakId: `k-${id}`,
    fullName: `Nhân Viên ${id}`,
    email: `nv${id}@nhdemo.local`,
    branchId: null,
    branchName: null,
    roles,
    status,
    createdAt: '2026-11-01T02:00:00Z',
  }
}

describe('countPendingEmployees', () => {
  it('Đếm người chưa có role; người đã có role và người đã khóa không tính', () => {
    // Chuẩn bị: 2 người chờ cấp quyền, 1 giao dịch viên, 1 người đã khóa chưa có role
    const employees = [
      employee(1, [], 'ACTIVE'),
      employee(2, [], 'ACTIVE'),
      employee(3, ['MAKER'], 'ACTIVE'),
      employee(4, [], 'LOCKED'),
    ]

    // Chạy
    const count = countPendingEmployees(employees)

    // Kiểm tra kết quả
    expect(count).toBe(2)
  })

  it('Danh sách rỗng (chưa tải xong) thì 0', () => {
    // Chuẩn bị, chạy, kiểm tra kết quả
    expect(countPendingEmployees([])).toBe(0)
  })
})