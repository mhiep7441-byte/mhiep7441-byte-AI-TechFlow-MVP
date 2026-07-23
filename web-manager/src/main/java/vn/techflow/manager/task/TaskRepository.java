package vn.techflow.manager.task;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskRepository extends JpaRepository<WorkTask, Long> {
    List<WorkTask> findAllByOrderByUpdatedAtDesc();
    long countByStatus(TaskStatus status);
    long countByStatusIn(List<TaskStatus> statuses);
    List<WorkTask> findByCampaignIdOrderByEpisodeNumberAsc(Long campaignId);
}
