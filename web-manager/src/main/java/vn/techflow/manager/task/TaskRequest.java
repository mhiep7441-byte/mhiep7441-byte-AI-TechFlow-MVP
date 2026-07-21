package vn.techflow.manager.task;
import jakarta.validation.constraints.*; import java.time.LocalDate;
public record TaskRequest(@NotBlank @Size(max=160) String title,@Size(max=2000) String description,@Size(max=500) String topic,TaskStatus status,Priority priority,LocalDate dueDate){}
