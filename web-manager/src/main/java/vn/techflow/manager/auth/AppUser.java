package vn.techflow.manager.auth;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "app_users")
public class AppUser implements UserDetails {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 190)
    private String email;
    @Column(nullable = false, length = 120)
    private String displayName;
    @Column(length = 255)
    private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;
    @Column(nullable = false)
    private boolean enabled = true;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private AuthProvider authProvider = AuthProvider.LOCAL;
    @Column(length = 1000)
    private String avatarUrl;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist void create() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return enabled; }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public AuthProvider getAuthProvider() { return authProvider; }
    public void setAuthProvider(AuthProvider authProvider) { this.authProvider = authProvider; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
