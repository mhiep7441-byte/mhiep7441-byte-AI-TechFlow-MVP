package vn.techflow.manager.feedback;

import java.util.Map;

public record FeedbackSummary(long total, double average, Map<Integer, Long> distribution) {}
