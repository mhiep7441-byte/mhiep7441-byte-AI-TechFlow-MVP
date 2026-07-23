package vn.techflow.manager.trends;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Trends")
@RestController
@RequestMapping("/api/trends")
public class TrendController {
    private final TrendService service;

    public TrendController(TrendService service) {
        this.service = service;
    }

    @Operation(summary = "Đọc xu hướng từ RSS allowlist và gợi ý fallback")
    @GetMapping
    public TrendsResponse trends() {
        return service.getTrends();
    }
}
