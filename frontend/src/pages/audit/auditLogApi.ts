// Gọi API Audit log (CN-23): chỉ đọc, chỉ Admin; backend vẫn tự kiểm quyền (QT-07).
import { httpClient } from '../../api/httpClient.ts'

/** Một trường đổi giá trị: nhãn và giá trị đều là chữ hiển thị. */
export interface AuditChange {
  label: string
  before: string
  after: string
}

/** Một dòng audit log. action, objectType là mã; màn hiện nhãn (auditLabels.ts). */
export interface AuditLogItem {
  id: number
  occurredAt: string
  /** null khi hệ thống làm (ví dụ job Ngủ đông). */
  employeeName: string | null
  action: string
  objectType: string
  objectId: string
  /** Họ tên khi đối tượng là nhân viên; loại khác là null. */
  objectName: string | null
  description: string
  ipAddress: string | null
  changes: AuditChange[]
}

export interface AuditLogPage {
  items: AuditLogItem[]
  total: number
}

/** Điều kiện lọc trên màn; để trống là không lọc. actor là id nhân viên hoặc 'SYSTEM' (Hệ thống). */
export interface AuditLogFilters {
  actor?: number | 'SYSTEM'
  /** Ngày dạng 2026-10-31. */
  from?: string
  to?: string
  action?: string
  objectType?: string
}

export const AUDIT_LOGS_QUERY_KEY = 'audit-logs'

/** Một trang 10 dòng; backend lọc và phân trang. Tham số undefined thì axios không gửi. */
export async function fetchAuditLogs(filters: AuditLogFilters, page: number): Promise<AuditLogPage> {
  const params = {
    employeeId: typeof filters.actor === 'number' ? filters.actor : undefined,
    system: filters.actor === 'SYSTEM' ? true : undefined,
    from: filters.from,
    to: filters.to,
    action: filters.action,
    objectType: filters.objectType,
    page,
  }
  const response = await httpClient.get<AuditLogPage>('/api/audit-logs', { params })
  return response.data
}