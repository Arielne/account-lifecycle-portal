package io.github.arielne.alp;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Cấu hình dùng chung cho test chậm khởi động cả app: chạy Redis thật trong Docker, cùng image với compose.yaml.
 * App có Redis (lưu session) thì /actuator/health kiểm cả Redis; thiếu Redis là health báo DOWN.
 * @ServiceConnection(name = "redis") báo Spring Boot nối vào container này thay cho Redis trên máy.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RedisTestcontainersConfiguration {

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:8.10.3-alpine")).withExposedPorts(6379);
    }
}