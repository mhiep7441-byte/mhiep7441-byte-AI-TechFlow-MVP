package vn.techflow.manager.auth;

public record AuthResponse(
        Long id,
        String email,
        String displayName,
        UserRole role,
        String avatarUrl,
        AuthProvider provider,
        String csrfToken
) {
    public static AuthResponse from(AppUser user, String csrfToken) {
        return new AuthResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
                user.getAvatarUrl(), user.getAuthProvider(), csrfToken);
    }
}
