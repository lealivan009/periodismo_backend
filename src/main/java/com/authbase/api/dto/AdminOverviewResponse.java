package com.authbase.api.dto;

import java.util.List;

public record AdminOverviewResponse(
        long totalViews,
        long viewsThisMonth,
        long publishedArticles,
        long professionals,
        long pendingProfessionals,
        long readers,
        List<AuthorStatsResponse.MonthStat> months,
        List<AuthorRank> ranking
) {
    public record AuthorRank(
            String id,
            String name,
            String username,
            long views,
            long articles,
            boolean publicProfile
    ) {
    }
}
