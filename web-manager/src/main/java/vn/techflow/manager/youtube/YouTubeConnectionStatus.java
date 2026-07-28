package vn.techflow.manager.youtube;

public record YouTubeConnectionStatus(
        boolean configured,
        boolean connected,
        String channelId,
        String channelTitle,
        boolean canUpload,
        String message
) {}
