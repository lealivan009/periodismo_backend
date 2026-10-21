package com.authbase.helpers;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

public final class Slugs {

    private Slugs() {
    }

    public static String from(String input) {
        String source = input == null ? "" : input.trim();
        String normalized = Normalizer.normalize(source, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (normalized.isBlank()) {
            normalized = "nota";
        }
        if (normalized.length() > 160) {
            normalized = normalized.substring(0, 160).replaceAll("-$", "");
        }
        return normalized;
    }

    public static String unique(String base, Predicate<String> taken) {
        String candidate = base;
        int suffix = 2;
        while (taken.test(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }
}
