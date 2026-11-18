package io.github.arielne.alp.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ClockProvider;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.PastOrPresent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test nhanh tái hiện lỗi "ngày cấp là hôm nay bị báo sau hôm nay" khi backend chạy giờ UTC (container).
 * Đồng hồ cố định ở 18:00 ngày 12/11/2026 giờ UTC, tức 01:00 ngày 13/11/2026 giờ Việt Nam: theo UTC thì 13/11 là
 * ngày mai, theo giờ Việt Nam thì 13/11 là hôm nay.
 */
class ValidationClockConfigTest {

    private static final Clock ONE_AM_IN_VIETNAM = Clock.fixed(Instant.parse("2026-11-12T18:00:00Z"), ZoneOffset.UTC);

    /** Một ô ngày có cùng quy tắc với ngày cấp CCCD trong NewCustomerRequest. */
    private record IssueDate(@PastOrPresent LocalDate value) {
    }

    @Test
    @DisplayName("Lúc 1 giờ sáng Việt Nam: ngày hôm nay theo giờ Việt Nam hợp lệ, ngày mai thì không")
    void todayInVietnamIsNotInTheFuture() {
        // Chuẩn bị: Validator dùng đồng hồ của ValidationClockConfig
        Validator validator = validatorWith(ValidationClockConfig.vietnamClockProvider(ONE_AM_IN_VIETNAM));

        // Chạy
        Set<ConstraintViolation<IssueDate>> today = validator.validate(new IssueDate(LocalDate.of(2026, 11, 13)));
        Set<ConstraintViolation<IssueDate>> tomorrow = validator.validate(new IssueDate(LocalDate.of(2026, 11, 14)));

        // Kiểm tra kết quả
        assertTrue(today.isEmpty(), "13/11 là hôm nay theo giờ Việt Nam nên phải hợp lệ");
        assertEquals(1, tomorrow.size());
    }

    @Test
    @DisplayName("Không có cấu hình này (đồng hồ giờ UTC) thì ngày hôm nay theo giờ Việt Nam bị coi là tương lai")
    void utcClockRejectsTodayInVietnam() {
        // Chuẩn bị: Validator dùng thẳng đồng hồ UTC, như container backend trước khi sửa
        Validator validator = validatorWith(() -> ONE_AM_IN_VIETNAM);

        // Chạy
        Set<ConstraintViolation<IssueDate>> today = validator.validate(new IssueDate(LocalDate.of(2026, 11, 13)));

        // Kiểm tra kết quả: đây chính là lỗi cần tránh
        assertEquals(1, today.size());
    }

    private Validator validatorWith(ClockProvider clockProvider) {
        ValidatorFactory factory = Validation.byDefaultProvider().configure().clockProvider(clockProvider)
                .buildValidatorFactory();
        return factory.getValidator();
    }
}