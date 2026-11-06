// Bước 3 của màn Tạo phiếu (CN-11): lý do (tối đa 500 ký tự) và giấy tờ đính kèm. Giấy tờ giữ trong Form, chưa tải lên
// cho tới khi gửi phiếu ở bước 4. Trình duyệt kiểm trước số file, cỡ file, đuôi file; backend kiểm lại cả loại thật
// của file bằng các byte đầu (QT-10).
import { Form, Input, Upload } from 'antd'
import type { FormRule, UploadFile } from 'antd'
import { InboxOutlined } from '@ant-design/icons'
import { REQUIRED_DOCUMENTS, type TicketType } from '../../components/ticketType.ts'

const MAX_FILES = 5
const MAX_FILE_BYTES = 5 * 1024 * 1024
const ALLOWED_EXTENSIONS = ['pdf', 'jpg', 'jpeg', 'png']

/** Lỗi của danh sách file; null là hợp lệ. */
function fileListProblem(fileList: UploadFile[]): string | null {
  if (fileList.length === 0) {
    return 'Cần đính kèm ít nhất 1 giấy tờ'
  }
  if (fileList.length > MAX_FILES) {
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

const filesRule: FormRule = {
  validator: (_rule, fileList?: UploadFile[]) => {
    const problem = fileListProblem(fileList ?? [])
    return problem === null ? Promise.resolve() : Promise.reject(new Error(problem))
  },
}

function ReasonStep({ type }: { type: TicketType }) {
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
        rules={[filesRule]}
      >
        {/* beforeUpload trả false: không tự tải lên, chỉ giữ file trong danh sách để gửi cùng phiếu. */}
        <Upload.Dragger multiple accept=".pdf,.jpg,.jpeg,.png" maxCount={MAX_FILES} beforeUpload={() => false}>
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