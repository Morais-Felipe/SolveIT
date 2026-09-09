package com.solveit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.repository.UsuarioRepository;
import com.solveit.security.UsuarioPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
@Import(AuthSecurityIntegrationTest.TestClockConfiguration.class)
class AuthSecurityIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-09T18:00:00Z");
    private static final String RAW_PASSWORD = "senha segura para bearer 123";

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
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private MutableClock clock;

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
        usuarioRepository.saveAndFlush(usuario);

        FilterObservation afterProfileChange = filter(login.accessToken(), null);
        assertThat(afterProfileChange.authentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");

        usuario.setAtivo(false);
        usuarioRepository.saveAndFlush(usuario);

        FilterObservation whileInactive = filter(login.accessToken(), null);
        assertThat(whileInactive.status()).isEqualTo(401);
        assertThat(whileInactive.authentication()).isNull();

        usuario.setAtivo(true);
        usuarioRepository.saveAndFlush(usuario);

        FilterObservation afterReactivation = filter(login.accessToken(), null);
        assertThat(afterReactivation.status()).isEqualTo(200);
        assertThat(afterReactivation.authentication()).isNotNull();
    }

    @Test
    void reactivatedAccountCannotReuseAnExpiredToken() throws Exception {
        LoginResult login = registerAndLogin("expira@example.com", "Expiracao");
        Usuario usuario = usuarioRepository.findById(login.usuarioId()).orElseThrow();

        usuario.setAtivo(false);
        usuarioRepository.saveAndFlush(usuario);
        clock.advance(Duration.ofMinutes(30));
        usuario.setAtivo(true);
        usuarioRepository.saveAndFlush(usuario);

        assertThat(filter(login.accessToken(), null).status()).isEqualTo(200);

        clock.advance(Duration.ofMinutes(30).plusSeconds(1));

        FilterObservation afterExpiration = filter(login.accessToken(), null);
        assertThat(afterExpiration.status()).isEqualTo(401);
        assertThat(afterExpiration.authentication()).isNull();
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

    private FilterObservation filter(String accessToken, CyclicBarrier barrier) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/security-probe");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
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
                contextCleared);
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
            boolean contextClearedAfterRequest) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(NOW);
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
