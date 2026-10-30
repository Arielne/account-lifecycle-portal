package io.github.arielne.alp.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Cấu hình Spring Security cho đăng nhập, đăng xuất và phân quyền chung (CN-01 đến CN-05).
 * Mọi request đi qua chuỗi filter này trước khi tới Controller.
 * Phân quyền riêng của từng API (ví dụ duyệt phiếu cần CHECKER) thêm ở tuần làm API đó.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final KeycloakRoleMapper keycloakRoleMapper;
    private final LoginSuccessHandler loginSuccessHandler;
    private final KeycloakLogoutSuccessHandler keycloakLogoutSuccessHandler;
    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;
    private final ApiAccessDeniedHandler apiAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Ai gọi được đường dẫn nào. Luật xét từ trên xuống, gặp luật khớp đầu tiên thì dừng.
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .requestMatchers("/api/me").authenticated()
                        // Danh mục (CN-20, CN-21): mọi role đọc được để chọn và lọc ở các màn khác; chỉ ADMIN ghi.
                        .requestMatchers(HttpMethod.GET, "/api/account-types/**", "/api/branches/**")
                        .hasAnyRole("MAKER", "CHECKER", "ADMIN")
                        .requestMatchers("/api/account-types/**", "/api/branches/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasAnyRole("MAKER", "CHECKER", "ADMIN")
                        .anyRequest().authenticated())
                // OAuth2 Login: tự có /oauth2/authorization/keycloak và /login/oauth2/code/keycloak.
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userAuthoritiesMapper(keycloakRoleMapper))
                        .successHandler(loginSuccessHandler))
                // Gọi /api khi chưa đăng nhập: trả 401 JSON, không chuyển sang trang đăng nhập.
                // Đã đăng nhập nhưng không đủ quyền: trả 403 JSON.
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                apiAuthenticationEntryPoint, PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
                        .defaultAccessDeniedHandlerFor(
                                apiAccessDeniedHandler, PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
                // CSRF cho SPA: token trong cookie XSRF-TOKEN, React gửi lại qua header X-XSRF-TOKEN.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                // POST /logout: xóa session rồi trả địa chỉ đăng xuất của KeyCloak dạng JSON.
                .logout(logout -> logout.logoutSuccessHandler(keycloakLogoutSuccessHandler));
        return http.build();
    }
}