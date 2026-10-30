// Màn Loại tài khoản (CN-20, màn 3.2): tìm, xem, thêm, sửa, xóa. Chỉ Admin vào được (RequireRole ở router).
// Danh mục ít dòng nên tìm và phân trang ngay ở React. Backend vẫn tự kiểm quyền và kiểm dữ liệu (QT-07).
import { useState } from 'react'
import { App, Button, Card, Flex, Input, Popconfirm, Space, Table, Tooltip, Typography } from 'antd'
import type { TableColumnsType } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { showApiError } from '../../api/apiError.ts'
import CodeText from '../../components/CodeText.tsx'
import PageHeading from '../../components/PageHeading.tsx'
import AccountTypeFormModal from './AccountTypeFormModal.tsx'
import {
  ACCOUNT_TYPES_QUERY_KEY,
  deleteAccountType,
  fetchAccountTypes,
  type AccountType,
} from './accountTypeApi.ts'
import { formatDate, formatNumber } from './format.ts'

function AccountTypePage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const [isModalOpen, setModalOpen] = useState(false)
  const [editingAccountType, setEditingAccountType] = useState<AccountType | null>(null)

  const accountTypesQuery = useQuery({ queryKey: ACCOUNT_TYPES_QUERY_KEY, queryFn: fetchAccountTypes })
  const deleteMutation = useMutation({
    mutationFn: deleteAccountType,
    onSuccess: () => {
      message.success('Đã xóa loại tài khoản')
      queryClient.invalidateQueries({ queryKey: ACCOUNT_TYPES_QUERY_KEY })
    },
    onError: (error) => showApiError(error, message),
  })

  const accountTypes = accountTypesQuery.data ?? []
  const totalAccounts = accountTypes.reduce((sum, accountType) => sum + accountType.accountCount, 0)

  function openModal(accountType: AccountType | null) {
    setEditingAccountType(accountType)
    setModalOpen(true)
  }

  return (
    <Flex vertical gap={16}>
      <PageHeading
        breadcrumb={['Danh mục', 'Loại tài khoản']}
        title="Loại tài khoản"
        description="Dùng khi mở tài khoản, lọc danh sách và xuất báo cáo"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openModal(null)}>
            Thêm loại tài khoản
          </Button>
        }
      />
      <Card>
        <Flex wrap justify="space-between" align="center" gap={12} style={{ marginBottom: 16 }}>
          <Input.Search
            allowClear
            placeholder="Tìm theo mã hoặc tên"
            aria-label="Tìm loại tài khoản"
            style={{ width: 320, maxWidth: '100%' }}
            onChange={(event) => setKeyword(event.target.value)}
          />
          <Typography.Text type="secondary">
            {accountTypes.length} loại · {formatNumber(totalAccounts)} tài khoản đang dùng
          </Typography.Text>
        </Flex>
        <Table
          rowKey="id"
          size="middle"
          loading={accountTypesQuery.isPending}
          columns={buildColumns(openModal, (id) => deleteMutation.mutate(id))}
          dataSource={filterAccountTypes(accountTypes, keyword)}
          pagination={{ pageSize: 10, showTotal: (total, range) => `${range[0]}–${range[1]} trên ${total}` }}
        />
      </Card>
      {isModalOpen && <AccountTypeFormModal accountType={editingAccountType} onClose={() => setModalOpen(false)} />}
    </Flex>
  )
}

/** Lọc theo mã hoặc tên, không phân biệt hoa thường. */
function filterAccountTypes(accountTypes: AccountType[], keyword: string): AccountType[] {
  const normalizedKeyword = keyword.trim().toLowerCase()
  if (normalizedKeyword === '') {
    return accountTypes
  }
  return accountTypes.filter(
    (accountType) =>
      accountType.code.toLowerCase().includes(normalizedKeyword) ||
      accountType.name.toLowerCase().includes(normalizedKeyword),
  )
}

function buildColumns(
  onEdit: (accountType: AccountType) => void,
  onDelete: (id: number) => void,
): TableColumnsType<AccountType> {
  return [
    { title: 'Mã', dataIndex: 'code', render: (code: string) => <CodeText>{code}</CodeText> },
    { title: 'Tên loại tài khoản', dataIndex: 'name' },
    {
      title: 'Mô tả',
      dataIndex: 'description',
      render: (description: string | null) => <Typography.Text type="secondary">{description}</Typography.Text>,
    },
    {
      // Tiêu đề 2 dòng như bản thiết kế, để cột không quá rộng.
      title: (
        <>
          Chuyển ngủ đông sau
          <br />
          <Typography.Text type="secondary" style={{ fontWeight: 'normal' }}>
            (tháng không giao dịch)
          </Typography.Text>
        </>
      ),
      dataIndex: 'dormantAfterMonths',
      render: (months: number) => `${months} tháng`,
    },
    { title: 'Số TK đang dùng', dataIndex: 'accountCount', align: 'right', render: formatNumber },
    { title: 'Cập nhật', dataIndex: 'updatedAt', render: formatDate },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 128,
      render: (_value, accountType) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => onEdit(accountType)}>
            Sửa
          </Button>
          <DeleteAccountTypeButton accountType={accountType} onDelete={onDelete} />
        </Space>
      ),
    },
  ]
}

/** Còn tài khoản dùng loại này thì nút Xóa bị khóa, Tooltip ghi số tài khoản (QT-08); không thì hỏi xác nhận. */
function DeleteAccountTypeButton({ accountType, onDelete }: { accountType: AccountType; onDelete: (id: number) => void }) {
  if (accountType.accountCount > 0) {
    return (
      <Tooltip title={`Đang có ${formatNumber(accountType.accountCount)} tài khoản dùng loại này`}>
        <Button type="link" size="small" danger disabled>
          Xóa
        </Button>
      </Tooltip>
    )
  }
  return (
    <Popconfirm
      title="Xóa loại tài khoản?"
      description={`“${accountType.name}” sẽ bị xóa khỏi danh mục. Không thể hoàn tác.`}
      okText="Xóa"
      cancelText="Hủy"
      okButtonProps={{ danger: true }}
      onConfirm={() => onDelete(accountType.id)}
    >
      <Button type="link" size="small" danger>
        Xóa
      </Button>
    </Popconfirm>
  )
}

export default AccountTypePage