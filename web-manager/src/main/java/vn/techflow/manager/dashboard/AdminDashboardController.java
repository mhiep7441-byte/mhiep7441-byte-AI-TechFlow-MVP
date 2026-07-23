package vn.techflow.manager.dashboard;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.techflow.manager.auth.UserRepository;
import vn.techflow.manager.campaign.CampaignRepository;
import vn.techflow.manager.campaign.CampaignStatus;
import vn.techflow.manager.publication.PublicationRepository;
import vn.techflow.manager.publication.PublicationStatus;
import vn.techflow.manager.task.TaskRepository;
import vn.techflow.manager.task.TaskStatus;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {
    private final UserRepository users;
    private final CampaignRepository campaigns;
    private final TaskRepository tasks;
    private final PublicationRepository publications;

    public AdminDashboardController(UserRepository users, CampaignRepository campaigns,
                                    TaskRepository tasks, PublicationRepository publications) {
        this.users = users;
        this.campaigns = campaigns;
        this.tasks = tasks;
        this.publications = publications;
    }

    @GetMapping
    public AdminDashboardResponse overview() {
        return new AdminDashboardResponse(
                users.count(),
                users.countByEnabledTrue(),
                campaigns.count(),
                campaigns.countByStatus(CampaignStatus.ACTIVE),
                campaigns.countByProductionEnabledTrue(),
                tasks.count(),
                tasks.countVisibleByStatus(null, TaskStatus.GENERATING),
                tasks.countVisibleByStatus(null, TaskStatus.DRAFT_REQUIRES_REVIEW),
                tasks.countVisibleByStatus(null, TaskStatus.DONE),
                tasks.countVisibleByStatus(null, TaskStatus.FAILED),
                publications.count(),
                publications.countByStatus(PublicationStatus.PUBLISHED)
        );
    }
}
