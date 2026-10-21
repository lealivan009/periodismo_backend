package com.authbase.security;

import com.authbase.domain.User;
import com.authbase.error.ApiException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import org.eclipse.microprofile.jwt.JsonWebToken;

@RequestScoped
public class CurrentUser {

    private final SecurityIdentity identity;
    private final JsonWebToken jwt;

    public CurrentUser(SecurityIdentity identity, JsonWebToken jwt) {
        this.identity = identity;
        this.jwt = jwt;
    }

    public User optional() {
        if (identity == null || identity.isAnonymous()) {
            return null;
        }
        String id = jwt == null ? null : jwt.getSubject();
        if (id == null || id.isBlank()) {
            return null;
        }
        return User.findActiveById(id).filter(user -> user.enabled).orElse(null);
    }

    public User require() {
        User user = optional();
        if (user == null) {
            throw ApiException.unauthorized("UNAUTHORIZED", "Sesión inválida");
        }
        return user;
    }

    public User requireApprovedJournalist() {
        User user = require();
        if (!user.canPublish()) {
            throw ApiException.forbidden(
                    "JOURNALIST_NOT_APPROVED",
                    "Solo un periodista verificado puede redactar y publicar"
            );
        }
        return user;
    }
}
