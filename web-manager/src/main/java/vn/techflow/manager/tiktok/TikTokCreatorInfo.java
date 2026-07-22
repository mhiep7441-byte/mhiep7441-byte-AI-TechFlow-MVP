package vn.techflow.manager.tiktok;

import java.util.List;

public record TikTokCreatorInfo(
        String username,
        String nickname,
        List<String> privacyLevelOptions,
        boolean commentDisabled,
        boolean duetDisabled,
        boolean stitchDisabled,
        int maxVideoPostDurationSec
) {}
