package io.github.arielne.alp.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật @Scheduled cho cả app: OutboxPublisher chạy 2 giây một lần (spec phiếu mục 9). Spring chỉ chạy method
 * @Scheduled khi có @EnableScheduling ở một class cấu hình.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}