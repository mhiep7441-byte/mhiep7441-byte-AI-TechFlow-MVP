package vn.techflow.manager.auth;

import java.time.LocalDateTime;

public record UserSummary(Long id, String email, String displayName, UserRole role, boolean enabled,
                          AuthProvider provider, String avatarUrl, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static UserSummary from(AppUser user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(), user.isEnabled(),
                user.getAuthProvider(), user.getAvatarUrl(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
