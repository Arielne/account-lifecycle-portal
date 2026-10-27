// Trang tạm của module setup, không thuộc chức năng CN nào.
// Chỉ để kiểm tra React, Ant Design (theme, tiếng Việt, icon), React Router và SCSS chạy cùng nhau.
// Tuần 3 thay bằng layout thật (menu theo role, header).
import { App, Button, Card, Layout, Result } from 'antd'
import { SmileOutlined } from '@ant-design/icons'
import styles from './SetupCheckPage.module.scss'

function SetupCheckPage() {
  // message lấy từ App.useApp() để thông báo dùng đúng theme trong ConfigProvider.
  const { message } = App.useApp()

  function handleClick() {
    message.success('Ant Design đã nhận theme và chạy được.')
  }

  return (
    <Layout className={styles.page}>
      <Card className={styles.card}>
        <Result
          status="success"
          title="Account Lifecycle Portal"
          subTitle="Khung frontend đã chạy: React, Ant Design, React Router, SCSS."
          extra={
            <Button type="primary" icon={<SmileOutlined />} onClick={handleClick}>
              Bấm thử
            </Button>
          }
        />
      </Card>
    </Layout>
  )
}

export default SetupCheckPage