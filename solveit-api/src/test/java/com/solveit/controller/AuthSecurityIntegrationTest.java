package com.solveit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solveit.config.JwtProperties;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.repository.UsuarioRepository;
import com.solveit.security.UsuarioPrincipal;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@Import({
        AuthSecurityIntegrationTest.TestClockConfiguration.class,
        AuthSecurityIntegrationTest.AuthorizationProbeConfiguration.class
})
@ExtendWith(OutputCaptureExtension.class)
class AuthSecurityIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-09T18:00:00Z");
    private static final String RAW_PASSWORD = "senha segura para bearer 123";
    private static final String AUTHENTICATION_FAILURE_ATTRIBUTE =
            "com.solveit.security.AuthenticationFailureReason";
    private static final String OTHER_SIGNING_MATERIAL = "fedcba9876543210".repeat(4);

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_security_test")
            .withUsername("solveit")
            .withPassword("solveit-test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private MutableClock clock;

    @Autowired(required = false)
    private AccessDeniedHandler restAccessDeniedHandler;

    @BeforeEach
    void resetState() {
        usuarioRepository.deleteAll();
        clock.setInstant(NOW);
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesBearerPerRequestWithCurrentActiveUserAndNoSession() throws Exception {
        LoginResult login = registerAndLogin("felipe@example.com", "Felipe");

        FilterObservation observation = filter(login.accessToken(), null);

        assertThat(observation.status()).isEqualTo(200);
        assertThat(observation.sessionCreated()).isFalse();
        assertThat(observation.contextClearedAfterRequest()).isTrue();
        assertThat(observation.authentication()).isNotNull();
        assertThat(observation.authentication().isAuthenticated()).isTrue();
        assertThat(observation.authentication().getPrincipal()).isInstanceOf(UsuarioPrincipal.class);

        UsuarioPrincipal principal = (UsuarioPrincipal) observation.authentication().getPrincipal();
        assertThat(principal.getId()).isEqualTo(login.usuarioId());
        assertThat(principal.getUsername()).isEqualTo("felipe@example.com");
        assertThat(principal.getPassword()).isNull();
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_SOLICITANTE");
    }

    @Test
    void reloadsProfileAndActiveStateOnEveryRequest() throws Exception {
        LoginResult login = registerAndLogin("estado@example.com", "Estado Atual");
        Usuario usuario = usuarioRepository.findById(login.usuarioId()).orElseThrow();

        usuario.setPerfil(PerfilUsuario.ADMIN);
        usuario = usuarioRepository.saveAndFlush(usuario);

        FilterObservation afterProfileChange = filter(login.accessToken(), null);
        assertThat(afterProfileChange.authentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");

        usuario.setAtivo(false);
        usuario = usuarioRepository.saveAndFlush(usuario);

        FilterObservation whileInactive = filter(login.accessToken(), null);
        assertThat(whileInactive.status()).isEqualTo(401);
        assertThat(whileInactive.authentication()).isNull();
        assertThat(whileInactive.failureReason()).hasToString("ACCOUNT_INACTIVE");

        usuario.setAtivo(true);
        usuario = usuarioRepository.saveAndFlush(usuario);

        FilterObservation afterReactivation = filter(login.accessToken(), null);
        assertThat(afterReactivation.status()).isEqualTo(200);
        assertThat(afterReactivation.authentication()).isNotNull();

        usuarioRepository.deleteById(usuario.getId());
        usuarioRepository.flush();

        FilterObservation afterDeletion = filter(login.accessToken(), null);
        assertThat(afterDeletion.status()).isEqualTo(401);
        assertThat(afterDeletion.authentication()).isNull();
        assertThat(afterDeletion.failureReason()).hasToString("TOKEN_INVALID");
    }

    @Test
    void reactivatedAccountCannotReuseAnExpiredToken() throws Exception {
        LoginResult login = registerAndLogin("expira@example.com", "Expiracao");
        Usuario usuario = usuarioRepository.findById(login.usuarioId()).orElseThrow();

        usuario.setAtivo(false);
        usuario = usuarioRepository.saveAndFlush(usuario);
        clock.advance(Duration.ofMinutes(30));
        usuario.setAtivo(true);
        usuario = usuarioRepository.saveAndFlush(usuario);

        assertThat(filter(login.accessToken(), null).status()).isEqualTo(200);

        clock.advance(Duration.ofMinutes(30).plusSeconds(1));

        FilterObservation afterExpiration = filter(login.accessToken(), null);
        assertThat(afterExpiration.status()).isEqualTo(401);
        assertThat(afterExpiration.authentication()).isNull();
        assertThat(afterExpiration.failureReason()).hasToString("TOKEN_EXPIRED");
    }

    @Test
    void classifiesEveryInvalidBearerShapeWithoutAuthenticatingTheRequest() throws Exception {
        LoginResult login = registerAndLogin("matriz@example.com", "Matriz JWT");

        assertRejectedWithReason(filterAuthorization(null, null), null);
        assertRejectedWithReason(filterAuthorization("Basic credentials", null), "TOKEN_INVALID");
        assertRejectedWithReason(filterAuthorization("Bearer not-a-jwt", null), "TOKEN_INVALID");
        assertRejectedWithReason(filterAuthorization(
                "Bearer " + signedToken(
                        "42",
                        signingKey(OTHER_SIGNING_MATERIAL),
                        Jwts.SIG.HS256,
                        jwtProperties.getIssuer(),
                        jwtProperties.getAudience()),
                null), "TOKEN_INVALID");
        assertRejectedWithReason(filterAuthorization(
                "Bearer " + signedToken(
                        "42",
                        signingKey(OTHER_SIGNING_MATERIAL),
                        Jwts.SIG.HS384,
                        jwtProperties.getIssuer(),
                        jwtProperties.getAudience()),
                null), "TOKEN_INVALID");
        assertRejectedWithReason(filterAuthorization(
                "Bearer " + signedToken(
                        "42",
                        configuredSigningKey(),
                        Jwts.SIG.HS256,
                        "unexpected-issuer",
                        jwtProperties.getAudience()),
                null), "TOKEN_INVALID");
        assertRejectedWithReason(filterAuthorization(
                "Bearer " + signedToken(
                        "42",
                        configuredSigningKey(),
                        Jwts.SIG.HS256,
                        jwtProperties.getIssuer(),
                        "unexpected-audience"),
                null), "TOKEN_INVALID");
        assertRejectedWithReason(filterAuthorization(
                "Bearer " + signedToken(
                        "not-a-number",
                        configuredSigningKey(),
                        Jwts.SIG.HS256,
                        jwtProperties.getIssuer(),
                        jwtProperties.getAudience()),
                null), "TOKEN_INVALID");

        usuarioRepository.deleteById(login.usuarioId());
        usuarioRepository.flush();
        assertRejectedWithReason(filter(login.accessToken(), null), "TOKEN_INVALID");
    }

    @Test
    void keepsParallelRequestSecurityContextsIndependent() throws Exception {
        LoginResult first = registerAndLogin("primeiro@example.com", "Primeiro");
        LoginResult second = registerAndLogin("segundo@example.com", "Segundo");
        Usuario secondUser = usuarioRepository.findById(second.usuarioId()).orElseThrow();
        secondUser.setPerfil(PerfilUsuario.ATENDENTE);
        usuarioRepository.saveAndFlush(secondUser);

        CyclicBarrier bothRequestsAuthenticated = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<FilterObservation> firstFuture = executor.submit(
                    () -> filter(first.accessToken(), bothRequestsAuthenticated));
            Future<FilterObservation> secondFuture = executor.submit(
                    () -> filter(second.accessToken(), bothRequestsAuthenticated));

            FilterObservation firstObservation = firstFuture.get(10, TimeUnit.SECONDS);
            FilterObservation secondObservation = secondFuture.get(10, TimeUnit.SECONDS);

            assertPrincipal(firstObservation, first.usuarioId(), "ROLE_SOLICITANTE");
            assertPrincipal(secondObservation, second.usuarioId(), "ROLE_ATENDENTE");
            assertThat(firstObservation.contextClearedAfterRequest()).isTrue();
            assertThat(secondObservation.contextClearedAfterRequest()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void returnsOnlyTheAuthenticatedUsersCurrentPublicDataFromMe() throws Exception {
        LoginResult authenticated = registerAndLogin("proprio@example.com", "Nome Antigo");
        LoginResult anotherUser = registerAndLogin("outro@example.com", "Outro Usuario");
        Usuario currentUser = usuarioRepository.findById(authenticated.usuarioId()).orElseThrow();
        currentUser.setNome("Nome Atualizado");
        currentUser.setPerfil(PerfilUsuario.ADMIN);
        usuarioRepository.saveAndFlush(currentUser);

        MvcResult result = mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authenticated.accessToken())
                        .queryParam("userId", anotherUser.usuarioId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(authenticated.usuarioId()))
                .andExpect(jsonPath("$.nome").value("Nome Atualizado"))
                .andExpect(jsonPath("$.email").value("proprio@example.com"))
                .andExpect(jsonPath("$.perfil").value("ADMIN"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.criadoEm").isNotEmpty())
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.hash").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        List<String> responseFields = new ArrayList<>();
        response.fieldNames().forEachRemaining(responseFields::add);
        assertThat(responseFields).containsExactlyInAnyOrder(
                "id", "nome", "email", "perfil", "ativo", "criadoEm");
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void returnsProblemDetailsWhenBearerIsMissing() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertProblemDetails(
                result.getResponse(),
                401,
                "AUTH_TOKEN_MISSING",
                "Autenticação necessária",
                "Autenticação necessária.",
                "/api/auth/me");
    }

    @Test
    void returnsInvalidProblemDetailsWithoutLeakingBearerInternals(CapturedOutput output)
            throws Exception {
        String malformedCredential = "Bearer leak-marker.not-a-jwt";
        int capturedBeforeRequest = output.getAll().length();

        MvcResult result = mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, malformedCredential))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertProblemDetails(
                result.getResponse(),
                401,
                "AUTH_TOKEN_INVALID",
                "Autenticação inválida",
                "Sua autenticação não é válida. Entre novamente.",
                "/api/auth/me");
        assertThat(output.getAll().substring(capturedBeforeRequest))
                .doesNotContain(
                        malformedCredential,
                        jwtProperties.getSecret(),
                        "Authorization",
                        "MalformedJwtException",
                        "io.jsonwebtoken");
    }

    @Test
    void returnsExpiredProblemDetailsForExpiredBearer() throws Exception {
        LoginResult login = registerAndLogin("http-expira@example.com", "Expira HTTP");
        clock.advance(Duration.ofHours(1).plusSeconds(1));

        MvcResult result = mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertProblemDetails(
                result.getResponse(),
                401,
                "AUTH_TOKEN_EXPIRED",
                "Sessão expirada",
                "Sua sessão expirou. Entre novamente para continuar.",
                "/api/auth/me");
    }

    @Test
    void returnsInactiveAccountProblemDetailsOnlyAfterValidBearerIdentification() throws Exception {
        LoginResult login = registerAndLogin("http-inativo@example.com", "Inativo HTTP");
        Usuario usuario = usuarioRepository.findById(login.usuarioId()).orElseThrow();
        usuario.setAtivo(false);
        usuarioRepository.saveAndFlush(usuario);

        MvcResult result = mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertProblemDetails(
                result.getResponse(),
                401,
                "AUTH_ACCOUNT_INACTIVE",
                "Conta inativa",
                "Sua conta está inativa. Entre em contato com um administrador.",
                "/api/auth/me");
    }

    @Test
    void returnsAccessDeniedProblemDetailsForAuthenticatedIdentity() throws Exception {
        assertThat(restAccessDeniedHandler).isNotNull();
        LoginResult login = registerAndLogin("sem-permissao@example.com", "Sem Permissao");
        Usuario usuario = usuarioRepository.findById(login.usuarioId()).orElseThrow();
        UsuarioPrincipal principal = UsuarioPrincipal.withoutCredentials(usuario);
        Authentication authentication =
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                        .authenticated(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/probe");
        MockHttpServletResponse response = new MockHttpServletResponse();

        restAccessDeniedHandler.handle(
                request,
                response,
                new AccessDeniedException("denied only inside the test"));

        assertProblemDetails(
                response,
                403,
                "ACCESS_DENIED",
                "Acesso negado",
                "Você não tem permissão para realizar esta operação.",
                "/api/admin/probe");
    }

    @Test
    void deniesAnonymousBeforeTheAuthorizationProbe() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/test/authorization/{resourceId}", 999_999L))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertProblemDetails(
                result.getResponse(),
                401,
                "AUTH_TOKEN_MISSING",
                "Autenticação necessária",
                "Autenticação necessária.",
                "/api/test/authorization/999999");
    }

    @Test
    void deniesCurrentNonAdminProfileAtTheMethodBoundary() throws Exception {
        LoginResult solicitante = registerAndLogin(
                "perfil-negado@example.com",
                "Perfil Negado");

        MvcResult result = mockMvc.perform(get(
                                "/api/test/authorization/{resourceId}",
                                solicitante.usuarioId())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + solicitante.accessToken()))
                .andExpect(status().isForbidden())
                .andReturn();

        assertProblemDetails(
                result.getResponse(),
                403,
                "ACCESS_DENIED",
                "Acesso negado",
                "Você não tem permissão para realizar esta operação.",
                "/api/test/authorization/" + solicitante.usuarioId());
    }

    @Test
    void deniesAdminWhenPersistedRelationshipDoesNotMatchEvenWithForgedUserId()
            throws Exception {
        LoginResult admin = registerAndLogin("admin-negado@example.com", "Admin Negado");
        promoteToAdmin(admin.usuarioId());
        LoginResult resourceOwner = registerAndLogin(
                "dono-recurso@example.com",
                "Dono Recurso");

        MvcResult result = mockMvc.perform(get(
                                "/api/test/authorization/{resourceId}",
                                resourceOwner.usuarioId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.accessToken())
                        .queryParam("userId", resourceOwner.usuarioId().toString()))
                .andExpect(status().isForbidden())
                .andReturn();

        assertProblemDetails(
                result.getResponse(),
                403,
                "ACCESS_DENIED",
                "Acesso negado",
                "Você não tem permissão para realizar esta operação.",
                "/api/test/authorization/" + resourceOwner.usuarioId());
    }

    @Test
    void allowsAdminWhenPersistedRelationshipMatchesAndIgnoresForgedUserId()
            throws Exception {
        LoginResult admin = registerAndLogin("admin-permitido@example.com", "Admin Permitido");
        promoteToAdmin(admin.usuarioId());
        LoginResult forgedIdentity = registerAndLogin(
                "identidade-forjada@example.com",
                "Identidade Forjada");

        MvcResult result = mockMvc.perform(get(
                                "/api/test/authorization/{resourceId}",
                                admin.usuarioId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.accessToken())
                        .queryParam("userId", forgedIdentity.usuarioId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principalId").value(admin.usuarioId()))
                .andExpect(jsonPath("$.resourceOwnerId").value(admin.usuarioId()))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    private LoginResult registerAndLogin(String email, String nome) throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                nome,
                                email,
                                RAW_PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn();
        Long usuarioId = objectMapper.readTree(registration.getResponse().getContentAsString())
                .path("id")
                .asLong();

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(
                                email,
                                RAW_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode loginBody = objectMapper.readTree(login.getResponse().getContentAsString());

        return new LoginResult(usuarioId, loginBody.path("accessToken").asText());
    }

    private void promoteToAdmin(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        usuario.setPerfil(PerfilUsuario.ADMIN);
        usuarioRepository.saveAndFlush(usuario);
    }

    private FilterObservation filter(String accessToken, CyclicBarrier barrier) throws Exception {
        return filterAuthorization("Bearer " + accessToken, barrier);
    }

    private FilterObservation filterAuthorization(String authorization, CyclicBarrier barrier) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/security-probe");
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Authentication> observedAuthentication = new AtomicReference<>();

        FilterChain terminalChain = (servletRequest, servletResponse) -> {
            awaitBarrier(barrier);
            observedAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
        };

        springSecurityFilterChain.doFilter(request, response, terminalChain);
        boolean contextCleared = SecurityContextHolder.getContext().getAuthentication() == null;

        return new FilterObservation(
                response.getStatus(),
                observedAuthentication.get(),
                request.getSession(false) != null,
                contextCleared,
                request.getAttribute(AUTHENTICATION_FAILURE_ATTRIBUTE));
    }

    private static void awaitBarrier(CyclicBarrier barrier) throws ServletException {
        if (barrier == null) {
            return;
        }

        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new ServletException("Parallel security requests did not meet", exception);
        }
    }

    private static void assertPrincipal(
            FilterObservation observation,
            Long expectedId,
            String expectedAuthority) {
        assertThat(observation.status()).isEqualTo(200);
        assertThat(observation.sessionCreated()).isFalse();
        assertThat(observation.authentication()).isNotNull();
        UsuarioPrincipal principal = (UsuarioPrincipal) observation.authentication().getPrincipal();
        assertThat(principal.getId()).isEqualTo(expectedId);
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactly(expectedAuthority);
    }

    private static void assertRejectedWithReason(
            FilterObservation observation,
            String expectedFailureReason) {
        assertThat(observation.status()).isEqualTo(401);
        assertThat(observation.authentication()).isNull();
        assertThat(observation.sessionCreated()).isFalse();
        assertThat(observation.contextClearedAfterRequest()).isTrue();
        if (expectedFailureReason == null) {
            assertThat(observation.failureReason()).isNull();
        } else {
            assertThat(observation.failureReason()).hasToString(expectedFailureReason);
        }
    }

    private void assertProblemDetails(
            MockHttpServletResponse response,
            int expectedStatus,
            String expectedCode,
            String expectedTitle,
            String expectedDetail,
            String expectedInstance) throws Exception {
        assertThat(response.getStatus()).isEqualTo(expectedStatus);
        assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());

        String responseBody = response.getContentAsString();
        JsonNode problem = objectMapper.readTree(responseBody);
        assertThat(problem.path("type").asText())
                .isEqualTo("urn:solveit:error:" + expectedCode.toLowerCase().replace('_', '-'));
        assertThat(problem.path("title").asText()).isEqualTo(expectedTitle);
        assertThat(problem.path("status").asInt()).isEqualTo(expectedStatus);
        assertThat(problem.path("detail").asText()).isEqualTo(expectedDetail);
        assertThat(problem.path("instance").asText()).isEqualTo(expectedInstance);
        assertThat(problem.path("code").asText()).isEqualTo(expectedCode);
        assertThat(problem.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("type", "title", "status", "detail", "instance", "code");
        assertThat(responseBody).doesNotContain(
                "Bearer ",
                jwtProperties.getSecret(),
                "io.jsonwebtoken",
                "Exception",
                "SELECT ",
                "select ");
    }

    private SecretKey configuredSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret()));
    }

    private SecretKey signingKey(String material) {
        return Keys.hmacShaKeyFor(material.getBytes(StandardCharsets.UTF_8));
    }

    private String signedToken(
            String subject,
            SecretKey key,
            io.jsonwebtoken.security.MacAlgorithm algorithm,
            String issuer,
            String audience) {
        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plus(Duration.ofHours(1))))
                .issuer(issuer)
                .audience().add(audience).and()
                .signWith(key, algorithm)
                .compact();
    }

    private record RegisterPayload(String nome, String email, String senha) {
    }

    private record LoginPayload(String email, String senha) {
    }

    private record LoginResult(Long usuarioId, String accessToken) {
    }

    private record FilterObservation(
            int status,
            Authentication authentication,
            boolean sessionCreated,
            boolean contextClearedAfterRequest,
            Object failureReason) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(NOW);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AuthorizationProbeConfiguration {

        @Bean
        AuthorizationProbeService authorizationProbeService(UsuarioRepository usuarioRepository) {
            return new AuthorizationProbeService(usuarioRepository);
        }

        @Bean
        AuthorizationProbeController authorizationProbeController(
                AuthorizationProbeService authorizationProbeService) {
            return new AuthorizationProbeController(authorizationProbeService);
        }
    }

    @RestController
    static class AuthorizationProbeController {

        private final AuthorizationProbeService authorizationProbeService;

        AuthorizationProbeController(AuthorizationProbeService authorizationProbeService) {
            this.authorizationProbeService = authorizationProbeService;
        }

        @GetMapping("/api/test/authorization/{resourceId}")
        @PreAuthorize("hasRole('ADMIN')")
        public Map<String, Long> authorize(
                @PathVariable Long resourceId,
                @AuthenticationPrincipal UsuarioPrincipal principal) {
            Long resourceOwnerId = authorizationProbeService.authorize(principal, resourceId);
            return Map.of(
                    "principalId", principal.getId(),
                    "resourceOwnerId", resourceOwnerId);
        }
    }

    static class AuthorizationProbeService {

        private final UsuarioRepository usuarioRepository;

        AuthorizationProbeService(UsuarioRepository usuarioRepository) {
            this.usuarioRepository = usuarioRepository;
        }

        @Transactional(readOnly = true)
        public Long authorize(UsuarioPrincipal principal, Long resourceId) {
            Usuario resourceOwner = usuarioRepository.findById(resourceId).orElseThrow();
            if (!resourceOwner.getId().equals(principal.getId())) {
                throw new AccessDeniedException("Authenticated user is not related to the resource");
            }
            return resourceOwner.getId();
        }
    }

    static final class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;

        MutableClock(Instant initialInstant) {
            this.instant = new AtomicReference<>(initialInstant);
        }

        void setInstant(Instant newInstant) {
            instant.set(newInstant);
        }

        void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return zone.equals(ZoneOffset.UTC) ? this : Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
