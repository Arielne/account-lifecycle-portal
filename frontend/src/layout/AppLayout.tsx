// Layout chung cho người đã có role (màn 2.1): Sider rộng 232 với menu theo role, Header, phần nội dung.
// Trang con hiện ở chỗ <Outlet /> (React Router).
import { Layout, Menu } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router'
import { useCurrentUser } from '../pages/auth/useCurrentUser.ts'
import AppHeader from './AppHeader.tsx'
import { buildMenuItems, MENU_GROUPS } from './menuItems.tsx'
import styles from './AppLayout.module.scss'

function AppLayout() {
  const currentUser = useCurrentUser()
  const location = useLocation()
  const navigate = useNavigate()

  return (
    <Layout className={styles.layout}>
      <Layout.Sider width={232}>
        <div className={styles.brand}>
          <div className={styles.bankName}>Ngân hàng Demo</div>
          <div className={styles.portalName}>Admin Portal</div>
        </div>
        <Menu
          theme="dark"
          mode="inline"
          items={buildMenuItems(currentUser.roles)}
          selectedKeys={[location.pathname]}
          defaultOpenKeys={MENU_GROUPS}
          onClick={(item) => navigate(item.key)}
        />
      </Layout.Sider>
      <Layout>
        <Layout.Header>
          <AppHeader />
        </Layout.Header>
        <Layout.Content className={styles.content}>
          <Outlet />
        </Layout.Content>
      </Layout>
    </Layout>
  )
}

export default AppLayout