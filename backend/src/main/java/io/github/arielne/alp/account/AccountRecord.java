package io.github.arielne.alp.account;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Một dòng app.account để INSERT (AccountMapper.insertPending). Class có setter để MyBatis gán lại id và số
 * tài khoản do database sinh.
 */
@Getter
@Setter
public class AccountRecord {

    private Long id;
    private String accountNumber;
    private Long customerId;
    private Long accountTypeId;
    private Long branchId;
    private LocalDate lastTransactionAt;
}