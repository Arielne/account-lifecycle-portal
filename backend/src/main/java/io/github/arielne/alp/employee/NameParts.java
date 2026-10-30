package io.github.arielne.alp.employee;

/**
 * Họ và Tên tách từ họ tên đầy đủ kiểu Việt (spec admin mục 6): Tên là từ cuối, Họ là phần trước.
 * Ví dụ "Nguyễn Thị Lan" → Họ "Nguyễn Thị", Tên "Lan". KeyCloak lưu Họ ở lastName, Tên ở firstName;
 * khi đăng nhập, VietnameseFullName ghép lại đúng thứ tự Họ trước Tên sau.
 */
public record NameParts(String familyName, String givenName) {

    /** Họ tên đã được kiểm tra có ít nhất 2 từ; khoảng trắng thừa được bỏ. */
    public static NameParts fromFullName(String fullName) {
        String normalized = fullName.trim().replaceAll("\\s+", " ");
        int lastSpace = normalized.lastIndexOf(' ');
        if (lastSpace < 0) {
            return new NameParts("", normalized);
        }
        return new NameParts(normalized.substring(0, lastSpace), normalized.substring(lastSpace + 1));
    }

    /** Họ tên đầy đủ để lưu vào employee.full_name. */
    public String fullName() {
        if (familyName.isEmpty()) {
            return givenName;
        }
        return familyName + " " + givenName;
    }
}