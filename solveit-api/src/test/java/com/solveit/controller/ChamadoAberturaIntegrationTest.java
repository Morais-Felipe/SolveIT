package com.solveit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
class ChamadoAberturaIntegrationTest {

    private static final String RAW_PASSWORD = "senha segura para chamados 123";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_chamado_abertura_test")
            .withUsername("solveit")
            .withPassword("solveit-test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM historico_status");
        jdbcTemplate.update("DELETE FROM comentario");
        jdbcTemplate.update("DELETE FROM chamado");
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void requiresBearerToOpenTicket() throws Exception {
        mockMvc.perform(post("/api/chamados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validPayload(1L))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
    }

    @Test
    void solicitanteOpensTicketWithInitialHistoryInTheSameInstant() throws Exception {
        LoginResult solicitante = registerAndLogin("abre@example.com", "Quem Abre");
        Categoria categoria = categoriaRepository.saveAndFlush(
                new Categoria("Rede", "Conectividade"));

        Map<String, Object> payload = validPayload(categoria.getId());
        payload.put("titulo", "  Sem acesso a VPN  ");

        MvcResult result = mockMvc.perform(post("/api/chamados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(solicitante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value("Sem acesso a VPN"))
                .andExpect(jsonPath("$.descricao").value("A VPN recusa a conexao desde ontem."))
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.prioridade").value("ALTA"))
                .andExpect(jsonPath("$.solicitante.id").value(solicitante.usuarioId()))
                .andExpect(jsonPath("$.solicitante.nome").value("Quem Abre"))
                .andExpect(jsonPath("$.atendente").isEmpty())
                .andExpect(jsonPath("$.categoria.id").value(categoria.getId()))
                .andExpect(jsonPath("$.categoria.nome").value("Rede"))
                .andExpect(jsonPath("$.categoria.ativo").value(true))
                .andExpect(jsonPath("$.resolvidoEm").isEmpty())
                .andExpect(jsonPath("$.versao").value(0))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertFields(body,
                "id", "titulo", "descricao", "status", "prioridade", "solicitante",
                "atendente", "categoria", "criadoEm", "atualizadoEm", "resolvidoEm", "versao");
        assertFields(body.path("solicitante"), "id", "nome");
        assertFields(body.path("categoria"), "id", "nome", "ativo");
        long chamadoId = body.path("id").asLong();
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("/api/chamados/" + chamadoId);
        assertThat(body.path("criadoEm").asText()).isEqualTo(body.path("atualizadoEm").asText());

        assertThat(countRows("chamado")).isOne();
        List<Map<String, Object>> historico = jdbcTemplate.queryForList("""
                SELECT h.chamado_id, h.status_anterior, h.status_novo, h.alterado_por_id,
                       h.alterado_em, h.observacao, c.criado_em
                FROM historico_status h
                JOIN chamado c ON c.id = h.chamado_id
                """);
        assertThat(historico).hasSize(1);
        Map<String, Object> abertura = historico.get(0);
        assertThat(((Number) abertura.get("chamado_id")).longValue()).isEqualTo(chamadoId);
        assertThat(abertura.get("status_anterior")).isNull();
        assertThat(abertura.get("status_novo")).isEqualTo("ABERTO");
        assertThat(((Number) abertura.get("alterado_por_id")).longValue())
                .isEqualTo(solicitante.usuarioId());
        assertThat(abertura.get("observacao")).isNull();
        assertThat(abertura.get("alterado_em")).isNotNull().isEqualTo(abertura.get("criado_em"));
    }

    @Test
    void forbidsAdminAndAtendenteFromOpeningTickets() throws Exception {
        Categoria categoria = categoriaRepository.saveAndFlush(new Categoria("Hardware", null));
        LoginResult admin = registerAndLogin("admin-abre@example.com", "Admin");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);
        LoginResult atendente = registerAndLogin("atendente-abre@example.com", "Atendente");
        changeProfile(atendente.usuarioId(), PerfilUsuario.ATENDENTE);

        for (LoginResult login : List.of(admin, atendente)) {
            mockMvc.perform(post("/api/chamados")
                            .header(HttpHeaders.AUTHORIZATION, bearer(login))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(validPayload(categoria.getId()))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        assertThat(countRows("chamado")).isZero();
        assertThat(countRows("historico_status")).isZero();
    }

    @Test
    void rejectsInvalidPayloadsWithoutPersistingAnything() throws Exception {
        LoginResult solicitante = registerAndLogin("invalido@example.com", "Invalido");
        Categoria categoria = categoriaRepository.saveAndFlush(new Categoria("Software", null));
        Long categoriaId = categoria.getId();

        List<Map<String, Object>> payloads = new ArrayList<>();
        payloads.add(with(validPayload(categoriaId), "titulo", "   "));
        payloads.add(with(validPayload(categoriaId), "titulo", "x".repeat(201)));
        payloads.add(with(validPayload(categoriaId), "descricao", "  "));
        payloads.add(with(validPayload(categoriaId), "descricao", "x".repeat(10_001)));
        payloads.add(with(validPayload(categoriaId), "prioridade", null));
        payloads.add(with(validPayload(categoriaId), "prioridade", "URGENTISSIMA"));
        payloads.add(with(validPayload(categoriaId), "categoriaId", null));
        payloads.add(with(validPayload(categoriaId), "categoriaId", 0));
        payloads.add(with(validPayload(categoriaId), "categoriaId", -5));

        for (Map<String, Object> payload : payloads) {
            mockMvc.perform(post("/api/chamados")
                            .header(HttpHeaders.AUTHORIZATION, bearer(solicitante))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(payload)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(countRows("chamado")).isZero();
    }

    @Test
    void rejectsServerOwnedOrUnknownFieldsInsteadOfIgnoringThem() throws Exception {
        LoginResult solicitante = registerAndLogin("forja@example.com", "Forja");
        LoginResult outro = registerAndLogin("outro@example.com", "Outro");
        Categoria categoria = categoriaRepository.saveAndFlush(new Categoria("Acesso", null));

        Map<String, Object> forbidden = new LinkedHashMap<>();
        forbidden.put("solicitanteId", outro.usuarioId());
        forbidden.put("atendenteId", outro.usuarioId());
        forbidden.put("status", "RESOLVIDO");
        forbidden.put("versao", 7);
        forbidden.put("version", 7);
        forbidden.put("criadoEm", "2020-01-01T00:00:00");
        forbidden.put("atualizadoEm", "2020-01-01T00:00:00");
        forbidden.put("resolvidoEm", "2020-01-01T00:00:00");
        forbidden.put("campoQueNaoExiste", "x");

        for (Map.Entry<String, Object> entry : forbidden.entrySet()) {
            mockMvc.perform(post("/api/chamados")
                            .header(HttpHeaders.AUTHORIZATION, bearer(solicitante))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(with(
                                    validPayload(categoria.getId()),
                                    entry.getKey(),
                                    entry.getValue()))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertThat(countRows("chamado")).isZero();
        assertThat(countRows("historico_status")).isZero();
    }

    @Test
    void rejectsInactiveCategoryWithConflict() throws Exception {
        LoginResult solicitante = registerAndLogin("inativa@example.com", "Inativa");
        Categoria categoria = new Categoria("Legada", null);
        categoria.setAtivo(false);
        categoria = categoriaRepository.saveAndFlush(categoria);

        mockMvc.perform(post("/api/chamados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(solicitante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validPayload(categoria.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        assertThat(countRows("chamado")).isZero();
        assertThat(countRows("historico_status")).isZero();
    }

    @Test
    void returnsNotFoundForMissingCategory() throws Exception {
        LoginResult solicitante = registerAndLogin("sem-categoria@example.com", "Sem Categoria");

        mockMvc.perform(post("/api/chamados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(solicitante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validPayload(999_999L))))
                .andExpect(status().isNotFound());
        assertThat(countRows("chamado")).isZero();
    }

    private static Map<String, Object> validPayload(Long categoriaId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("titulo", "Sem acesso a VPN");
        payload.put("descricao", "A VPN recusa a conexao desde ontem.");
        payload.put("prioridade", "ALTA");
        payload.put("categoriaId", categoriaId);
        return payload;
    }

    private static Map<String, Object> with(
            Map<String, Object> payload,
            String key,
            Object value) {
        payload.put(key, value);
        return payload;
    }

    private long countRows(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private String json(Object payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private LoginResult registerAndLogin(String email, String nome) throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", nome, "email", email, "senha", RAW_PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn();
        Long usuarioId = objectMapper.readTree(registration.getResponse().getContentAsString())
                .path("id")
                .asLong();

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "senha", RAW_PASSWORD))))
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

    private static void assertFields(JsonNode node, String... expectedFields) {
        List<String> actualFields = new ArrayList<>();
        node.fieldNames().forEachRemaining(actualFields::add);
        assertThat(actualFields).containsExactlyInAnyOrder(expectedFields);
    }

    private static String bearer(LoginResult login) {
        return "Bearer " + login.accessToken();
    }

    private record LoginResult(Long usuarioId, String accessToken) {
    }
}
