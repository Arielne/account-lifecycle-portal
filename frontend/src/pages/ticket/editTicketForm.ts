// Giá trị Form của màn Sửa và gửi lại (CN-12) và cách đổi qua lại với dữ liệu API.
// dayjs: thư viện ngày của AntD; DatePicker chỉ nhận ngày kiểu Dayjs nên ngày dạng chữ từ API phải đổi sang Dayjs.
import dayjs from 'dayjs'
import type { UploadFile } from 'antd'
import { toNewCustomerData, type NewCustomerFormValues } from './createTicketForm.ts'
import type { NewCustomerData, TicketEditData, TicketResubmitData } from './ticketApi.ts'

export interface EditTicketFormValues {
  accountTypeId?: number
  newCustomer?: NewCustomerFormValues
  reason: string
  /** Id các giấy tờ cũ được đánh dấu bỏ (D3). */
  removedAttachmentIds: number[]
  /** Giấy tờ thêm mới. */
  files: UploadFile[]
  confirmed: boolean
}

/** Các bước của màn sửa: bỏ bước chọn loại phiếu của màn Tạo phiếu (D10). */
export const EDIT_STEP_TITLES = ['Khách hàng & tài khoản', 'Lý do & giấy tờ', 'Xác nhận']

/** Giá trị ban đầu của Form, điền từ edit-data. */
export function toInitialValues(data: TicketEditData): EditTicketFormValues {
  return {
    accountTypeId: data.accountTypeId ?? undefined,
    newCustomer: data.newCustomer ? toFormCustomer(data.newCustomer) : undefined,
    reason: data.reason,
    removedAttachmentIds: [],
    files: [],
    confirmed: false,
  }
}

function toFormCustomer(customer: NewCustomerData): NewCustomerFormValues {
  return {
    fullName: customer.fullName,
    dateOfBirth: dayjs(customer.dateOfBirth),
    gender: customer.gender ?? undefined,
    cccd: customer.cccd,
    idIssueDate: dayjs(customer.idIssueDate),
    idIssuePlace: customer.idIssuePlace,
    phone: customer.phone,
    email: customer.email,
    address: customer.address,
  }
}

/** Tên các ô phải đúng trước khi sang bước sau; tên dạng mảng là ô nằm trong newCustomer. */
export function fieldsOfEditStep(step: number, data: TicketEditData) {
  if (step === 1) {
    return ['reason', 'files']
  }
  if (step === 2) {
    return ['confirmed']
  }
  if (data.type !== 'MO_TAI_KHOAN') {
    return []
  }
  if (data.newCustomer === null) {
    return ['accountTypeId']
  }
  const customerFields = ['fullName', 'dateOfBirth', 'gender', 'cccd', 'idIssueDate', 'idIssuePlace', 'phone', 'email',
    'address']
  return [...customerFields.map((field) => ['newCustomer', field]), 'accountTypeId']
}

/** Giá trị Form → phần "data" gửi backend; chỉ phiếu Mở tài khoản mới có loại tài khoản và hồ sơ khách. */
export function toResubmitData(values: EditTicketFormValues, data: TicketEditData): TicketResubmitData {
  const isOpening = data.type === 'MO_TAI_KHOAN'
  return {
    accountTypeId: isOpening ? (values.accountTypeId ?? null) : null,
    newCustomer: isOpening && data.newCustomer && values.newCustomer ? toNewCustomerData(values.newCustomer) : null,
    reason: values.reason.trim(),
    removedAttachmentIds: values.removedAttachmentIds,
    confirmed: values.confirmed,
  }
}