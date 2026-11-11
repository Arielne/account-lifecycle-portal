// Màn Sửa và gửi lại phiếu (CN-12, màn 3.9 ở chế độ sửa, spec phiếu mục 10.2, D10): dùng lại các bước của màn Tạo phiếu,
// bỏ bước chọn loại phiếu, điền sẵn từ edit-data, hiện lý do từ chối ở đầu. Không đổi loại phiếu, tài khoản, khách hàng.
// Chỉ người tạo mở được (edit-data trả 403 với người khác); phiếu không còn Bị từ chối (409) thì báo và cho về chi tiết.
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { Alert, App, Button, Card, Checkbox, Descriptions, Flex, Form, Result, Select, Spin, Steps, Typography } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { readApiError, showApiError } from '../../api/apiError.ts'
import AccountStatusTag from '../../components/AccountStatusTag.tsx'
import CodeText from '../../components/CodeText.tsx'
import ForbiddenPage from '../../components/ForbiddenPage.tsx'
import PageHeading from '../../components/PageHeading.tsx'
import { ticketTypeLabel } from '../../components/ticketType.ts'
import { useCurrentUser } from '../auth/useCurrentUser.ts'
import { ACCOUNT_TYPES_QUERY_KEY, fetchAccountTypes } from '../catalog/accountTypeApi.ts'
import ConfirmDocumentsField from './ConfirmDocumentsField.tsx'
import { selectedFiles } from './createTicketForm.ts'
import {
  EDIT_STEP_TITLES,
  fieldsOfEditStep,
  toInitialValues,
  toResubmitData,
  type EditTicketFormValues,
} from './editTicketForm.ts'
import NewCustomerFields from './NewCustomerFields.tsx'
import ReasonStep from './ReasonStep.tsx'
import {
  fetchEditData,
  PENDING_REVIEW_QUERY_KEY,
  resubmitTicket,
  TICKET_EDIT_QUERY_KEY,
  TICKET_QUERY_KEY,
  TICKETS_QUERY_KEY,
  type CustomerCheckResult,
  type TicketEditData,
} from './ticketApi.ts'
import { formatFileSize } from './ticketFormat.ts'

const LAST_STEP = EDIT_STEP_TITLES.length - 1

function EditTicketPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const editQuery = useQuery({
    queryKey: [TICKET_EDIT_QUERY_KEY, id],
    queryFn: () => fetchEditData(id),
    enabled: Number.isInteger(id),
    // Dữ liệu có hồ sơ khách đầy đủ (D2): không giữ lại trong bộ nhớ đệm sau khi rời màn.
    gcTime: 0,
  })

  const apiError = readApiError(editQuery.error)
  if (apiError?.status === 403) {
    return <ForbiddenPage />
  }
  if (apiError?.status === 409) {
    return (
      <Result
        status="warning"
        title={apiError.message}
        extra={<Button type="primary" onClick={() => navigate(`/phieu/${id}`)}>Về chi tiết phiếu</Button>}
      />
    )
  }
  if (!Number.isInteger(id) || editQuery.isError) {
    return <Alert type="error" showIcon title={apiError?.message ?? 'Không tìm thấy phiếu'} />
  }
  if (editQuery.isPending) {
    return (
      <Flex justify="center" style={{ padding: 48 }}>
        <Spin />
      </Flex>
    )
  }
  return <EditTicketForm data={editQuery.data} />
}

