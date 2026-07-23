package vn.techflow.manager.campaign;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaigns")
public class Campaign {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignStatus status = CampaignStatus.PLANNING;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void create() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void update() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getTheme() { return theme; }
    public void setTheme(String value) { theme = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public Integer getEpisodeCount() { return episodeCount; }
    public void setEpisodeCount(Integer value) { episodeCount = value; }
    public Integer getTargetDurationSeconds() { return targetDurationSeconds; }
    public void setTargetDurationSeconds(Integer value) { targetDurationSeconds = value; }
    public String getVisualStyle() { return visualStyle; }
    public void setVisualStyle(String value) { visualStyle = value; }
    public String getCharacterDescription() { return characterDescription; }
    public void setCharacterDescription(String value) { characterDescription = value; }
    public CampaignStatus getStatus() { return status; }
    public void setStatus(CampaignStatus value) { status = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
