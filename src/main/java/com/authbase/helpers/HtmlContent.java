package com.authbase.helpers;

import com.authbase.error.ApiException;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HtmlContent {

    private static final int MAX_LENGTH = 100_000;
    private static final Pattern SCRIPT = Pattern.compile("(?is)<script\\b[^>]*>.*?</script>");
    private static final Pattern STYLE = Pattern.compile("(?is)<style\\b[^>]*>.*?</style>");
    private static final Pattern ON_ATTR = Pattern.compile("(?i)\\s+on\\w+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern JS_URL = Pattern.compile("(?i)(href|src)\\s*=\\s*([\"'])\\s*javascript:[^\"']*\\2");
    private static final Pattern VIDEO_BLOCK = Pattern.compile(
            "(?is)<figure\\b[^>]*class\\s*=\\s*([\"'])[^\"']*article-video[^\"']*\\1[^>]*>.*?</figure>|<iframe\\b[^>]*>(?:\\s*</iframe>)?"
    );
    private static final Pattern SRC = Pattern.compile("(?is)\\bsrc\\s*=\\s*([\"'])(.*?)\\1");
    private static final Pattern PATH_ID = Pattern.compile("^/(?:embed|shorts|live)/([A-Za-z0-9_-]{11})(?:/|$)");

    private HtmlContent() {
    }

    public static String clean(String html) {
        if (html == null) {
            return "";
        }
        String value = SCRIPT.matcher(html).replaceAll("");
        value = STYLE.matcher(value).replaceAll("");
        value = ON_ATTR.matcher(value).replaceAll("");
        value = JS_URL.matcher(value).replaceAll("");
        value = videos(value);
        return value.trim();
    }

    public static String youtubeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
            return null;
        }
        String host = uri.getHost();
        if (host == null) {
            return null;
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (host.equals("youtu.be")) {
            String id = path.startsWith("/") ? path.substring(1) : path;
            int slash = id.indexOf('/');
            return validId(slash >= 0 ? id.substring(0, slash) : id);
        }
        if (!host.equals("youtube.com") && !host.equals("m.youtube.com") && !host.equals("youtube-nocookie.com")) {
            return null;
        }
        Matcher pathId = PATH_ID.matcher(path);
        if (pathId.find()) {
            return pathId.group(1);
        }
        String query = uri.getRawQuery();
        if (query == null) {
            return null;
        }
        for (String part : query.split("&")) {
            if (part.startsWith("v=")) {
                return validId(part.substring(2));
            }
        }
        return null;
    }

    private static String videos(String html) {
        Matcher matcher = VIDEO_BLOCK.matcher(html);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            Matcher src = SRC.matcher(matcher.group());
            String id = src.find() ? youtubeId(src.group(2)) : null;
            matcher.appendReplacement(out, Matcher.quoteReplacement(id == null ? "" : embed(id)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String embed(String id) {
        return """
                <figure class="article-video"><iframe src="https://www.youtube-nocookie.com/embed/%s" title="Video de YouTube" loading="lazy" referrerpolicy="strict-origin-when-cross-origin" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe></figure>"""
                .formatted(id);
    }

    private static String validId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{11}") ? id : null;
    }

    public static boolean isBlank(String html) {
        if (html == null || html.isBlank()) {
            return true;
        }
        if (html.contains("youtube-nocookie.com/embed/") || html.contains("class=\"article-video\"")) {
            return false;
        }
        String text = html
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return text.isEmpty();
    }

    public static void requireLength(String html) {
        if (html != null && html.length() > MAX_LENGTH) {
            throw ApiException.badRequest("CONTENT_TOO_LONG", "El texto es demasiado largo");
        }
    }
}
