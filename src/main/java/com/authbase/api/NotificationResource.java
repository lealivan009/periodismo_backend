package com.authbase.api;

import com.authbase.api.dto.NotificationResponse;
import com.authbase.security.CurrentUser;
import com.authbase.service.NotificationService;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class NotificationResource {

    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    public NotificationResource(NotificationService notificationService, CurrentUser currentUser) {
        this.notificationService = notificationService;
        this.currentUser = currentUser;
    }

    @GET
    public List<NotificationResponse> list() {
        return notificationService.list(currentUser.require());
    }

    @POST
    @Path("/{id}/read")
    public NotificationResponse markRead(@PathParam("id") String id) {
        return notificationService.markRead(currentUser.require(), id);
    }
}
