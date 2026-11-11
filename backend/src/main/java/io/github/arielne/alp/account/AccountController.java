package io.github.arielne.alp.account;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * API tài khoản (CN-09, CN-10): chỉ đọc, chỉ Giao dịch viên và Kiểm soát viên gọi được (SecurityConfig, QT-07).
 * @ModelAttribute ghép tham số trên URL vào record AccountQuery; @Valid kiểm số tài khoản chỉ gồm chữ số.
 */
@RestController
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/api/accounts")
    public AccountPage findPage(@Valid @ModelAttribute AccountQuery query) {
        return accountService.findPage(query);
    }

    @GetMapping("/api/accounts/{id}")
    public AccountItem findById(@PathVariable Long id) {
        return accountService.findById(id);
    }

    /** CN-10: Vòng đời tài khoản (spec phiếu mục 10.6). */
    @GetMapping("/api/accounts/{id}/status-history")
    public List<AccountStatusHistoryItem> statusHistory(@PathVariable Long id) {
        return accountService.findStatusHistory(id);
    }
}