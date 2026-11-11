// Tên bước và người làm của Vòng đời tài khoản (spec phiếu mục 10.6), kể cả mục mở của tài khoản mẫu (chốt 07/11/2026).
import { describe, expect, it } from 'vitest'
import type { AccountStatusHistoryItem } from './accountApi.ts'
import { lifecycleActorText, lifecycleStepName } from './accountLifecycle.ts'

function item(values: Partial<AccountStatusHistoryItem>): AccountStatusHistoryItem {
  return {
    id: 1,
    changedAt: '2026-11-05T02:12:00Z',
    fromStatus: null,
    toStatus: 'ACTIVE',
    actorName: 'Trần Minh Khoa',
    ticketId: 7,
    ticketCode: 'PYC-2026-0007',
    ticketType: null,
    ticketFromStatus: null,
    reason: null,
    dormantAfterMonths: 12,
    ...values,
  }
}

describe('lifecycleStepName', () => {
  it('Đặt tên bước theo cặp trạng thái trước → sau', () => {
    // Chuẩn bị, chạy, kiểm tra kết quả
    expect(lifecycleStepName(item({ fromStatus: null, toStatus: 'PENDING_OPEN' }))).toBe('Tạo phiếu mở tài khoản')
    expect(lifecycleStepName(item({ fromStatus: 'PENDING_OPEN', toStatus: 'ACTIVE' }))).toBe('Mở tài khoản')
    expect(lifecycleStepName(item({ fromStatus: 'PENDING_OPEN', toStatus: 'CLOSED' }))).toBe('Hủy phiếu mở tài khoản')
    expect(lifecycleStepName(item({ fromStatus: null, toStatus: 'ACTIVE' }))).toBe('Mở tài khoản theo lô')
    expect(lifecycleStepName(item({ fromStatus: null, toStatus: 'ACTIVE', ticketId: null }))).toBe('Mở tài khoản')
    expect(lifecycleStepName(item({ fromStatus: 'ACTIVE', toStatus: 'BLOCKED' }))).toBe('Phong tỏa')
    expect(lifecycleStepName(item({ fromStatus: 'BLOCKED', toStatus: 'ACTIVE' }))).toBe('Gỡ phong tỏa')
    expect(lifecycleStepName(item({ fromStatus: 'DORMANT', toStatus: 'ACTIVE' }))).toBe('Kích hoạt lại')
    expect(lifecycleStepName(item({ fromStatus: 'DORMANT', toStatus: 'CLOSED' }))).toBe('Đóng tài khoản')
    expect(lifecycleStepName(item({ fromStatus: 'ACTIVE', toStatus: 'DORMANT' }))).toBe('Tự chuyển trạng thái')
  })
})

describe('lifecycleActorText', () => {
  it('Người tạo phiếu ghi "tạo", người hủy ghi "hủy", người duyệt ghi "duyệt"; không có người làm là Hệ thống', () => {
    // Chuẩn bị, chạy, kiểm tra kết quả
    expect(lifecycleActorText(item({ fromStatus: null, toStatus: 'PENDING_OPEN', actorName: 'Nguyễn Thị Lan' })))
      .toBe('Nguyễn Thị Lan tạo')
    expect(lifecycleActorText(item({ fromStatus: 'PENDING_OPEN', toStatus: 'CLOSED', actorName: 'Nguyễn Thị Lan' })))
      .toBe('Nguyễn Thị Lan hủy')
    expect(lifecycleActorText(item({ fromStatus: 'ACTIVE', toStatus: 'BLOCKED' }))).toBe('Trần Minh Khoa duyệt')
    expect(lifecycleActorText(item({ actorName: null }))).toBe('Hệ thống')
  })
})