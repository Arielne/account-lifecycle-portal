package io.github.arielne.alp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Test chậm tái hiện lỗi: Supabase đã chạy 3 file seed R__ (container backend chạy profile demo),
 * sau đó app chạy ở profile mặc định (locations không có db/seeds/) thì Flyway báo
 * "Detected applied migration not resolved locally" và app không khởi động.
 * @DirtiesContext: test này nạp dữ liệu mẫu vào database, nên đóng context (kèm container Postgres)
 * sau khi chạy xong, để các test khác vẫn dùng database sạch.
 */
@SpringBootTest
@Import({PostgresTestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FlywaySeedValidationIT {

    // Flyway của app, cấu hình theo profile mặc định (chỉ có db/migrations/).
    @Autowired
    private Flyway flyway;

    @Test
    @DisplayName("Database đã chạy seed ở profile demo, app chạy lại ở profile mặc định thì Flyway không báo lỗi")
    void defaultProfileStartsAfterSeedsWereAppliedInDemoProfile() {
        // Chuẩn bị: chạy thêm db/seeds/ lên database, giống lúc container backend chạy profile demo
        Flyway demoFlyway = Flyway.configure()
                .configuration(flyway.getConfiguration())
                .locations("filesystem:../db/migrations", "filesystem:../db/seeds")
                .load();
        demoFlyway.migrate();

        // Chạy và kiểm tra kết quả: app khởi động lại ở profile mặc định cũng gọi migrate,
        // Flyway kiểm tra các migration đã chạy trước rồi mới chạy tiếp
        assertDoesNotThrow(() -> flyway.migrate());
    }
}