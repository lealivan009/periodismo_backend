package com.authbase.helpers;

import com.authbase.domain.User;
import com.authbase.error.ApiException;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class Usernames {

    private static final Pattern PATTERN = Pattern.compile("[a-z0-9._]{3,30}");
    private static final Set<String> RESERVED = Set.of(
            "admin", "api", "app", "login", "registro", "periodistas", "portada", "categoria", "nota", "media"
    );

    private Usernames() {
    }

    public static void ensure(User user) {
        if (user.username != null && !user.username.isBlank()) {
            return;
        }
        user.username = unique(fromName(user.firstName, user.lastName), user.id);
    }

    public static String require(String raw) {
        String value = normalize(raw);
        if (value == null
                || !PATTERN.matcher(value).matches()
                || value.startsWith(".")
                || value.endsWith(".")
                || value.contains("..")
                || RESERVED.contains(value)) {
            throw ApiException.badRequest(
                    "INVALID_USERNAME",
                    "El usuario debe tener entre 3 y 30 caracteres: letras, números, punto o guion bajo."
            );
        }
        return value;
    }

    public static String fromName(String firstName, String lastName) {
        String joined = Slugs.from(((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)))
                .replace("-", "");
        if (joined.length() < 3) {
            joined = joined + "periodista";
        }
        if (joined.length() > 30) {
            joined = joined.substring(0, 30).replaceAll("\\.$", "");
        }
        return joined;
    }

    public static String unique(String base, String currentUserId) {
        String candidate = base;
        int suffix = 2;
        while (taken(candidate, currentUserId)) {
            String extra = String.valueOf(suffix++);
            String trimmed = base.length() + extra.length() > 30 ? base.substring(0, 30 - extra.length()) : base;
            candidate = trimmed + extra;
        }
        return candidate;
    }

    public static void claim(User user, String raw) {
        String username = require(raw);
        if (taken(username, user.id)) {
            throw ApiException.conflict("USERNAME_TAKEN", "Ese usuario ya está en uso");
        }
        user.username = username;
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("@")) {
            value = value.substring(1);
        }
        return value.isBlank() ? null : value;
    }

    private static boolean taken(String username, String currentUserId) {
        User existing = User.find("username", username).firstResult();
        return existing != null && (currentUserId == null || !existing.id.equals(currentUserId));
    }
}
