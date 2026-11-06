// Màn Tạo phiếu (CN-11, màn 3.9, spec phiếu mục 10.2): 4 bước Loại phiếu → Khách hàng & tài khoản → Lý do & giấy tờ
// → Xác nhận, một Form giữ giá trị qua các bước. Tham số URL hợp lệ thì điền sẵn: ?loai= bỏ bước 1, ?taiKhoan= số
// tài khoản (đi từ chi tiết tài khoản), ?khachHang= khách đã có (đi từ chi tiết khách hàng). Giá trị lạ thì bỏ qua.
// Chi nhánh của phiếu là chi nhánh của người tạo, backend tự lấy (QT-04); React chỉ hiện tên chi nhánh.
import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { App, Button, Card, Flex, Form, Result, Segmented, Steps } from 'antd'
import { useMutation, useQuery } from '@tanstack/react-query'
import { showApiError } from '../../api/apiError.ts'
import CodeText from '../../components/CodeText.tsx'
import PageHeading from '../../components/PageHeading.tsx'
import { isSingleTicketType, ticketTypeLabel, type TicketType } from '../../components/ticketType.ts'
import { useCurrentUser } from '../auth/useCurrentUser.ts'
import { ACCOUNT_TYPES_QUERY_KEY, fetchAccountTypes } from '../catalog/accountTypeApi.ts'
import { CUSTOMER_QUERY_KEY, fetchCustomer } from '../customer/customerApi.ts'
import AccountTargetFields from './AccountTargetFields.tsx'
import {
  fieldsOfStep,
  selectedFiles,
  toCreateData,
  type CreateTicketFormValues,
  type CustomerMode,
} from './createTicketForm.ts'
import ExistingCustomerFields from './ExistingCustomerFields.tsx'
import NewCustomerFields from './NewCustomerFields.tsx'
import ReasonStep from './ReasonStep.tsx'
import ReviewStep from './ReviewStep.tsx'
import {
  createTicket,
  type CustomerCheckResult,
  type TicketCreated,
  type TicketEligibility,
} from './ticketApi.ts'
import TicketTypeStep from './TicketTypeStep.tsx'

const STEP_TITLES = ['Loại phiếu', 'Khách hàng & tài khoản', 'Lý do & giấy tờ', 'Xác nhận']
const LAST_STEP = STEP_TITLES.length - 1

interface UrlPrefill {
  type?: TicketType
  accountNumber?: string
  customerId?: number
  firstStep: number
}

/** Đọc tham số URL một lần lúc mở màn; loại phiếu không hợp lệ thì bỏ qua mọi tham số. */
function readPrefill(searchParams: URLSearchParams): UrlPrefill {
  const typeParam = searchParams.get('loai')
  if (!isSingleTicketType(typeParam)) {
    return { firstStep: 0 }
  }
  const accountParam = searchParams.get('taiKhoan') ?? ''
  const customerParam = Number(searchParams.get('khachHang'))
  const hasCustomer = typeParam === 'MO_TAI_KHOAN' && Number.isInteger(customerParam) && customerParam > 0
  return {
    type: typeParam,
    accountNumber: /^\d{10}$/.test(accountParam) ? accountParam : undefined,
    customerId: hasCustomer ? customerParam : undefined,
    firstStep: 1,
  }
}

function CreateTicketPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [prefill] = useState(() => readPrefill(searchParams))
  const [form] = Form.useForm<CreateTicketFormValues>()
  const { message } = App.useApp()
  const navigate = useNavigate()
  const branchName = useCurrentUser().branch?.name ?? 'của bạn'
  const [step, setStep] = useState(prefill.firstStep)
  // Kết quả kiểm tài khoản (bước 2, 3 loại phiếu tài khoản) và kiểm CCCD (bước 2, khách mới); bước 4 dùng lại.
  const [eligibility, setEligibility] = useState<TicketEligibility | null>(null)
  const [customerCheck, setCustomerCheck] = useState<CustomerCheckResult | null>(null)
  const [created, setCreated] = useState<TicketCreated | null>(null)
  // preserve: đọc cả giá trị của ô đang không hiện (ô Loại phiếu ở bước 1 đã ẩn khi sang bước 2); thiếu tùy chọn này
  // thì useWatch trả undefined cho ô không có trên màn.
  const type: TicketType | undefined = Form.useWatch('type', { form, preserve: true })
  const customerMode: CustomerMode | undefined = Form.useWatch('customerMode', { form, preserve: true })
  const customerId: number | undefined = Form.useWatch('customerId', { form, preserve: true })

  const accountTypesQuery = useQuery({ queryKey: ACCOUNT_TYPES_QUERY_KEY, queryFn: fetchAccountTypes })
  const accountTypeOptions = (accountTypesQuery.data ?? []).map((accountType) => ({
    value: accountType.id,
    label: accountType.name,
  }))
  // Cùng khóa cache với thẻ khách ở bước 2, nên không gọi API lần nữa.
  const existingCustomerQuery = useQuery({
    queryKey: [CUSTOMER_QUERY_KEY, customerId],
    queryFn: () => fetchCustomer(customerId as number),
    enabled: customerId !== undefined,
  })

  const createMutation = useMutation({
    mutationFn: (values: CreateTicketFormValues) => createTicket(toCreateData(values), selectedFiles(values.files)),
    onSuccess: (ticket) => setCreated(ticket),
    onError: (error) => showApiError(error, message),
  })

  /** Kiểm các ô của bước đang mở; đúng thì sang bước sau, ở bước cuối thì gửi phiếu. */
  async function goNext() {
    try {
      await form.validateFields(fieldsOfStep(step, type, customerMode))
    } catch {
      return
    }
    if (step < LAST_STEP) {
      setStep(step + 1)
      return
    }
    createMutation.mutate(form.getFieldsValue(true))
  }

  /** "Tạo phiếu khác": về bước 1 với Form trống, bỏ tham số URL đã dùng. */
  function startOver() {
    form.resetFields()
    form.setFieldsValue({ type: undefined, customerMode: 'NEW', customerId: undefined, accountNumber: undefined })
    setStep(0)
    setEligibility(null)
    setCustomerCheck(null)
    setCreated(null)
    setSearchParams({}, { replace: true })
  }

  if (created !== null) {
    return (
      <Card>
        <Result
          status="success"
          title={<>Đã gửi phiếu <CodeText>{created.code}</CodeText></>}
          subTitle={`Phiếu ${type ? ticketTypeLabel(type, eligibility?.currentStatus) : ''} đang chờ kiểm soát viên ${branchName} duyệt. Bạn sẽ nhận mail khi có kết quả.`}
          extra={[
            <Button key="view" type="primary" onClick={() => navigate(`/phieu/${created.id}`)}>Xem phiếu</Button>,
            <Button key="again" onClick={startOver}>Tạo phiếu khác</Button>,
          ]}
        />
      </Card>
    )
  }

  const accountTypeName = accountTypeOptions.find((option) => option.value === form.getFieldValue('accountTypeId'))?.label
  return (
    <Flex vertical gap={16}>
      <PageHeading
        breadcrumb={['Phiếu yêu cầu', 'Tạo phiếu']}
        title="Tạo phiếu yêu cầu"
        description={`Phiếu sẽ chuyển tới kiểm soát viên ${branchName} để duyệt`}
      />
      <Card>
        <Steps current={step} items={STEP_TITLES.map((title) => ({ title }))} style={{ marginBottom: 24 }} />
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            type: prefill.type,
            customerMode: prefill.customerId ? 'EXISTING' : 'NEW',
            customerId: prefill.customerId,
            accountNumber: prefill.accountNumber,
            files: [],
            confirmed: false,
          }}
        >
          {step === 0 && <TicketTypeStep />}
          {step === 1 && type && (
            <SubjectStep
              type={type}
              customerMode={customerMode}
              branchName={branchName}
              accountTypeOptions={accountTypeOptions}
              eligibility={eligibility}
              onEligibilityChange={setEligibility}
              customerCheck={customerCheck}
              onCustomerCheckChange={setCustomerCheck}
            />
          )}
          {step === 2 && type && <ReasonStep type={type} />}
          {step === LAST_STEP && (
            <ReviewStep
              values={form.getFieldsValue(true)}
              eligibility={eligibility}
              customerCheck={customerCheck}
              existingCustomerName={existingCustomerQuery.data?.fullName ?? null}
              accountTypeName={accountTypeName ?? null}
              branchName={branchName}
            />
          )}
        </Form>
        <Flex justify="space-between" style={{ marginTop: 24 }}>
          <Button onClick={() => navigate('/')}>Hủy</Button>
          <Flex gap={8}>
            {step > 0 && <Button onClick={() => setStep(step - 1)}>Quay lại</Button>}
            <Button
              type="primary"
              disabled={isNextBlocked(step, type, customerMode, eligibility, customerCheck)}
              loading={createMutation.isPending}
              onClick={goNext}
            >
              {step === LAST_STEP ? 'Gửi phiếu' : 'Tiếp tục'}
            </Button>
          </Flex>
        </Flex>
      </Card>
    </Flex>
  )
}

/** Khóa nút Tiếp tục khi bước 2 đã biết là không đi tiếp được: tài khoản không hợp lệ, CCCD đã có hoặc đang chờ. */
function isNextBlocked(step: number, type: TicketType | undefined, mode: CustomerMode | undefined,
                       eligibility: TicketEligibility | null, customerCheck: CustomerCheckResult | null): boolean {
  if (step !== 1) {
    return false
  }
  if (type !== 'MO_TAI_KHOAN') {
    return eligibility !== null && !eligibility.allowed
  }
  if (mode === 'NEW') {
    return customerCheck?.state === 'EXISTS' || customerCheck?.state === 'PENDING'
  }
  return false
}

interface SubjectStepProps {
  type: TicketType
  customerMode: CustomerMode | undefined
  branchName: string
  accountTypeOptions: { value: number; label: string }[]
  eligibility: TicketEligibility | null
  onEligibilityChange: (eligibility: TicketEligibility | null) => void
  customerCheck: CustomerCheckResult | null
  onCustomerCheckChange: (result: CustomerCheckResult | null) => void
}

/** Bước 2: phiếu Mở tài khoản chọn khách mới hay khách đã có; 3 loại còn lại chọn tài khoản. */
function SubjectStep(props: SubjectStepProps) {
  if (props.type !== 'MO_TAI_KHOAN') {
    return (
      <AccountTargetFields
        type={props.type}
        eligibility={props.eligibility}
        onEligibilityChange={props.onEligibilityChange}
      />
    )
  }
  return (
    <>
      <Form.Item name="customerMode" label="Khách hàng">
        <Segmented
          options={[
            { value: 'NEW', label: 'Khách hàng mới' },
            { value: 'EXISTING', label: 'Khách hàng đã có' },
          ]}
        />
      </Form.Item>
      {props.customerMode === 'EXISTING' ? (
        <ExistingCustomerFields accountTypeOptions={props.accountTypeOptions} />
      ) : (
        <NewCustomerFields
          branchName={props.branchName}
          accountTypeOptions={props.accountTypeOptions}
          customerCheck={props.customerCheck}
          onCustomerCheckChange={props.onCustomerCheckChange}
        />
      )}
    </>
  )
}

export default CreateTicketPage