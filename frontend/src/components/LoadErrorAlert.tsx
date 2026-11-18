// Báo lỗi khi một màn danh sách không tải được dữ liệu (màn 3.13, "Lỗi máy chủ / mất kết nối"), kèm nút Thử lại.
// Thiếu component này thì bảng chỉ hiện rỗng, người dùng tưởng là không có dữ liệu: ví dụ màn Chờ duyệt báo
// "Không còn phiếu nào chờ bạn duyệt" trong khi thật ra backend đang lỗi.
import { Alert, Button } from 'antd'

interface LoadErrorAlertProps {
  /** Gọi lại API; thường là refetch của useQuery. */
  onRetry: () => void
}

function LoadErrorAlert({ onRetry }: LoadErrorAlertProps) {
  return (
    <Alert
      type="error"
      showIcon
      title="Không tải được dữ liệu. Vui lòng thử lại sau ít phút."
      action={
        <Button size="small" onClick={onRetry}>
          Thử lại
        </Button>
      }
    />
  )
}

export default LoadErrorAlert