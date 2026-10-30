package io.github.arielne.alp.employee;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Hai cột của bảng employee mà bộ lọc phiên ở module auth đọc ở mỗi request vào /api (spec admin mục 6).
 * Chỉ đọc 2 cột này, không JOIN chi nhánh như Employee, vì câu SELECT chạy ở mọi request.
 */
@Getter
@Setter
public class EmployeeSessionState {

    /** ACTIVE hoặc LOCKED. */
    private String status;
    /** Phiên tạo trước thời điểm này đã bị thu hồi (bị bỏ role, bị khóa); rỗng khi chưa thu hồi lần nào. */
    private OffsetDateTime sessionsInvalidBefore;
}