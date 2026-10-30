package io.github.arielne.alp.employee;

import io.github.arielne.alp.common.BusinessException;
import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Mọi lời gọi Admin API của KeyCloak gom ở đây (CN-22, spec admin mục 6), để test thay class này bằng mock
 * (CI không có KeyCloak). Dùng thư viện keycloak-admin-client với client alp-admin: thư viện tự lấy token
 * bằng client credentials và tự làm mới khi hết hạn, nên code chỉ gọi hàm như gọi object thường.
 */
@Component
public class KeycloakUserClient {

    private static final String REALM = "alp";
    private static final int CONFLICT = 409;

    private final Keycloak keycloak;
    private final RealmResource realm;

    public KeycloakUserClient(@Value("${keycloak.backchannel-url}") String serverUrl,
                              @Value("${keycloak.admin-client-id}") String clientId,
                              @Value("${keycloak.admin-client-secret}") String clientSecret) {
        // Chỉ khai báo cách kết nối; chưa gọi KeyCloak cho tới lần dùng đầu tiên.
        this.keycloak = KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(REALM)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();
        this.realm = keycloak.realm(REALM);
    }

    /** Đóng kết nối HTTP của thư viện khi backend tắt. */
    @PreDestroy
    public void close() {
        keycloak.close();
    }

    /** Tạo user đăng nhập bằng email, bật sẵn; trả id KeyCloak (chính là keycloak_id). Email đã có tài khoản thì 409. */
    public String createUser(String email, NameParts name) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(email);
        user.setEmail(email);
        user.setLastName(name.familyName());
        user.setFirstName(name.givenName());
        user.setEnabled(true);
        // try-with-resources: tự đóng Response sau khi đọc xong.
        try (Response response = realm.users().create(user)) {
            if (response.getStatus() == CONFLICT) {
                throw new BusinessException(HttpStatus.CONFLICT, "Email " + email + " đã có tài khoản trên KeyCloak");
            }
            return CreatedResponseUtil.getCreatedId(response);
        }
    }

    /** Sửa email, Họ, Tên của user. Email mới đã thuộc tài khoản khác thì 409. */
    public void updateUser(String keycloakId, String email, NameParts name) {
        UserResource userResource = realm.users().get(keycloakId);
        UserRepresentation user = userResource.toRepresentation();
        user.setEmail(email);
        user.setLastName(name.familyName());
        user.setFirstName(name.givenName());
        try {
            userResource.update(user);
        } catch (WebApplicationException exception) {
            if (exception.getResponse().getStatus() == CONFLICT) {
                throw new BusinessException(HttpStatus.CONFLICT, "Email " + email + " đã có tài khoản khác trên KeyCloak");
            }
            throw exception;
        }
    }

    /** Xóa user; dùng để bù lại khi tạo trên KeyCloak xong mà lưu hồ sơ bị lỗi. */
    public void deleteUser(String keycloakId) {
        realm.users().delete(keycloakId).close();
    }

    /** KeyCloak gửi mail (qua SMTP của realm, tới Mailpit) để người đó tự đặt mật khẩu. */
    public void sendSetPasswordEmail(String keycloakId) {
        realm.users().get(keycloakId).executeActionsEmail(List.of("UPDATE_PASSWORD"));
    }

    /** keycloak_id của mọi người có role này. max = -1 để lấy đủ; mặc định KeyCloak chỉ trả 100 người. */
    public Set<String> findUserIdsWithRole(String role) {
        Set<String> userIds = new HashSet<>();
        for (UserRepresentation user : realm.roles().get(role).getUserMembers(0, -1)) {
            userIds.add(user.getId());
        }
        return userIds;
    }

    /** Role của app (MAKER, CHECKER, ADMIN) mà user đang có; bỏ qua role mặc định của KeyCloak. */
    public List<String> findAppRoles(String keycloakId) {
        List<String> roles = new ArrayList<>();
        for (RoleRepresentation role : realm.users().get(keycloakId).roles().realmLevel().listAll()) {
            if (AppRoles.ALL.contains(role.getName())) {
                roles.add(role.getName());
            }
        }
        return roles;
    }

    public void addRoles(String keycloakId, List<String> roleNames) {
        if (!roleNames.isEmpty()) {
            realm.users().get(keycloakId).roles().realmLevel().add(toRepresentations(roleNames));
        }
    }

    public void removeRoles(String keycloakId, List<String> roleNames) {
        if (!roleNames.isEmpty()) {
            realm.users().get(keycloakId).roles().realmLevel().remove(toRepresentations(roleNames));
        }
    }

    /** Bật hoặc tắt tài khoản; tắt thì KeyCloak không cho đăng nhập nữa. */
    public void setEnabled(String keycloakId, boolean enabled) {
        UserResource userResource = realm.users().get(keycloakId);
        UserRepresentation user = userResource.toRepresentation();
        user.setEnabled(enabled);
        userResource.update(user);
    }

    /** Kết thúc mọi phiên đăng nhập của user trên KeyCloak. */
    public void logout(String keycloakId) {
        realm.users().get(keycloakId).logout();
    }

    /** Admin API cần object role đầy đủ (có id), nên đọc từng role theo tên; việc này cần quyền view-realm. */
    private List<RoleRepresentation> toRepresentations(List<String> roleNames) {
        List<RoleRepresentation> roles = new ArrayList<>();
        for (String roleName : roleNames) {
            roles.add(realm.roles().get(roleName).toRepresentation());
        }
        return roles;
    }
}