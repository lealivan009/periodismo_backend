package com.authbase;

import com.authbase.domain.Article;
import com.authbase.domain.User;
import com.authbase.helpers.MediaUrls;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MediaUrlsTest {

    @Test
    void coverUrlChangesWhenTheFileChanges() {
        Article article = new Article();
        article.id = "nota-1";
        assertNull(MediaUrls.cover(article));

        article.coverImageUrl = "covers/nota-1/aaaa.jpg";
        assertEquals("/api/media/covers/nota-1?v=aaaa", MediaUrls.cover(article));

        article.coverImageUrl = "covers/nota-1/bbbb.png";
        assertEquals("/api/media/covers/nota-1?v=bbbb", MediaUrls.cover(article));
    }

    @Test
    void avatarUrlChangesWhenTheFileChanges() {
        User user = new User();
        user.id = "user-1";
        user.avatarUrl = "avatars/user-1/foto.webp";
        assertEquals("/api/media/avatars/user-1?v=foto", MediaUrls.avatar(user));
    }
}
