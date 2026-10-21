package com.authbase.service;

import com.authbase.api.dto.AuthResponse;
import com.authbase.api.dto.ChangePasswordRequest;
import com.authbase.api.dto.ForgotPasswordRequest;
import com.authbase.api.dto.LoginRequest;
import com.authbase.api.dto.RefreshRequest;
import com.authbase.api.dto.RegisterRequest;
import com.authbase.api.dto.ResetPasswordRequest;
import com.authbase.api.dto.UpdateProfileRequest;
import com.authbase.api.dto.UserResponse;
import com.authbase.config.AuthProperties;
import com.authbase.domain.PasswordResetToken;
import com.authbase.domain.RefreshToken;
import com.authbase.domain.Role;
import com.authbase.domain.User;
import com.authbase.domain.VerificationStatus;
import com.authbase.helpers.SocialLinks;
import com.authbase.helpers.UserProfiles;
import com.authbase.helpers.Usernames;
import com.authbase.error.ApiException;
import com.authbase.security.JwtIssuer;
import com.authbase.security.PasswordHasher;
import com.authbase.security.PasswordPolicy;
import com.authbase.security.TokenService;
import com.authbase.service.storage.MediaStore;
import com.authbase.service.storage.UploadedFile;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;

@ApplicationScoped
public class AuthService {

    private static final String FORGOT_PASSWORD_MESSAGE =
            "Si el email está registrado, te enviamos un enlace para restablecer la contraseña.";

    private final AuthProperties properties;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final TokenService tokenService;
    private final JwtIssuer jwtIssuer;
    private final SettingsService settingsService;
    private final MailService mailService;
    private final MediaStore mediaStore;

    public AuthService(
            AuthProperties properties,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            TokenService tokenService,
            JwtIssuer jwtIssuer,
            SettingsService settingsService,
            MailService mailService,
            MediaStore mediaStore
    ) {
        this.properties = properties;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.tokenService = tokenService;
        this.jwtIssuer = jwtIssuer;
        this.settingsService = settingsService;
        this.mailService = mailService;
        this.mediaStore = mediaStore;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!settingsService.current().allowPublicRegistration) {
            throw ApiException.forbidden(
                    "REGISTRATION_DISABLED",
                    "El registro público está deshabilitado. Pedile a un administrador que cree tu cuenta."
            );
        }
        passwordPolicy.validate(request.password());

