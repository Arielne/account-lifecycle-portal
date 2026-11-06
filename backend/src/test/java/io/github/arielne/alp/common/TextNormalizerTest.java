package io.github.arielne.alp.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.text.Normalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Test 7 của spec phiếu: họ tên dạng NFD ra NFC; khoảng trắng thừa bị gộp. */
class TextNormalizerTest {

    @Test
    @DisplayName("Họ tên dạng NFD (chữ và dấu tách rời) được đưa về NFC, khoảng trắng thừa bị gộp")
    void nfdNameBecomesNfcWithSingleSpaces() {
        // Chuẩn bị: "Đỗ  Thị   Bình" ở dạng NFD, có khoảng trắng thừa hai đầu và giữa các từ.
        // Dạng NFD khác chuỗi gõ thẳng dù nhìn giống hệt, nên so chuỗi hay tìm không dấu đều trượt.
        String nfdName = Normalizer.normalize("  Đỗ  Thị   Bình ", Normalizer.Form.NFD);
        assertNotEquals("Đỗ Thị Bình", Normalizer.normalize("Đỗ Thị Bình", Normalizer.Form.NFD));

        // Chạy
        String normalized = TextNormalizer.normalize(nfdName);

        // Kiểm tra kết quả: đúng từng ký tự với chuỗi NFC gõ thẳng
        assertEquals("Đỗ Thị Bình", normalized);
    }

    @Test
    @DisplayName("null giữ nguyên null; chuỗi chỉ có khoảng trắng thành chuỗi rỗng")
    void nullAndBlankText() {
        // Chuẩn bị, chạy, kiểm tra kết quả
        assertNull(TextNormalizer.normalize(null));
        assertEquals("", TextNormalizer.normalize("   "));
    }
}