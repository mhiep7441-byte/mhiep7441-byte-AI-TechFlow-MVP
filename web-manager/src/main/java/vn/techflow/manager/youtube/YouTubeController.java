package vn.techflow.manager.youtube;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Tag(name = "YouTube")
@RestController
public class YouTubeController {
    private final YouTubeService service;

    public YouTubeController(YouTubeService service) { this.service = service; }

    @GetMapping("/api/youtube/status")
    public YouTubeConnectionStatus status(Authentication authentication) {
        return service.status(authentication);
    }

    @GetMapping("/api/youtube/connect")
    public void connect(Authentication authentication, HttpSession session,
                        HttpServletResponse response) throws IOException {
        response.sendRedirect(service.authorizationUri(authentication, session).toString());
    }

    @GetMapping("/oauth/youtube/callback")
    public void callback(@RequestParam(required = false) String code,
                         @RequestParam(required = false) String state,
                         @RequestParam(required = false) String error,
                         Authentication authentication,
                         HttpSession session,
                         HttpServletResponse response) throws IOException {
        if (error != null || code == null || state == null || authentication == null) {
            response.sendRedirect("/profile?youtube=error");
            return;
        }
        try {
            service.completeAuthorization(code, state, authentication, session);
            response.sendRedirect("/profile?youtube=connected");
        } catch (RuntimeException exception) {
            response.sendRedirect("/profile?youtube=error");
        }
    }

    @DeleteMapping("/api/youtube/connection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(Authentication authentication) {
        service.disconnect(authentication);
    }

    @Operation(summary = "Upload video đã được người dùng duyệt lên YouTube")
    @PostMapping("/api/tasks/{id}/publish/youtube")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public YouTubePublishResponse publish(@PathVariable Long id,
                                          @Valid @RequestBody YouTubePublishRequest request,
                                          Authentication authentication) {
        return service.publish(id, request, authentication);
    }
}
