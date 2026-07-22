package vn.techflow.manager.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Email @Size(max = 190) String email,
        @NotBlank @Size(min = 10, max = 72) String password
) {}
