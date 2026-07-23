package vn.techflow.manager.youtube;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record YouTubePublishRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 5000) String description,
        @NotBlank String privacyStatus,
        boolean madeForKids,
        boolean consent
) {}
