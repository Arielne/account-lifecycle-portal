package io.github.arielne.alp.common;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * Tạo S3Client để gọi API S3 của Supabase Storage (giá trị trong .env, biến STORAGE_S3_*).
 * - forcePathStyle: địa chỉ dạng endpoint/bucket/key; Supabase và MinIO không hỗ trợ dạng bucket.endpoint.
 * - Checksum WHEN_REQUIRED: AWS SDK bản mới mặc định gửi thêm checksum mà dịch vụ ngoài AWS có thể không hiểu;
 *   chỉ gửi khi API bắt buộc.
 * - Chưa khai endpoint, khóa (CI, test không lưu file) thì vẫn tạo được client để app khởi động;
 *   lần lưu file đầu tiên mới báo lỗi.
 */
@Configuration
public class StorageConfig {

    @Bean
    public S3Client s3Client(@Value("${storage.s3.endpoint}") String endpoint,
                             @Value("${storage.s3.region}") String region,
                             @Value("${storage.s3.access-key}") String accessKey,
                             @Value("${storage.s3.secret-key}") String secretKey) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsOf(accessKey, secretKey))
                .forcePathStyle(true)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    /** Có đủ khóa thì ký request bằng khóa đó; thiếu khóa thì gửi không ký (Storage sẽ từ chối). */
    private AwsCredentialsProvider credentialsOf(String accessKey, String secretKey) {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            return AnonymousCredentialsProvider.create();
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }
}