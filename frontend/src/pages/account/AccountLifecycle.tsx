// Vòng đời tài khoản (CN-10, spec phiếu mục 10.6): Timeline các lần đổi trạng thái, mới nhất ở trên, màu chấm theo
// trạng thái đích. Dữ liệu lấy từ API của module tài khoản (bảng lịch sử trạng thái, V14).
import { Link } from 'react-router'
import { Empty, Flex, Spin, Timeline, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import AccountStatusTag from '../../components/AccountStatusTag.tsx'
import { ACCOUNT_STATUS_COLORS } from '../../components/accountStatus.ts'
import CodeText from '../../components/CodeText.tsx'
import { formatDateTime } from '../catalog/format.ts'
import { ACCOUNT_STATUS_HISTORY_QUERY_KEY, fetchStatusHistory } from './accountApi.ts'
import { lifecycleActorText, lifecycleNote, lifecycleStepName } from './accountLifecycle.ts'

function AccountLifecycle({ accountId }: { accountId: number }) {
  const historyQuery = useQuery({
    queryKey: [ACCOUNT_STATUS_HISTORY_QUERY_KEY, accountId],
    queryFn: () => fetchStatusHistory(accountId),
  })
  if (historyQuery.isPending) {
    return <Spin />
  }
  const items = historyQuery.data ?? []
  if (items.length === 0) {
    return <Empty description="Chưa có dữ liệu" />
  }
  return (
    <Timeline
      items={items.map((item) => {
        const note = lifecycleNote(item)
        // Màu Tag "default" của trạng thái Đã đóng không phải màu của Timeline, nên đổi sang xám.
        const color = ACCOUNT_STATUS_COLORS[item.toStatus] === 'default' ? 'gray' : ACCOUNT_STATUS_COLORS[item.toStatus]
        return {
          key: item.id,
          color,
          children: (
            <Flex vertical gap={2}>
              <Typography.Text type="secondary">{formatDateTime(item.changedAt)}</Typography.Text>
              <Flex wrap align="center" gap={8}>
                <Typography.Text strong>{lifecycleStepName(item)}</Typography.Text>
                <AccountStatusTag status={item.toStatus} />
              </Flex>
              <Typography.Text type="secondary">
                {lifecycleActorText(item)}
                {item.ticketId !== null && (
                  <>
                    {' · '}
                    <Link to={`/phieu/${item.ticketId}`}>
                      <CodeText>{item.ticketCode}</CodeText>
                    </Link>
                  </>
                )}
              </Typography.Text>
              {note && <Typography.Text type="secondary">{note}</Typography.Text>}
            </Flex>
          ),
        }
      })}
    />
  )
}

export default AccountLifecycle