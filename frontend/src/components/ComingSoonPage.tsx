// Trang chờ cho màn chưa làm (spec auth, A5): menu đã đủ mục theo role, màn thật thay vào ở tuần ghi trên trang.
import { Result } from 'antd'

interface ComingSoonPageProps {
  title: string
  week: number
}

function ComingSoonPage({ title, week }: ComingSoonPageProps) {
  return <Result status="info" title={title} subTitle={`Màn này làm ở tuần ${week}.`} />
}

export default ComingSoonPage