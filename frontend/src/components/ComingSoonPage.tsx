// Trang chờ cho màn chưa làm (spec auth, A5): menu đã đủ mục theo role, màn thật thay vào khi làm xong.
import { Result } from 'antd'

interface ComingSoonPageProps {
  title: string
}

function ComingSoonPage({ title }: ComingSoonPageProps) {
  return <Result status="info" title={title} subTitle="Màn này đang được xây dựng." />
}

export default ComingSoonPage