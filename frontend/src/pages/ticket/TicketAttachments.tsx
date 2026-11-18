// Giấy tờ đính kèm của phiếu (CN-15, spec phiếu mục 10.5, QT-10): chỉ giấy tờ đang dùng, nút Xem và Tải, không có
// nút xóa. Hai nút tải file qua httpClient như mọi API khác, không phải link thẳng tới API: hết phiên thì hiện hộp
// thoại "Phiên đăng nhập đã hết hạn", giấy tờ không có thì báo lỗi ngay trong app; trang đang mở không bị thay bằng
// chữ JSON của backend.
// Blob: nội dung file nằm trong bộ nhớ của trình duyệt. URL.createObjectURL tạo một đường dẫn tạm (blob:...) trỏ tới
// Blob đó, để hiện trong tab mới hoặc lưu thành file.
import { useState } from 'react'
import { App, Button, Card, List, Typography } from 'antd'
import { DownloadOutlined, EyeOutlined, FileOutlined } from '@ant-design/icons'
import { showApiError } from '../../api/apiError.ts'
import { fetchAttachment, type TicketDetail } from './ticketApi.ts'
import { formatFileSize } from './ticketFormat.ts'

// Đường dẫn tạm giữ file trong bộ nhớ; xóa sau 1 phút, khi trình duyệt chắc chắn đã đọc xong file.
const TEMPORARY_URL_LIFETIME_MS = 60_000

type Attachment = TicketDetail['attachments'][number]

interface TicketAttachmentsProps {
  ticketId: number
  attachments: Attachment[]
}

function TicketAttachments({ ticketId, attachments }: TicketAttachmentsProps) {
  const { message } = App.useApp()
  // Nút đang tải file, ví dụ "view-3" hoặc "download-3"; nút đó hiện vòng quay.
  const [loadingKey, setLoadingKey] = useState<string | null>(null)

  /**
   * Xem: mở tab trống ngay lúc bấm, rồi hiện file trong tab đó. Phải mở trước khi chờ tải file: trình duyệt chỉ cho
   * mở cửa sổ mới ngay khi người dùng bấm, mở sau một lần chờ thì bị chặn như cửa sổ quảng cáo.
   */
  async function view(attachment: Attachment) {
    const newTab = window.open('', '_blank')
    if (newTab === null) {
      message.warning('Trình duyệt đang chặn cửa sổ mới. Hãy cho phép cửa sổ bật lên hoặc dùng nút Tải.')
      return
    }
    // Tab mới chỉ hiện file, không cần quyền điều khiển ngược lại trang này.
    newTab.opener = null
    setLoadingKey(`view-${attachment.id}`)
    try {
      const file = await fetchAttachment(ticketId, attachment.id)
      newTab.location.href = temporaryUrl(file)
    } catch (error) {
      newTab.close()
      showApiError(error, message)
    } finally {
      setLoadingKey(null)
    }
  }

  /** Tải: lấy file qua API rồi lưu về máy bằng tên gốc. */
  async function download(attachment: Attachment) {
    setLoadingKey(`download-${attachment.id}`)
    try {
      const file = await fetchAttachment(ticketId, attachment.id)
      saveFile(file, attachment.originalName)
    } catch (error) {
      showApiError(error, message)
    } finally {
      setLoadingKey(null)
    }
  }

  return (
    <Card title="Giấy tờ đính kèm" extra={<Typography.Text type="secondary">{attachments.length} tệp</Typography.Text>}>
      <List
        dataSource={attachments}
        locale={{ emptyText: 'Không có giấy tờ' }}
        renderItem={(attachment) => (
          <List.Item
            actions={[
              <Button
                key="view"
                type="link"
                icon={<EyeOutlined />}
                loading={loadingKey === `view-${attachment.id}`}
                onClick={() => void view(attachment)}
              >
                Xem
              </Button>,
              <Button
                key="download"
                type="link"
                icon={<DownloadOutlined />}
                loading={loadingKey === `download-${attachment.id}`}
                onClick={() => void download(attachment)}
              >
                Tải
              </Button>,
            ]}
          >
            <List.Item.Meta
              avatar={<FileOutlined />}
              title={attachment.originalName}
              description={formatFileSize(attachment.sizeBytes)}
            />
          </List.Item>
        )}
      />
    </Card>
  )
}

/** Đường dẫn tạm (blob:...) tới file trong bộ nhớ; tự xóa sau 1 phút để không giữ file mãi. */
function temporaryUrl(file: Blob): string {
  const url = URL.createObjectURL(file)
  window.setTimeout(() => URL.revokeObjectURL(url), TEMPORARY_URL_LIFETIME_MS)
  return url
}

/** Lưu file về máy: tạo một link tạm có thuộc tính download (tên file khi lưu), bấm vào link rồi gỡ đi. */
function saveFile(file: Blob, fileName: string) {
  const link = document.createElement('a')
  link.href = temporaryUrl(file)
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
}

export default TicketAttachments