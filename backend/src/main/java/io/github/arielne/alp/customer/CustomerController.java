package io.github.arielne.alp.customer;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * API khách hàng (CN-07, CN-08): chỉ đọc, chỉ Giao dịch viên và Kiểm soát viên gọi được (SecurityConfig, QT-07).
 * Tìm dùng POST dù chỉ đọc, để số CCCD đi trong thân request chứ không nằm trên URL (spec khách hàng tài khoản, C1).
 */
@RestController
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping("/api/customers/search")
    public CustomerPage search(@Valid @RequestBody CustomerSearchRequest request) {
        return customerService.search(request);
    }

    @GetMapping("/api/customers/{id}")
    public CustomerDetail findById(@PathVariable Long id) {
        return customerService.findById(id);
    }
}