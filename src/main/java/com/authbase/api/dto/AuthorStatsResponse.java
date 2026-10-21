package com.authbase.api.dto;

import java.util.List;

public record AuthorStatsResponse(
        long totalViews,
        long viewsThisMonth,
        long publishedArticles,
        long followers,
        long subscribers,
        List<MonthStat> months,
        List<ArticleStat> articles,
        String authorName,
        String authorUsername,
        boolean publicProfile
) {
    public record MonthStat(int year, int month, long views) {
    }

    public record ArticleStat(String id, String title, String slug, long views) {
    }
}
