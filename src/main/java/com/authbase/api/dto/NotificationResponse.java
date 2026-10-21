package com.authbase.api.dto;

import com.authbase.domain.Notification;

import java.time.Instant;

public record NotificationResponse(
        String id,
        String articleTitle,
        String articleSlug,
        String authorName,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.id,
                notification.article.title,
                notification.article.slug,
                notification.article.author.displayName(),
                notification.readAt != null,
                notification.createdAt
        );
    }
}
