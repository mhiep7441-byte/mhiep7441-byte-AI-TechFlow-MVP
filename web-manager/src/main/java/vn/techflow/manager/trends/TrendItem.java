package vn.techflow.manager.trends;

/** A single item read from an allowlisted RSS/Atom source. */
public record TrendItem(
        String title,
        String url,
        String publishedAt,
        String source
) {}
