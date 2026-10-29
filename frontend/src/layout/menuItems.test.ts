// Test 8 (spec auth mục 8): menu đúng theo role; người giữ nhiều role thấy hợp các mục.
import { describe, expect, it } from 'vitest'
import type { AppRole } from '../pages/auth/currentUser.ts'
import { visiblePages } from './menuItems.tsx'

function labelsFor(roles: AppRole[]): string[] {
  return visiblePages(roles).map((page) => page.label)
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

  it('Người giữ cả MAKER và CHECKER thấy hợp các mục của hai role, không trùng', () => {
    // Chuẩn bị, chạy
    const labels = labelsFor(['MAKER', 'CHECKER'])

    // Kiểm tra kết quả
    expect(labels).toEqual([
      'Tổng quan', 'Khách hàng', 'Tài khoản', 'Tạo phiếu', 'Chờ duyệt', 'Tất cả phiếu', 'Import Excel', 'Xuất báo cáo',
    ])
  })
})