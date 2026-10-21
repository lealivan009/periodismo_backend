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
        return "/api/media/avatars/" + user.id + "?v=" + version(user.avatarUrl);
    }

    public static String cover(Article article) {
        if (article == null || article.coverImageUrl == null || article.coverImageUrl.isBlank()) {
            return null;
        }
        return "/api/media/covers/" + article.id + "?v=" + version(article.coverImageUrl);
    }

    private static String version(String storedPath) {
        int slash = storedPath.lastIndexOf('/');
        String name = slash >= 0 ? storedPath.substring(slash + 1) : storedPath;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
