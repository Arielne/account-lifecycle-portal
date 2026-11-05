package io.github.arielne.alp.common;

/**
 * Che thông tin cá nhân của khách trước khi trả xuống trình duyệt (QT-05, QT-06; spec khách hàng tài khoản mục 3).
 * Dùng cho mọi màn chỉ xem: tài khoản, khách hàng, sau này cả phiếu và báo cáo. Che ở backend để bản đầy đủ
 * không bao giờ có trong phản hồi của API, kể cả khi người dùng mở DevTools xem.
 * Class chỉ có hàm static vì không giữ trạng thái gì; constructor private để không ai tạo object.
 */
public final class PersonalDataMasker {

    private PersonalDataMasker() {
    }

    /** CCCD 12 số: giữ 4 số đầu, 4 số cuối. Ví dụ 079012345678 thành 0790****5678. */
    public static String maskCccd(String cccd) {
        return keepEnds(cccd, 4, 4);
    }

    /** Điện thoại 10–11 số: giữ 3 số đầu, 3 số cuối. Ví dụ 0901234321 thành 090****321. */
    public static String maskPhone(String phone) {
        return keepEnds(phone, 3, 3);
    }

    /**
     * Email: giữ 3 ký tự đầu của phần trước @ (phần đó dưới 4 ký tự thì giữ 1), thêm ***, giữ nguyên phần từ @.
     * Ví dụ an.pham@nhdemo.local thành an.***@nhdemo.local. Không có phần trước @ thì cả chuỗi thành ***.
     */
    public static String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }
        int keepLength = 3;
        if (atIndex < 4) {
            keepLength = 1;
        }
        return email.substring(0, keepLength) + "***" + email.substring(atIndex);
    }

    /** Giữ headLength ký tự đầu, tailLength ký tự cuối, phần giữa thay bằng *. Chuỗi quá ngắn thì thay hết bằng *. */
    private static String keepEnds(String text, int headLength, int tailLength) {
        if (text.length() <= headLength + tailLength) {
            return "*".repeat(text.length());
        }
        String middle = "*".repeat(text.length() - headLength - tailLength);
        return text.substring(0, headLength) + middle + text.substring(text.length() - tailLength);
    }
}