        String email = User.normalizeEmail(request.email());
        if (User.findByEmail(email).isPresent()) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "Ya existe una cuenta con ese email");
        }

        User user = new User();
        user.email = email;
        user.firstName = request.firstName().trim();
        user.lastName = request.lastName().trim();
        user.passwordHash = passwordHasher.hash(request.password());
        user.role = Role.READER;
        user.verificationStatus = VerificationStatus.NONE;
        UserProfiles.set(
                user,
                request.documentNumber(),
                request.phone(),
                request.birthDate(),
                request.street(),
                request.city(),
                request.province(),
                request.postalCode()
        );
        user.persist();

        return issueSession(user);
    }

    @Transactional
    public AuthResponse registerJournalist(
            RegisterRequest request,
            String specialty,
            String bio,
            String username,
            UploadedFile degree
    ) {
        if (!settingsService.current().allowPublicRegistration) {
            throw ApiException.forbidden(
                    "REGISTRATION_DISABLED",
                    "El registro público está deshabilitado. Pedile a un administrador que cree tu cuenta."
            );
        }
        passwordPolicy.validate(request.password());
        String specialtyClean = requireSpecialty(specialty);
        if (degree == null || degree.bytes() == null || degree.bytes().length == 0) {
            throw ApiException.badRequest("DEGREE_REQUIRED", "Adjuntá el título o la matrícula profesional");
        }

        String email = User.normalizeEmail(request.email());
        if (User.findByEmail(email).isPresent()) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "Ya existe una cuenta con ese email");
        }

        User user = new User();
        user.email = email;
        user.firstName = request.firstName().trim();
        user.lastName = request.lastName().trim();
        user.passwordHash = passwordHasher.hash(request.password());
        user.role = Role.JOURNALIST;
        user.verificationStatus = VerificationStatus.PENDING;
        user.specialty = specialtyClean;
        user.bio = cleanBio(bio);
        if (username == null || username.isBlank()) {
            throw ApiException.badRequest("USERNAME_REQUIRED", "El usuario es obligatorio");
        }
        Usernames.claim(user, username);
        UserProfiles.set(
                user,
                request.documentNumber(),
                request.phone(),
                request.birthDate(),
                request.street(),
                request.city(),
                request.province(),
                request.postalCode()
        );
        user.persist();
        user.degreeDocumentUrl = mediaStore.saveDegree(user.id, degree.fileName(), degree.contentType(), degree.bytes());
        return issueSession(user);
    }

    @Transactional
    public UserResponse updateAvatar(String userId, UploadedFile file) {
        User user = requireUser(userId);
        if (file == null || file.bytes() == null || file.bytes().length == 0) {
            throw ApiException.badRequest("AVATAR_REQUIRED", "Elegí una imagen");
        }
        String previous = user.avatarUrl;
        user.avatarUrl = mediaStore.saveImage("avatars", user.id, file.fileName(), file.contentType(), file.bytes());
        mediaStore.deleteQuietly(previous);
        return UserResponse.from(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Instant now = Instant.now();
        User user = User.findByEmail(request.email()).orElse(null);

        if (user == null) {
            passwordHasher.matchesDummy(request.password());
            throw invalidCredentials();
        }
        if (!user.enabled) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "La cuenta está deshabilitada");
        }
        if (user.isLocked(now)) {
            throw ApiException.forbidden("ACCOUNT_LOCKED", "La cuenta está bloqueada temporalmente por intentos fallidos");
        }
        if (!passwordHasher.matches(request.password(), user.passwordHash)) {
            user.failedLoginAttempts += 1;
            var settings = settingsService.current();
            if (user.failedLoginAttempts >= settings.maxFailedAttempts) {
                user.lockedUntil = now.plus(java.time.Duration.ofMinutes(settings.lockDurationMinutes));
                user.failedLoginAttempts = 0;
            }
            throw invalidCredentials();
        }

        user.failedLoginAttempts = 0;
        user.lockedUntil = null;
        user.lastLoginAt = now;
        return issueSession(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        Instant now = Instant.now();
        String hash = tokenService.hash(request.refreshToken());
        RefreshToken stored = RefreshToken.findActiveByHash(hash)
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "El refresh token no es válido"));

        if (!stored.isActive(now)) {
            stored.revoked = true;
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "El refresh token expiró");
        }

        User user = stored.user;
        if (user.isDeleted() || !user.enabled) {
            stored.revoked = true;
            throw ApiException.forbidden("ACCOUNT_DISABLED", "La cuenta está deshabilitada");
        }

        stored.revoked = true;
        return issueSession(user);
    }

    @Transactional
    public void logout(RefreshRequest request) {
        String hash = tokenService.hash(request.refreshToken());
        RefreshToken.findActiveByHash(hash).ifPresent(token -> token.revoked = true);
    }

    @Transactional
    public void logoutAll(String userId) {
        User user = User.findById(userId);
        if (user != null) {
            RefreshToken.revokeAllForUser(user);
        }
    }

    public UserResponse me(String userId) {
        User user = requireUser(userId);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = requireUser(userId);
        if (request.firstName() != null) {
            user.firstName = request.firstName().trim();
        }
        if (request.lastName() != null) {
            user.lastName = request.lastName().trim();
        }
        UserProfiles.set(
                user,
                request.documentNumber(),
                request.phone(),
                request.birthDate(),
                request.street(),
                request.city(),
                request.province(),
                request.postalCode()
        );
        if (request.bio() != null) {
            user.bio = cleanBio(request.bio());
        }
        if (request.specialty() != null) {
            String specialty = UserProfiles.clean(request.specialty());
            if (specialty != null && specialty.length() < 2) {
                throw ApiException.badRequest("SPECIALTY_REQUIRED", "La especialidad debe tener entre 2 y 120 caracteres");
            }
            user.specialty = specialty;
        }
        if (user.role == Role.JOURNALIST) {
            if (request.xUrl() != null) {
                user.xUrl = SocialLinks.x(request.xUrl());
            }
            if (request.instagramUrl() != null) {
                user.instagramUrl = SocialLinks.instagram(request.instagramUrl());
            }
            if (request.facebookUrl() != null) {
                user.facebookUrl = SocialLinks.facebook(request.facebookUrl());
            }
            if (request.username() != null) {
                if (request.username().isBlank()) {
                    throw ApiException.badRequest("USERNAME_REQUIRED", "El usuario es obligatorio");
                }
                Usernames.claim(user, request.username());
            } else if (user.username == null || user.username.isBlank()) {
                Usernames.ensure(user);
            }
        }
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = requireUser(userId);
        if (!passwordHasher.matches(request.currentPassword(), user.passwordHash)) {
            throw ApiException.unauthorized("INVALID_PASSWORD", "La contraseña actual no es correcta");
        }
        passwordPolicy.validate(request.newPassword());
        if (passwordHasher.matches(request.newPassword(), user.passwordHash)) {
            throw ApiException.badRequest("PASSWORD_REUSED", "La nueva contraseña debe ser distinta a la actual");
        }
        user.passwordHash = passwordHasher.hash(request.newPassword());
        RefreshToken.revokeAllForUser(user);
    }

    public String forgotPassword(ForgotPasswordRequest request) {
        String email = User.normalizeEmail(request.email());
        ResetMailJob[] job = {null};

        QuarkusTransaction.requiringNew().run(() -> {
            User user = User.findByEmail(email).orElse(null);
            if (user == null || !user.enabled) {
                return;
            }

            PasswordResetToken.invalidateAllForUser(user);

            String rawToken = tokenService.newRefreshToken();
            PasswordResetToken stored = new PasswordResetToken();
            stored.user = user;
            stored.tokenHash = tokenService.hash(rawToken);
            stored.expiresAt = Instant.now().plus(properties.passwordResetTokenTtl());
            stored.persist();

            job[0] = new ResetMailJob(user.email, rawToken);
        });

        if (job[0] != null) {
            // After commit: the reset link is valid as soon as the email arrives.
            mailService.sendPasswordReset(job[0].email(), job[0].rawToken());
        }
        return FORGOT_PASSWORD_MESSAGE;
    }

    private record ResetMailJob(String email, String rawToken) {
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        Instant now = Instant.now();
        String hash = tokenService.hash(request.token());
        PasswordResetToken stored = PasswordResetToken.findActiveByHash(hash)
                .orElseThrow(() -> ApiException.badRequest("INVALID_RESET_TOKEN", "El enlace no es válido o ya fue usado"));

        if (!stored.isActive(now)) {
            stored.used = true;
            throw ApiException.badRequest("INVALID_RESET_TOKEN", "El enlace expiró. Pedí uno nuevo.");
        }

        User user = stored.user;
        if (user.isDeleted() || !user.enabled) {
            stored.used = true;
            throw ApiException.forbidden("ACCOUNT_DISABLED", "La cuenta está deshabilitada");
        }

        passwordPolicy.validate(request.newPassword());
        user.passwordHash = passwordHasher.hash(request.newPassword());
        user.failedLoginAttempts = 0;
        user.lockedUntil = null;
        stored.used = true;
        PasswordResetToken.invalidateAllForUser(user);
        RefreshToken.revokeAllForUser(user);
    }

    private static String requireSpecialty(String specialty) {
        String clean = UserProfiles.clean(specialty);
        if (clean == null || clean.length() < 2 || clean.length() > 120) {
            throw ApiException.badRequest("SPECIALTY_REQUIRED", "La especialidad debe tener entre 2 y 120 caracteres");
        }
        return clean;
    }

    private static String cleanBio(String bio) {
        String clean = UserProfiles.clean(bio);
        if (clean != null && clean.length() > 4000) {
            throw ApiException.badRequest("BIO_TOO_LONG", "La descripción puede tener hasta 4000 caracteres");
        }
        return clean;
    }

    private User requireUser(String userId) {
        User user = User.findActiveById(userId).orElse(null);
        if (user == null || !user.enabled) {
            throw ApiException.unauthorized("UNAUTHORIZED", "Sesión inválida");
        }
        return user;
    }

    private AuthResponse issueSession(User user) {
        String refreshToken = tokenService.newRefreshToken();
        RefreshToken stored = new RefreshToken();
        stored.user = user;
        stored.tokenHash = tokenService.hash(refreshToken);
        stored.expiresAt = Instant.now().plus(properties.refreshTokenTtl());
        stored.persist();

        String accessToken = jwtIssuer.issueAccessToken(user);
        return AuthResponse.of(accessToken, refreshToken, jwtIssuer.expiresInSeconds(), UserResponse.from(user));
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Email o contraseña incorrectos");
    }
}
