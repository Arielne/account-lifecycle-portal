package io.github.arielne.alp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Bước cuối của POST /logout (CN-02). Spring Security đã xóa session trong Redis trước khi gọi class này.
 * Trả JSON {"logoutUrl": ...}: địa chỉ đăng xuất của KeyCloak kèm id_token_hint và post_logout_redirect_uri,
 * để React tự chuyển trình duyệt tới đó và KeyCloak kết thúc luôn phiên SSO.
 * Không trả chuyển hướng: axios đi theo chuyển hướng trong nền và gặp lỗi CORS (docs/02-auth.md, Lỗi hay gặp).
 */
@Component
public class KeycloakLogoutSuccessHandler implements LogoutSuccessHandler {

    private final String keycloakUrl;
    private final ObjectMapper objectMapper;

    public KeycloakLogoutSuccessHandler(@Value("${keycloak.url}") String keycloakUrl, ObjectMapper objectMapper) {
        this.keycloakUrl = keycloakUrl;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new LogoutResponse(buildLogoutUrl(request, authentication)));
    }

    private String buildLogoutUrl(HttpServletRequest request, Authentication authentication) {
        // Trang chủ của đúng địa chỉ người dùng đang mở: http://localhost/ hoặc http://localhost:5173/.
        String homeUrl = ServletUriComponentsBuilder.fromContextPath(request).path("/").toUriString();
        UriComponentsBuilder logoutUrl = UriComponentsBuilder
                .fromUriString(keycloakUrl + "/realms/alp/protocol/openid-connect/logout")
                .queryParam("post_logout_redirect_uri", homeUrl);
        // id_token_hint báo KeyCloak phiên nào cần kết thúc; có nó thì KeyCloak không hỏi lại người dùng.
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser user) {
            logoutUrl.queryParam("id_token_hint", user.getIdToken().getTokenValue());
        }
        return logoutUrl.encode().build().toUriString();
    }

    /** Nội dung JSON trả về cho React. */
    public record LogoutResponse(String logoutUrl) {
    }
}