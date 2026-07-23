package vn.techflow.manager.task;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerMetadataTest {
    @Test
    void parsesResearchQualityAndCaptionFromWorkerOutput() throws Exception {
        String json = """
                {
                  "caption":"Nội dung đã kiểm chứng",
                  "hashtags":["#AI","#TechFlowVN"],
                  "research":{"sources":[{"url":"https://example.com/official"}]},
                  "storyboard":{"provider":"gemini","scenes":[{"title":"HOOK"}]},
                  "fact_check":{"approved":true},
                  "quality":{"score":92}
                }
                """;
        String encoded = Base64.getUrlEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        WorkerMetadata metadata = WorkerMetadata.parse(
                "log\nVIDEO_METADATA_B64=" + encoded + "\nVIDEO_READY=https://cdn.example/video.mp4\n");

        assertEquals("https://cdn.example/video.mp4", metadata.videoUrl());
        assertEquals("Nội dung đã kiểm chứng", metadata.caption());
        assertEquals("#AI #TechFlowVN", metadata.hashtags());
        assertEquals("VERIFIED", metadata.factCheckStatus());
        assertEquals(92, metadata.qualityScore());
        assertEquals("gemini", metadata.aiProvider());
        assertTrue(metadata.sourceUrls().contains("https://example.com/official"));
    }

    @Test
    void acceptsLegacyWorkerOutputWithoutMetadata() throws Exception {
        WorkerMetadata metadata = WorkerMetadata.parse("VIDEO_READY=https://cdn.example/legacy.mp4\n");
        assertEquals("NOT_CHECKED", metadata.factCheckStatus());
        assertEquals("{}", metadata.researchJson());
        assertEquals("fallback", metadata.aiProvider());
    }

    @Test
    void rejectsMissingVideoUrl() {
        assertThrows(Exception.class, () -> WorkerMetadata.parse("VIDEO_METADATA_B64=abc"));
    }
}
