package io.github.arielne.alp.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.arielne.alp.PostgresTestcontainersConfiguration;
import io.github.arielne.alp.RedisTestcontainersConfiguration;
import jakarta.validation.ValidatorFactory;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Test chậm: trong app chạy thật (cả Spring context), Validator dùng chung đọc ngày theo giờ Việt Nam.
 * ValidationClockConfigTest chỉ kiểm đồng hồ đứng một mình; test này kiểm Spring Boot có thật sự lắp đồng hồ đó vào
 * Validator mà @Valid ở Controller dùng (Spring MVC bọc đúng bean này). Cùng cấu hình container với
 * DatabaseMigrationIT nên Spring dùng lại context đó, không khởi động thêm container nào.
 */
@SpringBootTest
@Import({PostgresTestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
class ValidationClockConfigIT {

    @Autowired
    private ValidatorFactory validatorFactory;

    @Test
    @DisplayName("Validator dùng chung của app tính ngày theo giờ Việt Nam, không theo múi giờ của máy chạy")
    void appValidatorUsesVietnamClock() {
        // Chuẩn bị: Validator do Spring Boot tạo cho cả app, đã qua ValidationClockConfig

        // Chạy
        ZoneId zone = validatorFactory.getClockProvider().getClock().getZone();

        // Kiểm tra kết quả
        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), zone);
    }
}