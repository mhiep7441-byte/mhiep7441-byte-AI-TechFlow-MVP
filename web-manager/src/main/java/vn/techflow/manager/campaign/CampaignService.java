package vn.techflow.manager.campaign;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.techflow.manager.auth.AppUser;
import vn.techflow.manager.auth.AuthService;
import vn.techflow.manager.auth.UserRole;
import vn.techflow.manager.task.*;

import java.util.ArrayList;
import java.util.List;

@Service
public class CampaignService {
    private static final List<String> ANGLES = List.of(
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
    private final AuthService authService;

    public CampaignService(CampaignRepository campaigns, TaskRepository tasks, AuthService authService) {
        this.campaigns = campaigns;
        this.tasks = tasks;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public Page<Campaign> search(String query, int page, int size, Authentication authentication) {
        AppUser viewer = authService.current(authentication);
        Long ownerId = viewer.getRole() == UserRole.ADMIN ? null : viewer.getId();
        return campaigns.search(ownerId, clean(query),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                        Sort.by(Sort.Direction.DESC, "updatedAt")));
    }

    @Transactional
    public Campaign create(CampaignRequest request, Authentication authentication) {
        Campaign campaign = new Campaign();
        campaign.setOwner(authService.current(authentication));
        apply(campaign, request);
        return campaigns.save(campaign);
    }

    @Transactional
    public Campaign update(Long id, CampaignRequest request, Authentication authentication) {
        Campaign campaign = accessible(id, authentication);
        apply(campaign, request);
        return campaigns.save(campaign);
    }

    @Transactional
    public List<WorkTask> createEpisodes(Long id, Authentication authentication) {
        Campaign campaign = accessible(id, authentication);
        List<WorkTask> existing = tasks.findByCampaignIdOrderByEpisodeNumberAsc(id);
        if (!existing.isEmpty()) return existing;

        List<WorkTask> episodes = new ArrayList<>();
        for (int number = 1; number <= campaign.getEpisodeCount(); number++) {
            String angle = ANGLES.get((number - 1) % ANGLES.size());
            WorkTask task = new WorkTask();
            task.setOwner(campaign.getOwner());
            task.setTitle(limit(campaign.getName() + " — Tập " + number, 160));
            task.setTopic(limit(campaign.getTheme() + ". Tập " + number + "/" + campaign.getEpisodeCount()
                    + " tập trung vào: " + angle + ". Không lặp lại nội dung của tập khác.", 500));
            task.setDescription(campaign.getDescription());
            task.setCampaignId(id);
            task.setEpisodeNumber(number);
            task.setTargetDurationSeconds(campaign.getTargetDurationSeconds());
            task.setVisualStyle(campaign.getVisualStyle());
            task.setCharacterDescription(campaign.getCharacterDescription());
            task.setPriority(Priority.MEDIUM);
            task.setStatus(TaskStatus.TODO);
            episodes.add(task);
        }
        tasks.saveAll(episodes);
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaigns.save(campaign);
        return tasks.findByCampaignIdOrderByEpisodeNumberAsc(id);
    }

    @Transactional
    public void delete(Long id, Authentication authentication) {
        campaigns.delete(accessible(id, authentication));
    }

    private Campaign accessible(Long id, Authentication authentication) {
        Campaign campaign = campaigns.findWithOwnerById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy campaign"));
        AppUser viewer = authService.current(authentication);
        if (viewer.getRole() != UserRole.ADMIN && !viewer.getId().equals(campaign.getOwner().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập campaign này");
        }
        return campaign;
    }

    private static void apply(Campaign campaign, CampaignRequest request) {
        campaign.setName(request.name().trim());
        campaign.setTheme(request.theme().trim());
        campaign.setDescription(clean(request.description()));
        campaign.setEpisodeCount(request.episodeCount() == null ? 5 : request.episodeCount());
        campaign.setTargetDurationSeconds(request.targetDurationSeconds() == null ? 60 : request.targetDurationSeconds());
        campaign.setVisualStyle(clean(request.visualStyle()));
        campaign.setCharacterDescription(clean(request.characterDescription()));
        campaign.setStatus(request.status() == null ? CampaignStatus.PLANNING : request.status());
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String limit(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
