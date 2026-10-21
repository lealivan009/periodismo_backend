package com.authbase.api;

import com.authbase.api.dto.ArticleSummary;
import com.authbase.api.dto.AdminOverviewResponse;
import com.authbase.api.dto.AuthorStatsResponse;
import com.authbase.api.dto.CategoryResponse;
import com.authbase.api.dto.CreateCategoryRequest;
import com.authbase.api.dto.JournalistApplicationResponse;
import com.authbase.api.dto.UpdateCategoryRequest;
import com.authbase.domain.User;
import com.authbase.domain.VerificationStatus;
import com.authbase.security.CurrentUser;
import com.authbase.service.ArticleService;
import com.authbase.service.CategoryService;
import com.authbase.service.JournalistService;
import com.authbase.service.storage.MediaStore;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AdminEditorialResource {

    private final JournalistService journalistService;
    private final ArticleService articleService;
    private final CategoryService categoryService;
    private final MediaStore mediaStore;
    private final CurrentUser currentUser;

    public AdminEditorialResource(
            JournalistService journalistService,
            ArticleService articleService,
            CategoryService categoryService,
            MediaStore mediaStore,
            CurrentUser currentUser
    ) {
        this.journalistService = journalistService;
        this.articleService = articleService;
        this.categoryService = categoryService;
        this.mediaStore = mediaStore;
        this.currentUser = currentUser;
    }

    @GET
    @Path("/overview")
    public AdminOverviewResponse overview() {
        return articleService.overview();
    }

    @GET
    @Path("/journalists/{id}/stats")
    public AuthorStatsResponse stats(@PathParam("id") String id) {
        return articleService.stats(journalistService.requireJournalist(id));
    }

    @GET
    @Path("/journalists")
    public List<JournalistApplicationResponse> journalists(@QueryParam("status") VerificationStatus status) {
        return journalistService.applications(status);
    }

    @POST
    @Path("/journalists/{id}/approve")
    public JournalistApplicationResponse approve(@PathParam("id") String id) {
        return journalistService.approve(id);
    }

    @POST
    @Path("/journalists/{id}/reject")
    public JournalistApplicationResponse reject(@PathParam("id") String id) {
        return journalistService.reject(id);
    }

    @GET
    @Path("/journalists/{id}/document")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response document(@PathParam("id") String id) {
        User user = journalistService.documentOwner(id);
        String path = user.degreeDocumentUrl;
        return Response.ok(mediaStore.open(path), mediaStore.mediaType(path))
                .header("Content-Disposition", "inline; filename=\"titulo-" + user.id + "\"")
                .build();
    }

    @GET
    @Path("/articles")
    public List<ArticleSummary> articles() {
        return articleService.listPublished(null);
    }

    @POST
    @Path("/articles/{id}/archive")
    public ArticleSummary archive(@PathParam("id") String id) {
        return articleService.archive(id, currentUser.require()).summary();
    }

    @GET
    @Path("/categories")
    public List<CategoryResponse> categories() {
        return categoryService.listAll();
    }

    @POST
    @Path("/categories")
    @Consumes(MediaType.APPLICATION_JSON)
    public CategoryResponse createCategory(@Valid CreateCategoryRequest request) {
        return categoryService.create(request);
    }

    @PATCH
    @Path("/categories/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public CategoryResponse updateCategory(@PathParam("id") String id, @Valid UpdateCategoryRequest request) {
        return categoryService.update(id, request);
    }
}
