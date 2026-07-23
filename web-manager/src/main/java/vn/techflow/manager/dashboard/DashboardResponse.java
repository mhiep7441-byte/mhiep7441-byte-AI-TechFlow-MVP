package vn.techflow.manager.dashboard;

public record DashboardResponse(long total, long processing, long review, long done, long failed, long scheduled) {}
