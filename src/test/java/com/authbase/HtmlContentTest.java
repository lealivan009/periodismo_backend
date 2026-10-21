package com.authbase;

import com.authbase.helpers.HtmlContent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlContentTest {

    @Test
    void readsYoutubeIds() {
        assertEquals("dQw4w9WgXcQ", HtmlContent.youtubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertEquals("dQw4w9WgXcQ", HtmlContent.youtubeId("https://youtu.be/dQw4w9WgXcQ?t=12"));
        assertEquals("dQw4w9WgXcQ", HtmlContent.youtubeId("https://www.youtube.com/shorts/dQw4w9WgXcQ"));
        assertNull(HtmlContent.youtubeId("https://vimeo.com/123456789"));
        assertNull(HtmlContent.youtubeId("javascript:alert(1)"));
    }

    @Test
    void keepsOnlyYoutubeEmbeds() {
        String clean = HtmlContent.clean("""
                <p>Antes</p>
                <iframe src="https://www.youtube.com/embed/dQw4w9WgXcQ"></iframe>
                <iframe src="https://evil.example/embed/dQw4w9WgXcQ"></iframe>
                <p>Después</p>
                """);
        assertTrue(clean.contains("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"));
        assertTrue(clean.contains("<p>Antes</p>"));
        assertTrue(clean.contains("<p>Después</p>"));
        assertFalse(clean.contains("evil.example"));
        assertFalse(HtmlContent.isBlank(clean));
    }
}
