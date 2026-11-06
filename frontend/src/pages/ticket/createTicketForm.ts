// Giá trị Form của màn Tạo phiếu (CN-11) và cách đổi sang dữ liệu gửi API. Một Form giữ giá trị qua cả 4 bước.
// Dayjs là kiểu ngày của DatePicker (thư viện ngày sẵn có của AntD); chỉ import kiểu, không thêm thư viện chạy.
import type { Dayjs } from 'dayjs'
import type { UploadFile } from 'antd'
import type { TicketType } from '../../components/ticketType.ts'
import type { TicketCreateData } from './ticketApi.ts'

/** Câu báo khi số CCCD thuộc hồ sơ chưa từng mở được tài khoản (D6): hiện ở bước 2 và nhắc lại ở bước Xác nhận. */
export const REUSABLE_PROFILE_NOTE =
  'Số CCCD này có hồ sơ cũ chưa từng mở được tài khoản. Gửi phiếu sẽ thay hồ sơ đó bằng thông tin bạn nhập.'

/** Khách hàng mới hay khách đã có hồ sơ, ở bước 2 của phiếu Mở tài khoản. */
export type CustomerMode = 'NEW' | 'EXISTING'

export interface NewCustomerFormValues {
  fullName: string
  dateOfBirth: Dayjs
  gender?: string
  cccd: string
  idIssueDate: Dayjs
  idIssuePlace: string
  phone: string
  email: string
  address: string
}

export interface CreateTicketFormValues {
  type: TicketType
  customerMode: CustomerMode
  newCustomer?: NewCustomerFormValues
  /** Ô tìm khách đã có theo CCCD; chỉ để tìm, không gửi lên. */
  existingCccd?: string
  customerId?: number
  accountTypeId?: number
  accountNumber?: string
  reason: string
  files: UploadFile[]
  confirmed: boolean
}

/** Tên các ô phải đúng trước khi sang bước sau; tên dạng mảng là ô nằm trong newCustomer. */
export function fieldsOfStep(step: number, type: TicketType | undefined, mode: CustomerMode | undefined) {
  if (step === 0) {
    return ['type']
  }
  if (step === 2) {
    return ['reason', 'files']
  }
  if (step === 3) {
    return ['confirmed']
  }
  if (type !== 'MO_TAI_KHOAN') {
    return ['accountNumber']
  }
  if (mode === 'EXISTING') {
    return ['customerId', 'accountTypeId']
  }
  const customerFields = ['fullName', 'dateOfBirth', 'gender', 'cccd', 'idIssueDate', 'idIssuePlace', 'phone', 'email',
    'address']
  return [...customerFields.map((field) => ['newCustomer', field]), 'accountTypeId']
}

/** Giá trị Form → phần "data" gửi backend; chỉ gửi đúng các trường của loại phiếu đang tạo. */
export function toCreateData(values: CreateTicketFormValues): TicketCreateData {
  const isOpening = values.type === 'MO_TAI_KHOAN'
  const isNewCustomer = isOpening && values.customerMode === 'NEW' && values.newCustomer !== undefined
  return {
    type: values.type,
    customerId: isOpening && !isNewCustomer ? (values.customerId ?? null) : null,
    newCustomer: isNewCustomer && values.newCustomer ? toNewCustomerData(values.newCustomer) : null,
    accountTypeId: isOpening ? (values.accountTypeId ?? null) : null,
    accountNumber: isOpening ? null : (values.accountNumber ?? null),
    reason: values.reason.trim(),
    confirmed: values.confirmed,
  }
}

function toNewCustomerData(customer: NewCustomerFormValues) {
  return {
    fullName: customer.fullName.trim(),
    dateOfBirth: customer.dateOfBirth.format('YYYY-MM-DD'),
    gender: customer.gender ?? null,
    cccd: customer.cccd,
    idIssueDate: customer.idIssueDate.format('YYYY-MM-DD'),
    idIssuePlace: customer.idIssuePlace.trim(),
    phone: customer.phone,
    email: customer.email.trim(),
    address: customer.address.trim(),
  }
}

/** File người dùng đã chọn (Upload giữ File gốc ở originFileObj vì không tự tải lên). */
export function selectedFiles(fileList: UploadFile[]): File[] {
  const files: File[] = []
  for (const uploadFile of fileList) {
    if (uploadFile.originFileObj) {
      files.push(uploadFile.originFileObj)
    }
  }
  return files
}