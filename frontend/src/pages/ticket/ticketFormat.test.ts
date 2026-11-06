// Test 29 của spec phiếu: đổi số phút chờ sang chữ; nhãn "Gửi lại lần N" theo D1.
import { describe, expect, it } from 'vitest'
import { formatWaitingTime, resubmitLabel } from './ticketFormat.ts'

describe('formatWaitingTime', () => {
  it('Dưới 1 phút là "Vừa gửi"; dưới 1 giờ ghi phút; dưới 1 ngày ghi giờ; từ 1 ngày ghi ngày và giờ', () => {
    // Chuẩn bị, chạy, kiểm tra kết quả
    expect(formatWaitingTime(0)).toBe('Vừa gửi')
    expect(formatWaitingTime(20)).toBe('20 phút')
    expect(formatWaitingTime(1200)).toBe('20 giờ')
    expect(formatWaitingTime(4080)).toBe('2 ngày 20 giờ')
    expect(formatWaitingTime(2880)).toBe('2 ngày')
  })
})

describe('resubmitLabel', () => {
  it('D1: gửi lần đầu không có nhãn; gửi lần 2 là "Gửi lại lần 1"', () => {
    // Chuẩn bị, chạy, kiểm tra kết quả
    expect(resubmitLabel(1)).toBeNull()
    expect(resubmitLabel(2)).toBe('Gửi lại lần 1')
    expect(resubmitLabel(3)).toBe('Gửi lại lần 2')
  })
})