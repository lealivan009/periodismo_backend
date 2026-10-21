package com.authbase.service;

import com.authbase.api.dto.JournalistApplicationResponse;
import com.authbase.api.dto.JournalistCardResponse;
import com.authbase.api.dto.JournalistProfileResponse;
import com.authbase.api.dto.RelationResponse;
import com.authbase.api.dto.UserResponse;
import com.authbase.domain.Follow;
import com.authbase.domain.Role;
import com.authbase.domain.Subscription;
import com.authbase.domain.User;
import com.authbase.domain.VerificationStatus;
import com.authbase.error.ApiException;
import com.authbase.helpers.MediaUrls;
import com.authbase.service.storage.MediaStore;
import com.authbase.service.storage.UploadedFile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@ApplicationScoped
public class JournalistService {

    private final ArticleService articleService;
    private final MediaStore mediaStore;

    public JournalistService(ArticleService articleService, MediaStore mediaStore) {
        this.articleService = articleService;
        this.mediaStore = mediaStore;
    }

    public List<JournalistCardResponse> listApproved() {
        return User.<User>find(
                        "role = ?1 and verificationStatus = ?2 and enabled = true and deletedAt is null order by lastName, firstName",
                        Role.JOURNALIST,
                        VerificationStatus.APPROVED
                )
                .page(0, 100)
                .list()
                .stream()
                .map(this::card)
                .toList();
    }

    @Transactional
    public JournalistProfileResponse profile(String id, User viewer) {
        User journalist = requirePublic(id);
        return new JournalistProfileResponse(
                journalist.id,
                journalist.firstName,
                journalist.lastName,
                journalist.username,
                journalist.bio,
                journalist.specialty,
                MediaUrls.avatar(journalist),
                journalist.xUrl,
                journalist.instagramUrl,
                journalist.facebookUrl,
                Follow.count("journalist.id", journalist.id),
                Subscription.count("journalist.id", journalist.id),
                articleService.countPublished(journalist.id),
                viewer != null && Follow.count("follower.id = ?1 and journalist.id = ?2", viewer.id, journalist.id) > 0,
                viewer != null && Subscription.count("subscriber.id = ?1 and journalist.id = ?2", viewer.id, journalist.id) > 0,
                articleService.byAuthorPublished(journalist.id)
        );
    }

    @Transactional
    public RelationResponse follow(User reader, String journalistId) {
        User journalist = requirePublic(journalistId);
        rejectSelf(reader, journalist);
        if (Follow.count("follower.id = ?1 and journalist.id = ?2", reader.id, journalist.id) == 0) {
            Follow follow = new Follow();
            follow.follower = reader;
            follow.journalist = journalist;
            follow.persist();
        }
        return relation(reader, journalist);
    }

    @Transactional
    public RelationResponse unfollow(User reader, String journalistId) {
        User journalist = requirePublic(journalistId);
        Follow.find("follower.id = ?1 and journalist.id = ?2", reader.id, journalist.id)
                .firstResultOptional()
                .ifPresent(follow -> follow.delete());
        return relation(reader, journalist);
    }

    @Transactional
    public RelationResponse subscribe(User reader, String journalistId) {
        User journalist = requirePublic(journalistId);
        rejectSelf(reader, journalist);
        if (Subscription.count("subscriber.id = ?1 and journalist.id = ?2", reader.id, journalist.id) == 0) {
            Subscription subscription = new Subscription();
            subscription.subscriber = reader;
            subscription.journalist = journalist;
            subscription.persist();
        }
        return relation(reader, journalist);
    }

    @Transactional
    public RelationResponse unsubscribe(User reader, String journalistId) {
        User journalist = requirePublic(journalistId);
        Subscription.find("subscriber.id = ?1 and journalist.id = ?2", reader.id, journalist.id)
                .firstResultOptional()
                .ifPresent(subscription -> subscription.delete());
        return relation(reader, journalist);
    }

