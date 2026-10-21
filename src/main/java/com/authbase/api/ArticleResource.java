package com.authbase.api;

import com.authbase.api.dto.ArticleResponse;
import com.authbase.api.dto.ArticleSummary;
import com.authbase.api.dto.ArticleWriteRequest;
import com.authbase.api.dto.RecordViewRequest;
import com.authbase.api.dto.ViewCountResponse;
import com.authbase.domain.User;
import com.authbase.security.CurrentUser;
import com.authbase.service.ArticleService;
import io.quarkus.security.Authenticated;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;

@Path("/api/articles")
@Produces(MediaType.APPLICATION_JSON)
public class ArticleResource {

    private final ArticleService articleService;
    private final CurrentUser currentUser;

    public ArticleResource(ArticleService articleService, CurrentUser currentUser) {
        this.articleService = articleService;
        this.currentUser = currentUser;
    }

    @GET
    public List<ArticleSummary> list(@QueryParam("category") String category) {
        if (category == null || category.isBlank()) {
            User viewer = currentUser.optional();
            if (viewer != null) {
                return articleService.listFollowed(viewer);
            }
        }
        return articleService.listPublished(category);
    }

    @GET
    @Path("/mine")
    @Authenticated
    public List<ArticleResponse> mine() {
        return articleService.mine(currentUser.requireApprovedJournalist());
    }

    @GET
    @Path("/slug/{slug}")
    public ArticleResponse bySlug(@PathParam("slug") String slug) {
        return articleService.publishedBySlug(slug);
    }

    @POST
    @Path("/slug/{slug}/view")
    @Consumes(MediaType.APPLICATION_JSON)
    public ViewCountResponse view(@PathParam("slug") String slug, RecordViewRequest request) {
        String visitorId = request == null ? null : request.visitorId();
        return new ViewCountResponse(articleService.recordView(slug, currentUser.optional(), visitorId));
    }

    @GET
    @Path("/{id}")
    public ArticleResponse byId(@PathParam("id") String id) {
        return articleService.get(id, currentUser.optional());
    }

    @POST
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    public ArticleResponse create(@Valid ArticleWriteRequest request) {
        return articleService.create(currentUser.requireApprovedJournalist(), request);
    }

    @PUT
    @Path("/{id}")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    public ArticleResponse update(@PathParam("id") String id, @Valid ArticleWriteRequest request) {
        return articleService.update(id, currentUser.requireApprovedJournalist(), request);
    }

    @POST
    @Path("/{id}/publish")
    @Authenticated
    public ArticleResponse publish(@PathParam("id") String id) {
        return articleService.publish(id, currentUser.requireApprovedJournalist());
    }

    @POST
    @Path("/{id}/archive")
    @Authenticated
    public ArticleResponse archive(@PathParam("id") String id) {
        User actor = currentUser.require();
        return articleService.archive(id, actor);
    }

    @POST
    @Path("/{id}/cover")
    @Authenticated
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ArticleResponse cover(@PathParam("id") String id, @RestForm("file") FileUpload file) {
        return articleService.cover(id, currentUser.requireApprovedJournalist(), Uploads.read(file));
    }
}
