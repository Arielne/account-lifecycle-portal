// Theme của Ant Design, chép nguyên khối từ Design System (docs/design/Main.dc.html, mục 03).
// Màu, chữ, bo góc chỉ đổi ở đây; không ghi đè class CSS của AntD trong component.
import type { ThemeConfig } from 'antd'

export const theme: ThemeConfig = {
  token: {
    // Màu thương hiệu và màu chức năng
    colorPrimary: '#1554C0',
    colorInfo: '#1554C0',
    colorLink: '#1554C0',
    colorSuccess: '#1A7F37',
    colorSuccessBg: '#ECF8EF',
    colorSuccessBorder: '#A9DDB6',
    colorWarning: '#D48806',
    colorError: '#C8322A',
    // Màu trung tính
    colorTextBase: '#1B2333',
    colorTextSecondary: '#566173',
    colorTextTertiary: '#687083',
    colorBgLayout: '#F4F6F9',
    colorBorder: '#D5DAE1',
    colorBorderSecondary: '#E6E9EE',
    // Chữ
    fontFamily: "'Be Vietnam Pro', -apple-system, 'Segoe UI', sans-serif",
    fontFamilyCode: "'JetBrains Mono', Consolas, monospace",
    fontSize: 14,
    // Hình khối
    borderRadius: 6,
    borderRadiusLG: 8,
    borderRadiusSM: 4,
    controlHeight: 32,
  },
  components: {
    Layout: {
      siderBg: '#0C2340',
      headerBg: '#FFFFFF',
      headerHeight: 64,
      headerPadding: '0 24px',
      bodyBg: '#F4F6F9',
    },
    Menu: {
      darkItemBg: '#0C2340',
      darkSubMenuItemBg: '#091B33',
      darkItemSelectedBg: '#1554C0',
      darkItemColor: 'rgba(255,255,255,0.72)',
      darkItemHoverColor: '#FFFFFF',
    },
    Table: {
      headerBg: '#F7F8FA',
      headerColor: '#566173',
      rowHoverBg: '#F3F7FD',
      cellPaddingInlineMD: 16,
    },
    Descriptions: { labelBg: '#F7F8FA' },
    Card: { headerFontSize: 16 },
    Statistic: { contentFontSize: 28 },
  },
}