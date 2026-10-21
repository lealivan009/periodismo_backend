package com.authbase.api;

import com.authbase.api.dto.AuthResponse;
import com.authbase.api.dto.ChangePasswordRequest;
import com.authbase.api.dto.ForgotPasswordRequest;
import com.authbase.api.dto.LoginRequest;
import com.authbase.api.dto.MessageResponse;
import com.authbase.api.dto.RefreshRequest;
import com.authbase.api.dto.RegisterRequest;
import com.authbase.api.dto.ResetPasswordRequest;
import com.authbase.api.dto.UpdateProfileRequest;
import com.authbase.api.dto.UserResponse;
import com.authbase.error.ApiException;
import com.authbase.service.AuthService;
import com.authbase.service.storage.UploadedFile;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Path("/api/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Auth", description = "Registro, login y sesión")
public class AuthResource {

    private final AuthService authService;
    private final SecurityIdentity identity;
    private final JsonWebToken jwt;
    private final Validator validator;

    public AuthResource(AuthService authService, SecurityIdentity identity, JsonWebToken jwt, Validator validator) {
        this.authService = authService;
        this.identity = identity;
        this.jwt = jwt;
        this.validator = validator;
    }

    @POST
    @Path("/register")
    @Operation(summary = "Crear una cuenta")
    public AuthResponse register(@Valid RegisterRequest request) {
        return authService.register(request);
    }

    @POST
    @Path("/register-journalist")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Solicitar una cuenta de periodista")
    public AuthResponse registerJournalist(
            @RestForm String email,
            @RestForm String password,
            @RestForm String firstName,
            @RestForm String lastName,
            @RestForm String specialty,
            @RestForm String bio,
            @RestForm String username,
            @RestForm String documentNumber,
            @RestForm String phone,
            @RestForm String birthDate,
            @RestForm String street,
            @RestForm String city,
            @RestForm String province,
            @RestForm String postalCode,
            @RestForm("degree") FileUpload degree
    ) {
        RegisterRequest request = new RegisterRequest(
                email,
                password,
                firstName,
                lastName,
                blankToNull(documentNumber),
                blankToNull(phone),
                parseBirthDate(birthDate),
                blankToNull(street),
                blankToNull(city),
                blankToNull(province),
                blankToNull(postalCode)
        );
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            ConstraintViolation<RegisterRequest> violation = violations.iterator().next();
            throw ApiException.badRequest("VALIDATION", violation.getMessage());
        }
        UploadedFile file = Uploads.read(degree);
        return authService.registerJournalist(request, specialty, bio, username, file);
    }

    @POST
    @Path("/login")
    @Operation(summary = "Iniciar sesión")
    public AuthResponse login(@Valid LoginRequest request) {
        return authService.login(request);
    }

    @POST
    @Path("/refresh")
    @Operation(summary = "Renovar el access token")
    public AuthResponse refresh(@Valid RefreshRequest request) {
        return authService.refresh(request);
    }

    @POST
    @Path("/logout")
    @Operation(summary = "Cerrar sesión (revoca el refresh token)")
    public MessageResponse logout(@Valid RefreshRequest request) {
        authService.logout(request);
        return new MessageResponse("Sesión cerrada");
    }

    @POST
    @Path("/forgot-password")
    @Operation(summary = "Pedir enlace de restablecimiento por email")
    public MessageResponse forgotPassword(@Valid ForgotPasswordRequest request) {
        return new MessageResponse(authService.forgotPassword(request));
    }

    @POST
    @Path("/reset-password")
    @Operation(summary = "Restablecer contraseña con token del email")
    public MessageResponse resetPassword(@Valid ResetPasswordRequest request) {
        authService.resetPassword(request);
        return new MessageResponse("Contraseña actualizada. Ya podés iniciar sesión.");
    }

    @GET
    @Path("/me")
    @Authenticated
    @Operation(summary = "Usuario autenticado")
    public UserResponse me() {
        return authService.me(currentUserId());
    }

    @PATCH
    @Path("/me")
    @Authenticated
    @Operation(summary = "Actualizar datos personales")
    public UserResponse updateProfile(@Valid UpdateProfileRequest request) {
        return authService.updateProfile(currentUserId(), request);
    }

    @POST
    @Path("/me/avatar")
    @Authenticated
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Actualizar la foto de perfil")
    public UserResponse updateAvatar(@RestForm("file") FileUpload file) {
        return authService.updateAvatar(currentUserId(), Uploads.read(file));
    }

    @POST
    @Path("/change-password")
    @Authenticated
    @Operation(summary = "Cambiar contraseña y revocar sesiones")
    public MessageResponse changePassword(@Valid ChangePasswordRequest request) {
        authService.changePassword(currentUserId(), request);
        return new MessageResponse("Contraseña actualizada");
    }

    private String currentUserId() {
        if (identity.isAnonymous()) {
            throw ApiException.unauthorized("UNAUTHORIZED", "Sesión inválida");
        }
        return jwt.getSubject();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static LocalDate parseBirthDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(value.trim());
            if (!date.isBefore(LocalDate.now())) {
                throw ApiException.badRequest(
                        "INVALID_DATE",
                        "La fecha de nacimiento no puede ser futura"
                );
            }
            return date;
        } catch (DateTimeParseException exception) {
            throw ApiException.badRequest("INVALID_DATE", "La fecha de nacimiento no es válida");
        }
    }
}
