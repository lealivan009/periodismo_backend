package com.authbase.domain;

import com.authbase.helpers.Names;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    public String id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    public String email;

    @Column(name = "password_hash", nullable = false)
    public String passwordHash;

    @Column(name = "first_name", length = 80)
    public String firstName;

    @Column(name = "last_name", length = 80)
    public String lastName;

    @Column(name = "full_name", nullable = false, length = 160)
    public String fullName;

    @Column(name = "document_number", length = 20)
    public String documentNumber;

    @Column(name = "phone", length = 30)
    public String phone;

    @Column(name = "birth_date")
    public LocalDate birthDate;

    @Column(name = "street", length = 160)
    public String street;

    @Column(name = "city", length = 80)
    public String city;

    @Column(name = "province", length = 80)
    public String province;

    @Column(name = "postal_code", length = 20)
    public String postalCode;

    @Column(name = "avatar_url", length = 500)
    public String avatarUrl;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "bio", columnDefinition = "TEXT")
    public String bio;

    @Column(name = "specialty", length = 120)
    public String specialty;

    @Column(name = "x_url", length = 300)
    public String xUrl;

    @Column(name = "instagram_url", length = 300)
    public String instagramUrl;

    @Column(name = "facebook_url", length = 300)
    public String facebookUrl;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 32)
    public VerificationStatus verificationStatus = VerificationStatus.NONE;

    @Column(name = "degree_document_url", length = 500)
    public String degreeDocumentUrl;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    public Role role = Role.READER;

    @Column(name = "enabled", nullable = false)
    public boolean enabled = true;

    @Column(name = "failed_login_attempts", nullable = false)
    public int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    public Instant lockedUntil;

    @Column(name = "last_login_at")
    public Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Column(name = "deleted_at")
    public Instant deletedAt;

    @PostLoad
    void onLoad() {
        if (verificationStatus == null) {
            verificationStatus = VerificationStatus.NONE;
        }
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        email = normalizeEmail(email);
        syncLegacyFullName();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        email = normalizeEmail(email);
        syncLegacyFullName();
    }

    public String displayName() {
        return Names.display(firstName, lastName);
    }

    private void syncLegacyFullName() {
        String display = displayName();
        if (!display.isBlank()) {
            fullName = display;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean canPublish() {
        return role == Role.JOURNALIST
                && verificationStatus == VerificationStatus.APPROVED
                && enabled
                && !isDeleted();
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public static Optional<User> findByEmail(String email) {
        return find("email = ?1 and deletedAt is null", normalizeEmail(email)).firstResultOptional();
    }

    public static Optional<User> findActiveById(String id) {
        return find("id = ?1 and deletedAt is null", id).firstResultOptional();
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
