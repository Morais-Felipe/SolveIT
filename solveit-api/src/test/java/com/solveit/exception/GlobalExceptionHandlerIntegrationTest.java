package com.solveit.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solveit.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Plano 07-01 (D-1, D-2): exceção inesperada vira 500 padronizado sem vazar detalhes,
 * e as recusas que já existiam (403, 404, 405) continuam com seus status.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@Import(GlobalExceptionHandlerIntegrationTest.FalhaController.class)
class GlobalExceptionHandlerIntegrationTest {

    private static final String RAW_PASSWORD = "senha segura para erros 12345";
    private static final String SEGREDO = "SELECT senha_hash FROM usuario WHERE token='abc123'";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_erros_test")
            .withUsername("solveit")
            .withPassword("solveit-test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private String token;

    /** Rota que só existe neste teste: simula um defeito inesperado na aplicação. */
    @RestController
    static class FalhaController {

        @GetMapping("/api/teste-erro/falha")
        String falhar() {
            throw new IllegalStateException(SEGREDO);
        }
    }

    @BeforeEach
    void loginAsSolicitante() throws Exception {
        usuarioRepository.deleteAll();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                "Erro", "erro@example.com", RAW_PASSWORD))))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(
                                "erro@example.com", RAW_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        token = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("accessToken")
                .asText();
    }

    @Test
    void unexpectedExceptionBecomesSafeInternalError() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/teste-erro/falha")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Ocorreu um erro inesperado. Tente novamente."))
                .andExpect(jsonPath("$.instance").value("/api/teste-erro/falha"))
                .andExpect(jsonPath("$.errorId").isString())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(
                "SELECT", "senha_hash", "abc123", "IllegalStateException", "FalhaController", "at com.");
    }

    @Test
    void accessDeniedStaysForbidden() throws Exception {
        mockMvc.perform(get("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void missingResourceBecomesNotFoundProblem() throws Exception {
        mockMvc.perform(get("/api/categorias/{id}", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/rota-inexistente")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void wrongMethodBecomesMethodNotAllowedProblem() throws Exception {
        mockMvc.perform(delete("/api/categorias")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unauthenticatedRequestStaysUnauthorized() throws Exception {
        mockMvc.perform(get("/api/teste-erro/falha"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
    }

    private record RegisterPayload(String nome, String email, String senha) {
    }

    private record LoginPayload(String email, String senha) {
    }
}
