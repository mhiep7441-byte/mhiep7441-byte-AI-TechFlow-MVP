package vn.techflow.manager.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.techflow.manager.publication.PublicationRepository;
import vn.techflow.manager.task.TaskRepository;
import vn.techflow.manager.task.TaskStatus;

@Tag(name = "Dashboard")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final TaskRepository tasks;
    private final PublicationRepository publications;

    public DashboardController(TaskRepository tasks, PublicationRepository publications) {
        this.tasks = tasks;
        this.publications = publications;
    }

    @Operation(summary = "Số liệu tổng quan")
    @GetMapping
    public DashboardResponse overview() {
        return new DashboardResponse(
                tasks.count(),
                tasks.countByStatusIn(java.util.List.of(TaskStatus.IN_PROGRESS, TaskStatus.GENERATING)),
                tasks.countByStatus(TaskStatus.DRAFT_REQUIRES_REVIEW),
                tasks.countByStatus(TaskStatus.DONE),
                tasks.countByStatus(TaskStatus.FAILED),
                publications.count()
        );
    }
}
