package com.authbase;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class JournalismFlowTest {

    @Test
    void journalistNeedsApprovalBeforePublishingAndSubscribersAreNotified() {
        String readerEmail = "reader-" + System.nanoTime() + "@example.com";
        String readerToken = registerReader(readerEmail);

        String journalistEmail = "journalist-" + System.nanoTime() + "@example.com";
        String journalistToken = registerJournalist(journalistEmail);

        given()
                .header("Authorization", "Bearer " + journalistToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"title":"Borrador temprano","content":"<p>Texto de prueba suficiente</p>"}
                        """)
                .when()
                .post("/api/articles")
                .then()
                .statusCode(403);

        String adminToken = login("admin@example.com", "Admin1234!");
        String journalistId = given()
                .header("Authorization", "Bearer " + adminToken)
                .queryParam("status", "PENDING")
                .when()
                .get("/api/admin/journalists")
                .then()
                .statusCode(200)
                .body("email", hasItem(journalistEmail))
                .extract()
                .path("find { it.email == '%s' }.id".formatted(journalistEmail));

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .post("/api/admin/journalists/" + journalistId + "/approve")
                .then()
                .statusCode(200)
                .body("verificationStatus", equalTo("APPROVED"));

        String categoryId = given()
                .when()
                .get("/api/categories")
                .then()
                .statusCode(200)
                .body("slug", hasItem("deportes"))
                .body("slug", hasItem("politica"))
                .body("slug", hasItem("tecnologia"))
                .extract()
                .path("find { it.slug == 'deportes' }.id");

        String articleId = given()
                .header("Authorization", "Bearer " + journalistToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "El clásico se define en los descuentos",
                          "content": "<p>Un relato verificado del partido.</p>",
                          "categoryId": "%s"
                        }
                        """.formatted(categoryId))
                .when()
                .post("/api/articles")
                .then()
                .statusCode(200)
                .body("status", equalTo("DRAFT"))
                .extract()
                .path("id");

        String slug = given()
                .header("Authorization", "Bearer " + journalistToken)
                .when()
                .post("/api/articles/" + articleId + "/publish")
                .then()
                .statusCode(200)
                .body("status", equalTo("PUBLISHED"))
                .extract()
                .path("slug");

        given()
                .when()
                .get("/api/articles/slug/" + slug)
                .then()
                .statusCode(200)
                .body("title", equalTo("El clásico se define en los descuentos"))
                .body("viewCount", equalTo(0));

        String visitor = "11111111-1111-4111-8111-111111111111";
        given()
                .contentType(ContentType.JSON)
                .body("{\"visitorId\":\"%s\"}".formatted(visitor))
                .when()
                .post("/api/articles/slug/" + slug + "/view")
                .then()
                .statusCode(200)
                .body("viewCount", equalTo(1));

        given()
                .contentType(ContentType.JSON)
                .body("{\"visitorId\":\"%s\"}".formatted(visitor))
                .when()
                .post("/api/articles/slug/" + slug + "/view")
                .then()
                .statusCode(200)
                .body("viewCount", equalTo(1));

        given()
                .header("Authorization", "Bearer " + journalistToken)
                .contentType(ContentType.JSON)
                .body("{\"visitorId\":\"22222222-2222-4222-8222-222222222222\"}")
                .when()
                .post("/api/articles/slug/" + slug + "/view")
                .then()
                .statusCode(200)
                .body("viewCount", equalTo(1));

        given()
                .header("Authorization", "Bearer " + readerToken)
                .when()
                .get("/api/articles")
                .then()
                .statusCode(200)
                .body("title", not(hasItem("El clásico se define en los descuentos")));

        given()
                .when()
                .get("/api/articles")
                .then()
                .statusCode(200)
                .body("title", hasItem("El clásico se define en los descuentos"));

        given()
                .header("Authorization", "Bearer " + readerToken)
                .when()
                .post("/api/journalists/" + journalistId + "/subscribe")
                .then()
                .statusCode(200)
                .body("subscribed", equalTo(true));

        given()
                .header("Authorization", "Bearer " + readerToken)
                .when()
                .post("/api/journalists/" + journalistId + "/follow")
                .then()
                .statusCode(200)
                .body("following", equalTo(true))
                .body("followers", greaterThanOrEqualTo(1));

        given()
                .header("Authorization", "Bearer " + readerToken)
                .when()
                .get("/api/articles")
                .then()
                .statusCode(200)
                .body("title", hasItem("El clásico se define en los descuentos"));

        String secondId = given()
                .header("Authorization", "Bearer " + journalistToken)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "La fecha se mueve al domingo",
                          "content": "<p>Cambio de calendario confirmado.</p>",
                          "categoryId": "%s"
                        }
                        """.formatted(categoryId))
                .when()
                .post("/api/articles")
                .then()
                .statusCode(200)
                .extract()
                .path("id");

        given()
                .header("Authorization", "Bearer " + journalistToken)
                .when()
                .post("/api/articles/" + secondId + "/publish")
                .then()
                .statusCode(200);

        given()
                .header("Authorization", "Bearer " + readerToken)
                .when()
                .get("/api/notifications")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("articleTitle", hasItem("La fecha se mueve al domingo"));

        given()
                .header("Authorization", "Bearer " + readerToken)
                .when()
                .get("/api/journalists/" + journalistId)
                .then()
                .statusCode(200)
                .body("followers", greaterThanOrEqualTo(1))
                .body("subscribers", greaterThanOrEqualTo(1))
                .body("following", equalTo(true))
                .body("articles.slug", hasItem(slug));
    }

    private static String registerReader(String email) {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "email": "%s",
                          "password": "Secret123",
                          "firstName": "Lector",
                          "lastName": "Demo"
                        }
                        """.formatted(email))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(200)
                .body("user.role", equalTo("READER"))
                .body("user.verificationStatus", equalTo("NONE"))
                .extract()
                .path("accessToken");
    }

    private static String registerJournalist(String email) {
        byte[] pdf = "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes(StandardCharsets.UTF_8);
        return given()
                .multiPart("email", email)
                .multiPart("password", "Secret123")
                .multiPart("firstName", "Lara")
                .multiPart("lastName", "Gomez")
                .multiPart("specialty", "Futbol")
                .multiPart("username", "laragomez")
                .multiPart("degree", "titulo.pdf", pdf, "application/pdf")
                .when()
                .post("/api/auth/register-journalist")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("user.role", equalTo("JOURNALIST"))
                .body("user.verificationStatus", equalTo("PENDING"))
                .body("user.username", equalTo("laragomez"))
                .extract()
                .path("accessToken");
    }

    private static String login(String email, String password) {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
    }
}
