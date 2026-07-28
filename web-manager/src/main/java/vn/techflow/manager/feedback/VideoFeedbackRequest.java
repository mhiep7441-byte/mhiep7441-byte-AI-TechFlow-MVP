package vn.techflow.manager.feedback;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record VideoFeedbackRequest(
        @Min(1) @Max(5) int rating,
        @Size(max = 500) String aspects,
        @Size(max = 2000) String comment
) {}
