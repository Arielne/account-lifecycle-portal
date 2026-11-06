// Nhãn tiếng Việt cho mã của audit log (spec admin mục 4): database lưu mã, màn Audit log hiện nhãn.

export const OBJECT_TYPE_LABELS: Record<string, string> = {
  ACCOUNT_TYPE: 'Loại TK',
  BRANCH: 'Chi nhánh',
  EMPLOYEE: 'Nhân viên',
  TICKET: 'Phiếu',
}

/** Màn nơi thao tác được làm, ghi dưới bảng Trước, Sau khi mở rộng dòng. */
export const SCREEN_LABELS: Record<string, string> = {
  ACCOUNT_TYPE: 'Danh mục › Loại tài khoản',
  BRANCH: 'Danh mục › Chi nhánh',
  EMPLOYEE: 'Nhân viên',
  TICKET: 'Phiếu yêu cầu',
}

/** Hành động trên phiếu (spec phiếu mục 12); các hành động sau tạo phiếu có ở bước duyệt, sửa, hủy. */
const TICKET_ACTION_LABELS: Record<string, string> = {
  CREATE: 'Tạo phiếu',
  RESUBMIT: 'Gửi lại phiếu',
  CANCEL: 'Hủy phiếu',
  APPROVE: 'Duyệt phiếu',
  REJECT: 'Từ chối phiếu',
  COMPLETE: 'Hoàn tất phiếu',
}

const CATALOG_ACTION_LABELS: Record<string, string> = {
  CREATE: 'Thêm danh mục',
  UPDATE: 'Sửa danh mục',
  DELETE: 'Xóa danh mục',
}

const EMPLOYEE_ACTION_LABELS: Record<string, string> = {
  CREATE: 'Thêm nhân viên',
  UPDATE: 'Sửa nhân viên',
  ASSIGN_ROLE: 'Gán role',
  LOCK: 'Khóa nhân viên',
  UNLOCK: 'Mở khóa nhân viên',
  SELF_REGISTER: 'Tự đăng ký',
}

const ACTION_LABELS_BY_OBJECT: Record<string, Record<string, string>> = {
  EMPLOYEE: EMPLOYEE_ACTION_LABELS,
  TICKET: TICKET_ACTION_LABELS,
}

/** Nhãn của hành động phụ thuộc loại đối tượng: CREATE trên chi nhánh là "Thêm danh mục", trên phiếu là "Tạo phiếu". */
export function actionLabel(objectType: string, action: string): string {
  const labels = ACTION_LABELS_BY_OBJECT[objectType] ?? CATALOG_ACTION_LABELS
  return labels[action] ?? action
}

/** Lựa chọn của ô lọc Hành động: lọc theo mã, kết hợp với ô Đối tượng để ra đúng hành động. */
export const ACTION_FILTER_OPTIONS = [
  { value: 'CREATE', label: 'Thêm' },
  { value: 'UPDATE', label: 'Sửa' },
  { value: 'DELETE', label: 'Xóa' },
  { value: 'ASSIGN_ROLE', label: 'Gán role' },
  { value: 'LOCK', label: 'Khóa' },
  { value: 'UNLOCK', label: 'Mở khóa' },
  { value: 'SELF_REGISTER', label: 'Tự đăng ký' },
]

export const OBJECT_TYPE_FILTER_OPTIONS = Object.keys(OBJECT_TYPE_LABELS).map((objectType) => ({
  value: objectType,
  label: OBJECT_TYPE_LABELS[objectType],
}))