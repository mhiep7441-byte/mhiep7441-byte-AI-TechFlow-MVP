package vn.techflow.manager.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TaskRequest(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 500) String topic,
        @Size(max = 2200) String caption,
        @Size(max = 500) String hashtags,
        TaskStatus status,
        Priority priority,
        LocalDate dueDate,
        @Min(30) @Max(600) Integer targetDurationSeconds,
        @Size(max = 240) String visualStyle,
        @Size(max = 240) String characterDescription
) {}
