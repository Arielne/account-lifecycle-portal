// Bước 3 của màn Tạo phiếu (CN-11), dùng lại ở màn Sửa phiếu (CN-12): lý do (tối đa 500 ký tự) và giấy tờ đính kèm mới.
// Giấy tờ giữ trong Form, chưa tải lên
// cho tới khi gửi phiếu ở bước 4. Trình duyệt kiểm trước số file, cỡ file, đuôi file; backend kiểm lại cả loại thật
// của file bằng các byte đầu (QT-10).
import { Form, Input, Upload } from 'antd'
import type { FormRule, UploadFile } from 'antd'
import { InboxOutlined } from '@ant-design/icons'
import { REQUIRED_DOCUMENTS, type TicketType } from '../../components/ticketType.ts'

const MAX_FILES = 5
const MAX_FILE_BYTES = 5 * 1024 * 1024
const ALLOWED_EXTENSIONS = ['pdf', 'jpg', 'jpeg', 'png']

/** Lỗi của danh sách file mới; null là hợp lệ. keptCount: số giấy tờ cũ còn giữ (màn sửa phiếu, D3). */
function fileListProblem(fileList: UploadFile[], keptCount: number): string | null {
  const totalCount = keptCount + fileList.length
  if (totalCount === 0) {
    return 'Cần đính kèm ít nhất 1 giấy tờ'
  }
  if (totalCount > MAX_FILES) {
    return 'Tối đa 5 tệp'
  }
  for (const file of fileList) {
    const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
    if (!ALLOWED_EXTENSIONS.includes(extension)) {
      return `Tệp ${file.name} không phải PDF, JPG hoặc PNG`
    }
    if ((file.size ?? 0) > MAX_FILE_BYTES) {
      return `Tệp ${file.name} lớn hơn 5 MB`
    }
  }
  return null
}

/**
 * Rule dạng hàm: Form truyền getFieldValue, nên số giấy tờ cũ còn giữ tính ngay lúc kiểm từ ô "Bỏ" (removedAttachmentIds)
 * của màn sửa, không dùng con số đã tính từ lần vẽ màn trước. Màn tạo phiếu không có giấy tờ cũ.
 */
function filesRule(existingCount: number): FormRule {
  return ({ getFieldValue }) => ({
    validator: (_rule, fileList?: UploadFile[]) => {
      const removedIds: number[] = getFieldValue('removedAttachmentIds') ?? []
      const problem = fileListProblem(fileList ?? [], existingCount - removedIds.length)
      return problem === null ? Promise.resolve() : Promise.reject(new Error(problem))
    },
  })
}

interface ReasonStepProps {
  type: TicketType
  /** Màn sửa phiếu: số giấy tờ cũ đang dùng; giấy tờ còn giữ cộng file mới phải từ 1 đến 5 (D3). Màn tạo phiếu là 0. */
  existingCount?: number
  /** Số giấy tờ cũ còn giữ (sau khi trừ ô "Bỏ"), để giới hạn số file chọn thêm. */
  keptCount?: number
}

function ReasonStep({ type, existingCount = 0, keptCount = 0 }: ReasonStepProps) {
  return (
    <>
      <Form.Item name="reason" label="Lý do" rules={[
        { required: true, whitespace: true, message: 'Vui lòng nhập lý do' },
        { max: 500, message: 'Lý do tối đa 500 ký tự' },
      ]}>
        <Input.TextArea rows={4} maxLength={500} showCount />
      </Form.Item>
      <Form.Item
        name="files"
        label="Giấy tờ đính kèm"
        extra={`Cần có: ${REQUIRED_DOCUMENTS[type]}`}
        // Upload trả cả sự kiện; Form chỉ giữ danh sách file.
        valuePropName="fileList"
        getValueFromEvent={(event: { fileList: UploadFile[] }) => event.fileList}
        rules={[filesRule(existingCount)]}
      >
        {/* beforeUpload trả false: không tự tải lên, chỉ giữ file trong danh sách để gửi cùng phiếu. */}
        <Upload.Dragger
          multiple
          accept=".pdf,.jpg,.jpeg,.png"
          maxCount={MAX_FILES - keptCount}
          beforeUpload={() => false}
        >
          <p className="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p className="ant-upload-text">Kéo thả tệp vào đây hoặc bấm để chọn</p>
          <p className="ant-upload-hint">PDF, JPG, PNG · tối đa 5 MB mỗi tệp · tối đa 5 tệp</p>
        </Upload.Dragger>
      </Form.Item>
    </>
  )
}

export default ReasonStep