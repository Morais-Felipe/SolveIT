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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
class UsuarioIntegrationTest {

    private static final String RAW_PASSWORD = "senha segura para usuarios 123";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_usuario_test")
            .withUsername("solveit")
            .withPassword("solveit-test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @BeforeEach
    void cleanDatabase() {
        usuarioRepository.deleteAll();
    }

    @Test
    void requiresBearerToListUsers() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
    }

    @Test
    void forbidsNonAdminProfilesFromListingAndReadingUsers() throws Exception {
        LoginResult solicitante = registerAndLogin("solicitante@example.com", "Solicitante");
        LoginResult atendente = registerAndLogin("atendente@example.com", "Atendente");
        changeProfile(atendente.usuarioId(), PerfilUsuario.ATENDENTE);

        for (LoginResult login : List.of(solicitante, atendente)) {
            mockMvc.perform(get("/api/usuarios")
                            .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

            mockMvc.perform(get("/api/usuarios/{id}", solicitante.usuarioId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Test
    void listsAllUsersOrderedByIdWithStablePaginationAndNoCredentials() throws Exception {
        LoginResult admin = registerAndLogin("admin@example.com", "Admin");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);
        LoginResult segundo = registerAndLogin("segundo@example.com", "Segundo");
        LoginResult inativo = registerAndLogin("inativo@example.com", "Inativo");
        deactivate(inativo.usuarioId());

        MvcResult firstPage = mockMvc.perform(get("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .queryParam("page", "0")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(admin.usuarioId()))
                .andExpect(jsonPath("$.items[0].nome").value("Admin"))
                .andExpect(jsonPath("$.items[0].email").value("admin@example.com"))
                .andExpect(jsonPath("$.items[0].perfil").value("ADMIN"))
                .andExpect(jsonPath("$.items[0].ativo").value(true))
                .andExpect(jsonPath("$.items[0].criadoEm").isNotEmpty())
                .andExpect(jsonPath("$.items[1].id").value(segundo.usuarioId()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andReturn();

        assertResponseFields(firstPage, "items", "page", "size", "totalElements", "totalPages");
        JsonNode firstItem = objectMapper.readTree(firstPage.getResponse().getContentAsString())
                .path("items")
                .get(0);
        assertItemFields(firstItem, "id", "nome", "email", "perfil", "ativo", "criadoEm");

        mockMvc.perform(get("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .queryParam("page", "1")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(inativo.usuarioId()))
                .andExpect(jsonPath("$.items[0].ativo").value(false))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void returnsAnEmptyPageBeyondTheLastPage() throws Exception {
        LoginResult admin = registerAndLogin("admin-vazio@example.com", "Admin Vazio");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);

        mockMvc.perform(get("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .queryParam("page", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(5))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void rejectsInvalidPaginationParameters() throws Exception {
        LoginResult admin = registerAndLogin("admin-pagina@example.com", "Admin Pagina");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);

        for (String[] params : List.of(
                new String[] {"page", "-1"},
                new String[] {"size", "0"},
                new String[] {"size", "101"})) {
            mockMvc.perform(get("/api/usuarios")
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                            .queryParam(params[0], params[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors." + params[0]).exists());
        }
    }

    @Test
    void returnsUserDetailsForAdminWithoutCredentials() throws Exception {
        LoginResult admin = registerAndLogin("admin-detalhe@example.com", "Admin Detalhe");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);
        LoginResult alvo = registerAndLogin("alvo@example.com", "Alvo");

        MvcResult result = mockMvc.perform(get("/api/usuarios/{id}", alvo.usuarioId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alvo.usuarioId()))
                .andExpect(jsonPath("$.nome").value("Alvo"))
                .andExpect(jsonPath("$.email").value("alvo@example.com"))
                .andExpect(jsonPath("$.perfil").value("SOLICITANTE"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn();

        assertResponseFields(result, "id", "nome", "email", "perfil", "ativo", "criadoEm");
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(RAW_PASSWORD)
                .doesNotContain("$2");
    }

    @Test
    void returnsNotFoundForMissingUser() throws Exception {
        LoginResult admin = registerAndLogin("admin-ausente@example.com", "Admin Ausente");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);

        mockMvc.perform(get("/api/usuarios/{id}", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void appliesProfileChangesBetweenRequestsWithoutNewToken() throws Exception {
        LoginResult admin = registerAndLogin("admin-rebaixado@example.com", "Admin Rebaixado");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);

        mockMvc.perform(get("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());

        changeProfile(admin.usuarioId(), PerfilUsuario.ATENDENTE);

        mockMvc.perform(get("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
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
        String accessToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("accessToken")
                .asText();
        return new LoginResult(usuarioId, accessToken);
    }

    private void changeProfile(Long usuarioId, PerfilUsuario perfil) {
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        usuario.setPerfil(perfil);
        usuarioRepository.saveAndFlush(usuario);
    }

    private void deactivate(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        usuario.setAtivo(false);
        usuarioRepository.saveAndFlush(usuario);
    }

    private void assertResponseFields(MvcResult result, String... expectedFields) throws Exception {
        assertItemFields(
                objectMapper.readTree(result.getResponse().getContentAsString()),
                expectedFields);
    }

    private static void assertItemFields(JsonNode node, String... expectedFields) {
        List<String> actualFields = new ArrayList<>();
        node.fieldNames().forEachRemaining(actualFields::add);
        assertThat(actualFields).containsExactlyInAnyOrder(expectedFields);
    }

    private static String bearer(LoginResult login) {
        return "Bearer " + login.accessToken();
    }

    private record RegisterPayload(String nome, String email, String senha) {
    }

    private record LoginPayload(String email, String senha) {
    }

    private record LoginResult(Long usuarioId, String accessToken) {
    }
}
