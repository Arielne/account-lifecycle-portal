package io.github.arielne.alp;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Cấu hình cho test chậm của luồng mail: chạy Kafka thật trong Docker, cùng image với compose.yaml (apache/kafka
 * 4.3.2, ghim theo digest). KafkaContainer chạy Kafka ở chế độ KRaft, không cần ZooKeeper, giống compose.yaml.
 * @ServiceConnection báo Spring Boot đặt spring.kafka.bootstrap-servers trỏ vào container này.
 * Test nào cần Kafka thì thêm KafkaTestcontainersConfiguration vào @Import; nhớ bật lại các phần Kafka mà
 * src/test/resources/application.properties đã tắt.
 */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestcontainersConfiguration {

    private static final String KAFKA_IMAGE = "apache/kafka"
            + "@sha256:852a0d49fc4e656b022e7f23785411802c9a088c78a7c5e39f33969de9c227a7";

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE));
    }
}