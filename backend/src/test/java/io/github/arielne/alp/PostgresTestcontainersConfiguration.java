package io.github.arielne.alp;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Cấu hình dùng chung cho test chậm (*IT): chạy Postgres 17 thật trong Docker bằng Testcontainers.
 * @ServiceConnection báo Spring Boot nối DataSource (và Flyway) vào container này thay cho Supabase,
 * nên test không bao giờ ghi vào database thật. Container tự xóa khi chạy test xong.
 * Test nào cần database thì thêm @Import(PostgresTestcontainersConfiguration.class).
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17.11-alpine"));
    }
}