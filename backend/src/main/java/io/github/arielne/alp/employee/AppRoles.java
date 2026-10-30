package io.github.arielne.alp.employee;

import java.util.List;
import java.util.Map;

/**
 * 3 role của app và nhãn tiếng Việt (CN-22). Role chỉ lưu ở KeyCloak (realm role);
 * class này chỉ dùng để kiểm tra role hợp lệ và để ghi nhãn vào audit log, mail.
 */
public final class AppRoles {

    public static final List<String> ALL = List.of("MAKER", "CHECKER", "ADMIN");

    private static final Map<String, String> LABELS = Map.of(
            "MAKER", "Giao dịch viên",
            "CHECKER", "Kiểm soát viên",
            "ADMIN", "Admin");

    private AppRoles() {
    }

    public static String label(String role) {
        return LABELS.get(role);
    }

    /** Giao dịch viên và Kiểm soát viên làm việc theo chi nhánh nên phải có chi nhánh; Admin thì không cần. */
    public static boolean needsBranch(String role) {
        return role.equals("MAKER") || role.equals("CHECKER");
    }

    /** Nhãn của nhiều role, cách nhau dấu phẩy, theo thứ tự MAKER, CHECKER, ADMIN; không có role nào thì "(trống)". */
    public static String labels(List<String> roles) {
        StringBuilder text = new StringBuilder();
        for (String role : ALL) {
            if (roles.contains(role)) {
                if (!text.isEmpty()) {
                    text.append(", ");
                }
                text.append(label(role));
            }
        }
        if (text.isEmpty()) {
            return "(trống)";
        }
        return text.toString();
    }
}