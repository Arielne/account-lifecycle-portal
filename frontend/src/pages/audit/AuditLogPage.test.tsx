// Test 14 (spec admin mục 8): màn Audit log. Chỉ dòng có từ 2 trường thay đổi mới mở rộng được; hiện nhãn, không hiện mã.
// vi.mock thay các hàm gọi API bằng hàm giả, phần còn lại của module giữ nguyên.
import { fireEvent, render, screen, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { describe, expect, it, vi } from 'vitest'
import AuditLogPage from './AuditLogPage.tsx'
import { fetchAuditLogs, type AuditLogItem } from './auditLogApi.ts'

vi.mock('./auditLogApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('./auditLogApi.ts')>()
  return { ...original, fetchAuditLogs: vi.fn() }
})

vi.mock('../employee/employeeApi.ts', async (importOriginal) => {
  const original = await importOriginal<typeof import('../employee/employeeApi.ts')>()
  return { ...original, fetchEmployees: vi.fn().mockResolvedValue([]) }
})

function auditItem(id: number, values: Partial<AuditLogItem>): AuditLogItem {
  return {
    id,
    occurredAt: '2026-10-18T09:58:40Z',
    employeeName: 'Lê Hoàng Phúc',
    action: 'UPDATE',
    objectType: 'BRANCH',
    objectId: 'CN-Q3',
    objectName: null,
    description: 'Mô tả',
    ipAddress: '10.20.1.8',
    changes: [],
    ...values,
  }
}

function renderPage() {
  vi.mocked(fetchAuditLogs).mockResolvedValue({
    total: 3,
    items: [
      auditItem(1, {
        description: 'Sửa 2 trường: Địa chỉ, Số điện thoại',
        changes: [
          { label: 'Địa chỉ', before: '88 Trần Quốc Thảo', after: '210 Võ Văn Tần' },
          { label: 'Số điện thoại', before: '028 3932 4051', after: '028 3930 7712' },
        ],
      }),
      auditItem(2, {
        objectType: 'ACCOUNT_TYPE',
        objectId: 'TKL',
        description: 'Chuyển ngủ đông sau: 18 tháng → 12 tháng',
        changes: [{ label: 'Chuyển ngủ đông sau', before: '18 tháng', after: '12 tháng' }],
      }),
      auditItem(3, {
        employeeName: null,
        action: 'LOCK',
        objectType: 'EMPLOYEE',
        objectId: 'khoa.tm@nhdemo.local',
        objectName: 'Trần Minh Khoa',
        description: 'Trần Minh Khoa',
        ipAddress: null,
      }),
    ],
  })
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AntApp>
        <AuditLogPage />
      </AntApp>
    </QueryClientProvider>,
  )
}

/** Dòng của bảng chính chứa đoạn chữ này. */
async function rowWith(text: string): Promise<HTMLElement> {
  return (await screen.findByText(text)).closest('tr') as HTMLElement
}

/**
 * Nút mở rộng của AntD là button có class ant-table-row-expand-icon. Dòng không mở rộng được chỉ có nút giữ chỗ
 * (thêm class ant-table-row-expand-icon-spaced, bị ẩn) để các cột thẳng hàng, nên loại nút đó ra.
 */
function expandButtonOf(row: HTMLElement): HTMLElement | null {
  return row.querySelector('button.ant-table-row-expand-icon:not(.ant-table-row-expand-icon-spaced)')
}

describe('Màn Audit log', () => {
  it('Chỉ dòng có từ 2 trường thay đổi mới mở rộng được; mở ra thấy bảng Trước, Sau', async () => {
    // Chuẩn bị
    renderPage()
    const twoFieldsRow = await rowWith('Sửa 2 trường: Địa chỉ, Số điện thoại')
    const oneFieldRow = await rowWith('Chuyển ngủ đông sau: 18 tháng → 12 tháng')

    // Chạy: mở rộng dòng sửa 2 trường
    expect(expandButtonOf(oneFieldRow)).toBeNull()
    fireEvent.click(expandButtonOf(twoFieldsRow) as HTMLElement)

    // Kiểm tra kết quả
    expect(await screen.findByText('Thông tin')).toBeInTheDocument()
    expect(screen.getByText('028 3930 7712')).toBeInTheDocument()
    expect(screen.getByText('Thực hiện tại màn Danh mục › Chi nhánh')).toBeInTheDocument()
  })

  it('Hành động và đối tượng hiện nhãn tiếng Việt, không hiện mã; người làm rỗng là Hệ thống', async () => {
    // Chuẩn bị, chạy
    renderPage()

    // Kiểm tra kết quả
    const lockRow = await rowWith('Khóa nhân viên')
    expect(within(lockRow).getByText('Nhân viên Trần Minh Khoa')).toBeInTheDocument()
    expect(within(lockRow).getByText('Hệ thống')).toBeInTheDocument()
    expect(screen.getAllByText('Sửa danh mục')).toHaveLength(2)
    expect(screen.getByText('Loại TK', { exact: false })).toBeInTheDocument()
    expect(screen.queryByText('UPDATE')).not.toBeInTheDocument()
    expect(screen.queryByText('LOCK')).not.toBeInTheDocument()
    expect(screen.queryByText('BRANCH', { exact: false })).not.toBeInTheDocument()
  })
})