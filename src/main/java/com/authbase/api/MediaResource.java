package com.authbase.api;

import com.authbase.domain.User;
import com.authbase.error.ApiException;
import com.authbase.service.ArticleService;
import com.authbase.service.storage.MediaStore;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/media")
public class MediaResource {

    private final MediaStore mediaStore;
    private final ArticleService articleService;

    public MediaResource(MediaStore mediaStore, ArticleService articleService) {
        this.mediaStore = mediaStore;
        this.articleService = articleService;
    }

    @GET
    @Path("/avatars/{userId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response avatar(@PathParam("userId") String userId) {
        User user = User.findActiveById(userId)
                .orElseThrow(() -> ApiException.notFound("FILE_NOT_FOUND", "No se encontró la imagen"));
        if (user.avatarUrl == null || user.avatarUrl.isBlank()) {
            throw ApiException.notFound("FILE_NOT_FOUND", "No se encontró la imagen");
        }
        return stream(user.avatarUrl, "avatar-" + user.id);
    }

    @GET
    @Path("/covers/{articleId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response cover(@PathParam("articleId") String articleId) {
        String path = articleService.coverPath(articleId);
        return stream(path, "tapa-" + articleId);
    }

    private Response stream(String relative, String filename) {
        return Response.ok(mediaStore.open(relative), mediaStore.mediaType(relative))
                .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
                .header("Cache-Control", "public, max-age=31536000, immutable")
                .build();
    }
}
