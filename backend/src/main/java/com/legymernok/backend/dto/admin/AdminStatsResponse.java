package com.legymernok.backend.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminStatsResponse {
    private long totalCadets;
    private long totalStarSystems;
    private long totalMissions;
    private long newCadetsThisWeek;
    private long openFeedbackCount;
    private List<RecentCadetSummary> recentCadets;
    private List<PopularStarSystemSummary> mostStartedStarSystems;
}
