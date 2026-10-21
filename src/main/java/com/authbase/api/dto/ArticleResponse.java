package com.authbase.api.dto;

import com.authbase.domain.Article;
import com.authbase.domain.ArticleStatus;
import com.authbase.helpers.MediaUrls;

import java.time.Instant;

public record ArticleResponse(
        String id,
        String title,
        String slug,
        String content,
        String excerpt,
        String coverImageUrl,
        ArticleStatus status,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        String categoryId,
        String categoryName,
        String categorySlug,
        String authorId,
        String authorName,
        String authorSpecialty,
        long viewCount,
        String authorUsername
) {
    public static ArticleResponse from(Article article) {
        return new ArticleResponse(
                article.id,
                article.title,
                article.slug,
                article.content,
                article.excerpt,
                MediaUrls.cover(article),
                article.status,
                article.publishedAt,
                article.createdAt,
                article.updatedAt,
                article.category.id,
                article.category.name,
                article.category.slug,
                article.author.id,
                article.author.displayName(),
                article.author.specialty,
                article.viewCount == null ? 0L : article.viewCount,
                article.author.username
        );
    }

    public ArticleSummary summary() {
        return new ArticleSummary(
                id,
                title,
                slug,
                excerpt,
                coverImageUrl,
                publishedAt,
                categoryName,
                categorySlug,
                authorId,
                authorName,
                viewCount,
                authorUsername
        );
    }
}
