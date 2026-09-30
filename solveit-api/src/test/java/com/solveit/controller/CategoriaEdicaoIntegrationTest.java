package com.solveit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solveit.entity.Categoria;
import com.solveit.entity.Chamado;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.enums.Prioridade;
import com.solveit.enums.StatusChamado;
import com.solveit.repository.CategoriaRepository;
import com.solveit.repository.ChamadoRepository;
import com.solveit.repository.UsuarioRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout = 1"
})
@AutoConfigureMockMvc
class CategoriaEdicaoIntegrationTest {

    private static final String RAW_PASSWORD = "senha segura para editar categorias 123";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_categoria_edicao_test")
            .withUsername("solveit")
            .withPassword("solveit-test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ChamadoRepository chamadoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        chamadoRepository.deleteAll();
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void editsCategoryAsAdminAndRejectsRepeatedStalePayload() throws Exception {
        LoginResult admin = registerAndLogin("admin-edita@example.com", "Admin Edita");
        promoteToAdmin(admin.usuarioId());
        Categoria categoria = categoriaRepository.saveAndFlush(
                new Categoria("Infra", "Descricao original"));

        MvcResult result = mockMvc.perform(put("/api/categorias/{id}", categoria.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditCategoryPayload(
                                "  Infraestrutura  ",
                                "Descricao atualizada",
                                0L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(categoria.getId()))
                .andExpect(jsonPath("$.nome").value("Infraestrutura"))
                .andExpect(jsonPath("$.descricao").value("Descricao atualizada"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.versao").value(1))
                .andReturn();

        assertResponseFields(result, "id", "nome", "descricao", "ativo", "versao");

        MvcResult staleResult = mockMvc.perform(put("/api/categorias/{id}", categoria.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditCategoryPayload(
                                "Infraestrutura antiga",
                                "Tentativa desatualizada",
                                0L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:solveit:error:stale-resource"))
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE"))
                .andReturn();

        assertThat(staleResult.getResponse().getContentAsString()).doesNotContain(
                "OptimisticLockException",
                "ObjectOptimisticLockingFailureException",
                "SQL");

        Categoria persisted = categoriaRepository.findById(categoria.getId()).orElseThrow();
        assertThat(persisted.getNome()).isEqualTo("Infraestrutura");
        assertThat(persisted.getDescricao()).isEqualTo("Descricao atualizada");
    }

    @Test
    void rejectsAnotherCategoryNameWithoutChangingTarget() throws Exception {
        LoginResult admin = registerAndLogin("admin-duplicada@example.com", "Admin Duplicada");
        promoteToAdmin(admin.usuarioId());
        Categoria target = categoriaRepository.saveAndFlush(
                new Categoria("Hardware", "Categoria alvo"));
        categoriaRepository.saveAndFlush(new Categoria("Software", "Nome ocupado"));

        mockMvc.perform(put("/api/categorias/{id}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditCategoryPayload(
                                "Software",
                                "Nao deve persistir",
                                0L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_NAME_ALREADY_EXISTS"));

        Categoria persisted = categoriaRepository.findById(target.getId()).orElseThrow();
        assertThat(persisted.getNome()).isEqualTo("Hardware");
        assertThat(persisted.getDescricao()).isEqualTo("Categoria alvo");
    }

    @Test
    void returnsCoreBusyAndRollsBackWhenCategoryRowIsLocked() throws Exception {
        LoginResult admin = registerAndLogin("admin-lock@example.com", "Admin Lock");
        promoteToAdmin(admin.usuarioId());
        Categoria categoria = categoriaRepository.saveAndFlush(
                new Categoria("Impressoras", "Descricao original"));

        assertCoreBusyWhileCategoryLocked(
                categoria.getId(),
                put("/api/categorias/{id}", categoria.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditCategoryPayload(
                                "Impressoras alteradas",
                                "Nao deve persistir",
                                0L))));
        assertCategoryUnchanged(categoria.getId());

        assertCoreBusyWhileCategoryLocked(
                categoria.getId(),
                patch("/api/categorias/{id}/ativo", categoria.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeActivePayload(false, 0L))));
        assertCategoryUnchanged(categoria.getId());
    }

    @Test
    void forbidsSolicitanteFromEditingWithoutWriting() throws Exception {
        LoginResult solicitante = registerAndLogin(
                "solicitante-edita@example.com",
                "Solicitante Edita");
        Categoria categoria = categoriaRepository.saveAndFlush(
                new Categoria("Redes", "Descricao original"));

        mockMvc.perform(put("/api/categorias/{id}", categoria.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(solicitante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditCategoryPayload(
                                "Redes alteradas",
                                "Sem permissao",
                                0L))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        Categoria persisted = categoriaRepository.findById(categoria.getId()).orElseThrow();
        assertThat(persisted.getNome()).isEqualTo("Redes");
        assertThat(persisted.getDescricao()).isEqualTo("Descricao original");
    }

    @Test
    void rejectsMissingExpectedVersionBeforeWriting() throws Exception {
        LoginResult admin = registerAndLogin("admin-sem-versao@example.com", "Admin Sem Versao");
        promoteToAdmin(admin.usuarioId());
        Categoria categoria = categoriaRepository.saveAndFlush(
                new Categoria("Telefonia", "Descricao original"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nome", "Telefonia IP");
        payload.put("descricao", "Nao deve persistir");

        mockMvc.perform(put("/api/categorias/{id}", categoria.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.versaoEsperada")
                        .value("A versão esperada é obrigatória"));

        Categoria persisted = categoriaRepository.findById(categoria.getId()).orElseThrow();
        assertThat(persisted.getNome()).isEqualTo("Telefonia");
        assertThat(persisted.getDescricao()).isEqualTo("Descricao original");
    }

    @Test
    void returnsNotFoundWhenEditingMissingCategory() throws Exception {
        LoginResult admin = registerAndLogin("admin-ausente@example.com", "Admin Ausente");
        promoteToAdmin(admin.usuarioId());

        mockMvc.perform(put("/api/categorias/{id}", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditCategoryPayload(
                                "Inexistente",
                                null,
                                0L))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivatesReferencedCategoryWithoutDeletingTicketRelationship() throws Exception {
        LoginResult admin = registerAndLogin("admin-desativa@example.com", "Admin Desativa");
        promoteToAdmin(admin.usuarioId());
        LoginResult solicitante = registerAndLogin(
                "solicitante-vinculo@example.com",
                "Solicitante Vinculo");
        Usuario solicitantePersistido = usuarioRepository.findById(solicitante.usuarioId()).orElseThrow();
        Categoria categoria = categoriaRepository.saveAndFlush(
                new Categoria("Acesso", "Categoria vinculada"));
        Chamado chamado = chamadoRepository.saveAndFlush(new Chamado(
                "Sem acesso ao sistema",
                "O usuario nao consegue autenticar",
                StatusChamado.ABERTO,
                Prioridade.MEDIA,
                solicitantePersistido,
                null,
                categoria));

        MvcResult result = mockMvc.perform(patch("/api/categorias/{id}/ativo", categoria.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeActivePayload(false, 0L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(categoria.getId()))
                .andExpect(jsonPath("$.nome").value("Acesso"))
                .andExpect(jsonPath("$.descricao").value("Categoria vinculada"))
                .andExpect(jsonPath("$.ativo").value(false))
                .andExpect(jsonPath("$.versao").value(1))
                .andReturn();

        assertResponseFields(result, "id", "nome", "descricao", "ativo", "versao");
        mockMvc.perform(get("/api/categorias")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        Map<String, Object> relationship = jdbcTemplate.queryForMap("""
                SELECT ch.categoria_id, c.nome, c.ativo
                FROM chamado ch
                JOIN categoria c ON c.id = ch.categoria_id
                WHERE ch.id = ?
                """, chamado.getId());
        assertThat(((Number) relationship.get("categoria_id")).longValue())
                .isEqualTo(categoria.getId());
        assertThat(relationship.get("nome")).isEqualTo("Acesso");
        assertThat(relationship.get("ativo")).isEqualTo(false);
        assertThat(categoriaRepository.existsById(categoria.getId())).isTrue();
    }

    @Test
    void reactivatesCategoryAndReturnsItToCatalog() throws Exception {
        LoginResult admin = registerAndLogin("admin-reativa@example.com", "Admin Reativa");
        promoteToAdmin(admin.usuarioId());
        Categoria categoria = new Categoria("Banco de dados", "Categoria inativa");
        categoria.setAtivo(false);
        categoria = categoriaRepository.saveAndFlush(categoria);

        mockMvc.perform(patch("/api/categorias/{id}/ativo", categoria.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeActivePayload(true, 0L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.versao").value(1));

        mockMvc.perform(get("/api/categorias")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(categoria.getId()))
                .andExpect(jsonPath("$.items[0].nome").value("Banco de dados"));
    }

    @Test
    void keepsVersionOnNoOpAndRejectsStaleActivationChange() throws Exception {
        LoginResult admin = registerAndLogin("admin-noop@example.com", "Admin Noop");
        promoteToAdmin(admin.usuarioId());
        Categoria categoria = categoriaRepository.saveAndFlush(
                new Categoria("Monitoramento", "Categoria ativa"));

        mockMvc.perform(patch("/api/categorias/{id}/ativo", categoria.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeActivePayload(true, 0L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.versao").value(0));

        mockMvc.perform(patch("/api/categorias/{id}/ativo", categoria.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeActivePayload(false, 0L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false))
                .andExpect(jsonPath("$.versao").value(1));

        mockMvc.perform(patch("/api/categorias/{id}/ativo", categoria.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeActivePayload(true, 0L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE"));

        Categoria persisted = categoriaRepository.findById(categoria.getId()).orElseThrow();
        assertThat(persisted.getAtivo()).isFalse();
        assertThat(persisted.getVersion()).isOne();
    }

    @Test
    void forbidsSolicitanteFromChangingCategoryActivation() throws Exception {
        LoginResult solicitante = registerAndLogin(
                "solicitante-ativacao@example.com",
                "Solicitante Ativacao");
        Categoria categoria = categoriaRepository.saveAndFlush(
                new Categoria("Seguranca", "Categoria protegida"));

        mockMvc.perform(patch("/api/categorias/{id}/ativo", categoria.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(solicitante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeActivePayload(false, 0L))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        Categoria persisted = categoriaRepository.findById(categoria.getId()).orElseThrow();
        assertThat(persisted.getAtivo()).isTrue();
        assertThat(persisted.getVersion()).isZero();
    }

    @Test
    void rejectsMissingExpectedVersionForActivationChange() throws Exception {
        LoginResult admin = registerAndLogin(
                "admin-ativacao-sem-versao@example.com",
                "Admin Ativacao Sem Versao");
        promoteToAdmin(admin.usuarioId());
        Categoria categoria = categoriaRepository.saveAndFlush(
                new Categoria("Observabilidade", null));

        mockMvc.perform(patch("/api/categorias/{id}/ativo", categoria.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.versaoEsperada")
                        .value("A versão esperada é obrigatória"));

        assertThat(categoriaRepository.findById(categoria.getId()).orElseThrow().getAtivo()).isTrue();
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

    private void assertCoreBusyWhileCategoryLocked(
            Long categoriaId,
            MockHttpServletRequestBuilder request) throws Exception {
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> lockHolder = executor.submit(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    jdbcTemplate.queryForObject(
                            "SELECT id FROM categoria WHERE id = ? FOR UPDATE",
                            Long.class,
                            categoriaId);
                    lockAcquired.countDown();
                    await(releaseLock, "Category row lock was not released");
                }));

        try {
            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
            MvcResult result = mockMvc.perform(request)
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.type").value("urn:solveit:error:core-busy"))
                    .andExpect(jsonPath("$.code").value("CORE_BUSY"))
                    .andReturn();

            assertThat(result.getResponse().getContentAsString()).doesNotContain(
                    "LockTimeoutException",
                    "PessimisticLockException",
                    "SQL");
        } finally {
            releaseLock.countDown();
            lockHolder.get(10, TimeUnit.SECONDS);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertCategoryUnchanged(Long categoriaId) {
        Categoria persisted = categoriaRepository.findById(categoriaId).orElseThrow();
        assertThat(persisted.getNome()).isEqualTo("Impressoras");
        assertThat(persisted.getDescricao()).isEqualTo("Descricao original");
        assertThat(persisted.getAtivo()).isTrue();
        assertThat(persisted.getVersion()).isZero();
    }

    private static void await(CountDownLatch latch, String message) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(message);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Lock holder was interrupted", exception);
        }
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

    private record EditCategoryPayload(
            String nome,
            String descricao,
            Long versaoEsperada) {
    }

    private record ChangeActivePayload(Boolean ativo, Long versaoEsperada) {
    }

    private record LoginResult(Long usuarioId, String accessToken) {
    }
}
