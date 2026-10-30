package io.github.arielne.alp.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Bật @Transactional và đặt thứ tự của nó so với AuditAspect (spec admin mục 4).
 * Method vừa có @Transactional vừa có @Audited được bọc 2 lớp: lớp có order nhỏ hơn nằm ngoài.
 * Transaction (order 0) ở ngoài, AuditAspect (order 1) ở trong, nên dòng audit được ghi trước khi transaction
 * commit: thao tác và dòng audit cùng thành công hoặc cùng bị hủy. Không đặt order thì Spring không hứa thứ tự nào.
 */
@Configuration
@EnableTransactionManagement(order = TransactionConfig.TRANSACTION_ORDER)
public class TransactionConfig {

    public static final int TRANSACTION_ORDER = 0;
    public static final int AUDIT_ORDER = TRANSACTION_ORDER + 1;
}