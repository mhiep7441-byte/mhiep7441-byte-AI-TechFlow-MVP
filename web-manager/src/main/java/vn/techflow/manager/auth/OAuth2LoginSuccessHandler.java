package vn.techflow.manager.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final AuthService authService;

    public OAuth2LoginSuccessHandler(AuthService authService) { this.authService = authService; }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        authService.current(authentication);
        response.sendRedirect("/");
    }
}
