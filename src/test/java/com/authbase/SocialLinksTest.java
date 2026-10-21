package com.authbase;

import com.authbase.error.ApiException;
import com.authbase.helpers.SocialLinks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocialLinksTest {

    @Test
    void acceptsHandlesAndOfficialUrls() {
        assertEquals("https://x.com/hugo", SocialLinks.x("@hugo"));
        assertEquals("https://x.com/hugo", SocialLinks.x("https://twitter.com/hugo/status/1"));
        assertEquals("https://www.instagram.com/hugo.videla", SocialLinks.instagram("instagram.com/hugo.videla/"));
        assertEquals("https://www.facebook.com/hugo", SocialLinks.facebook("https://fb.com/hugo"));
        assertEquals("https://www.facebook.com/profile.php?id=12345", SocialLinks.facebook("https://www.facebook.com/profile.php?id=12345&ref=x"));
        assertNull(SocialLinks.x("  "));
    }

    @Test
    void rejectsOtherSites() {
        assertThrows(ApiException.class, () -> SocialLinks.x("https://evil.com/hugo"));
        assertThrows(ApiException.class, () -> SocialLinks.instagram("javascript:alert(1)"));
        assertThrows(ApiException.class, () -> SocialLinks.facebook("no es un usuario"));
    }
}
