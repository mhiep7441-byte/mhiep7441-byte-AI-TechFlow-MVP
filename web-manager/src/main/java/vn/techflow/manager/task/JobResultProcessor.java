package vn.techflow.manager.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.techflow.manager.campaign.Campaign;
import vn.techflow.manager.campaign.CampaignRepository;
import vn.techflow.manager.task.entity.GenerationJob;
import vn.techflow.manager.task.repository.GenerationJobRepository;

import java.util.List;

@Service
public class JobResultProcessor {
    private static final Logger log = LoggerFactory.getLogger(JobResultProcessor.class);

    private final GenerationJobRepository jobRepository;
    private final CampaignRepository campaignRepository;
    private final TaskRepository taskRepository;
    private final ObjectMapper json;

    public JobResultProcessor(GenerationJobRepository jobRepository, CampaignRepository campaignRepository, TaskRepository taskRepository, ObjectMapper json) {
        this.jobRepository = jobRepository;
        this.campaignRepository = campaignRepository;
        this.taskRepository = taskRepository;
        this.json = json;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processCompletedJobs() {
        List<GenerationJob> completedJobs = jobRepository.findAll().stream()
                .filter(job -> "COMPLETED".equals(job.getStatus()))
                .toList();

        for (GenerationJob job : completedJobs) {
            try {
                if ("GENERATE_CHARACTER".equals(job.getJobType()) && job.getOutputJson() != null) {
                    JsonNode output = json.readTree(job.getOutputJson());
                    if (output.has("character_image_url") && job.getInputJson() != null) {
                        JsonNode input = json.readTree(job.getInputJson());
                        Long campaignId = input.get("campaign_id").asLong();
                        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
                        if (campaign != null) {
                            campaign.setCharacterImageUrl(output.get("character_image_url").asText());
                            campaignRepository.save(campaign);
                        }
                    }
                } else if ("GENERATE_SCRIPT".equals(job.getJobType()) && job.getOutputJson() != null) {
                    JsonNode output = json.readTree(job.getOutputJson());
                    JsonNode input = json.readTree(job.getInputJson());
                    Long episodeId = input.get("episode_id").asLong();
                    WorkTask task = taskRepository.findById(episodeId).orElse(null);
                    
                    if (task != null) {
                        task.setWorkflowState("SCRIPT_COMPLETED");
                        task.setStatus(TaskStatus.DRAFT_REQUIRES_REVIEW);
                        if (output.has("research")) task.setResearchJson(output.get("research").toString());
                        if (output.has("storyboard")) task.setStoryboardJson(output.get("storyboard").toString());
                        
                        if (task.getCaption() == null || task.getCaption().isBlank()) {
                            task.setCaption(output.has("caption") && !output.get("caption").asText().isBlank() ? output.get("caption").asText() : task.getTitle());
                        }
                        if (task.getHashtags() == null || task.getHashtags().isBlank()) {
                            task.setHashtags(output.has("hashtags") && !output.get("hashtags").asText().isBlank() ? output.get("hashtags").asText() : "#techflow");
                        }
                        taskRepository.save(task);
                    }
                } else if ("GENERATE_VIDEO".equals(job.getJobType()) && job.getOutputJson() != null) {
                    JsonNode output = json.readTree(job.getOutputJson());
                    JsonNode input = json.readTree(job.getInputJson());
                    Long episodeId = input.get("episode_id").asLong();
                    WorkTask task = taskRepository.findById(episodeId).orElse(null);
                    
                    if (task != null) {
                        task.setWorkflowState("COMPLETED");
                        task.setStatus(TaskStatus.DONE);
                        if (output.has("video_url")) task.setOutputPath(output.get("video_url").asText());
                        if (output.has("script_url")) task.setScriptUrl(output.get("script_url").asText());
                        
                        taskRepository.save(task);
                    }
                }
                
                // Mark job as processed
                job.setStatus("PROCESSED");
                jobRepository.save(job);
            } catch (Exception e) {
                log.error("Failed to process completed job {}", job.getId(), e);
                job.setStatus("FAILED");
                job.setErrorMessage(e.getMessage());
                jobRepository.save(job);
            }
        }
        
        // Also handle failed jobs
        List<GenerationJob> failedJobs = jobRepository.findAll().stream()
                .filter(job -> "FAILED".equals(job.getStatus()))
                .toList();
                
        for (GenerationJob job : failedJobs) {
             if ("GENERATE_VIDEO".equals(job.getJobType()) && job.getInputJson() != null) {
                 try {
                     JsonNode input = json.readTree(job.getInputJson());
                     Long episodeId = input.get("episode_id").asLong();
                     WorkTask task = taskRepository.findById(episodeId).orElse(null);
                     if (task != null && !"FAILED".equals(task.getWorkflowState())) {
                         task.setWorkflowState("FAILED");
                         task.setStatus(TaskStatus.ERROR);
                         task.setErrorMessage("Lỗi từ worker: " + job.getErrorMessage());
                         taskRepository.save(task);
                     }
                 } catch (Exception e) {
                     log.error("Failed to process failed job {}", job.getId(), e);
                 }
             }
             // Leave job in FAILED status, maybe mark as ACKNOWLEDGED later
        }
    }
}
