package com.authbase.service;

import com.authbase.api.dto.NotificationResponse;
import com.authbase.domain.Article;
import com.authbase.domain.ArticlePublishedEvent;
import com.authbase.domain.Notification;
import com.authbase.domain.NotificationType;
import com.authbase.domain.Subscription;
import com.authbase.domain.User;
import com.authbase.error.ApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class NotificationService {

    @Transactional
    public void onArticlePublished(@Observes ArticlePublishedEvent event) {
        Article article = Article.findById(event.articleId());
        if (article == null) {
            return;
        }
        List<Subscription> subscriptions = Subscription.list("journalist.id", article.author.id);
        for (Subscription subscription : subscriptions) {
            if (Notification.count("recipient.id = ?1 and article.id = ?2", subscription.subscriber.id, article.id) > 0) {
                continue;
            }
            Notification notification = new Notification();
            notification.recipient = subscription.subscriber;
            notification.article = article;
            notification.type = NotificationType.ARTICLE_PUBLISHED;
            notification.persist();
        }
    }

    @Transactional
    public List<NotificationResponse> list(User recipient) {
        return Notification.<Notification>find("recipient.id = ?1 order by createdAt desc", recipient.id)
                .page(0, 30)
                .list()
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public NotificationResponse markRead(User recipient, String id) {
        Notification notification = Notification.findById(id);
        if (notification == null || !notification.recipient.id.equals(recipient.id)) {
            throw ApiException.notFound("NOTIFICATION_NOT_FOUND", "No se encontró el aviso");
        }
        if (notification.readAt == null) {
            notification.readAt = Instant.now();
        }
        return NotificationResponse.from(notification);
    }
}
