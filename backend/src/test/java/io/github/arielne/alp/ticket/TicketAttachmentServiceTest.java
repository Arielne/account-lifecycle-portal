package io.github.arielne.alp.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.arielne.alp.common.BusinessException;
import io.github.arielne.alp.common.FileStorage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Test 3 của spec phiếu (QT-10): không có file, quá 5 file, file đổi đuôi đều 400 và không lưu file nào;
 * transaction bị hủy thì file vừa lưu bị xóa. FileStorage là mock; không có transaction thật, test tự bật danh sách
 * việc chờ transaction kết thúc rồi gọi như Spring gọi sau khi rollback.
 */
class TicketAttachmentServiceTest {

    private static final byte[] PDF_CONTENT = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31};
    private static final byte[] PNG_CONTENT = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private FileStorage fileStorage;
    private TicketAttachmentService service;

    @BeforeEach
    void setUp() {
        fileStorage = mock(FileStorage.class);
        service = new TicketAttachmentService(fileStorage, "giay-to");
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("Không có file hoặc 6 file thì 400, không lưu file nào")
    void fileCountOutsideOneToFiveIsRejected() {
        // Chuẩn bị
        List<MultipartFile> sixFiles = new ArrayList<>();
        for (int index = 1; index <= 6; index++) {
            sixFiles.add(pdf("giay-to-" + index + ".pdf"));
        }

        // Chạy
        BusinessException noFile = assertThrows(BusinessException.class, () -> service.storeAll(List.of()));
        BusinessException tooMany = assertThrows(BusinessException.class, () -> service.storeAll(sixFiles));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.BAD_REQUEST, noFile.getStatus());
        assertEquals("Cần đính kèm từ 1 đến 5 giấy tờ", tooMany.getMessage());
        verify(fileStorage, never()).save(anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("File .pdf mà bên trong là ảnh PNG thì 400, kể cả file hợp lệ đi cùng cũng chưa được lưu")
    void renamedFileRejectsWholeRequestBeforeSaving() {
        // Chuẩn bị
        List<MultipartFile> files = List.of(pdf("dung.pdf"),
                new MockMultipartFile("files", "gia.pdf", "application/pdf", PNG_CONTENT));

        // Chạy
        BusinessException exception = assertThrows(BusinessException.class, () -> service.storeAll(files));

        // Kiểm tra kết quả
        assertEquals("File gia.pdf không phải PDF, JPG hoặc PNG", exception.getMessage());
        verify(fileStorage, never()).save(anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("Tên lưu là UUID, tên gốc bỏ phần đường dẫn; transaction bị hủy thì file vừa lưu bị xóa")
    void storedFileIsDeletedWhenTransactionRollsBack() {
        // Chuẩn bị: trình duyệt gửi cả đường dẫn trên máy người dùng
        MockMultipartFile file = new MockMultipartFile("files", "C:\\Users\\lan\\Giấy tờ.pdf", "application/pdf",
                PDF_CONTENT);

        // Chạy
        StoredAttachment stored = service.storeAll(List.of(file)).get(0);
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        // Kiểm tra kết quả
        assertEquals("Giấy tờ.pdf", stored.originalName());
        assertEquals(36, stored.storageKey().length());
        verify(fileStorage).save(eq("giay-to"), eq(stored.storageKey()), eq(PDF_CONTENT), eq("application/pdf"));
        verify(fileStorage).delete("giay-to", stored.storageKey());
    }

    private MockMultipartFile pdf(String name) {
        return new MockMultipartFile("files", name, "application/pdf", PDF_CONTENT);
    }
}