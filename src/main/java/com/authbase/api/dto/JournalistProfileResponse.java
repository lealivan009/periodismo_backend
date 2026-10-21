package com.authbase.api.dto;

import java.util.List;

public record JournalistProfileResponse(
        String id,
        String firstName,
        String lastName,
        String username,
        String bio,
        String specialty,
        String avatarUrl,
        String xUrl,
        String instagramUrl,
        String facebookUrl,
        long followers,
        long subscribers,
        long articleCount,
        boolean following,
        boolean subscribed,
        List<ArticleSummary> articles
) {
}
