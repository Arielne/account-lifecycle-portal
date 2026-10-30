// Đọc lỗi dạng chung của backend (spec admin mục 3) và hiện cho người dùng (module common).
// Lỗi có fieldErrors thì đưa vào đúng ô của Form; lỗi khác hiện message.error với câu của backend.
import axios from 'axios'
import type { FormInstance } from 'antd'
import type { MessageInstance } from 'antd/es/message/interface'
import { isUnauthorizedError } from './httpClient.ts'

/** Lỗi của một ô: tên trường trùng tên ô trong Form (cũng là tên trong JSON gửi lên). */
export interface ApiFieldError {
  field: string
  message: string
}

/** Thân lỗi mọi API trả về: mã HTTP, câu thông báo tiếng Việt, lỗi từng ô (rỗng nếu không có). */
export interface ApiErrorResponse {
  status: number
  message: string
  fieldErrors: ApiFieldError[]
}

const SYSTEM_ERROR_MESSAGE = 'Lỗi hệ thống. Vui lòng thử lại sau.'

/** Thân lỗi dạng chung; null khi lỗi không đến từ backend (mất mạng, backend tắt...). */
export function readApiError(error: unknown): ApiErrorResponse | null {
  if (axios.isAxiosError<ApiErrorResponse>(error) && error.response?.data?.message) {
    return error.response.data
  }
  return null
}

/**
 * Hiện lỗi của một lần gọi API ghi. Có form và lỗi có fieldErrors thì gắn lỗi vào từng ô;
 * còn lại hiện message.error. Lỗi 401 bỏ qua vì httpClient đã mở hộp thoại phiên hết hạn.
 */
export function showApiError(error: unknown, messageApi: MessageInstance, form?: FormInstance) {
  if (isUnauthorizedError(error)) {
    return
  }
  const apiError = readApiError(error)
  if (form && apiError && apiError.fieldErrors.length > 0) {
    form.setFields(toFormFieldErrors(apiError.fieldErrors))
    return
  }
  messageApi.error(apiError?.message ?? SYSTEM_ERROR_MESSAGE)
}

/** Gom lỗi theo ô (một ô có thể có nhiều lỗi) thành dạng form.setFields cần: { name, errors }. */
function toFormFieldErrors(fieldErrors: ApiFieldError[]) {
  const errorsByField = new Map<string, string[]>()
  for (const fieldError of fieldErrors) {
    const errors = errorsByField.get(fieldError.field) ?? []
    errors.push(fieldError.message)
    errorsByField.set(fieldError.field, errors)
  }
  return Array.from(errorsByField, ([name, errors]) => ({ name, errors }))
}