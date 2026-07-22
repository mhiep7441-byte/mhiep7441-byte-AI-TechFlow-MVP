package vn.techflow.manager.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "ai-techflow-studio", "timestamp", Instant.now());
    }
}
