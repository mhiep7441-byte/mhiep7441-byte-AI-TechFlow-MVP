package vn.techflow.manager.campaign;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import vn.techflow.manager.auth.AppUser;

import java.time.LocalDateTime;

@Entity
@Table(name = "campaigns")
public class Campaign {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(nullable = false, length = 500)
    private String theme;
    @Column(nullable = false, length = 2000)
    private String description = "";
    @Column(nullable = false)
    private Integer episodeCount = 5;
    @Column(nullable = false)
    private Integer targetDurationSeconds = 60;
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
    @Column(nullable = false, length = 160)
    private String audience = "";
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private CampaignCadence cadence = CampaignCadence.MANUAL;
    @Column(nullable = false)
    private boolean productionEnabled;
    private LocalDateTime nextRunAt;
    private LocalDateTime lastRunAt;
    @Column(columnDefinition = "TEXT")
    private String seriesPlanJson = "";
    @Column(columnDefinition = "TEXT")
    private String characterImageUrl;
    @Column(columnDefinition = "TEXT")
    private String characterReferencePrompt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private CampaignStatus status = CampaignStatus.PLANNING;
    @JsonIgnore @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist void create() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getEpisodeCount() { return episodeCount; }
    public void setEpisodeCount(Integer episodeCount) { this.episodeCount = episodeCount; }
    public Integer getTargetDurationSeconds() { return targetDurationSeconds; }
    public void setTargetDurationSeconds(Integer targetDurationSeconds) { this.targetDurationSeconds = targetDurationSeconds; }
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
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public CampaignCadence getCadence() { return cadence; }
    public void setCadence(CampaignCadence cadence) { this.cadence = cadence; }
    public boolean isProductionEnabled() { return productionEnabled; }
    public void setProductionEnabled(boolean productionEnabled) { this.productionEnabled = productionEnabled; }
    public LocalDateTime getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(LocalDateTime nextRunAt) { this.nextRunAt = nextRunAt; }
    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    public String getSeriesPlanJson() { return seriesPlanJson; }
    public void setSeriesPlanJson(String seriesPlanJson) { this.seriesPlanJson = seriesPlanJson; }
    public String getCharacterImageUrl() { return characterImageUrl; }
    public void setCharacterImageUrl(String characterImageUrl) { this.characterImageUrl = characterImageUrl; }
    public String getCharacterReferencePrompt() { return characterReferencePrompt; }
    public void setCharacterReferencePrompt(String characterReferencePrompt) { this.characterReferencePrompt = characterReferencePrompt; }
    public CampaignStatus getStatus() { return status; }
    public void setStatus(CampaignStatus status) { this.status = status; }
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }
    public Long getOwnerId() { return owner == null ? null : owner.getId(); }
    public String getOwnerName() { return owner == null ? "Chưa gán" : owner.getDisplayName(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
