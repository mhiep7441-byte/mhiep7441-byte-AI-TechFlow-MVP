package vn.techflow.manager.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUserService {
    private final UserRepository users;

    public AdminUserService(UserRepository users) { this.users = users; }

    @Transactional(readOnly = true)
    public Page<UserSummary> search(String query, UserRole role, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return users.search(query == null ? "" : query.trim(), role,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(UserSummary::from);
    }

    @Transactional
    public UserSummary update(Long id, UserUpdateRequest request) {
        AppUser user = users.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
        boolean removesActiveAdmin = user.getRole() == UserRole.ADMIN && user.isEnabled()
                && (request.role() != UserRole.ADMIN || !request.enabled());
        if (removesActiveAdmin && users.countByRoleAndEnabledTrue(UserRole.ADMIN) <= 1) {
            throw new IllegalArgumentException("Không thể khóa hoặc hạ quyền quản trị viên cuối cùng");
        }
        user.setDisplayName(request.displayName().trim());
        user.setRole(request.role());
        user.setEnabled(request.enabled());
        return UserSummary.from(users.save(user));
    }
}