function EditTicketForm({ data }: { data: TicketEditData }) {
  const [form] = Form.useForm<EditTicketFormValues>()
  const { message } = App.useApp()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const branchName = useCurrentUser().branch?.name ?? 'của bạn'
  const [step, setStep] = useState(0)
  const [customerCheck, setCustomerCheck] = useState<CustomerCheckResult | null>(null)
  const removedIds: number[] = Form.useWatch('removedAttachmentIds', { form, preserve: true }) ?? []
  const keptCount = data.attachments.length - removedIds.length
  const accountTypesQuery = useQuery({ queryKey: ACCOUNT_TYPES_QUERY_KEY, queryFn: fetchAccountTypes })
  const accountTypeOptions = (accountTypesQuery.data ?? []).map((type) => ({ value: type.id, label: type.name }))

  const resubmitMutation = useMutation({
    mutationFn: (values: EditTicketFormValues) =>
      resubmitTicket(data.id, toResubmitData(values, data), selectedFiles(values.files)),
    onSuccess: () => {
      message.success(`Đã gửi lại phiếu ${data.code}`)
      void queryClient.invalidateQueries({ queryKey: [TICKET_QUERY_KEY, data.id] })
      void queryClient.invalidateQueries({ queryKey: [TICKETS_QUERY_KEY] })
      void queryClient.invalidateQueries({ queryKey: [PENDING_REVIEW_QUERY_KEY] })
      navigate(`/phieu/${data.id}`)
    },
    onError: (error) => showApiError(error, message, form),
  })

  /** Kiểm các ô của bước đang mở; đúng thì sang bước sau, ở bước cuối thì gửi lại. */
  async function goNext() {
    try {
      await form.validateFields(fieldsOfEditStep(step, data))
    } catch {
      return
    }
    if (step < LAST_STEP) {
      setStep(step + 1)
      return
    }
    resubmitMutation.mutate(form.getFieldsValue(true))
  }

  // Số CCCD mới đã có hồ sơ khác hoặc đang chờ ở phiếu khác thì không cho đi tiếp (D2, D5).
  const blocked = step === 0 && (customerCheck?.state === 'EXISTS' || customerCheck?.state === 'PENDING')
  return (
    <Flex vertical gap={16}>
      <PageHeading
        breadcrumb={['Phiếu yêu cầu', data.code, 'Sửa và gửi lại']}
        title={`Sửa phiếu ${data.code} · ${ticketTypeLabel(data.type, data.fromStatus)}`}
        description="Gửi lại thì phiếu về Chờ duyệt, thời gian chờ đếm lại từ đầu"
      />
      {data.rejectReason && <Alert type="error" showIcon title={`Lý do từ chối: ${data.rejectReason}`} />}
      <Card>
        <Steps current={step} items={EDIT_STEP_TITLES.map((title) => ({ title }))} style={{ marginBottom: 24 }} />
        <Form form={form} layout="vertical" initialValues={toInitialValues(data)}>
          {step === 0 && (
            <SubjectPanel
              data={data}
              branchName={branchName}
              accountTypeOptions={accountTypeOptions}
              customerCheck={customerCheck}
              onCustomerCheckChange={setCustomerCheck}
            />
          )}
          {step === 1 && (
            <>
              <KeptAttachments data={data} />
              <ReasonStep type={data.type} existingCount={data.attachments.length} keptCount={keptCount} />
            </>
          )}
          {step === LAST_STEP && <EditReview data={data} values={form.getFieldsValue(true)} />}
        </Form>
        <Flex justify="space-between" style={{ marginTop: 24 }}>
          <Button onClick={() => navigate(`/phieu/${data.id}`)}>Hủy</Button>
          <Flex gap={8}>
            {step > 0 && <Button onClick={() => setStep(step - 1)}>Quay lại</Button>}
            <Button type="primary" disabled={blocked} loading={resubmitMutation.isPending} onClick={goNext}>
              {step === LAST_STEP ? 'Gửi lại' : 'Tiếp tục'}
            </Button>
          </Flex>
        </Flex>
      </Card>
    </Flex>
  )
}

interface SubjectPanelProps {
  data: TicketEditData
  branchName: string
  accountTypeOptions: { value: number; label: string }[]
  customerCheck: CustomerCheckResult | null
  onCustomerCheckChange: (result: CustomerCheckResult | null) => void
}

/**
 * Bước 1: phiếu Mở tài khoản cho khách mới sửa được hồ sơ và loại tài khoản (D2); cho khách đã có chỉ sửa loại tài
 * khoản; 3 loại còn lại chỉ xem tài khoản, vì chỉ sửa được lý do và giấy tờ.
 */
