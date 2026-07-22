package vn.techflow.manager.publication;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record PublicationRequest(
        @NotNull Long taskId,
        @NotNull Platform platform,
        PublicationStatus status,
        LocalDateTime scheduledAt,
        @Size(max = 255) String externalId,
        @Size(max = 1000) String note
) {}
