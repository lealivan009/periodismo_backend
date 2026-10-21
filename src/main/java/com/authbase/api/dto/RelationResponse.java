package com.authbase.api.dto;

public record RelationResponse(
        boolean following,
        long followers,
        boolean subscribed,
        long subscribers
) {
}
