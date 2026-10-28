package io.github.arielne.alp.auth;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Ghép họ tên kiểu Việt từ ID token: family_name (Họ) + dấu cách + given_name (Tên), ví dụ "Nguyễn Thị Lan".
 * Không dùng claim name, vì KeyCloak ghép Tên trước Họ sau ("Lan Nguyễn Thị").
 */
public final class VietnameseFullName {

    private VietnameseFullName() {
    }

    public static String fromToken(OidcUser user) {
        String familyName = user.getFamilyName();
        String givenName = user.getGivenName();
        boolean hasFamilyName = familyName != null && !familyName.isBlank();
        boolean hasGivenName = givenName != null && !givenName.isBlank();
        if (hasFamilyName && hasGivenName) {
            return familyName + " " + givenName;
        }
        if (hasFamilyName) {
            return familyName;
        }
        if (hasGivenName) {
            return givenName;
        }
        // Cột full_name không được rỗng: thiếu cả Họ lẫn Tên thì tạm dùng email.
        return user.getEmail();
    }
}