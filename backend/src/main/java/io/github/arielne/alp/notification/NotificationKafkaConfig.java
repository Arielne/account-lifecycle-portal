package io.github.arielne.alp.notification;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Cách thử lại khi listener của notification ném lỗi, thường là gửi mail lỗi vì Mailpit tắt (spec phiếu mục 9 bước 4).
 * DefaultErrorHandler: Spring Kafka đọc lại đúng message vừa lỗi và gọi lại listener, chưa chuyển sang message sau.
 * ExponentialBackOff: chờ 1 giây trước lần thử đầu, mỗi lần sau chờ gấp đôi lần trước, chờ tối đa 1 phút một lần,
 * không giới hạn số lần thử. Spring Boot tự gắn bean này vào mọi @KafkaListener.
 * Trong lúc thử lại, phiếu vẫn ở Đã duyệt; màn hình hiện "Đang gửi mail".
 */
@Configuration
public class NotificationKafkaConfig {

    private static final long FIRST_WAIT_MILLIS = 1_000;
    private static final double WAIT_MULTIPLIER = 2.0;
    private static final long MAX_WAIT_MILLIS = 60_000;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(FIRST_WAIT_MILLIS, WAIT_MULTIPLIER);
        backOff.setMaxInterval(MAX_WAIT_MILLIS);
        return new DefaultErrorHandler(backOff);
    }
}