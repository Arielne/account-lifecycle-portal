// Chữ hiển thị riêng của các màn phiếu: dung lượng file, thời gian chờ duyệt, tên bước trong lịch sử xử lý.
import type { TicketAction } from './ticketApi.ts'

const MINUTES_PER_HOUR = 60
const MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

/**
 * Số phút đã chờ (backend tính, spec phiếu mục 3.2) đổi sang chữ: 0 → "Vừa gửi", 20 → "20 phút", 1200 → "20 giờ",
 * 4080 → "2 ngày 20 giờ". Chỉ đổi chữ; việc "quá 24 giờ" do backend quyết định, React không tự so.
 */
export function formatWaitingTime(minutes: number): string {
  if (minutes < 1) {
    return 'Vừa gửi'
  }
  if (minutes < MINUTES_PER_HOUR) {
    return `${minutes} phút`
  }
  if (minutes < MINUTES_PER_DAY) {
    return `${Math.floor(minutes / MINUTES_PER_HOUR)} giờ`
  }
  const days = Math.floor(minutes / MINUTES_PER_DAY)
  const hours = Math.floor((minutes % MINUTES_PER_DAY) / MINUTES_PER_HOUR)
  return hours === 0 ? `${days} ngày` : `${days} ngày ${hours} giờ`
}

/** Nhãn "Gửi lại lần N" với N là số lần gửi lại (D1); gửi lần đầu thì không có nhãn (null). */
export function resubmitLabel(submitCount: number): string | null {
  const resubmitTimes = submitCount - 1
  return resubmitTimes >= 1 ? `Gửi lại lần ${resubmitTimes}` : null
}

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