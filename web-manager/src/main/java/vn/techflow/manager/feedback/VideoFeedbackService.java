package vn.techflow.manager.feedback;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.techflow.manager.auth.AppUser;
import vn.techflow.manager.auth.AuthService;
import vn.techflow.manager.task.TaskService;
import vn.techflow.manager.task.WorkTask;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class VideoFeedbackService {
    private final VideoFeedbackRepository feedback;
    private final TaskService tasks;
    private final AuthService authService;

    public VideoFeedbackService(VideoFeedbackRepository feedback, TaskService tasks, AuthService authService) {
        this.feedback = feedback;
        this.tasks = tasks;
        this.authService = authService;
    }

    @Transactional
    public VideoFeedbackResponse save(Long taskId, VideoFeedbackRequest request, Authentication authentication) {
        WorkTask task = tasks.getAccessible(taskId, authentication);
        AppUser owner = authService.current(authentication);
        VideoFeedback item = feedback.findByOwnerIdAndTaskId(owner.getId(), taskId).orElseGet(VideoFeedback::new);
        if (item.getOwner() == null) item.setOwner(owner);
        if (item.getTask() == null) item.setTask(task);
        item.setRating(request.rating());
        item.setAspects(clean(request.aspects()));
        item.setComment(clean(request.comment()));
        return VideoFeedbackResponse.from(feedback.save(item));
    }

    @Transactional(readOnly = true)
    public Optional<VideoFeedbackResponse> mine(Long taskId, Authentication authentication) {
        tasks.getAccessible(taskId, authentication);
        AppUser owner = authService.current(authentication);
        return feedback.findByOwnerIdAndTaskId(owner.getId(), taskId).map(VideoFeedbackResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<VideoFeedbackResponse> search(Integer rating, int page, int size) {
        Integer safeRating = rating != null && rating >= 1 && rating <= 5 ? rating : null;
        return feedback.search(safeRating, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)))
                .map(VideoFeedbackResponse::from);
    }

    @Transactional(readOnly = true)
    public FeedbackSummary summary() {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) distribution.put(star, feedback.countByRating(star));
        return new FeedbackSummary(feedback.count(), Math.round(feedback.averageRating() * 100.0) / 100.0,
                distribution);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
