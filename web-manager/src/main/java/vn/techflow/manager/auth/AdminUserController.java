package vn.techflow.manager.auth;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserService service;

    public AdminUserController(AdminUserService service) { this.service = service; }

    @GetMapping
    public Page<UserSummary> search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) UserRole role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(query, role, page, size);
    }

    @PutMapping("/{id}")
    public UserSummary update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return service.update(id, request);
    }
}
