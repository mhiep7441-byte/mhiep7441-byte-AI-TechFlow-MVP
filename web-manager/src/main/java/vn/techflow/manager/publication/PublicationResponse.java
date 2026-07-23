package vn.techflow.manager.publication;

import java.time.LocalDateTime;

public record PublicationResponse(
        Long id,
        Long taskId,
        String taskTitle,
        Platform platform,
        PublicationStatus status,
        LocalDateTime scheduledAt,
        LocalDateTime publishedAt,
        String externalId,
        String note,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static PublicationResponse from(Publication item) {
        return new PublicationResponse(
                item.getId(), item.getTask().getId(), item.getTask().getTitle(),
                item.getPlatform(), item.getStatus(), item.getScheduledAt(),
                item.getPublishedAt(), item.getExternalId(), item.getNote(),
                item.getCreatedAt(), item.getUpdatedAt()
        );
    }
}
