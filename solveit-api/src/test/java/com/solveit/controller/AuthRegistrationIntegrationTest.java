package com.solveit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
class AuthRegistrationIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_auth_test")
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

    @BeforeEach
    void cleanDatabase() {
        usuarioRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    void registersAnActiveApplicantWithNormalizedEmailAndOnlyPublicFields() throws Exception {
        String rawPassword = "  senha segura 123!  ";

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Felipe Iniciante",
                                  "email": "  Felipe@Example.COM  ",
                                  "senha": "  senha segura 123!  ",
                                  "perfil": "ADMIN"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Felipe Iniciante"))
                .andExpect(jsonPath("$.email").value("felipe@example.com"))
                .andExpect(jsonPath("$.perfil").value("SOLICITANTE"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.criadoEm").isNotEmpty())
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        List<String> responseFields = new ArrayList<>();
        response.fieldNames().forEachRemaining(responseFields::add);
        assertThat(responseFields).containsExactlyInAnyOrder(
                "id", "nome", "email", "perfil", "ativo", "criadoEm");

        Usuario persisted = usuarioRepository.findByEmail("felipe@example.com").orElseThrow();
        assertThat(persisted.getPerfil()).isEqualTo(PerfilUsuario.SOLICITANTE);
        assertThat(persisted.getAtivo()).isTrue();
        assertThat(persisted.getSenha()).isNotEqualTo(rawPassword).startsWith("$2");
        assertThat(new BCryptPasswordEncoder().matches(rawPassword, persisted.getSenha())).isTrue();
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(springSecurityFilterChain).isNotNull();
    }

    @Test
    void keepsOnlyPostRegistrationPublicAndDoesNotCreateAnHttpSession() throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Segunda pessoa",
                                  "email": "segunda@example.com",
                                  "senha": "outra senha segura 123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult protectedGet = mockMvc.perform(get("/api/auth/register"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult protectedPost = mockMvc.perform(post("/api/not-public"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(registration.getRequest().getSession(false)).isNull();
        assertThat(protectedGet.getRequest().getSession(false)).isNull();
        assertThat(protectedPost.getRequest().getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsPasswordWithFewerThanFifteenUnicodeCodePoints() throws Exception {
        String password = "🔐".repeat(14);

        assertThat(password.codePointCount(0, password.length())).isEqualTo(14);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                "Senha curta",
                                "curta@example.com",
                                password))))
                .andExpect(status().isBadRequest());

        assertThat(usuarioRepository.count()).isZero();
    }

    @Test
    void acceptsExactlyFifteenUnicodeCodePointsIncludingEmoji() throws Exception {
        String password = "🔐".repeat(15);

        assertThat(password.codePointCount(0, password.length())).isEqualTo(15);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                "Senha Unicode",
                                "unicode@example.com",
                                password))))
                .andExpect(status().isCreated());

        Usuario persisted = usuarioRepository.findByEmail("unicode@example.com").orElseThrow();
        assertThat(new BCryptPasswordEncoder().matches(password, persisted.getSenha())).isTrue();
    }

    @Test
    void acceptsPasswordWithExactlySeventyTwoUtf8Bytes() throws Exception {
        String password = "a".repeat(72);

        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(72);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                "Senha no limite",
                                "limite@example.com",
                                password))))
                .andExpect(status().isCreated());

        Usuario persisted = usuarioRepository.findByEmail("limite@example.com").orElseThrow();
        assertThat(new BCryptPasswordEncoder().matches(password, persisted.getSenha())).isTrue();
    }

    @Test
    void rejectsPasswordWithSeventyThreeUtf8Bytes() throws Exception {
        String password = "a".repeat(71) + "é";

        assertThat(password.codePointCount(0, password.length())).isEqualTo(72);
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(73);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                "Senha longa",
                                "longa@example.com",
                                password))))
                .andExpect(status().isBadRequest());

        assertThat(usuarioRepository.count()).isZero();
    }

    private record RegisterPayload(String nome, String email, String senha) {
    }
}
