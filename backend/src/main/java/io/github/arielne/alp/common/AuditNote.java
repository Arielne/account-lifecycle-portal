package io.github.arielne.alp.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Nội dung nghiệp vụ của dòng audit cho request đang chạy (spec admin mục 4).
 * Service gắn @Audited ghi vào đây mã đối tượng, mô tả và các trường đổi giá trị; AuditAspect đọc lại để lưu.
 * @RequestScope: mỗi request có một object AuditNote riêng. Service (một object dùng chung cho mọi request)
 * được tiêm một proxy; gọi vào proxy thì Spring chuyển tới object của đúng request đang chạy.
 */
@Component
@RequestScope
@Getter
public class AuditNote {

    private static final String EMPTY_VALUE = "(trống)";

    /** Mã nghiệp vụ của đối tượng, ví dụ mã chi nhánh CN-Q1; lưu vào audit_log.object_id. */
    @Setter
    private String objectId;

    /** Mô tả bằng lời nghiệp vụ, ví dụ "Chi nhánh Quận 7"; lưu vào audit_log.description. */
    @Setter
    private String description;

    private final List<AuditChange> changes = new ArrayList<>();

    /** Ghi một trường khi giá trị trước và sau khác nhau. Giá trị truyền vào là chữ hiển thị; rỗng ghi "(trống)". */
    public void addChangeIfDifferent(String label, String before, String after) {
        if (Objects.equals(before, after)) {
            return;
        }
        changes.add(new AuditChange(label, displayValue(before), displayValue(after)));
    }

    /**
     * Mô tả cho thao tác sửa, theo bản thiết kế: 1 trường thì "Nhãn: trước → sau";
     * từ 2 trường thì "Sửa 2 trường: Địa chỉ, Số điện thoại" (giá trị nằm ở cột changes).
     */
    public void describeChanges() {
        if (changes.isEmpty()) {
            description = "Không có trường nào thay đổi";
        } else if (changes.size() == 1) {
            AuditChange change = changes.get(0);
            description = change.label() + ": " + change.before() + " → " + change.after();
        } else {
            List<String> labels = new ArrayList<>();
            for (AuditChange change : changes) {
                labels.add(change.label());
            }
            description = "Sửa " + changes.size() + " trường: " + String.join(", ", labels);
        }
    }

    private String displayValue(String value) {
        if (value == null || value.isBlank()) {
            return EMPTY_VALUE;
        }
        return value;
    }
}