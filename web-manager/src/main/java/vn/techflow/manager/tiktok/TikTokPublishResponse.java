package vn.techflow.manager.tiktok;

public record TikTokPublishResponse(
        String publishId,
        Long publicationId,
        String status,
        String message
) {}
