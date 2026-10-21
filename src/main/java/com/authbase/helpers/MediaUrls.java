package com.authbase.helpers;

import com.authbase.domain.Article;
import com.authbase.domain.User;

public final class MediaUrls {

    private MediaUrls() {
    }

    public static String avatar(User user) {
        if (user == null || user.avatarUrl == null || user.avatarUrl.isBlank()) {
            return null;
        }
        return "/api/media/avatars/" + user.id;
    }

    public static String cover(Article article) {
        if (article == null || article.coverImageUrl == null || article.coverImageUrl.isBlank()) {
            return null;
        }
        return "/api/media/covers/" + article.id;
    }
}
