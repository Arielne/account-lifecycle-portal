package io.github.arielne.alp.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test nhanh cho cách che CCCD, điện thoại, email (QT-05, QT-06; spec khách hàng tài khoản mục 3, test 1).
 * Toàn bộ số và email ở đây là dữ liệu giả (QT-12).
 */
class PersonalDataMaskerTest {

    @Test
    @DisplayName("CCCD 12 số: giữ 4 số đầu, 4 số cuối")
    void masksCccd() {
        // Chuẩn bị
        String cccd = "099012345678";

        // Chạy
        String masked = PersonalDataMasker.maskCccd(cccd);

        // Kiểm tra kết quả
        assertEquals("0990****5678", masked);
    }

    @Test
    @DisplayName("Điện thoại 10 số và 11 số: giữ 3 số đầu, 3 số cuối, phần giữa đủ số dấu *")
    void masksPhoneOfTenAndElevenDigits() {
        // Chuẩn bị
        String tenDigits = "0901234321";
        String elevenDigits = "02839112200";

        // Chạy
        String maskedTen = PersonalDataMasker.maskPhone(tenDigits);
        String maskedEleven = PersonalDataMasker.maskPhone(elevenDigits);

        // Kiểm tra kết quả
        assertEquals("090****321", maskedTen);
        assertEquals("028*****200", maskedEleven);
    }

    @Test
    @DisplayName("Email: giữ 3 ký tự đầu của phần trước @; phần đó dưới 4 ký tự thì giữ 1; không có phần trước @ thì che hết")
    void masksEmail() {
        // Chuẩn bị
        String longLocalPart = "an.pham@nhdemo.local";
        String shortLocalPart = "abc@nhdemo.local";
        String withoutAt = "khongphaiemail";
        String emptyLocalPart = "@nhdemo.local";

        // Chạy, kiểm tra kết quả
        assertEquals("an.***@nhdemo.local", PersonalDataMasker.maskEmail(longLocalPart));
        assertEquals("a***@nhdemo.local", PersonalDataMasker.maskEmail(shortLocalPart));
        assertEquals("***", PersonalDataMasker.maskEmail(withoutAt));
        assertEquals("***", PersonalDataMasker.maskEmail(emptyLocalPart));
    }
}