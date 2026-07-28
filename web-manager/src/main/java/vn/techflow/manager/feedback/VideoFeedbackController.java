package vn.techflow.manager.feedback;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class VideoFeedbackController {
    private final VideoFeedbackService service;

    public VideoFeedbackController(VideoFeedbackService service) { this.service = service; }

    @PutMapping("/api/tasks/{taskId}/feedback")
    public VideoFeedbackResponse save(@PathVariable Long taskId,
                                      @Valid @RequestBody VideoFeedbackRequest request,
                                      Authentication authentication) {
        return service.save(taskId, request, authentication);
    }

    @GetMapping("/api/tasks/{taskId}/feedback")
    public ResponseEntity<VideoFeedbackResponse> mine(@PathVariable Long taskId, Authentication authentication) {
        return service.mine(taskId, authentication).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/api/admin/feedback")
    public Page<VideoFeedbackResponse> search(@RequestParam(required = false) Integer rating,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return service.search(rating, page, size);
    }

    @GetMapping("/api/admin/feedback/summary")
    public FeedbackSummary summary() {
        return service.summary();
    }
}
