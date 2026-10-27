package io.github.arielne.alp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Điểm khởi động của backend Account Lifecycle Portal.
 * Class nằm ở package gốc io.github.arielne.alp vì Spring chỉ tự quét các class
 * ở package này và package con; các module (catalog, account, ticket...) đều nằm bên dưới.
 */
@SpringBootApplication
public class AccountLifecyclePortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountLifecyclePortalApplication.class, args);
    }
}