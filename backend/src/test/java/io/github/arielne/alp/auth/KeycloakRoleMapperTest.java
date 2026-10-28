package io.github.arielne.alp.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

/**
 * Test nhanh (unit test, không cần Spring) cho việc đổi realm_access.roles thành quyền của Spring (spec mục 8, test 6).
 */
class KeycloakRoleMapperTest {

    private final KeycloakRoleMapper keycloakRoleMapper = new KeycloakRoleMapper();

    @Test
    @DisplayName("Token có role: chỉ giữ 3 role của app, bỏ các role mặc định của KeyCloak")
    void keepsOnlyAppRolesFromIdToken() {
        // Chuẩn bị: ID token như KeyCloak cấp cho Anh (MAKER, CHECKER kèm role mặc định)
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token")
                .subject("6356f260-3832-45bc-bb17-9ce71e0a4407")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("realm_access", Map.of("roles",
                        List.of("default-roles-alp", "offline_access", "uma_authorization", "MAKER", "CHECKER")))
                .build();
        List<GrantedAuthority> authoritiesFromLogin = List.of(new OidcUserAuthority(idToken));

        // Chạy
        Collection<? extends GrantedAuthority> mappedAuthorities = keycloakRoleMapper.mapAuthorities(authoritiesFromLogin);

        // Kiểm tra kết quả: có ROLE_MAKER, ROLE_CHECKER; không có role mặc định nào
        List<String> authorityNames = namesOf(mappedAuthorities);
        assertTrue(authorityNames.contains("ROLE_MAKER"));
        assertTrue(authorityNames.contains("ROLE_CHECKER"));
        assertTrue(authorityNames.stream().noneMatch(name -> name.contains("offline_access")));
        assertTrue(authorityNames.stream().noneMatch(name -> name.contains("default-roles-alp")));
    }

    @Test
    @DisplayName("Token không có role nào của app (người tự đăng ký): không có quyền ROLE_ nào")
    void returnsNoRoleWhenUserHasOnlyDefaultRoles() {
        // Chuẩn bị
        Map<String, Object> claims = Map.of("realm_access",
                Map.of("roles", List.of("default-roles-alp", "offline_access", "uma_authorization")));

        // Chạy
        List<GrantedAuthority> roleAuthorities = keycloakRoleMapper.toRoleAuthorities(claims);

        // Kiểm tra kết quả
        assertEquals(0, roleAuthorities.size());
    }

    @Test
    @DisplayName("Token thiếu claim realm_access: không lỗi, không có quyền ROLE_ nào")
    void returnsNoRoleWhenRealmAccessClaimIsMissing() {
        // Chuẩn bị
        Map<String, Object> claims = Map.of("sub", "5b30439a-4e15-42ac-bf7d-c4e8dba67784");

        // Chạy
        List<GrantedAuthority> roleAuthorities = keycloakRoleMapper.toRoleAuthorities(claims);

        // Kiểm tra kết quả
        assertEquals(0, roleAuthorities.size());
    }

    private List<String> namesOf(Collection<? extends GrantedAuthority> authorities) {
        List<String> names = new ArrayList<>();
        for (GrantedAuthority authority : authorities) {
            names.add(authority.getAuthority());
        }
        return names;
    }
}