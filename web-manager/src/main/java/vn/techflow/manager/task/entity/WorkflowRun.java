package vn.techflow.manager.task.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "workflow_runs")
public class WorkflowRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id")
    private Long campaignId;

    @Column(name = "episode_id")
    private Long episodeId;

    @Column(nullable = false)
    private String status = "CREATED";

    @Column(name = "current_step")
    private String currentStep;

    private Integer progress = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCampaignId() { return campaignId; }
    public void setCampaignId(Long campaignId) { this.campaignId = campaignId; }
    public Long getEpisodeId() { return episodeId; }
    public void setEpisodeId(Long episodeId) { this.episodeId = episodeId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCurrentStep() { return currentStep; }
    public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }
}
