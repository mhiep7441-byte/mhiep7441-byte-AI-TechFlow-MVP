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
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }
    public Long getOwnerId() { return owner == null ? null : owner.getId(); }
    public String getOwnerName() { return owner == null ? "Chưa gán" : owner.getDisplayName(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
