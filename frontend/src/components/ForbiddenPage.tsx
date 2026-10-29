// Trang 403 (màn 3.13): vào route không đủ role.
import { Button, Result } from 'antd'
import { useNavigate } from 'react-router'

function ForbiddenPage() {
  const navigate = useNavigate()
  return (
    <Result
      status="403"
      title="403"
      subTitle="Bạn không có quyền truy cập trang này."
      extra={
        <Button type="primary" onClick={() => navigate('/')}>
          Về Tổng quan
        </Button>
      }
    />
  )
}

export default ForbiddenPage