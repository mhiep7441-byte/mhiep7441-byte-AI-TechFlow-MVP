package vn.techflow.manager.youtube;

public record YouTubePublishResponse(
        String videoId,
        Long publicationId,
        String privacyStatus,
        String message
) {}
