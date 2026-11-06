package io.github.arielne.alp.common;

import java.text.Normalizer;

/**
 * Chuẩn hóa chữ người dùng gõ trước khi kiểm và lưu (spec phiếu, mục 7.2): bỏ khoảng trắng hai đầu, gộp nhiều
 * khoảng trắng thành một, đưa chữ có dấu về dạng Unicode NFC.
 * Vì sao cần NFC: chữ "ì" có thể là 1 ký tự (NFC) hoặc chữ "i" cộng dấu huyền tách rời (NFD), ví dụ khi dán từ file PDF.
 * Hàm tìm tên không dấu app.unaccent_vi chỉ nhận ra dạng NFC, nên tên lưu ở dạng NFD sẽ không tìm ra.
 * Dùng cho họ tên, nơi cấp, địa chỉ của khách hàng và từ khóa tìm khách.
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /** null giữ nguyên null; chuỗi chỉ có khoảng trắng thành chuỗi rỗng. */
    public static String normalize(String text) {
        if (text == null) {
            return null;
        }
        String nfcText = Normalizer.normalize(text, Normalizer.Form.NFC);
        return nfcText.trim().replaceAll("\\s+", " ");
    }
}