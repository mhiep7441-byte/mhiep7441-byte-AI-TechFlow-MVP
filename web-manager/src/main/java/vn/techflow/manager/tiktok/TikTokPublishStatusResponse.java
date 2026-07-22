package vn.techflow.manager.tiktok;

public record TikTokPublishStatusResponse(
        String publishId,
        Long publicationId,
        String tiktokStatus,
        String publicationStatus,
        String message
) {}
