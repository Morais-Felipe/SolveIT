package com.solveit.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solveit.config.JwtProperties;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.repository.UsuarioRepository;
import com.solveit.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Diagnostic characterization, not regression approval: assertions describe the observed defects. */
@Testcontainers
@DirtiesContext
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=test", "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=validate", "solveit.security.bcrypt.strength=4"
})
class Phase2AuditProbeTest {
    @Container @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_phase2_audit");
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired UsuarioRepository users;
    @Autowired JwtProperties properties;
    @Autowired JwtService jwt;
    final HttpClient http = HttpClient.newHttpClient();

    @Test void observesSignedTokenWithoutExpirationAccepted() throws Exception {
        Usuario user = users.saveAndFlush(new Usuario("Audit", "jwt-audit@example.com",
                "unused-for-bearer", PerfilUsuario.SOLICITANTE));
        String token = Jwts.builder().subject(user.getId().toString())
                .issuedAt(Date.from(Instant.now().minusSeconds(86400)))
                .issuer(properties.getIssuer()).audience().add(properties.getAudience()).and()
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecret())), Jwts.SIG.HS256)
                .compact();
        HttpResponse<String> result = get("/api/auth/me", token);
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(jwt.parse(token).getExpiration()).isNull();
        System.out.println("AUDIT missing-exp: signed token issued yesterday, exp absent, HTTP=200");
    }

    @Test void observesInvalidEmailAccepted() throws Exception {
        HttpResponse<String> result = register("Audit", "not-an-email");
        assertThat(result.statusCode()).isEqualTo(201);
        assertThat(users.findByEmail("not-an-email")).isPresent();
        System.out.println("AUDIT invalid-email: persisted, HTTP=201");
    }

    @Test void observesOversizedNameAsInternalError() throws Exception {
        HttpResponse<String> result = register("a".repeat(121), "long-name@example.com");
        assertThat(result.statusCode()).isEqualTo(500);
        assertThat(mapper.readTree(result.body()).path("code").asText()).isEqualTo("INTERNAL_ERROR");
        System.out.println("AUDIT name-121: HTTP=500, code=INTERNAL_ERROR");
    }

    @Test void observesOversizedEmailAsInternalError() throws Exception {
        HttpResponse<String> result = register("Audit", "a".repeat(244) + "@example.com");
        assertThat(result.statusCode()).isEqualTo(500);
        System.out.println("AUDIT email-256: HTTP=500");
    }

    @Test void observesMalformedJsonThroughRealErrorDispatch() throws Exception {
        HttpResponse<String> result = post("/api/auth/login", "{", "application/json");
        assertThat(result.statusCode()).isEqualTo(401);
        assertThat(mapper.readTree(result.body()).path("code").asText()).isEqualTo("AUTH_TOKEN_MISSING");
        System.out.println("AUDIT malformed-json: HTTP=401, code=AUTH_TOKEN_MISSING, instance="
                + mapper.readTree(result.body()).path("instance").asText());
    }

    @Test void observesUnsupportedMediaTypeThroughRealErrorDispatch() throws Exception {
        HttpResponse<String> result = post("/api/auth/register", "hello", "text/plain");
        assertThat(result.statusCode()).isEqualTo(401);
        System.out.println("AUDIT media-type: HTTP=401, code=" + mapper.readTree(result.body()).path("code").asText());
    }

    @Test void checksRealHttpHappyPathAndNoCookie() throws Exception {
        assertThat(register("Audit HTTP", "http-audit@example.com").statusCode()).isEqualTo(201);
        HttpResponse<String> login = post("/api/auth/login", mapper.writeValueAsString(Map.of(
                "email", "http-audit@example.com", "senha", "audit password 12345")), "application/json");
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().allValues("Set-Cookie")).isEmpty();
        String token = mapper.readTree(login.body()).path("accessToken").asText();
        assertThat(get("/api/auth/me", token).statusCode()).isEqualTo(200);
        assertThat(get("/api/auth/me", null).statusCode()).isEqualTo(401);
        System.out.println("AUDIT normal-http: register=201, login=200, me=200, anonymous=401, no-cookie");
    }

    HttpResponse<String> register(String name, String email) throws Exception {
        return post("/api/auth/register", mapper.writeValueAsString(Map.of(
                "nome", name, "email", email, "senha", "audit password 12345")), "application/json");
    }
    HttpResponse<String> post(String path, String body, String contentType) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
