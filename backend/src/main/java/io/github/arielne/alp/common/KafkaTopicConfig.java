package io.github.arielne.alp.common;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 2 topic Kafka của app (spec phiếu mục 9, 9.1): mỗi topic 1 partition, 1 bản sao, khớp Kafka 1 broker trong Compose.
 * Bean NewTopic: lúc backend khởi động, Spring Kafka (KafkaAdmin) tạo topic nếu Kafka chưa có; có rồi thì để nguyên.
 * 1 partition nên mọi sự kiện trong topic được đọc lần lượt, đúng thứ tự gửi.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic ticketEventsTopic() {
        return TopicBuilder.name(KafkaTopics.TICKET_EVENTS).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic employeeEventsTopic() {
        return TopicBuilder.name(KafkaTopics.EMPLOYEE_EVENTS).partitions(1).replicas(1).build();
    }
}