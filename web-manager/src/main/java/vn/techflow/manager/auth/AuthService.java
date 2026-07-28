package vn.techflow.manager.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService implements UserDetailsService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminName;

    public AuthService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${techflow.bootstrap-admin.email:}") String adminEmail,
            @Value("${techflow.bootstrap-admin.password:}") String adminPassword,
            @Value("${techflow.bootstrap-admin.name:TechFlow Admin}") String adminName) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        this.adminPassword = adminPassword;
        this.adminName = adminName.trim();
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return users.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản"));
    }

    @Transactional
    public AppUser register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email đã được sử dụng");
        }
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        user.setAuthProvider(AuthProvider.LOCAL);
        return users.save(user);
    }

    @Transactional
    public AppUser current(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UsernameNotFoundException("Bạn chưa đăng nhập");
        }
        if (authentication.getPrincipal() instanceof AppUser appUser) {
            AppUser user = users.findById(appUser.getId()).orElseThrow(() -> new UsernameNotFoundException("Tài khoản không tồn tại"));
            return ensureEnabled(user);
        }
        if (authentication instanceof OAuth2AuthenticationToken oauth) {
            Object emailAttribute = oauth.getPrincipal().getAttribute("email");
            if (!(emailAttribute instanceof String emailValue) || emailValue.isBlank()) {
                throw new IllegalArgumentException("Google không cung cấp email cho ứng dụng");
            }
            String email = emailValue.trim().toLowerCase(Locale.ROOT);
            Object nameAttribute = oauth.getPrincipal().getAttribute("name");
            String name = nameAttribute instanceof String value ? value.trim() : "";
            Object pictureAttribute = oauth.getPrincipal().getAttribute("picture");
            String picture = pictureAttribute instanceof String value ? value : null;
            AppUser existing = users.findByEmailIgnoreCase(email).orElse(null);
            if (existing != null) {
                ensureEnabled(existing);
                existing.setDisplayName(name.isBlank() ? existing.getDisplayName() : name);
                existing.setAvatarUrl(picture);
                return users.save(existing);
            }
            return users.save(newGoogleUser(email, name, picture));
        }
        AppUser user = users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Tài khoản không tồn tại"));
        return ensureEnabled(user);
    }

    public boolean isAdmin(Authentication authentication) {
        return current(authentication).getRole() == UserRole.ADMIN;
    }

    @Transactional
    public AppUser updateProfile(Authentication authentication, ProfileUpdateRequest request) {
        AppUser user = current(authentication);
        user.setDisplayName(request.displayName().trim());
        return users.save(user);
    }

    private static AppUser ensureEnabled(AppUser user) {
        if (!user.isEnabled()) throw new DisabledException("Tài khoản đã bị khóa");
        return user;
    }

    private static AppUser newGoogleUser(String email, String name, String picture) {
        AppUser created = new AppUser();
        created.setEmail(email);
        created.setDisplayName(name.isBlank() ? email : name);
        created.setAvatarUrl(picture);
        created.setAuthProvider(AuthProvider.GOOGLE);
        return created;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrapAdmin() {
        if (adminEmail.isBlank() || adminPassword.isBlank()) return;
        AppUser admin = users.findByEmailIgnoreCase(adminEmail).orElseGet(AppUser::new);
        admin.setEmail(adminEmail);
        admin.setDisplayName(adminName.isBlank() ? "TechFlow Admin" : adminName);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(UserRole.ADMIN);
        admin.setEnabled(true);
        admin.setAuthProvider(AuthProvider.LOCAL);
        users.save(admin);
    }
}
