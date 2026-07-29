package vn.techflow.manager.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

record WorkerMetadata(
        String videoUrl,
        String caption,
        String hashtags,
        String researchJson,
        String storyboardJson,
        String sourceUrls,
        String factCheckStatus,
        Integer qualityScore,
        String aiProvider,
        String scriptUrl,
        String storyboardUrl,
        String scenePromptsUrl,
        String imageSetUrl,
        String narrationUrl,
        String subtitleUrl,
        String projectArchiveUrl,
        String assetManifestUrl
) {
    private static final ObjectMapper JSON = new ObjectMapper();

    static WorkerMetadata parse(String output) throws IOException {
        String videoUrl = markerValue(output, "VIDEO_READY=");
        if (videoUrl.isBlank()) throw new IOException("Worker trả về URL video trống");

        String encodedMetadata = markerValue(output, "VIDEO_METADATA_B64=");
        if (encodedMetadata.isBlank()) {
            return new WorkerMetadata(videoUrl, "", "", "{}", "{}", "", "NOT_CHECKED", null, "fallback",
                    "", "", "", "", "", "", "", "");
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encodedMetadata);
            JsonNode metadata = JSON.readTree(new String(decoded, StandardCharsets.UTF_8));
            JsonNode research = metadata.path("research");
            JsonNode storyboard = metadata.path("storyboard");
            JsonNode factCheck = metadata.path("fact_check");
            JsonNode quality = metadata.path("quality");
            List<String> hashtags = new ArrayList<>();
            metadata.path("hashtags").forEach(value -> hashtags.add(value.asText()));
            List<String> sources = new ArrayList<>();
            research.path("sources").forEach(source -> {
                String url = source.path("url").asText("").trim();
                if (url.startsWith("https://")) sources.add(url);
            });
            return new WorkerMetadata(
                    videoUrl,
                    limit(metadata.path("caption").asText(""), 2200),
                    limit(String.join(" ", hashtags), 500),
                    limit(JSON.writeValueAsString(research), 80_000),
                    limit(JSON.writeValueAsString(storyboard), 120_000),
                    limit(String.join("\n", sources), 20_000),
                    factCheck.path("approved").asBoolean(false) ? "VERIFIED" : "NEEDS_REVIEW",
                    quality.path("score").isInt() ? quality.path("score").asInt() : null,
                    limit(storyboard.path("provider").asText("fallback"), 30),
                    metadata.path("script_url").asText(""),
                    metadata.path("storyboard_url").asText(""),
                    metadata.path("scene_prompts_url").asText(""),
                    metadata.path("image_set_url").asText(""),
                    metadata.path("narration_url").asText(""),
                    metadata.path("subtitle_url").asText(""),
                    metadata.path("project_archive_url").asText(""),
                    metadata.path("asset_manifest_url").asText("")
            );
        } catch (IllegalArgumentException exception) {
            throw new IOException("Worker trả về metadata Base64 không hợp lệ", exception);
        }
    }

    private static String markerValue(String output, String marker) {
        int index = output.lastIndexOf(marker);
        if (index < 0) return "";
        return output.substring(index + marker.length()).lines().findFirst().orElse("").trim();
    }

    private static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
