package com.authbase.api.dto;

import com.authbase.domain.Role;
import com.authbase.domain.User;

import java.time.Instant;
import java.time.LocalDate;

public record UserResponse(
        String id,
        String email,
        String firstName,
        String lastName,
        String documentNumber,
        String phone,
        LocalDate birthDate,
        String street,
        String city,
        String province,
        String postalCode,
        Role role,
        boolean enabled,
        Instant lockedUntil,
        Instant createdAt,
        Instant lastLoginAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.id,
                user.email,
                user.firstName,
                user.lastName,
                user.documentNumber,
                user.phone,
                user.birthDate,
                user.street,
                user.city,
                user.province,
                user.postalCode,
                user.role,
                user.enabled,
                user.lockedUntil,
                user.createdAt,
                user.lastLoginAt
        );
    }
}
