package vn.techflow.manager.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final AuthService authService;
    private final ObjectProvider<ClientRegistrationRepository> registrations;

    public AuthController(AuthenticationManager authenticationManager, AuthService authService,
                          ObjectProvider<ClientRegistrationRepository> registrations) {
        this.authenticationManager = authenticationManager;
        this.authService = authService;
        this.registrations = registrations;
    }

    @GetMapping("/config")
    public Map<String, Boolean> config() {
        return Map.of("googleEnabled", registrations.getIfAvailable() != null);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest,
                              CsrfToken csrfToken) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email().trim(), request.password()));
        persist(authentication, servletRequest);
        return AuthResponse.from(authService.current(authentication), csrfToken.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest,
                                 CsrfToken csrfToken) {
        authService.register(request);
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email().trim(), request.password()));
        persist(authentication, servletRequest);
        return AuthResponse.from(authService.current(authentication), csrfToken.getToken());
    }

    @GetMapping("/me")
    public AuthResponse me(Authentication authentication, CsrfToken csrfToken) {
        return AuthResponse.from(authService.current(authentication), csrfToken.getToken());
    }

    @PutMapping("/profile")
    public AuthResponse updateProfile(@Valid @RequestBody ProfileUpdateRequest request,
                                      Authentication authentication, CsrfToken csrfToken) {
        return AuthResponse.from(authService.updateProfile(authentication, request), csrfToken.getToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
    }

    private static void persist(Authentication authentication, HttpServletRequest request) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }
}
