package vn.techflow.manager.feedback;

import jakarta.persistence.*;
import vn.techflow.manager.auth.AppUser;
import vn.techflow.manager.task.WorkTask;

import java.time.LocalDateTime;

@Entity
@Table(name = "video_feedback",
        uniqueConstraints = @UniqueConstraint(name = "uq_video_feedback_owner_task", columnNames = {"owner_id", "task_id"}))
public class VideoFeedback {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "task_id", nullable = false)
    private WorkTask task;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;
    @Column(nullable = false) private Integer rating;
    @Column(nullable = false, length = 500) private String aspects = "";
    @Column(nullable = false, length = 2000) private String comment = "";
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    @PrePersist void create() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public WorkTask getTask() { return task; }
    public void setTask(WorkTask task) { this.task = task; }
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getAspects() { return aspects; }
    public void setAspects(String aspects) { this.aspects = aspects; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
