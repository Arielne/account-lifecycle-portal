// Phần đầu của một màn theo bản thiết kế: breadcrumb, tiêu đề, câu mô tả và nút chính bên phải.
import type { ReactNode } from 'react'
import { Breadcrumb, Flex, Typography } from 'antd'

interface PageHeadingProps {
  /** Các cấp của breadcrumb, cấp cuối là màn đang mở. */
  breadcrumb: string[]
  title: string
  description: string
  /** Nút chính của màn, ví dụ "Thêm chi nhánh". */
  extra?: ReactNode
}

function PageHeading({ breadcrumb, title, description, extra }: PageHeadingProps) {
  return (
    <Flex vertical gap={8}>
      <Breadcrumb items={breadcrumb.map((label) => ({ title: label }))} />
      <Flex wrap justify="space-between" align="flex-end" gap={12}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {title}
          </Typography.Title>
          <Typography.Text type="secondary">{description}</Typography.Text>
        </div>
        {extra}
      </Flex>
    </Flex>
  )
}

export default PageHeading