package vn.techflow.manager.publication;

import jakarta.persistence.*;
import vn.techflow.manager.task.WorkTask;

import java.time.LocalDateTime;

@Entity
@Table(name = "publications")
public class Publication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private WorkTask task;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Platform platform;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PublicationStatus status = PublicationStatus.PENDING;
    private LocalDateTime scheduledAt;
    private LocalDateTime publishedAt;
    @Column(length = 255) private String externalId;
    @Column(nullable = false, length = 1000) private String note = "";
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    @PrePersist void create() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public WorkTask getTask() { return task; }
    public void setTask(WorkTask task) { this.task = task; }
    public Platform getPlatform() { return platform; }
    public void setPlatform(Platform platform) { this.platform = platform; }
    public PublicationStatus getStatus() { return status; }
    public void setStatus(PublicationStatus status) { this.status = status; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
