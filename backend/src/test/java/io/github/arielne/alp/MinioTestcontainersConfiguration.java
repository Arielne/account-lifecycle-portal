package io.github.arielne.alp;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Cấu hình cho test chậm có lưu file: chạy MinIO trong Docker, cùng API S3 với Supabase Storage, nên S3FileStorage
 * chạy y như thật mà không đụng Storage của Supabase.
 * Image minio/minio trên Docker Hub đã ngừng phát hành; dùng bản Chainguard dựng (Supabase cũng gợi ý bản này),
 * ghim theo digest để lần chạy nào cũng đúng một phiên bản. asCompatibleSubstituteFor báo Testcontainers đây là MinIO.
 * DynamicPropertyRegistrar: sau khi container chạy, đặt storage.s3.* trỏ vào nó.
 * Test nào lưu file thì thêm MinioTestcontainersConfiguration vào @Import và tự tạo bucket trước khi chạy.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MinioTestcontainersConfiguration {

    private static final String MINIO_IMAGE = "cgr.dev/chainguard/minio"
            + "@sha256:cee9a8e35885e93f14b3cb32960e5017aff74e2adfb52ba87c65003b0163886b";

    @Bean
    MinIOContainer minioContainer() {
        return new MinIOContainer(DockerImageName.parse(MINIO_IMAGE).asCompatibleSubstituteFor("minio/minio"));
    }

    @Bean
    DynamicPropertyRegistrar storageProperties(MinIOContainer minioContainer) {
        return registry -> {
            registry.add("storage.s3.endpoint", minioContainer::getS3URL);
            registry.add("storage.s3.access-key", minioContainer::getUserName);
            registry.add("storage.s3.secret-key", minioContainer::getPassword);
        };
    }
}