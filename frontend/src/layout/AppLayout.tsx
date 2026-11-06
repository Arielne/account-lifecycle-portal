// Layout chung cho người đã có role (màn 2.1): Sider rộng 232 với menu theo role, Header, phần nội dung.
// Trang con hiện ở chỗ <Outlet /> (React Router).
import { Suspense } from 'react'
import { Flex, Layout, Menu, Spin } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { useCurrentUser } from '../pages/auth/useCurrentUser.ts'
import { fetchPendingReviewSummary, PENDING_REVIEW_SUMMARY_QUERY_KEY } from '../pages/ticket/ticketApi.ts'
import AppHeader from './AppHeader.tsx'
import { buildMenuItems, MENU_GROUPS } from './menuItems.tsx'
import styles from './AppLayout.module.scss'

function AppLayout() {
  const currentUser = useCurrentUser()
  const location = useLocation()
  const navigate = useNavigate()
  // Badge số phiếu chờ duyệt (D9): chỉ hỏi khi có role CHECKER. Đường dẫn nằm trong queryKey nên mỗi lần đổi trang
  // là một lần gọi lại; duyệt, từ chối xong thì ReviewActions báo TanStack Query gọi lại.
  const pendingSummaryQuery = useQuery({
    queryKey: [PENDING_REVIEW_SUMMARY_QUERY_KEY, location.pathname],
    queryFn: fetchPendingReviewSummary,
    enabled: currentUser.roles.includes('CHECKER'),
  })

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
          items={buildMenuItems(currentUser.roles, pendingSummaryQuery.data?.pendingCount)}
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
          {/* Suspense: trong lúc tải file JS của màn (React.lazy trong router.tsx) thì hiện Spin ở vùng nội dung,
              menu và header vẫn giữ nguyên. */}
          <Suspense
            fallback={
              <Flex justify="center" style={{ padding: 48 }}>
                <Spin />
              </Flex>
            }
          >
            <Outlet />
          </Suspense>
        </Layout.Content>
      </Layout>
    </Layout>
  )
}

export default AppLayout