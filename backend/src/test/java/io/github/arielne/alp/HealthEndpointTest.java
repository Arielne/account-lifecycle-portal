package io.github.arielne.alp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Test mẫu (test nhanh, không cần Docker) cho /actuator/health.
 * Docker Compose dựa vào đường dẫn này để biết backend đã sẵn sàng (healthcheck trong compose.yaml).
 * MockMvc gửi request giả thẳng vào Spring MVC, không mở cổng mạng thật.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Backend đã khởi động thì /actuator/health trả 200 và trạng thái UP")
    void healthEndpointReturnsUpWhenApplicationIsRunning() throws Exception {
        // Chuẩn bị: không cần dữ liệu, @SpringBootTest đã khởi động toàn bộ ứng dụng.

        // Chạy
        ResultActions result = mockMvc.perform(get("/actuator/health"));

        // Kiểm tra kết quả
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}