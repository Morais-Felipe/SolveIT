package com.solveit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.solveit.security.JwtService;
import com.solveit.security.UsuarioPrincipal;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
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
@ExtendWith(OutputCaptureExtension.class)
class AuthLoginIntegrationTest {

    private static final String RAW_PASSWORD = "senha exata com espaços 123";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_login_test")
            .withUsername("solveit")
            .withPassword("solveit-test");

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    @BeforeEach
    void cleanDatabase() {
        usuarioRepository.deleteAll();
    }

    @Test
    void authenticatesActiveUserWithNormalizedEmailAndErasesCredentials() {
        Usuario saved = saveUser("felipe@example.com", RAW_PASSWORD, true);

        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "  FELIPE@Example.COM  ",
                        RAW_PASSWORD));

        assertThat(authenticationManager).isInstanceOf(ProviderManager.class);
        ProviderManager providerManager = (ProviderManager) authenticationManager;
        assertThat(providerManager.getProviders())
                .singleElement()
                .isInstanceOf(DaoAuthenticationProvider.class);
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(UsuarioPrincipal.class);

        UsuarioPrincipal principal = (UsuarioPrincipal) authentication.getPrincipal();
        assertThat(principal.getId()).isEqualTo(saved.getId());
        assertThat(principal.getUsername()).isEqualTo("felipe@example.com");
        assertThat(principal.getPerfil()).isEqualTo(PerfilUsuario.SOLICITANTE);
        assertThat(principal.getPassword()).isNull();
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_SOLICITANTE");
    }

    @Test
    void rejectsIncorrectPasswordThroughDaoAuthenticationProvider() {
        saveUser("felipe@example.com", RAW_PASSWORD, true);

        assertThatThrownBy(() -> authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "felipe@example.com",
                        "senha incorreta 123")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsInactiveAccountThroughDaoAuthenticationProvider() {
        saveUser("felipe@example.com", RAW_PASSWORD, false);

        assertThatThrownBy(() -> authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "felipe@example.com",
                        RAW_PASSWORD)))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    void returnsBearerTokenWithoutSessionCookieOrSensitiveLogs(CapturedOutput output)
            throws Exception {
        Usuario saved = saveUser("felipe@example.com", RAW_PASSWORD, true);
        int requestLogStart = output.getAll().length();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(
                                "  FELIPE@Example.COM  ",
                                RAW_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        List<String> responseFields = new ArrayList<>();
        response.fieldNames().forEachRemaining(responseFields::add);
        assertThat(responseFields)
                .containsExactlyInAnyOrder("accessToken", "tokenType", "expiresIn");

        String token = response.path("accessToken").asText();
        Claims claims = jwtService.parse(token);
        assertThat(claims.getSubject()).isEqualTo(saved.getId().toString());
        assertThat(claims.keySet()).containsExactlyInAnyOrder("sub", "iat", "exp", "iss", "aud");
        assertThat(Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()))
                .isEqualTo(jwtProperties.getAccessTokenTtl());
        assertThat(result.getRequest().getSession(false)).isNull();

        String allLogs = output.getAll();
        String requestLogs = allLogs.substring(Math.min(requestLogStart, allLogs.length()));
        assertThat(requestLogs).doesNotContain(
                RAW_PASSWORD,
                saved.getSenha(),
                jwtProperties.getSecret(),
                token,
                "Authorization",
                "Bearer ",
                "select ",
                "insert ",
                "Exception",
                "at com.solveit");
    }

    private Usuario saveUser(String email, String rawPassword, boolean active) {
        Usuario usuario = new Usuario(
                "Felipe",
                email,
                passwordEncoder.encode(rawPassword),
                PerfilUsuario.SOLICITANTE);
        usuario.setAtivo(active);
        return usuarioRepository.saveAndFlush(usuario);
    }

    private record LoginPayload(String email, String senha) {
    }
}
