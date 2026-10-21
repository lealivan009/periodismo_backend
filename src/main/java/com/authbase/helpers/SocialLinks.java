package com.authbase.helpers;

import com.authbase.error.ApiException;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class SocialLinks {

    private static final Pattern X_HANDLE = Pattern.compile("[A-Za-z0-9_]{1,15}");
    private static final Pattern INSTAGRAM_HANDLE = Pattern.compile("[A-Za-z0-9._]{1,30}");
    private static final Pattern FACEBOOK_SLUG = Pattern.compile("[A-Za-z0-9.]{1,50}");
    private static final Pattern FACEBOOK_ID = Pattern.compile("[0-9]{1,20}");
    private static final Set<String> X_HOSTS = Set.of("x.com", "twitter.com");
    private static final Set<String> X_RESERVED = Set.of("home", "explore", "search", "intent", "share", "i", "messages", "settings");
    private static final Set<String> INSTAGRAM_RESERVED = Set.of("p", "reel", "reels", "stories", "explore", "accounts", "direct");

    private SocialLinks() {
    }

    public static String x(String raw) {
        return normalize(raw, Network.X);
    }

    public static String instagram(String raw) {
        return normalize(raw, Network.INSTAGRAM);
    }

    public static String facebook(String raw) {
        return normalize(raw, Network.FACEBOOK);
    }

    private static String normalize(String raw, Network network) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > 300 || value.indexOf(' ') >= 0 || value.indexOf('\n') >= 0) {
            throw invalid(network);
        }
        if (value.startsWith("@")) {
            value = value.substring(1);
        }
        if (looksLikeUrl(value)) {
            return fromUrl(value, network);
        }
        return fromHandle(value, network);
    }

    private static boolean looksLikeUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("://")
                || lower.startsWith("www.")
                || lower.contains("x.com")
                || lower.contains("twitter.com")
                || lower.contains("instagram.com")
                || lower.contains("facebook.com")
                || lower.contains("fb.com");
    }

    private static String fromHandle(String handle, Network network) {
        return switch (network) {
            case X -> {
                if (!X_HANDLE.matcher(handle).matches() || X_RESERVED.contains(handle.toLowerCase(Locale.ROOT))) {
                    throw invalid(network);
                }
                yield "https://x.com/" + handle;
            }
            case INSTAGRAM -> {
                String lower = handle.toLowerCase(Locale.ROOT);
                if (!INSTAGRAM_HANDLE.matcher(lower).matches()
                        || lower.startsWith(".")
                        || lower.endsWith(".")
                        || INSTAGRAM_RESERVED.contains(lower)) {
                    throw invalid(network);
                }
                yield "https://www.instagram.com/" + lower;
            }
            case FACEBOOK -> {
                if (!FACEBOOK_SLUG.matcher(handle).matches() || handle.equalsIgnoreCase("profile.php")) {
                    throw invalid(network);
                }
                yield "https://www.facebook.com/" + handle;
            }
        };
    }

    private static String fromUrl(String value, Network network) {
        String withScheme = value.contains("://") ? value : "https://" + value;
        URI uri;
        try {
            uri = URI.create(withScheme);
        } catch (IllegalArgumentException ex) {
            throw invalid(network);
        }
        String scheme = uri.getScheme();
        if (uri.getUserInfo() != null || scheme == null || !(scheme.equals("https") || scheme.equals("http"))) {
            throw invalid(network);
        }
        String host = bareHost(uri.getHost());
        if (host == null || !allowed(host, network)) {
            throw invalid(network);
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        String[] parts = path.split("/");
        return switch (network) {
            case X -> {
                String handle = firstSegment(parts);
                if (handle == null || !X_HANDLE.matcher(handle).matches() || X_RESERVED.contains(handle.toLowerCase(Locale.ROOT))) {
                    throw invalid(network);
                }
                yield "https://x.com/" + handle;
            }
            case INSTAGRAM -> {
                String handle = firstSegment(parts);
                if (handle == null) {
                    throw invalid(network);
                }
                handle = handle.toLowerCase(Locale.ROOT);
                if (!INSTAGRAM_HANDLE.matcher(handle).matches()
                        || handle.startsWith(".")
                        || handle.endsWith(".")
                        || INSTAGRAM_RESERVED.contains(handle)) {
                    throw invalid(network);
                }
                yield "https://www.instagram.com/" + handle;
            }
            case FACEBOOK -> facebookUrl(uri, parts);
        };
    }

    private static String facebookUrl(URI uri, String[] parts) {
        String first = firstSegment(parts);
        if ("profile.php".equalsIgnoreCase(first)) {
            String id = queryParam(uri.getRawQuery(), "id");
            if (id == null || !FACEBOOK_ID.matcher(id).matches()) {
                throw invalid(Network.FACEBOOK);
            }
            return "https://www.facebook.com/profile.php?id=" + id;
        }
        if (first == null || !FACEBOOK_SLUG.matcher(first).matches()) {
            throw invalid(Network.FACEBOOK);
        }
        return "https://www.facebook.com/" + first;
    }

    private static String firstSegment(String[] parts) {
        for (String part : parts) {
            if (!part.isBlank()) {
                return part;
            }
        }
        return null;
    }

    private static String queryParam(String query, String name) {
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            if (pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }

    private static String bareHost(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        String value = host.toLowerCase(Locale.ROOT);
        if (value.startsWith("www.")) {
            value = value.substring(4);
        }
        if (value.startsWith("mobile.")) {
            value = value.substring("mobile.".length());
        } else if (value.startsWith("m.")) {
            value = value.substring(2);
        }
        return value;
    }

    private static boolean allowed(String host, Network network) {
        return switch (network) {
            case X -> X_HOSTS.contains(host);
            case INSTAGRAM -> host.equals("instagram.com");
            case FACEBOOK -> host.equals("facebook.com") || host.equals("fb.com");
        };
    }

    private static ApiException invalid(Network network) {
        String label = switch (network) {
            case X -> "X";
            case INSTAGRAM -> "Instagram";
            case FACEBOOK -> "Facebook";
        };
        return ApiException.badRequest(
                "INVALID_SOCIAL_LINK",
                "El enlace de " + label + " no es válido. Usá el usuario o el enlace del perfil."
        );
    }

    private enum Network {
        X,
        INSTAGRAM,
        FACEBOOK
    }
}
