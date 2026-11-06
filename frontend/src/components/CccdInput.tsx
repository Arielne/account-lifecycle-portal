// Ô nhập số CCCD (QT-05): hiện đủ số khi đang gõ, che phần giữa khi rời ô (0991****0002), như cách backend che.
// Chỉ nhận chữ số, tối đa 12. Dùng trong Form.Item: Form truyền value, onChange, id, và onBlur (khi Form.Item kiểm
// lúc rời ô) vào như một ô Input thường, nên phải gọi lại onBlur của Form.
import { useState } from 'react'
import { Input } from 'antd'

interface CccdInputProps {
  id?: string
  value?: string
  onChange?: (value: string) => void
  onBlur?: () => void
  placeholder?: string
}

/** Đủ 12 số thì giữ 4 số đầu, 4 số cuối; chưa đủ thì để nguyên (người dùng cần thấy mình đang gõ dở). */
function maskCccd(cccd: string): string {
  if (cccd.length !== 12) {
    return cccd
  }
  return `${cccd.slice(0, 4)}****${cccd.slice(8)}`
}

function CccdInput({ id, value = '', onChange, onBlur, placeholder }: CccdInputProps) {
  const [focused, setFocused] = useState(false)
  const shownValue = focused ? value : maskCccd(value)
  return (
    <Input
      id={id}
      inputMode="numeric"
      autoComplete="off"
      placeholder={placeholder ?? 'Nhập đủ 12 số CCCD'}
      value={shownValue}
      onFocus={() => setFocused(true)}
      onBlur={() => {
        setFocused(false)
        onBlur?.()
      }}
      // Bỏ ký tự không phải số và cắt còn 12 số. Lúc che, ô chưa được chọn nên không gõ được, không lo ghi đè số thật.
      onChange={(event) => onChange?.(event.target.value.replace(/\D/g, '').slice(0, 12))}
    />
  )
}

export default CccdInput