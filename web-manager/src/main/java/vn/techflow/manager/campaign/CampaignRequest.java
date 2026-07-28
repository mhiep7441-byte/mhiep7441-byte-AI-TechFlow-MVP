package vn.techflow.manager.campaign;

import jakarta.validation.constraints.*;

public record CampaignRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 500) String theme,
        @Size(max = 2000) String description,
        @Min(1) @Max(30) Integer episodeCount,
        @Min(30) @Max(600) Integer targetDurationSeconds,
        @Size(max = 240) String visualStyle,
        @Size(max = 240) String characterDescription,
        @Size(max = 160) String audience,
        CampaignCadence cadence,
        Boolean productionEnabled,
        java.time.LocalDateTime nextRunAt,
        CampaignStatus status,
        String characterImageUrl,
        String characterReferencePrompt,
        @Pattern(regexp = "narrated|silent_animation") String audioMode,
        @Pattern(regexp = "kenburns|seedance2_fast|veo") String videoProvider,
        @Pattern(regexp = "9:16|16:9") String aspectRatio,
        @Pattern(regexp = "draft|hd|2k") String renderQuality
) {}
