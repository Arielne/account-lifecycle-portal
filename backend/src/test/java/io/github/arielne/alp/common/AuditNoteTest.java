package io.github.arielne.alp.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test nhanh cho AuditNote giữ trong ThreadLocal (spec phiếu T3): mỗi lần gọi @Audited có bản ghi chú riêng, mỗi
 * luồng thấy bản của riêng mình, xong việc thì luồng không còn dữ liệu cũ.
 */
class AuditNoteTest {

    @Test
    @DisplayName("begin mở bản trống; end trả lại bản trước nên method @Audited lồng nhau không lẫn dữ liệu")
    void nestedCallsKeepTheirOwnContent() {
        // Chuẩn bị: lần gọi ngoài đã ghi mã đối tượng
        AuditNote auditNote = new AuditNote();
        AuditNote.Content beforeOuter = auditNote.begin();
        auditNote.setObjectId("PYC-2026-0001");

        // Chạy: lần gọi trong ghi đè mã đối tượng của nó, rồi kết thúc
        AuditNote.Content beforeInner = auditNote.begin();
        auditNote.setObjectId("PYC-2026-0002");
        String innerObjectId = auditNote.getObjectId();
        auditNote.end(beforeInner);

        // Kiểm tra kết quả: lần gọi ngoài lấy lại đúng bản của nó; xong cả hai thì luồng không còn gì
        assertNull(beforeOuter);
        assertEquals("PYC-2026-0002", innerObjectId);
        assertEquals("PYC-2026-0001", auditNote.getObjectId());
        auditNote.end(beforeOuter);
        assertNull(auditNote.getObjectId());
        assertTrue(auditNote.getChanges().isEmpty());
    }

    @Test
    @DisplayName("Hai luồng dùng chung một AuditNote mà mỗi luồng chỉ thấy ghi chú của mình")
    void eachThreadSeesItsOwnContent() throws InterruptedException {
        // Chuẩn bị: luồng của test giống luồng request HTTP, đã ghi mô tả
        AuditNote auditNote = new AuditNote();
        auditNote.begin();
        auditNote.setDescription("Luồng request");
        // AtomicReference: hộp giữ một giá trị, luồng khác ghi vào thì luồng của test đọc ra được an toàn.
        AtomicReference<String> descriptionSeenByOtherThread = new AtomicReference<>();

        // Chạy: luồng khác (giống luồng đọc Kafka) đọc trước khi ghi, rồi ghi mô tả của nó
        Thread otherThread = new Thread(() -> {
            descriptionSeenByOtherThread.set(auditNote.getDescription());
            auditNote.setDescription("Luồng Kafka");
        });
        otherThread.start();
        otherThread.join();

        // Kiểm tra kết quả
        assertNull(descriptionSeenByOtherThread.get());
        assertEquals("Luồng request", auditNote.getDescription());
    }
}