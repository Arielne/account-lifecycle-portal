package io.github.arielne.alp.catalog;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API Loại tài khoản (CN-20, màn 3.2). Quyền kiểm tra ở SecurityConfig: mọi role đọc, chỉ ADMIN ghi (QT-07).
 * Controller không tự bắt lỗi: lỗi dữ liệu và lỗi nghiệp vụ do ApiExceptionHandler trả về.
 */
@RestController
@RequestMapping("/api/account-types")
@RequiredArgsConstructor
public class AccountTypeController {

    private final AccountTypeService accountTypeService;

    @GetMapping
    public List<AccountType> findAll() {
        return accountTypeService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountType create(@Valid @RequestBody AccountTypeCreateRequest request) {
        return accountTypeService.create(request);
    }

    @PutMapping("/{id}")
    public AccountType update(@PathVariable Long id, @Valid @RequestBody AccountTypeUpdateRequest request) {
        return accountTypeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        accountTypeService.delete(id);
    }
}