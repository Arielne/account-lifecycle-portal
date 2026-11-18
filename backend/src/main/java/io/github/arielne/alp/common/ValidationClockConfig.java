package io.github.arielne.alp.common;

import jakarta.validation.ClockProvider;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cho Bean Validation tính "hôm nay" theo giờ Việt Nam (spec phiếu mục 7.1: ngày sinh ở quá khứ, ngày cấp không sau
 * hôm nay). @Past, @PastOrPresent so ngày với đồng hồ của Bean Validation; mặc định đồng hồ đó theo múi giờ của máy
 * chạy backend. Container backend chạy giờ UTC, chậm hơn Việt Nam 7 giờ: từ 0 giờ tới 7 giờ sáng Việt Nam, "hôm nay"
 * của container vẫn là ngày hôm qua, nên ngày cấp CCCD là hôm nay bị báo "sau hôm nay".
 * ClockProvider: chỗ Bean Validation hỏi "bây giờ là mấy giờ"; ValidationConfigurationCustomizer: cách Spring Boot
 * cho sửa cấu hình của Bean Validation trước khi tạo Validator dùng chung.
 */
@Configuration
public class ValidationClockConfig {

    static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    @Bean
    public ValidationConfigurationCustomizer vietnamValidationClock() {
        return configuration -> configuration.clockProvider(vietnamClockProvider(Clock.systemUTC()));
    }

    /**
     * Đồng hồ cùng thời điểm với baseClock nhưng đọc ngày, giờ theo múi giờ Việt Nam. Tách riêng để test truyền
     * một giờ cố định vào.
     */
    static ClockProvider vietnamClockProvider(Clock baseClock) {
        return () -> baseClock.withZone(VIETNAM);
    }
}