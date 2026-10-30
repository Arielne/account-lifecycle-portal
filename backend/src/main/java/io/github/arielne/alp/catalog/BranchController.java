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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API Chi nhánh (CN-21, màn 3.3). Quyền kiểm tra ở SecurityConfig: mọi role đọc, chỉ ADMIN ghi (QT-07).
 * Controller không tự bắt lỗi: lỗi dữ liệu và lỗi nghiệp vụ do ApiExceptionHandler trả về.
 */
@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    public List<Branch> findAll() {
        return branchService.findAll();
    }

    @GetMapping("/code-available")
    public CodeAvailability isCodeAvailable(@RequestParam String code) {
        return new CodeAvailability(branchService.isCodeAvailable(code));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Branch create(@Valid @RequestBody BranchRequest request) {
        return branchService.create(request);
    }

    @PutMapping("/{id}")
    public Branch update(@PathVariable Long id, @Valid @RequestBody BranchRequest request) {
        return branchService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        branchService.delete(id);
    }

    /** JSON trả về cho GET /api/branches/code-available: {"available": true}. */
    public record CodeAvailability(boolean available) {
    }
}