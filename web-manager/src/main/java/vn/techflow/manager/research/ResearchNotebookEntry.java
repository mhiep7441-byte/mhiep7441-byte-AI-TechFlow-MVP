package vn.techflow.manager.research;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record ResearchNotebookEntry(
        Long taskId,
        String title,
        String topic,
        String summary,
        JsonNode claims,
        JsonNode sources,
        JsonNode caveats,
        String mode,
        String factCheckStatus,
        Integer qualityScore,
        LocalDateTime updatedAt
) {}
