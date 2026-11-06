// Test 8 (spec auth mục 8): menu đúng theo role; người giữ nhiều role thấy hợp các mục. Badge Chờ duyệt (spec phiếu D9).
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import type { AppRole } from '../pages/auth/currentUser.ts'
import { buildMenuItems, visiblePages } from './menuItems.tsx'

function labelsFor(roles: AppRole[]): string[] {
  return visiblePages(roles).map((page) => page.label)
}

/** Mục "Chờ duyệt" trong nhóm "Phiếu yêu cầu" của menu đã dựng. */
function pendingReviewItem(items: ReturnType<typeof buildMenuItems>): { label?: ReactNode } | undefined {
  for (const item of items) {
    if (item && 'children' in item && item.children) {
      for (const child of item.children) {
        if (child && child.key === '/phieu/cho-duyet' && 'label' in child) {
          return child
        }
      }
    }
  }
  return undefined
}

describe('Menu theo role', () => {
  it('Giao dịch viên thấy các mục của MAKER', () => {
    // Chuẩn bị, chạy
    const labels = labelsFor(['MAKER'])

    // Kiểm tra kết quả
    expect(labels).toEqual([
      'Tổng quan', 'Khách hàng', 'Tài khoản', 'Tạo phiếu', 'Tất cả phiếu', 'Import Excel', 'Xuất báo cáo',
    ])
  })

  it('Kiểm soát viên thấy các mục của CHECKER', () => {
    // Chuẩn bị, chạy
    const labels = labelsFor(['CHECKER'])

    // Kiểm tra kết quả
    expect(labels).toEqual(['Tổng quan', 'Khách hàng', 'Tài khoản', 'Chờ duyệt', 'Tất cả phiếu', 'Xuất báo cáo'])
  })

  it('Admin thấy danh mục, nhân viên, audit log và không thấy mục nghiệp vụ', () => {
    // Chuẩn bị, chạy
    const labels = labelsFor(['ADMIN'])

    // Kiểm tra kết quả
    expect(labels).toEqual(['Tổng quan', 'Loại tài khoản', 'Chi nhánh', 'Nhân viên', 'Audit log'])
  })

  it('D9: mục Chờ duyệt có badge khi còn phiếu chờ; không còn phiếu thì chỉ là chữ', () => {
    // Chuẩn bị, chạy
    const withPending = pendingReviewItem(buildMenuItems(['CHECKER'], 3))
    const withoutPending = pendingReviewItem(buildMenuItems(['CHECKER'], 0))

    // Kiểm tra kết quả
    expect(typeof withPending?.label).not.toBe('string')
    expect(withoutPending?.label).toBe('Chờ duyệt')
  })

  it('Người giữ cả MAKER và CHECKER thấy hợp các mục của hai role, không trùng', () => {
    // Chuẩn bị, chạy
    const labels = labelsFor(['MAKER', 'CHECKER'])

    // Kiểm tra kết quả
    expect(labels).toEqual([
      'Tổng quan', 'Khách hàng', 'Tài khoản', 'Tạo phiếu', 'Chờ duyệt', 'Tất cả phiếu', 'Import Excel', 'Xuất báo cáo',
    ])
  })
})