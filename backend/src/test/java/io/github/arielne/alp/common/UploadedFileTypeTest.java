package io.github.arielne.alp.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Test 8 của spec phiếu (QT-10): nhận dạng PDF, PNG, JPG bằng byte đầu; file đổi đuôi bị từ chối. */
class UploadedFileTypeTest {

    static final byte[] PDF_CONTENT = "%PDF-1.7 noi dung gia".getBytes(StandardCharsets.US_ASCII);
    static final byte[] PNG_CONTENT = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01};
    static final byte[] JPEG_CONTENT = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};

    @Test
    @DisplayName("PDF, PNG, JPG đúng byte đầu và đúng đuôi thì nhận, trả đúng Content-Type")
    void detectsThreeAllowedTypes() {
        // Chuẩn bị, chạy, kiểm tra kết quả
        assertEquals("application/pdf", UploadedFileType.check("cccd.pdf", PDF_CONTENT).getContentType());
        assertEquals("image/png", UploadedFileType.check("anh.PNG", PNG_CONTENT).getContentType());
        assertEquals("image/jpeg", UploadedFileType.check("anh.jpeg", JPEG_CONTENT).getContentType());
        assertEquals("image/jpeg", UploadedFileType.check("anh.jpg", JPEG_CONTENT).getContentType());
    }

    @Test
    @DisplayName("File đổi đuôi .pdf mà bên trong là ảnh PNG thì 400")
    void renamedFileIsRejected() {
        // Chuẩn bị, chạy
        BusinessException exception = assertThrows(BusinessException.class,
                () -> UploadedFileType.check("giay-to.pdf", PNG_CONTENT));

        // Kiểm tra kết quả
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("File giay-to.pdf không phải PDF, JPG hoặc PNG", exception.getMessage());
    }

    @Test
    @DisplayName("File lạ (ví dụ .exe) và file rỗng thì 400")
    void unknownAndEmptyFilesAreRejected() {
        // Chuẩn bị
        byte[] executable = {0x4D, 0x5A, (byte) 0x90, 0x00};

        // Chạy, kiểm tra kết quả
        assertThrows(BusinessException.class, () -> UploadedFileType.check("chay.exe", executable));
        assertThrows(BusinessException.class, () -> UploadedFileType.check("rong.pdf", new byte[0]));
    }
}