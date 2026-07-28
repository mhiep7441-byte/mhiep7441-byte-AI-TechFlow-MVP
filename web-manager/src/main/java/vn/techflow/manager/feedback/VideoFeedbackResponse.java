package vn.techflow.manager.feedback;

import java.time.LocalDateTime;

public record VideoFeedbackResponse(
        Long id,
        Long taskId,
        String taskTitle,
        Long ownerId,
        String ownerName,
        String ownerEmail,
        int rating,
        String aspects,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static VideoFeedbackResponse from(VideoFeedback feedback) {
        return new VideoFeedbackResponse(
                feedback.getId(), feedback.getTask().getId(), feedback.getTask().getTitle(),
                feedback.getOwner().getId(), feedback.getOwner().getDisplayName(), feedback.getOwner().getEmail(),
                feedback.getRating(), feedback.getAspects(), feedback.getComment(),
                feedback.getCreatedAt(), feedback.getUpdatedAt());
    }
}
