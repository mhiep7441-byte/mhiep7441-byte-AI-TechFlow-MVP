package vn.techflow.manager.campaign;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.techflow.manager.task.*;

import java.util.List;

@Service
public class CampaignService {
    private static final List<String> EPISODE_ANGLES = List.of(
            "Bối cảnh và vấn đề người xem thường gặp",
            "Khái niệm cốt lõi cần hiểu đúng",
            "Quy trình thực hành từng bước",
            "Sai lầm phổ biến và cách tránh",
            "Ví dụ ứng dụng thực tế",
            "Checklist đánh giá kết quả",
            "Góc nhìn chuyên sâu và giới hạn",
            "Tổng kết hành trình và bước tiếp theo"
    );

    private final CampaignRepository campaigns;
    private final TaskRepository tasks;

    public CampaignService(CampaignRepository campaigns, TaskRepository tasks) {
        this.campaigns = campaigns;
        this.tasks = tasks;
    }

    public List<Campaign> all() {
        return campaigns.findAllByOrderByUpdatedAtDesc();
    }

    public Campaign get(Long id) {
        return campaigns.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy campaign " + id));
    }

    public Campaign save(Campaign campaign, CampaignRequest request) {
        campaign.setName(request.name().trim());
        campaign.setTheme(request.theme().trim());
        campaign.setDescription(request.description() == null ? "" : request.description().trim());
        campaign.setEpisodeCount(request.episodeCount() == null ? 5 : request.episodeCount());
        campaign.setTargetDurationSeconds(request.targetDurationSeconds() == null ? 60 : request.targetDurationSeconds());
        campaign.setVisualStyle(request.visualStyle() == null ? "" : request.visualStyle().trim());
        campaign.setCharacterDescription(request.characterDescription() == null ? "" : request.characterDescription().trim());
        campaign.setStatus(request.status() == null ? CampaignStatus.PLANNING : request.status());
        return campaigns.save(campaign);
    }

    @Transactional
    public List<WorkTask> createEpisodes(Long id) {
        Campaign campaign = get(id);
        List<WorkTask> existing = tasks.findByCampaignIdOrderByEpisodeNumberAsc(id);
        if (!existing.isEmpty()) {
            return existing;
        }
        for (int episode = 1; episode <= campaign.getEpisodeCount(); episode++) {
            String angle = EPISODE_ANGLES.get((episode - 1) % EPISODE_ANGLES.size());
            WorkTask task = new WorkTask();
            task.setTitle(campaign.getName() + " — Tập " + episode);
            task.setTopic(campaign.getTheme() + ". Tập " + episode + "/" + campaign.getEpisodeCount()
                    + " tập trung vào: " + angle + ". Không lặp lại nội dung của các tập khác.");
            task.setDescription(campaign.getDescription());
            task.setPriority(Priority.MEDIUM);
            task.setStatus(TaskStatus.TODO);
            task.setCampaignId(id);
            task.setEpisodeNumber(episode);
            task.setTargetDurationSeconds(campaign.getTargetDurationSeconds());
            task.setVisualStyle(campaign.getVisualStyle());
            task.setCharacterDescription(campaign.getCharacterDescription());
            tasks.save(task);
        }
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaigns.save(campaign);
        return tasks.findByCampaignIdOrderByEpisodeNumberAsc(id);
    }

    public void delete(Long id) {
        campaigns.delete(get(id));
    }
}
