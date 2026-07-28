package vn.techflow.manager.dashboard;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.techflow.manager.auth.AppUser;
import vn.techflow.manager.auth.AuthService;
import vn.techflow.manager.publication.PublicationRepository;
import vn.techflow.manager.task.TaskRepository;
import vn.techflow.manager.task.TaskStatus;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final TaskRepository tasks;
    private final PublicationRepository publications;
    private final AuthService authService;

    public DashboardController(TaskRepository tasks, PublicationRepository publications, AuthService authService) {
        this.tasks = tasks;
        this.publications = publications;
        this.authService = authService;
    }

    @GetMapping
    public DashboardResponse overview(Authentication authentication) {
        AppUser viewer = authService.current(authentication);
        Long ownerId = viewer.getId();
        return new DashboardResponse(
                tasks.countVisible(ownerId),
                tasks.countVisibleByStatusIn(ownerId, List.of(TaskStatus.IN_PROGRESS, TaskStatus.GENERATING)),
                tasks.countVisibleByStatus(ownerId, TaskStatus.DRAFT_REQUIRES_REVIEW),
                tasks.countVisibleByStatus(ownerId, TaskStatus.DONE),
                tasks.countVisibleByStatus(ownerId, TaskStatus.FAILED),
                publications.countVisible(ownerId)
        );
    }
}