    @Transactional
    public UserResponse replaceDegree(User user, UploadedFile file) {
        if (user.role != Role.JOURNALIST) {
            throw ApiException.forbidden("NOT_JOURNALIST", "Solo un periodista puede cargar el título");
        }
        if (user.verificationStatus == VerificationStatus.APPROVED) {
            throw ApiException.badRequest("ALREADY_APPROVED", "Tu cuenta ya está verificada");
        }
        if (file == null || file.bytes() == null || file.bytes().length == 0) {
            throw ApiException.badRequest("DEGREE_REQUIRED", "Adjuntá el título o la matrícula profesional");
        }
        String previous = user.degreeDocumentUrl;
        user.degreeDocumentUrl = mediaStore.saveDegree(user.id, file.fileName(), file.contentType(), file.bytes());
        user.verificationStatus = VerificationStatus.PENDING;
        mediaStore.deleteQuietly(previous);
        return UserResponse.from(user);
    }

    public List<JournalistApplicationResponse> applications(VerificationStatus status) {
        VerificationStatus filter = status == null ? VerificationStatus.PENDING : status;
        return User.<User>find(
                        "role = ?1 and verificationStatus = ?2 and deletedAt is null order by createdAt",
                        Role.JOURNALIST,
                        filter
                )
                .list()
                .stream()
                .map(JournalistApplicationResponse::from)
                .toList();
    }

    @Transactional
    public JournalistApplicationResponse approve(String id) {
        User user = requireApplication(id);
        user.verificationStatus = VerificationStatus.APPROVED;
        user.role = Role.JOURNALIST;
        return JournalistApplicationResponse.from(user);
    }

    @Transactional
    public JournalistApplicationResponse reject(String id) {
        User user = requireApplication(id);
        user.verificationStatus = VerificationStatus.REJECTED;
        return JournalistApplicationResponse.from(user);
    }

    @Transactional
    public User documentOwner(String id) {
        User user = User.findActiveById(id)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "No se encontró el periodista"));
        if (user.degreeDocumentUrl == null || user.degreeDocumentUrl.isBlank()) {
            throw ApiException.notFound("FILE_NOT_FOUND", "Esta solicitud no tiene documento");
        }
        return user;
    }

    private JournalistCardResponse card(User journalist) {
        return new JournalistCardResponse(
                journalist.id,
                journalist.firstName,
                journalist.lastName,
                journalist.username,
                journalist.bio,
                journalist.specialty,
                MediaUrls.avatar(journalist),
                journalist.xUrl,
                journalist.instagramUrl,
                journalist.facebookUrl,
                Follow.count("journalist.id", journalist.id),
                Subscription.count("journalist.id", journalist.id)
        );
    }

    private RelationResponse relation(User reader, User journalist) {
        return new RelationResponse(
                Follow.count("follower.id = ?1 and journalist.id = ?2", reader.id, journalist.id) > 0,
                Follow.count("journalist.id", journalist.id),
                Subscription.count("subscriber.id = ?1 and journalist.id = ?2", reader.id, journalist.id) > 0,
                Subscription.count("journalist.id", journalist.id)
        );
    }

    private Optional<User> findPublic(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        if (key.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            return User.findActiveById(key);
        }
        String username = key.trim().toLowerCase(Locale.ROOT);
        if (username.startsWith("@")) {
            username = username.substring(1);
        }
        return User.find("username = ?1 and deletedAt is null", username).firstResultOptional();
    }

    private User requirePublic(String key) {
        User user = findPublic(key)
                .orElseThrow(() -> ApiException.notFound("JOURNALIST_NOT_FOUND", "No se encontró el periodista"));
        if (user.role != Role.JOURNALIST || user.verificationStatus != VerificationStatus.APPROVED || !user.enabled) {
            throw ApiException.notFound("JOURNALIST_NOT_FOUND", "No se encontró el periodista");
        }
        return user;
    }

    private User requireApplication(String id) {
        User user = User.findActiveById(id)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "No se encontró el periodista"));
        if (user.role != Role.JOURNALIST) {
            throw ApiException.badRequest("NOT_JOURNALIST", "La cuenta no es de un periodista");
        }
        return user;
    }

    private static void rejectSelf(User reader, User journalist) {
        if (reader.id.equals(journalist.id)) {
            throw ApiException.badRequest("SELF_RELATION", "No podés seguirte ni suscribirte a tu propia cuenta");
        }
    }
}
