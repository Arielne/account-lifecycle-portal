package io.github.arielne.alp.common;

import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * 3 loại giấy tờ nhận được: PDF, PNG, JPG (QT-10). Loại thật đọc từ các byte đầu file ("magic number"),
 * không tin đuôi file hay Content-Type trình duyệt gửi, vì cả hai đổi được tùy ý. Đuôi file vẫn phải khớp
 * loại đọc được, để file "anh.pdf" mà bên trong là ảnh PNG cũng bị từ chối.
 */
public enum UploadedFileType {

    PDF("application/pdf", List.of("pdf"), new int[] {0x25, 0x50, 0x44, 0x46, 0x2D}),
    PNG("image/png", List.of("png"), new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
    JPEG("image/jpeg", List.of("jpg", "jpeg"), new int[] {0xFF, 0xD8, 0xFF});

    private final String contentType;
    private final List<String> extensions;
    // Các byte đầu của loại file: PDF bắt đầu bằng "%PDF-", PNG có 8 byte cố định, JPG bắt đầu bằng FF D8 FF.
    private final int[] leadingBytes;

    UploadedFileType(String contentType, List<String> extensions, int[] leadingBytes) {
        this.contentType = contentType;
        this.extensions = extensions;
        this.leadingBytes = leadingBytes;
    }

    public String getContentType() {
        return contentType;
    }

    /** Loại của file theo byte đầu và đuôi tên file; không khớp loại nào thì 400 nói rõ file nào sai. */
    public static UploadedFileType check(String fileName, byte[] content) {
        for (UploadedFileType type : values()) {
            if (type.startsWithLeadingBytes(content) && type.matchesExtension(fileName)) {
                return type;
            }
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "File " + fileName + " không phải PDF, JPG hoặc PNG");
    }

    private boolean startsWithLeadingBytes(byte[] content) {
        if (content.length < leadingBytes.length) {
            return false;
        }
        for (int index = 0; index < leadingBytes.length; index++) {
            // byte của Java có dấu (-128..127); & 0xFF đổi về 0..255 để so với giá trị viết dạng hex.
            if ((content[index] & 0xFF) != leadingBytes[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) {
            return false;
        }
        String extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return extensions.contains(extension);
    }
}