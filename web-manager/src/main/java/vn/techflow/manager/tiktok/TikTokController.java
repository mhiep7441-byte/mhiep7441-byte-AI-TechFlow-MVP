package vn.techflow.manager.tiktok;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Tag(name = "TikTok")
@RestController
public class TikTokController {
    private final TikTokService service;

    public TikTokController(TikTokService service) { this.service = service; }

    @GetMapping("/api/tiktok/status")
    public TikTokConnectionStatus status(Authentication authentication) {
        return service.status(authentication);
    }

    @Operation(summary = "Bắt đầu TikTok OAuth")
    @GetMapping("/api/tiktok/connect")
    public void connect(Authentication authentication, HttpSession session, HttpServletResponse response)
            throws IOException {
        response.sendRedirect(service.authorizationUri(authentication, session).toString());
    }

    @GetMapping("/api/tiktok/creator-info")
    public TikTokCreatorInfo creatorInfo(Authentication authentication) {
        return service.creatorInfo(authentication);
    }

    @GetMapping("/oauth/tiktok/callback")
    public void callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            Authentication authentication,
            HttpSession session,
            HttpServletResponse response) throws IOException {
        if (error != null || code == null || state == null || authentication == null) {
            response.sendRedirect("/videos?tiktok=error");
            return;
        }
        try {
            service.completeAuthorization(code, state, authentication, session);
            response.sendRedirect("/videos?tiktok=connected");
        } catch (RuntimeException exception) {
            response.sendRedirect("/videos?tiktok=error&reason=oauth_failed");
        }
    }

    @Operation(summary = "Gửi video đã duyệt tới TikTok Direct Post")
    @PostMapping("/api/tasks/{id}/publish/tiktok")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TikTokPublishResponse publish(
            @PathVariable Long id,
            @Valid @RequestBody TikTokPublishRequest request,
            Authentication authentication) {
        return service.publish(id, request, authentication);
    }

    @Operation(summary = "Đồng bộ trạng thái xử lý TikTok")
    @PostMapping("/api/publications/{id}/tiktok/refresh")
    public TikTokPublishStatusResponse refresh(
            @PathVariable Long id,
            Authentication authentication) {
        return service.refreshPublishStatus(id, authentication);
    }
}
