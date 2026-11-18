package io.github.arielne.alp.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Nội dung nghiệp vụ của dòng audit cho lần gọi method @Audited đang chạy (spec admin mục 4, spec phiếu T3).
 * Service gắn @Audited ghi vào đây mã đối tượng, mô tả và các trường đổi giá trị; AuditAspect đọc lại để lưu.
 * Mỗi lần gọi method @Audited có một bản ghi chú riêng (Content), giữ trong ThreadLocal: mỗi luồng xử lý (luồng của
 * một request HTTP, hoặc luồng đọc Kafka của notification) thấy bản của riêng mình, không lẫn với luồng khác.
 * Bản cũ dùng @RequestScope (một ghi chú cho mỗi request HTTP), nên luồng Kafka không có request thì không ghi được
 * audit cho bước hệ thống như Hoàn tất phiếu.
 * AuditAspect gọi begin() trước khi method chạy và end() trong finally, để luồng được dùng lại không còn dữ liệu cũ.
 */
@Component
public class AuditNote {

    private static final String EMPTY_VALUE = "(trống)";

    private final ThreadLocal<Content> current = new ThreadLocal<>();

    /**
     * Mở bản ghi chú mới cho lần gọi @Audited sắp chạy. Trả bản đang mở trước đó (thường là null) để end() trả lại,
     * phòng khi một method @Audited gọi một method @Audited khác.
     */
    public Content begin() {
        Content previous = current.get();
        current.set(new Content());
        return previous;
    }

    /** Lần gọi @Audited đã xong: trả lại bản trước đó; không có bản trước thì xóa hẳn khỏi luồng này. */
    public void end(Content previous) {
        if (previous == null) {
            current.remove();
            return;
        }
        current.set(previous);
    }

    /** Mã nghiệp vụ của đối tượng, ví dụ mã chi nhánh CN-Q1; lưu vào audit_log.object_id. */
    public String getObjectId() {
        return content().objectId;
    }

    public void setObjectId(String objectId) {
        content().objectId = objectId;
    }

    /** Mô tả bằng lời nghiệp vụ, ví dụ "Chi nhánh Quận 7"; lưu vào audit_log.description. */
    public String getDescription() {
        return content().description;
    }

    public void setDescription(String description) {
        content().description = description;
    }

    public List<AuditChange> getChanges() {
        return content().changes;
    }

    /** Ghi một trường khi giá trị trước và sau khác nhau. Giá trị truyền vào là chữ hiển thị; rỗng ghi "(trống)". */
    public void addChangeIfDifferent(String label, String before, String after) {
        if (Objects.equals(before, after)) {
            return;
        }
        content().changes.add(new AuditChange(label, displayValue(before), displayValue(after)));
    }

    /**
     * Mô tả cho thao tác sửa, theo bản thiết kế: 1 trường thì "Nhãn: trước → sau";
     * từ 2 trường thì "Sửa 2 trường: Địa chỉ, Số điện thoại" (giá trị nằm ở cột changes).
     */
    public void describeChanges() {
        List<AuditChange> changes = content().changes;
        if (changes.isEmpty()) {
            setDescription("Không có trường nào thay đổi");
        } else if (changes.size() == 1) {
            AuditChange change = changes.get(0);
            setDescription(change.label() + ": " + change.before() + " → " + change.after());
        } else {
            List<String> labels = new ArrayList<>();
            for (AuditChange change : changes) {
                labels.add(change.label());
            }
            setDescription("Sửa " + changes.size() + " trường: " + String.join(", ", labels));
        }
    }

    /** Bản đang mở của luồng này. Gọi ngoài method @Audited (ví dụ test gọi thẳng Service) thì tự mở một bản. */
    private Content content() {
        Content content = current.get();
        if (content == null) {
            content = new Content();
            current.set(content);
        }
        return content;
    }

    private String displayValue(String value) {
        if (value == null || value.isBlank()) {
            return EMPTY_VALUE;
        }
        return value;
    }

    /** Một bản ghi chú: mã đối tượng, mô tả, các trường đổi giá trị. Bên ngoài chỉ cầm nó để trả lại cho end(). */
    public static final class Content {

        private String objectId;
        private String description;
        private final List<AuditChange> changes = new ArrayList<>();
    }
}