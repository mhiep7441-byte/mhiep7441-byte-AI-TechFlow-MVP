package vn.techflow.manager.task;
import jakarta.persistence.*;
import java.time.*;
@Entity @Table(name="work_tasks")
public class WorkTask {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=160) private String title;
 @Column(length=2000) private String description="";
 @Column(length=500) private String topic="";
 @Enumerated(EnumType.STRING) @Column(nullable=false) private TaskStatus status=TaskStatus.TODO;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private Priority priority=Priority.MEDIUM;
 private LocalDate dueDate;
 @Column(length=240) private String visualStyle="";
 @Column(length=240) private String characterDescription="";
 @Column(length=1000) private String researchSources="";
 @Column(length=1000) private String outputPath; @Column(length=4000) private String errorMessage;
 @Column(nullable=false,updatable=false) private LocalDateTime createdAt; @Column(nullable=false) private LocalDateTime updatedAt;
 @PrePersist void create(){createdAt=updatedAt=LocalDateTime.now();} @PreUpdate void update(){updatedAt=LocalDateTime.now();}
 public Long getId(){return id;} public String getTitle(){return title;} public void setTitle(String v){title=v;}
 public String getDescription(){return description;} public void setDescription(String v){description=v;} public String getTopic(){return topic;} public void setTopic(String v){topic=v;}
 public TaskStatus getStatus(){return status;} public void setStatus(TaskStatus v){status=v;} public Priority getPriority(){return priority;} public void setPriority(Priority v){priority=v;}
 public LocalDate getDueDate(){return dueDate;} public void setDueDate(LocalDate v){dueDate=v;} public String getOutputPath(){return outputPath;} public void setOutputPath(String v){outputPath=v;}
 public String getVisualStyle(){return visualStyle;} public void setVisualStyle(String v){visualStyle=v;}
 public String getCharacterDescription(){return characterDescription;} public void setCharacterDescription(String v){characterDescription=v;}
 public String getResearchSources(){return researchSources;} public void setResearchSources(String v){researchSources=v;}
 public String getErrorMessage(){return errorMessage;} public void setErrorMessage(String v){errorMessage=v;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}
