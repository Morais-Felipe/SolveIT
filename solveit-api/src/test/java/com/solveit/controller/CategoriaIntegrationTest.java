package com.solveit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solveit.entity.Categoria;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.repository.CategoriaRepository;
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
class CategoriaIntegrationTest {

    private static final String RAW_PASSWORD = "senha segura para categorias 123";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_categoria_test")
            .withUsername("solveit")
            .withPassword("solveit-test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @BeforeEach
    void cleanDatabase() {
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void requiresBearerToListCategories() throws Exception {
        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
    }

    @Test
    void returnsAnEmptyPageForAnAuthenticatedUser() throws Exception {
        LoginResult solicitante = registerAndLogin(
                "catalogo-vazio@example.com",
                "Catalogo Vazio");

        MvcResult result = mockMvc.perform(get("/api/categorias")
                        .header(HttpHeaders.AUTHORIZATION, bearer(solicitante)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();

        assertResponseFields(
                result,
                "items", "page", "size", "totalElements", "totalPages");
    }

    @Test
    void listsOnlyActiveCategoriesWithStablePagination() throws Exception {
        LoginResult solicitante = registerAndLogin(
                "catalogo@example.com",
                "Catalogo");
        Categoria zeta = categoriaRepository.saveAndFlush(
                new Categoria("Zeta", "Ultima categoria"));
        Categoria alpha = categoriaRepository.saveAndFlush(
                new Categoria("Alpha", "Primeira categoria"));
        Categoria inactive = new Categoria("Oculta", "Nao deve aparecer");
        inactive.setAtivo(false);
        categoriaRepository.saveAndFlush(inactive);

        mockMvc.perform(get("/api/categorias")
                        .header(HttpHeaders.AUTHORIZATION, bearer(solicitante))
                        .queryParam("page", "0")
                        .queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(alpha.getId()))
                .andExpect(jsonPath("$.items[0].nome").value("Alpha"))
                .andExpect(jsonPath("$.items[0].descricao").value("Primeira categoria"))
                .andExpect(jsonPath("$.items[0].ativo").value(true))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/categorias")
                        .header(HttpHeaders.AUTHORIZATION, bearer(solicitante))
                        .queryParam("page", "1")
                        .queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(zeta.getId()))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void returnsActiveCategoryByIdForAnyAuthenticatedUser() throws Exception {
        LoginResult solicitante = registerAndLogin(
                "detalhe@example.com",
                "Detalhe");
        Categoria categoria = categoriaRepository.saveAndFlush(
                new Categoria("Hardware", "Equipamentos fisicos"));

        MvcResult result = mockMvc.perform(get("/api/categorias/{id}", categoria.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(solicitante)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(categoria.getId()))
                .andExpect(jsonPath("$.nome").value("Hardware"))
                .andExpect(jsonPath("$.descricao").value("Equipamentos fisicos"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn();

        assertResponseFields(result, "id", "nome", "descricao", "ativo");
    }

    @Test
    void forbidsInactiveCategoryForSolicitanteButAllowsAdmin() throws Exception {
        LoginResult solicitante = registerAndLogin(
                "solicitante@example.com",
                "Solicitante");
        LoginResult admin = registerAndLogin("admin@example.com", "Admin");
        promoteToAdmin(admin.usuarioId());
        Categoria inactive = new Categoria("Legada", "Somente consulta administrativa");
        inactive.setAtivo(false);
        categoriaRepository.saveAndFlush(inactive);

        mockMvc.perform(get("/api/categorias/{id}", inactive.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(solicitante)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/categorias/{id}", inactive.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(inactive.getId()))
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void returnsNotFoundForMissingCategory() throws Exception {
        LoginResult solicitante = registerAndLogin(
                "ausente@example.com",
                "Ausente");

        mockMvc.perform(get("/api/categorias/{id}", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(solicitante)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createsAnActiveCategoryAsAdmin() throws Exception {
        LoginResult admin = registerAndLogin("admin-cria@example.com", "Admin Cria");
        promoteToAdmin(admin.usuarioId());

        MvcResult result = mockMvc.perform(post("/api/categorias")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCategoryPayload(
                                "  Redes  ",
                                "Conectividade corporativa"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Redes"))
                .andExpect(jsonPath("$.descricao").value("Conectividade corporativa"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.matchesPattern(
                        "/api/categorias/\\d+")))
                .andReturn();

        Long categoriaId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("id")
                .asLong();
        Categoria persisted = categoriaRepository.findById(categoriaId).orElseThrow();
        assertThat(persisted.getNome()).isEqualTo("Redes");
        assertThat(persisted.getDescricao()).isEqualTo("Conectividade corporativa");
        assertThat(persisted.getAtivo()).isTrue();
    }

    @Test
    void forbidsSolicitanteFromCreatingCategoryWithoutWriting() throws Exception {
        LoginResult solicitante = registerAndLogin(
                "solicitante-cria@example.com",
                "Solicitante Cria");

        mockMvc.perform(post("/api/categorias")
                        .header(HttpHeaders.AUTHORIZATION, bearer(solicitante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCategoryPayload(
                                "Sem Permissao",
                                null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(categoriaRepository.count()).isZero();
    }

    @Test
    void rejectsBlankCategoryNameBeforeWriting() throws Exception {
        LoginResult admin = registerAndLogin("admin-valida@example.com", "Admin Valida");
        promoteToAdmin(admin.usuarioId());

        mockMvc.perform(post("/api/categorias")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCategoryPayload(
                                "   ",
                                "Descricao"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.nome").value("O nome é obrigatório"));

        assertThat(categoriaRepository.count()).isZero();
    }

    @Test
    void returnsControlledConflictForDuplicateCategoryName() throws Exception {
        LoginResult admin = registerAndLogin("admin-duplica@example.com", "Admin Duplica");
        promoteToAdmin(admin.usuarioId());
        categoriaRepository.saveAndFlush(new Categoria("Software", "Existente"));

        MvcResult result = mockMvc.perform(post("/api/categorias")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCategoryPayload(
                                "Software",
                                "Duplicada"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type")
                        .value("urn:solveit:error:category-name-already-exists"))
                .andExpect(jsonPath("$.title").value("Conflito de categoria"))
                .andExpect(jsonPath("$.detail").value("Já existe uma categoria com este nome."))
                .andExpect(jsonPath("$.code").value("CATEGORY_NAME_ALREADY_EXISTS"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(
                "uk_categoria_nome",
                "ConstraintViolationException",
                "DataIntegrityViolationException",
                "SQL");
        assertThat(categoriaRepository.count()).isEqualTo(1);
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

    private void promoteToAdmin(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        usuario.setPerfil(PerfilUsuario.ADMIN);
        usuarioRepository.saveAndFlush(usuario);
    }

    private void assertResponseFields(MvcResult result, String... expectedFields) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        List<String> actualFields = new ArrayList<>();
        response.fieldNames().forEachRemaining(actualFields::add);
        assertThat(actualFields).containsExactlyInAnyOrder(expectedFields);
    }

    private static String bearer(LoginResult login) {
        return "Bearer " + login.accessToken();
    }

    private record RegisterPayload(String nome, String email, String senha) {
    }

    private record LoginPayload(String email, String senha) {
    }

    private record CreateCategoryPayload(String nome, String descricao) {
    }

    private record LoginResult(Long usuarioId, String accessToken) {
    }
}
