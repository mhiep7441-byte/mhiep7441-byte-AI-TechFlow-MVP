package vn.techflow.manager.campaign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.techflow.manager.task.TaskService;

import java.time.LocalDateTime;

@Component
public class CampaignProductionScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CampaignProductionScheduler.class);
    private final CampaignService campaigns;
    private final TaskService tasks;

    public CampaignProductionScheduler(CampaignService campaigns, TaskService tasks) {
        this.campaigns = campaigns;
        this.tasks = tasks;
    }

    @Scheduled(fixedDelayString = "${techflow.campaign-scheduler-delay-ms:60000}")
    public void produceDueDrafts() {
        for (Long taskId : campaigns.claimDueBatch(LocalDateTime.now(), 3)) {
            LOGGER.info("Campaign scheduler queued draft generation for task {}", taskId);
            tasks.generate(taskId);
        }
    }
}
