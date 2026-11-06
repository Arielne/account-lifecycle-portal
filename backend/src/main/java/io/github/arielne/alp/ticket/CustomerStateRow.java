package io.github.arielne.alp.ticket;

import lombok.Getter;
import lombok.Setter;

/**
 * Kết quả câu TicketMapper.findCustomerState: hồ sơ khách theo CCCD hoặc id, mã phiếu Mở tài khoản cho khách mới
 * đang giữ hồ sơ (nếu có), và hồ sơ có thuộc loại chưa từng mở được tài khoản không (D5, D6).
 */
@Getter
@Setter
public class CustomerStateRow {

    private Long customerId;
    private String pendingTicketCode;
    private boolean reusable;
}