// Định dạng số, ngày, điện thoại trên 2 màn danh mục (CN-20, CN-21), theo bản thiết kế.

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