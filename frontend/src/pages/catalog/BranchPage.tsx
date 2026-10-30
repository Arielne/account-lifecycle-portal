// Màn Chi nhánh (CN-21, màn 3.3): tìm, xem, thêm, sửa, xóa. Chỉ Admin vào được (RequireRole ở router).
// Danh mục ít dòng nên tìm và phân trang ngay ở React. Xóa: hỏi xác nhận, chi nhánh còn được dùng thì
// backend trả 409 và màn hiện câu báo lỗi của backend (QT-08).
import { useState } from 'react'
import { App, Button, Card, Flex, Input, Space, Table, Typography } from 'antd'
import type { TableColumnsType } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { showApiError } from '../../api/apiError.ts'
import CodeText from '../../components/CodeText.tsx'
import PageHeading from '../../components/PageHeading.tsx'
import BranchFormModal from './BranchFormModal.tsx'
import { BRANCHES_QUERY_KEY, deleteBranch, fetchBranches, type Branch } from './branchApi.ts'
import { formatNumber, formatPhone } from './format.ts'

function BranchPage() {
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const [isModalOpen, setModalOpen] = useState(false)
  const [editingBranch, setEditingBranch] = useState<Branch | null>(null)

  const branchesQuery = useQuery({ queryKey: BRANCHES_QUERY_KEY, queryFn: fetchBranches })
  const deleteMutation = useMutation({
    mutationFn: deleteBranch,
    onSuccess: () => {
      message.success('Đã xóa chi nhánh')
      queryClient.invalidateQueries({ queryKey: BRANCHES_QUERY_KEY })
    },
    onError: (error) => showApiError(error, message),
  })

  const branches = branchesQuery.data ?? []

  function openModal(branch: Branch | null) {
    setEditingBranch(branch)
    setModalOpen(true)
  }

  function confirmDelete(branch: Branch) {
    modal.confirm({
      title: 'Xóa chi nhánh?',
      content: `“${branch.name}” sẽ bị xóa khỏi danh mục. Không thể hoàn tác.`,
      okText: 'Xóa',
      okButtonProps: { danger: true },
      cancelText: 'Hủy',
      onOk: () => deleteMutation.mutate(branch.id),
    })
  }

  return (
    <Flex vertical gap={16}>
      <PageHeading
        breadcrumb={['Danh mục', 'Chi nhánh']}
        title="Chi nhánh"
        description="Nơi mở tài khoản và nơi nhân viên làm việc"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openModal(null)}>
            Thêm chi nhánh
          </Button>
        }
      />
      <Card>
        <Flex wrap justify="space-between" align="center" gap={12} style={{ marginBottom: 16 }}>
          <Input.Search
            allowClear
            placeholder="Tìm theo mã, tên hoặc địa chỉ"
            aria-label="Tìm chi nhánh"
            style={{ width: 320, maxWidth: '100%' }}
            onChange={(event) => setKeyword(event.target.value)}
          />
          <Typography.Text type="secondary">{branches.length} chi nhánh</Typography.Text>
        </Flex>
        <Table
          rowKey="id"
          size="middle"
          loading={branchesQuery.isPending}
          columns={buildColumns(openModal, confirmDelete)}
          dataSource={filterBranches(branches, keyword)}
          pagination={{ pageSize: 10, showTotal: (total, range) => `${range[0]}–${range[1]} trên ${total}` }}
        />
      </Card>
      {isModalOpen && <BranchFormModal branch={editingBranch} onClose={() => setModalOpen(false)} />}
    </Flex>
  )
}

/** Lọc theo mã, tên hoặc địa chỉ, không phân biệt hoa thường. */
function filterBranches(branches: Branch[], keyword: string): Branch[] {
  const normalizedKeyword = keyword.trim().toLowerCase()
  if (normalizedKeyword === '') {
    return branches
  }
  return branches.filter(
    (branch) =>
      branch.code.toLowerCase().includes(normalizedKeyword) ||
      branch.name.toLowerCase().includes(normalizedKeyword) ||
      branch.address.toLowerCase().includes(normalizedKeyword),
  )
}

function buildColumns(onEdit: (branch: Branch) => void, onDelete: (branch: Branch) => void): TableColumnsType<Branch> {
  return [
    { title: 'Mã', dataIndex: 'code', render: (code: string) => <CodeText>{code}</CodeText> },
    { title: 'Tên chi nhánh', dataIndex: 'name' },
    {
      title: 'Địa chỉ',
      dataIndex: 'address',
      render: (address: string) => <Typography.Text type="secondary">{address}</Typography.Text>,
    },
    { title: 'Điện thoại', dataIndex: 'phone', render: (phone: string) => <CodeText>{formatPhone(phone)}</CodeText> },
    { title: 'Nhân viên', dataIndex: 'employeeCount', align: 'right', render: formatNumber },
    {
      title: 'Tài khoản',
      dataIndex: 'accountCount',
      align: 'right',
      // Chi nhánh không mở tài khoản (ví dụ Hội sở) hiện "—" như bản thiết kế.
      render: (count: number) => (count === 0 ? <Typography.Text type="secondary">—</Typography.Text> : formatNumber(count)),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 116,
      render: (_value, branch) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => onEdit(branch)}>
            Sửa
          </Button>
          <Button type="link" size="small" danger onClick={() => onDelete(branch)}>
            Xóa
          </Button>
        </Space>
      ),
    },
  ]
}

export default BranchPage