package io.github.arielne.alp.employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.arielne.alp.PostgresTestcontainersConfiguration;
import io.github.arielne.alp.RedisTestcontainersConfiguration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Test chậm cho việc trả KeyCloak về như cũ khi mất kết nối database đúng lúc commit (spec admin mục 8, test 9).
 * Gọi API thật qua MockMvc trên Postgres của Testcontainers; KeycloakUserClient là mock (CI không có KeyCloak).
 * Không có transaction của test: Service tự mở transaction thật, và test ngắt kết nối của transaction đó ngay trước
 * bước commit. Khi đó commit lỗi, rollback cũng lỗi, nên Spring báo STATUS_UNKNOWN (không rõ database đã lưu chưa).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class EmployeeKeycloakUndoIT {

    private static final String ADMIN_KEYCLOAK_ID = "0a1b2c3d-0000-4000-8000-0000000000b1";
    private static final String NEW_KEYCLOAK_ID = "0a1b2c3d-0000-4000-8000-0000000000b2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private KeycloakUserClient keycloakUserClient;

    @Test
    @DisplayName("Mất kết nối database đúng lúc commit thì vẫn xóa user vừa tạo trên KeyCloak và ghi log để đối chiếu")
    void lostConnectionAtCommitDeletesNewKeycloakUser(CapturedOutput output) throws Exception {
        // Chuẩn bị: KeyCloak tạo được user. Gửi mail đặt mật khẩu là bước KeyCloak cuối cùng của việc thêm nhân viên,
        // nên tới bước đó thì hẹn ngắt kết nối database ngay trước commit.
        when(keycloakUserClient.createUser(anyString(), any())).thenReturn(NEW_KEYCLOAK_ID);
        doAnswer(invocation -> {
            terminateConnectionBeforeCommit();
            return null;
        }).when(keycloakUserClient).sendSetPasswordEmail(NEW_KEYCLOAK_ID);

        // Chạy
        mockMvc.perform(post("/api/employees").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Nguyễn Văn Đứt", "email": "dut.nv@nhdemo.local", "roles": ["ADMIN"]}
                                """))
                .andExpect(status().isInternalServerError());

        // Kiểm tra kết quả: user trên KeyCloak bị xóa, log báo cần đối chiếu, database không có hồ sơ
        verify(keycloakUserClient).deleteUser(NEW_KEYCLOAK_ID);
        assertTrue(output.getOut().contains("Không rõ database đã lưu hay chưa"));
        Integer employeeCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app.employee WHERE email = 'dut.nv@nhdemo.local'", Integer.class);
        assertEquals(0, employeeCount);
    }

    /**
     * Chạy bên trong transaction của Service. jdbcTemplate lúc này dùng đúng kết nối đang giữ transaction, nên hỏi được
     * mã tiến trình Postgres (pg_backend_pid) của kết nối đó. Rồi đăng ký một việc chạy ngay trước commit (beforeCommit),
     * sau khi mọi câu SQL kể cả ghi audit đã chạy xong: việc đó ngắt kết nối kia bằng pg_terminate_backend.
     */
    private void terminateConnectionBeforeCommit() {
        Integer backendPid = jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                terminateBackend(backendPid);
            }
        });
    }

    /**
     * Ngắt tiến trình Postgres có mã backendPid và chờ tối đa 5 giây cho tới khi nó dừng hẳn.
     * Phải dùng kết nối khác lấy thẳng từ DataSource: jdbcTemplate vẫn đang gắn với kết nối sắp bị ngắt.
     */
    private void terminateBackend(Integer backendPid) {
        try (Connection otherConnection = dataSource.getConnection();
             PreparedStatement statement = otherConnection.prepareStatement("SELECT pg_terminate_backend(?, 5000)")) {
            statement.setInt(1, backendPid);
            statement.execute();
        } catch (SQLException exception) {
            throw new IllegalStateException("Không ngắt được kết nối database của transaction", exception);
        }
    }

    private RequestPostProcessor admin() {
        return oidcLogin().idToken(token -> token.subject(ADMIN_KEYCLOAK_ID))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}