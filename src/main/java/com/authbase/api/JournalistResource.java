package com.authbase.api;

import com.authbase.api.dto.JournalistCardResponse;
import com.authbase.api.dto.JournalistProfileResponse;
import com.authbase.api.dto.RelationResponse;
import com.authbase.api.dto.UserResponse;
import com.authbase.security.CurrentUser;
import com.authbase.service.JournalistService;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;

@Path("/api/journalists")
@Produces(MediaType.APPLICATION_JSON)
public class JournalistResource {

    private final JournalistService journalistService;
    private final CurrentUser currentUser;

    public JournalistResource(JournalistService journalistService, CurrentUser currentUser) {
        this.journalistService = journalistService;
        this.currentUser = currentUser;
    }

    @GET
    public List<JournalistCardResponse> list() {
        return journalistService.listApproved();
    }

    @GET
    @Path("/{id}")
    public JournalistProfileResponse profile(@PathParam("id") String id) {
        return journalistService.profile(id, currentUser.optional());
    }

    @POST
    @Path("/{id}/follow")
    @Authenticated
    public RelationResponse follow(@PathParam("id") String id) {
        return journalistService.follow(currentUser.require(), id);
    }

    @DELETE
    @Path("/{id}/follow")
    @Authenticated
    public RelationResponse unfollow(@PathParam("id") String id) {
        return journalistService.unfollow(currentUser.require(), id);
    }

    @POST
    @Path("/{id}/subscribe")
    @Authenticated
    public RelationResponse subscribe(@PathParam("id") String id) {
        return journalistService.subscribe(currentUser.require(), id);
    }

    @DELETE
    @Path("/{id}/subscribe")
    @Authenticated
    public RelationResponse unsubscribe(@PathParam("id") String id) {
        return journalistService.unsubscribe(currentUser.require(), id);
    }

    @POST
    @Path("/me/degree")
    @Authenticated
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public UserResponse replaceDegree(@RestForm("degree") FileUpload degree) {
        return journalistService.replaceDegree(currentUser.require(), Uploads.read(degree));
    }
}
