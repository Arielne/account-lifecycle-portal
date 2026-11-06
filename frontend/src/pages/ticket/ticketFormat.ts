// Chữ hiển thị riêng của các màn phiếu: dung lượng file, tên bước trong lịch sử xử lý.
import type { TicketAction } from './ticketApi.ts'

/** 420000 → "410 KB"; từ 1 MB trở lên → "1,2 MB" (dấu phẩy thập phân kiểu Việt Nam). */
export function formatFileSize(sizeBytes: number): string {
  const kilobytes = sizeBytes / 1024
  if (kilobytes < 1024) {
    return `${Math.max(1, Math.round(kilobytes))} KB`
  }
  const megabytes = kilobytes / 1024
  return `${megabytes.toLocaleString('vi-VN', { maximumFractionDigits: 1 })} MB`
}

/** Việc đã làm ở mỗi bước, ghép sau tên người làm: "Nguyễn Thị Lan gửi phiếu". */
export const HISTORY_ACTION_TEXTS: Record<TicketAction, string> = {
  SUBMITTED: 'gửi phiếu',
  RESUBMITTED: 'sửa và gửi lại phiếu',
  REJECTED: 'từ chối phiếu',
  APPROVED: 'duyệt phiếu',
  CANCELLED: 'hủy phiếu',
  COMPLETED: 'gửi mail kết quả, phiếu hoàn tất',
}