function SubjectPanel({ data, branchName, accountTypeOptions, customerCheck, onCustomerCheckChange }:
  SubjectPanelProps) {
  if (data.type === 'MO_TAI_KHOAN' && data.newCustomer !== null) {
    return (
      <NewCustomerFields
        branchName={branchName}
        accountTypeOptions={accountTypeOptions}
        customerCheck={customerCheck}
        onCustomerCheckChange={onCustomerCheckChange}
        ownCustomerId={data.customerId ?? undefined}
      />
    )
  }
  if (data.type === 'MO_TAI_KHOAN') {
    return (
      <Form.Item name="accountTypeId" label="Loại tài khoản" rules={[{ required: true, message: 'Chọn loại tài khoản' }]}
        extra="Khách hàng đã có hồ sơ; muốn đổi khách thì hủy phiếu này và tạo phiếu mới.">
        <Select placeholder="Chọn loại tài khoản" options={accountTypeOptions} style={{ maxWidth: 420 }} />
      </Form.Item>
    )
  }
  return (
    <Flex vertical gap={8}>
      <Flex wrap align="center" gap={8}>
        <Typography.Text>Tài khoản <CodeText>{data.accountNumber}</CodeText></Typography.Text>
        {data.fromStatus && <AccountStatusTag status={data.fromStatus} />}→
        {data.toStatus && <AccountStatusTag status={data.toStatus} />}
      </Flex>
      <Typography.Text type="secondary">
        Phiếu này chỉ sửa được lý do và giấy tờ. Muốn đổi tài khoản thì hủy phiếu này và tạo phiếu mới.
      </Typography.Text>
    </Flex>
  )
}

/** Giấy tờ đang dùng: đánh dấu để bỏ (bỏ mềm, D3); giấy tờ đã bỏ vẫn được lưu để tra cứu. */
function KeptAttachments({ data }: { data: TicketEditData }) {
  if (data.attachments.length === 0) {
    return null
  }
  const options = data.attachments.map((attachment) => ({
    value: attachment.id,
    label: `Bỏ ${attachment.originalName} (${formatFileSize(attachment.sizeBytes)})`,
  }))
  return (
    <Form.Item name="removedAttachmentIds" label="Giấy tờ đang dùng">
      <Checkbox.Group options={options} style={{ display: 'flex', flexDirection: 'column', gap: 4 }} />
    </Form.Item>
  )
}

/** Bước cuối: xem lại và xác nhận đã đối chiếu giấy tờ gốc. */
function EditReview({ data, values }: { data: TicketEditData; values: EditTicketFormValues }) {
  const keptNames = data.attachments
    .filter((attachment) => !values.removedAttachmentIds.includes(attachment.id))
    .map((attachment) => attachment.originalName)
  const items = [
    { key: 'type', label: 'Loại phiếu', children: ticketTypeLabel(data.type, data.fromStatus) },
    { key: 'subject', label: 'Đối tượng', children: subjectText(data, values) },
    { key: 'reason', label: 'Lý do', children: values.reason },
    { key: 'kept', label: 'Giấy tờ giữ lại', children: keptNames.join(', ') || '—' },
    { key: 'added', label: 'Giấy tờ thêm', children: values.files.map((file) => file.name).join(', ') || '—' },
  ]
  return (
    <Flex vertical gap={16}>
      <Typography.Title level={5} style={{ margin: 0 }}>
        Kiểm tra lại trước khi gửi lại
      </Typography.Title>
      <Descriptions bordered size="small" column={1} items={items} />
      <ConfirmDocumentsField />
      <Typography.Text type="secondary">
        Sau khi gửi lại, phiếu không sửa được cho tới khi kiểm soát viên trả về.
      </Typography.Text>
    </Flex>
  )
}

function subjectText(data: TicketEditData, values: EditTicketFormValues): string {
  if (data.type !== 'MO_TAI_KHOAN') {
    return `TK ${data.accountNumber ?? ''}`
  }
  if (values.newCustomer) {
    return `${values.newCustomer.fullName} (khách hàng mới)`
  }
  return 'Khách hàng đã có hồ sơ'
}

export default EditTicketPage