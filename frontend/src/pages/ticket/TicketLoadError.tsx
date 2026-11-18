// Trang báo lỗi khi không mở được một phiếu (màn 3.13), dùng cho Chi tiết phiếu và Sửa phiếu: phiếu không có (404, hoặc
// id trên URL không phải số) thì Result 404 kèm nút về danh sách, giống chi tiết tài khoản và khách hàng; lỗi khác
// (backend lỗi, mất mạng) thì báo không tải được, để người dùng không tưởng nhầm là phiếu không tồn tại.
import { Link } from 'react-router'
import { Button, Result } from 'antd'
import { readApiError } from '../../api/apiError.ts'

interface TicketLoadErrorProps {
  /** Lỗi của useQuery; null khi id trên URL không hợp lệ nên chưa gọi API. */
  error: unknown
}

function TicketLoadError({ error }: TicketLoadErrorProps) {
  const status = readApiError(error)?.status
  if (error === null || status === 404) {
    return (
      <Result
        status="404"
        title="Không tìm thấy phiếu"
        extra={
          <Link to="/phieu">
            <Button type="primary">Về danh sách phiếu</Button>
          </Link>
        }
      />
    )
  }
  return <Result status="error" title="Không tải được phiếu. Vui lòng thử lại sau." />
}

export default TicketLoadError