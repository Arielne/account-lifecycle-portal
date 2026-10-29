// Trang 404 (màn 3.13): route không tồn tại.
import { Button, Result } from 'antd'
import { useNavigate } from 'react-router'

function NotFoundPage() {
  const navigate = useNavigate()
  return (
    <Result
      status="404"
      title="404"
      subTitle="Không tìm thấy trang này."
      extra={
        <Button type="primary" onClick={() => navigate('/')}>
          Về Tổng quan
        </Button>
      }
    />
  )
}

export default NotFoundPage