package vn.techflow.manager.tiktok;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TikTokPublishRequest(
        @AssertTrue(message = "Bạn phải xác nhận đã xem video và đồng ý gửi TikTok") boolean consent,
        @NotBlank @Pattern(regexp = "PUBLIC_TO_EVERYONE|MUTUAL_FOLLOW_FRIENDS|FOLLOWER_OF_CREATOR|SELF_ONLY")
        String privacyLevel,
        @Size(max = 2200) String title,
        boolean disableComment,
        boolean disableDuet,
        boolean disableStitch
) {}
