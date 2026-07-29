package vn.techflow.manager.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import vn.techflow.manager.auth.AppUser;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "work_tasks")
public class WorkTask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(nullable = false, length = 2000)
    private String description = "";
    @Column(nullable = false, length = 500)
    private String topic = "";
    @Column(nullable = false, length = 2200)
    private String caption = "";
    @Column(nullable = false, length = 500)
    private String hashtags = "";
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private TaskStatus status = TaskStatus.TODO;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Priority priority = Priority.MEDIUM;
    private LocalDate dueDate;
    @Column(length = 1000)
    private String outputPath;
    @Column(length = 4000)
    private String errorMessage;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String researchJson = "{}";
    @Column(nullable = false, columnDefinition = "TEXT")
    private String storyboardJson = "{}";
    @Column(nullable = false, columnDefinition = "TEXT")
    private String sourceUrls = "";
    @Column(nullable = false, length = 40)
    private String factCheckStatus = "NOT_CHECKED";
    private Integer qualityScore;
    @Column(nullable = false)
    private Integer targetDurationSeconds = 60;
    private Long campaignId;
    private Integer episodeNumber;
    @Column(nullable = false, length = 30)
    private String aiProvider = "";
    @Column(nullable = false, length = 240)
    private String visualStyle = "";
    @Column(nullable = false, length = 240)
    private String characterDescription = "";
    @Column(nullable = false, length = 30)
    private String audioMode = "narrated";
    @Column(nullable = false, length = 30)
    private String videoProvider = "kenburns";
    @Column(nullable = false, length = 10)
    private String aspectRatio = "9:16";
    @Column(nullable = false, length = 20)
    private String renderQuality = "draft";
    @Column(columnDefinition = "TEXT")
    private String characterImageUrl;
    @Column(columnDefinition = "TEXT")
    private String scriptUrl;
    @Column(columnDefinition = "TEXT")
    private String storyboardUrl;
    @Column(columnDefinition = "TEXT")
    private String scenePromptsUrl;
    @Column(columnDefinition = "TEXT")
    private String imageSetUrl;
    @Column(columnDefinition = "TEXT")
    private String narrationUrl;
    @Column(columnDefinition = "TEXT")
    private String subtitleUrl;
    @Column(columnDefinition = "TEXT")
    private String projectArchiveUrl;
    @Column(columnDefinition = "TEXT")
    private String assetManifestUrl;
    @Column(length = 80)
    private String workflowState = "OUTLINE_READY";
    @JsonIgnore @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist void create() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public String getHashtags() { return hashtags; }
    public void setHashtags(String hashtags) { this.hashtags = hashtags; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getOutputPath() { return outputPath; }
    public void setOutputPath(String outputPath) { this.outputPath = outputPath; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getResearchJson() { return researchJson; }
    public void setResearchJson(String researchJson) { this.researchJson = researchJson; }
    public String getStoryboardJson() { return storyboardJson; }
    public void setStoryboardJson(String storyboardJson) { this.storyboardJson = storyboardJson; }
    public String getSourceUrls() { return sourceUrls; }
    public void setSourceUrls(String sourceUrls) { this.sourceUrls = sourceUrls; }
    public String getFactCheckStatus() { return factCheckStatus; }
    public void setFactCheckStatus(String factCheckStatus) { this.factCheckStatus = factCheckStatus; }
    public Integer getQualityScore() { return qualityScore; }
    public void setQualityScore(Integer qualityScore) { this.qualityScore = qualityScore; }
    public Integer getTargetDurationSeconds() { return targetDurationSeconds; }
    public void setTargetDurationSeconds(Integer targetDurationSeconds) { this.targetDurationSeconds = targetDurationSeconds; }
    public Long getCampaignId() { return campaignId; }
    public void setCampaignId(Long campaignId) { this.campaignId = campaignId; }
    public Integer getEpisodeNumber() { return episodeNumber; }
    public void setEpisodeNumber(Integer episodeNumber) { this.episodeNumber = episodeNumber; }
    public String getAiProvider() { return aiProvider; }
    public void setAiProvider(String aiProvider) { this.aiProvider = aiProvider; }
    public String getVisualStyle() { return visualStyle; }
    public void setVisualStyle(String visualStyle) { this.visualStyle = visualStyle; }
    public String getCharacterDescription() { return characterDescription; }
    public void setCharacterDescription(String characterDescription) { this.characterDescription = characterDescription; }
    public String getAudioMode() { return audioMode; }
    public void setAudioMode(String audioMode) { this.audioMode = audioMode; }
    public String getVideoProvider() { return videoProvider; }
    public void setVideoProvider(String videoProvider) { this.videoProvider = videoProvider; }
    public String getAspectRatio() { return aspectRatio; }
    public void setAspectRatio(String aspectRatio) { this.aspectRatio = aspectRatio; }
    public String getRenderQuality() { return renderQuality; }
    public void setRenderQuality(String renderQuality) { this.renderQuality = renderQuality; }
    public String getCharacterImageUrl() { return characterImageUrl; }
    public void setCharacterImageUrl(String characterImageUrl) { this.characterImageUrl = characterImageUrl; }
    public String getScriptUrl() { return scriptUrl; }
    public void setScriptUrl(String scriptUrl) { this.scriptUrl = scriptUrl; }
    public String getStoryboardUrl() { return storyboardUrl; }
    public void setStoryboardUrl(String storyboardUrl) { this.storyboardUrl = storyboardUrl; }
    public String getScenePromptsUrl() { return scenePromptsUrl; }
    public void setScenePromptsUrl(String scenePromptsUrl) { this.scenePromptsUrl = scenePromptsUrl; }
    public String getImageSetUrl() { return imageSetUrl; }
    public void setImageSetUrl(String imageSetUrl) { this.imageSetUrl = imageSetUrl; }
    public String getNarrationUrl() { return narrationUrl; }
    public void setNarrationUrl(String narrationUrl) { this.narrationUrl = narrationUrl; }
    public String getSubtitleUrl() { return subtitleUrl; }
    public void setSubtitleUrl(String subtitleUrl) { this.subtitleUrl = subtitleUrl; }
    public String getProjectArchiveUrl() { return projectArchiveUrl; }
    public void setProjectArchiveUrl(String projectArchiveUrl) { this.projectArchiveUrl = projectArchiveUrl; }
    public String getAssetManifestUrl() { return assetManifestUrl; }
    public void setAssetManifestUrl(String assetManifestUrl) { this.assetManifestUrl = assetManifestUrl; }
    public String getWorkflowState() { return workflowState; }
    public void setWorkflowState(String workflowState) { this.workflowState = workflowState; }
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }
    public Long getOwnerId() { return owner == null ? null : owner.getId(); }
    public String getOwnerName() { return owner == null ? "Chưa gán" : owner.getDisplayName(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
