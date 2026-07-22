package vn.techflow.manager.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @NotBlank @Size(max = 120) String displayName,
        @NotNull UserRole role,
        boolean enabled
) {}
