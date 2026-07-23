package vn.techflow.manager.dashboard;

public record AdminDashboardResponse(
        long users,
        long activeUsers,
        long campaigns,
        long activeCampaigns,
        long automatedCampaigns,
        long videos,
        long generating,
        long awaitingReview,
        long completed,
        long failed,
        long publications,
        long published
) {}
