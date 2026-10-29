// Header của layout chung (màn 2.1, CN-02): họ tên, chi nhánh, một Tag cho mỗi role, nút Đăng xuất.
// Người chưa gán chi nhánh (Admin) thì không hiện phần chi nhánh.
import { App, Button, Divider, Flex, Tag, Typography } from 'antd'
import { EditOutlined, KeyOutlined, LogoutOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import type { ReactNode } from 'react'
import { ROLE_LABELS, type AppRole } from '../pages/auth/currentUser.ts'
import { logout } from '../pages/auth/logout.ts'
import { useCurrentUser } from '../pages/auth/useCurrentUser.ts'

// Màu có sẵn của AntD gần nhất với màu role trong Design System (không viết CSS đè class của AntD).
const ROLE_TAG_COLORS: Record<AppRole, string> = {
  MAKER: 'cyan',
  CHECKER: 'geekblue',
  ADMIN: 'volcano',
}

const ROLE_TAG_ICONS: Record<AppRole, ReactNode> = {
  MAKER: <EditOutlined />,
  CHECKER: <SafetyCertificateOutlined />,
  ADMIN: <KeyOutlined />,
}

function AppHeader() {
  const currentUser = useCurrentUser()
  const { message } = App.useApp()

  async function handleLogout() {
    try {
      await logout()
    } catch {
      message.error('Không đăng xuất được. Vui lòng thử lại.')
    }
  }

  return (
    <Flex justify="flex-end" align="center" gap="small">
      <Typography.Text strong>{currentUser.fullName}</Typography.Text>
      {currentUser.branch !== null && <Typography.Text type="secondary">{currentUser.branch.name}</Typography.Text>}
      {currentUser.roles.map((role) => (
        <Tag key={role} color={ROLE_TAG_COLORS[role]} icon={ROLE_TAG_ICONS[role]}>
          {ROLE_LABELS[role]}
        </Tag>
      ))}
      <Divider orientation="vertical" />
      <Button icon={<LogoutOutlined />} onClick={handleLogout}>
        Đăng xuất
      </Button>
    </Flex>
  )
}

export default AppHeader