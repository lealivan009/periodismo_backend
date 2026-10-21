package com.authbase.api.dto;

import java.time.Instant;

public record ArticleSummary(
        String id,
        String title,
        String slug,
        String excerpt,
        String coverImageUrl,
        Instant publishedAt,
        String categoryName,
        String categorySlug,
        String authorId,
        String authorName,
        long viewCount,
        String authorUsername
) {
}
