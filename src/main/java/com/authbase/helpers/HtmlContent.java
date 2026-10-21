package com.authbase.helpers;

import com.authbase.error.ApiException;

import java.util.regex.Pattern;

public final class HtmlContent {

    private static final int MAX_LENGTH = 100_000;
    private static final Pattern SCRIPT = Pattern.compile("(?is)<script\\b[^>]*>.*?</script>");
    private static final Pattern STYLE = Pattern.compile("(?is)<style\\b[^>]*>.*?</style>");
    private static final Pattern ON_ATTR = Pattern.compile("(?i)\\s+on\\w+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern JS_URL = Pattern.compile("(?i)(href|src)\\s*=\\s*([\"'])\\s*javascript:[^\"']*\\2");

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
        return value.trim();
    }

    public static boolean isBlank(String html) {
        if (html == null || html.isBlank()) {
            return true;
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
