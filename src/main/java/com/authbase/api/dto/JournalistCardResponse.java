package com.authbase.api.dto;

public record JournalistCardResponse(
        String id,
        String firstName,
        String lastName,
        String bio,
        String specialty,
        String avatarUrl,
        String xUrl,
        String instagramUrl,
        String facebookUrl,
        long followers,
        long subscribers
) {
}
