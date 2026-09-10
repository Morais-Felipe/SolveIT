package com.solveit.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solveit.security.JwtAuthenticationFilter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DirtiesContext
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=test",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class AuthHttpErrorIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_http_error_test")
            .withUsername("solveit")
            .withPassword("solveit-test");

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void returnsBadRequestProblemDetailsForMalformedJson() throws Exception {
        HttpResponse<String> response = post(
                "/api/auth/login",
                MediaType.APPLICATION_JSON_VALUE,
                "{");

        assertProblemDetails(
                response,
                400,
                "REQUEST_BODY_INVALID",
                "/api/auth/login");
    }

    @Test
    void returnsUnsupportedMediaTypeProblemDetailsForPlainText() throws Exception {
        HttpResponse<String> response = post(
                "/api/auth/register",
                MediaType.TEXT_PLAIN_VALUE,
                "not-json");

        assertProblemDetails(
                response,
                415,
                "MEDIA_TYPE_NOT_SUPPORTED",
                "/api/auth/register");
    }

    @Test
    void keepsDirectErrorUrlProtected() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/error")).GET().build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString());

        assertProblemDetails(response, 401, "AUTH_TOKEN_MISSING", "/error");
    }

    @Test
    void keepsJwtFilterOutOfAutomaticServletRegistration() {
        assertThat(jwtAuthenticationFilterRegistration.isEnabled()).isFalse();
    }

    private HttpResponse<String> post(String path, String contentType, String body)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private void assertProblemDetails(
            HttpResponse<String> response,
            int expectedStatus,
            String expectedCode,
            String expectedInstance) throws Exception {
        assertThat(response.statusCode()).isEqualTo(expectedStatus);
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE))
                .hasValueSatisfying(value -> assertThat(value)
                        .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE));

        JsonNode problem = objectMapper.readTree(response.body());
        assertThat(problem.path("status").asInt()).isEqualTo(expectedStatus);
        assertThat(problem.path("code").asText()).isEqualTo(expectedCode);
        assertThat(problem.path("instance").asText()).isEqualTo(expectedInstance);
        assertThat(response.body()).doesNotContain(
                "Exception",
                "org.springframework");
    }
}
