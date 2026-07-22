package vn.techflow.manager.tiktok;

import java.util.List;

public record TikTokConnectionStatus(
        boolean configured,
        boolean connected,
        String displayName,
        String openId,
        List<String> scopes,
        boolean canDirectPost,
        String message
) {}
