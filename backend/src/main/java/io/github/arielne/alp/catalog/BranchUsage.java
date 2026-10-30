package io.github.arielne.alp.catalog;

import lombok.Getter;
import lombok.Setter;

/**
 * Số dòng ở các bảng khác còn thuộc một chi nhánh (CN-21). Còn dòng nào thì không xóa được chi nhánh (QT-08).
 */
@Getter
@Setter
public class BranchUsage {

    private long employeeCount;
    private long customerCount;
    private long accountCount;
    private long ticketCount;
}