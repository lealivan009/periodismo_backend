package com.authbase.api.dto;

import com.authbase.domain.User;
import com.authbase.domain.VerificationStatus;

import java.time.Instant;

public record JournalistApplicationResponse(
        String id,
        String email,
        String firstName,
        String lastName,
        String specialty,
        String bio,
        VerificationStatus verificationStatus,
        boolean hasDocument,
        Instant createdAt
) {
    public static JournalistApplicationResponse from(User user) {
        return new JournalistApplicationResponse(
                user.id,
                user.email,
                user.firstName,
                user.lastName,
                user.specialty,
                user.bio,
                user.verificationStatus,
                user.degreeDocumentUrl != null && !user.degreeDocumentUrl.isBlank(),
                user.createdAt
        );
    }
}
