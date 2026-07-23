package vn.techflow.manager.trends;

import java.util.List;

public record TrendsResponse(
        List<TrendItem> items,
        boolean fallback,
        List<String> warnings
) {}
