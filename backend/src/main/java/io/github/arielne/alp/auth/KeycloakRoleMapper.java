package io.github.arielne.alp.auth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.stereotype.Component;

/**
 * Đổi role của KeyCloak thành quyền của Spring (CN-01): đọc claim realm_access.roles trong ID token,
 * mỗi role MAKER, CHECKER, ADMIN thành ROLE_MAKER, ROLE_CHECKER, ROLE_ADMIN.
 * Spring gọi class này một lần lúc đăng nhập (userAuthoritiesMapper trong SecurityConfig);
 * kết quả nằm trong session, nên đổi role trên KeyCloak thì phải đăng nhập lại.
 */
@Component
public class KeycloakRoleMapper implements GrantedAuthoritiesMapper {

    /**
     * 3 role của app. realm_access.roles còn có role mặc định của KeyCloak
     * (default-roles-alp, offline_access, uma_authorization); các role đó bị bỏ qua.
     */
    public static final List<String> APP_ROLES = List.of("MAKER", "CHECKER", "ADMIN");

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        List<GrantedAuthority> mappedAuthorities = new ArrayList<>(authorities);
        for (GrantedAuthority authority : authorities) {
            if (authority instanceof OidcUserAuthority oidcUserAuthority) {
                Map<String, Object> idTokenClaims = oidcUserAuthority.getIdToken().getClaims();
                mappedAuthorities.addAll(toRoleAuthorities(idTokenClaims));
            }
        }
        return mappedAuthorities;
    }

    /** Lấy 3 role của app từ claim realm_access.roles; thiếu claim hoặc sai kiểu thì trả danh sách rỗng. */
    public List<GrantedAuthority> toRoleAuthorities(Map<String, Object> claims) {
        List<GrantedAuthority> roleAuthorities = new ArrayList<>();
        Object realmAccess = claims.get("realm_access");
        if (!(realmAccess instanceof Map<?, ?> realmAccessMap)) {
            return roleAuthorities;
        }
        Object roleNames = realmAccessMap.get("roles");
        if (!(roleNames instanceof Collection<?> roleNameList)) {
            return roleAuthorities;
        }
        for (String appRole : APP_ROLES) {
            if (roleNameList.contains(appRole)) {
                roleAuthorities.add(new SimpleGrantedAuthority("ROLE_" + appRole));
            }
        }
        return roleAuthorities;
    }
}