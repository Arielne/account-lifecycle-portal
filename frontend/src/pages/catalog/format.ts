// Định dạng số, ngày, điện thoại theo bản thiết kế. Viết cho 2 màn danh mục (CN-20, CN-21), nay các màn khác dùng chung.

/** 3204 → "3.204": dấu chấm ngăn hàng nghìn kiểu Việt Nam. */
export function formatNumber(value: number): string {
  return value.toLocaleString('vi-VN')
}

/** Chuỗi ngày giờ ISO backend trả về → "02/09/2026". */
export function formatDate(isoDateTime: string): string {
  return new Date(isoDateTime).toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })
}

/**
 * Ngày không kèm giờ backend trả về ("2024-01-10") → "10/01/2024"; null → "—".
 * Tách chuỗi thay vì dùng new Date(): new Date("2024-01-10") hiểu là 0 giờ UTC, máy ở múi giờ âm sẽ lùi một ngày.
 */
export function formatLocalDate(isoDate: string | null): string {
  if (isoDate === null) {
    return '—'
  }
  const [year, month, day] = isoDate.split('-')
  return `${day}/${month}/${year}`
}

/** Điện thoại theo nhóm số: 11 số nhóm 3-4-4 (028 3911 2200), 10 số nhóm 4-3-3 (0901 234 567). */
export function formatPhone(digits: string): string {
  if (digits.length === 11) {
    return `${digits.slice(0, 3)} ${digits.slice(3, 7)} ${digits.slice(7)}`
  }
  if (digits.length === 10) {
    return `${digits.slice(0, 4)} ${digits.slice(4, 7)} ${digits.slice(7)}`
  }
  return digits
}