// Danh sách mục menu và route theo role (spec auth mục 6, route theo mục 3 của docs/01-chuc-nang.md).
// Một mảng duy nhất dùng chung cho menu (layout) và chặn route (router), để hai nơi không lệch nhau.
// Người giữ nhiều role thấy hợp các mục của các role đó.
import type { ReactNode } from 'react'
import type { MenuProps } from 'antd'
import {
  AppstoreOutlined,
  AuditOutlined,
  BarChartOutlined,
  CreditCardOutlined,
  DashboardOutlined,
  FileTextOutlined,
  IdcardOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import type { AppRole } from '../pages/auth/currentUser.ts'

export interface PageEntry {
  route: string
  label: string
  /** Tên nhóm menu (mục cha); không có thì là mục cấp 1. */
  group?: string
  icon?: ReactNode
  /** Role được thấy mục này và vào được route này. */
  roles: AppRole[]
  /** Tuần làm màn thật; trước đó route hiện trang "Màn này làm ở tuần N". */
  week: number
}

export const PAGES: PageEntry[] = [
  { route: '/', label: 'Tổng quan', icon: <DashboardOutlined />, roles: ['MAKER', 'CHECKER', 'ADMIN'], week: 5 },
  { route: '/khach-hang', label: 'Khách hàng', icon: <TeamOutlined />, roles: ['MAKER', 'CHECKER'], week: 5 },
  { route: '/tai-khoan', label: 'Tài khoản', icon: <CreditCardOutlined />, roles: ['MAKER', 'CHECKER'], week: 5 },
  { route: '/phieu/tao-moi', label: 'Tạo phiếu', group: 'Phiếu yêu cầu', roles: ['MAKER'], week: 6 },
  { route: '/phieu/cho-duyet', label: 'Chờ duyệt', group: 'Phiếu yêu cầu', roles: ['CHECKER'], week: 6 },
  { route: '/phieu', label: 'Tất cả phiếu', group: 'Phiếu yêu cầu', roles: ['MAKER', 'CHECKER'], week: 6 },
  { route: '/danh-muc/loai-tai-khoan', label: 'Loại tài khoản', group: 'Danh mục', roles: ['ADMIN'], week: 4 },
  { route: '/danh-muc/chi-nhanh', label: 'Chi nhánh', group: 'Danh mục', roles: ['ADMIN'], week: 4 },
  { route: '/nhan-vien', label: 'Nhân viên', icon: <IdcardOutlined />, roles: ['ADMIN'], week: 4 },
  { route: '/bao-cao/import', label: 'Import Excel', group: 'Báo cáo & Import', roles: ['MAKER'], week: 8 },
  { route: '/bao-cao/export', label: 'Xuất báo cáo', group: 'Báo cáo & Import', roles: ['MAKER', 'CHECKER'], week: 8 },
  { route: '/audit-log', label: 'Audit log', icon: <AuditOutlined />, roles: ['ADMIN'], week: 4 },
]

const GROUP_ICONS: Record<string, ReactNode> = {
  'Phiếu yêu cầu': <FileTextOutlined />,
  'Danh mục': <AppstoreOutlined />,
  'Báo cáo & Import': <BarChartOutlined />,
}

/** Tên các nhóm menu, dùng để mở sẵn mọi nhóm. */
export const MENU_GROUPS = Object.keys(GROUP_ICONS)

/** Người có ít nhất 1 role trong danh sách roles của mục thì được thấy mục đó. */
export function canAccess(page: PageEntry, roles: AppRole[]): boolean {
  return page.roles.some((role) => roles.includes(role))
}

/** Các trang người này được thấy, giữ đúng thứ tự trong PAGES. */
export function visiblePages(roles: AppRole[]): PageEntry[] {
  return PAGES.filter((page) => canAccess(page, roles))
}

type MenuItem = Required<MenuProps>['items'][number]

/** Dựng items cho Menu của AntD: mục cấp 1 giữ nguyên, mục có group được gom vào một mục cha. */
export function buildMenuItems(roles: AppRole[]): MenuItem[] {
  const menuItems: MenuItem[] = []
  // Mục con của từng nhóm; mảng này cũng chính là children của mục cha đã thêm vào menuItems.
  const groupChildren: Record<string, MenuItem[]> = {}
  for (const page of visiblePages(roles)) {
    const pageItem: MenuItem = { key: page.route, icon: page.icon, label: page.label }
    if (page.group === undefined) {
      menuItems.push(pageItem)
      continue
    }
    if (groupChildren[page.group] === undefined) {
      groupChildren[page.group] = []
      menuItems.push({
        key: page.group,
        icon: GROUP_ICONS[page.group],
        label: page.group,
        children: groupChildren[page.group],
      })
    }
    groupChildren[page.group].push(pageItem)
  }
  return menuItems
}