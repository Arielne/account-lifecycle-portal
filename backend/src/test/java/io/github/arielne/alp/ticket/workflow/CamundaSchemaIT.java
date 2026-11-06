package io.github.arielne.alp.ticket.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.arielne.alp.PostgresTestcontainersConfiguration;
import io.github.arielne.alp.RedisTestcontainersConfiguration;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test 9 của spec phiếu (T1): Camunda tạo bảng trong schema camunda, schema app không có bảng nào của Camunda,
 * và quy trình ticket-approval đã được deploy khi app khởi động. Chạy trên Postgres mới tinh của Testcontainers,
 * nên cũng kiểm luôn thứ tự: Flyway tạo schema camunda trước khi Camunda tạo bảng.
 */
@SpringBootTest
@Import({PostgresTestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class CamundaSchemaIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RepositoryService repositoryService;

    @Test
    @DisplayName("Bảng của Camunda nằm trong schema camunda; schema app không có bảng ACT_ nào")
    void camundaTablesLiveInCamundaSchemaOnly() {
        // Chuẩn bị: câu đếm bảng có tên bắt đầu bằng act_ theo schema (Camunda dùng tên chữ thường trên Postgres)
        String countSql = "SELECT count(*) FROM information_schema.tables"
                + " WHERE table_schema = ? AND table_name LIKE 'act\\_%'";

        // Chạy
        Integer camundaTableCount = jdbcTemplate.queryForObject(countSql, Integer.class, "camunda");
        Integer appTableCount = jdbcTemplate.queryForObject(countSql, Integer.class, "app");

        // Kiểm tra kết quả
        assertTrue(camundaTableCount > 30, "Camunda phải tạo bảng trong schema camunda, đếm được " + camundaTableCount);
        assertEquals(0, appTableCount);
    }

    @Test
    @DisplayName("Quy trình ticket-approval được deploy khi app khởi động")
    void ticketApprovalProcessIsDeployed() {
        // Chuẩn bị, chạy
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("ticket-approval")
                .latestVersion()
                .singleResult();

        // Kiểm tra kết quả
        assertNotNull(definition);
        assertEquals("Duyệt phiếu yêu cầu", definition.getName());
    }
}