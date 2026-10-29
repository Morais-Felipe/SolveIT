package com.solveit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class ComentarioHistoricoConsultaIntegrationTest {

    private static final String RAW_PASSWORD = "senha segura para leitura 123";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_leitura_test")
            .withUsername("solveit")
            .withPassword("solveit-test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ChamadoRepository chamadoRepository;

    private Login dono;
    private Login outroSolicitante;
    private Login joao;
    private Login maria;
    private Login admin;
    private Categoria categoria;

    @BeforeEach
    void prepare() throws Exception {
        jdbcTemplate.update("DELETE FROM historico_status");
        jdbcTemplate.update("DELETE FROM comentario");
        jdbcTemplate.update("DELETE FROM chamado");
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();

        dono = registerAndLogin("dono@example.com", "Dono", PerfilUsuario.SOLICITANTE);
        outroSolicitante = registerAndLogin("outro@example.com", "Outro", PerfilUsuario.SOLICITANTE);
        joao = registerAndLogin("joao@example.com", "Joao", PerfilUsuario.ATENDENTE);
        maria = registerAndLogin("maria@example.com", "Maria", PerfilUsuario.ATENDENTE);
        admin = registerAndLogin("admin@example.com", "Admin", PerfilUsuario.ADMIN);
        categoria = categoriaRepository.saveAndFlush(new Categoria("Rede", null));
    }

    @Test
    void returnsCommentsChronologicallyWithIdAsTieBreaker() throws Exception {
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, joao);
        long tarde = insertComment(chamado, dono, "Terceiro", "2026-10-01 12:00:00.000000");
        long cedoA = insertComment(chamado, joao, "Primeiro", "2026-10-01 09:00:00.000000");
        long cedoB = insertComment(chamado, dono, "Segundo", "2026-10-01 09:00:00.000000");

        JsonNode comentarios = read(dono, "/api/chamados/{id}/comentarios", chamado.getId());

        assertThat(ids(comentarios)).containsExactly(cedoA, cedoB, tarde);
        JsonNode primeiro = comentarios.get(0);
        assertFields(primeiro, "id", "texto", "autor", "criadoEm");
        assertFields(primeiro.path("autor"), "id", "nome");
        assertThat(primeiro.path("texto").asText()).isEqualTo("Primeiro");
        assertThat(primeiro.path("autor").path("id").asLong()).isEqualTo(joao.usuarioId());
        assertThat(primeiro.path("autor").path("nome").asText()).isEqualTo("Joao");
        assertThat(comentarios.toString()).doesNotContain("@example.com");
    }

    @Test
    void returnsEmptyArrayWhenThereAreNoComments() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);

        JsonNode comentarios = read(dono, "/api/chamados/{id}/comentarios", chamado.getId());

        assertThat(comentarios.isArray()).isTrue();
        assertThat(comentarios).isEmpty();
    }

    @Test
    void closedConversationsStayReadableForCurrentlyAuthorizedUsers() throws Exception {
        Chamado fechado = chamado(StatusChamado.FECHADO, joao);
        Chamado cancelado = chamado(StatusChamado.CANCELADO, null);
        long noFechado = insertComment(fechado, dono, "Antes de fechar", "2026-10-01 09:00:00.000000");
        long noCancelado = insertComment(cancelado, dono, "Antes de cancelar", "2026-10-01 09:00:00.000000");

        for (Login login : List.of(dono, joao, admin)) {
            assertThat(ids(read(login, "/api/chamados/{id}/comentarios", fechado.getId())))
                    .containsExactly(noFechado);
        }
        for (Login login : List.of(dono, admin)) {
            assertThat(ids(read(login, "/api/chamados/{id}/comentarios", cancelado.getId())))
                    .containsExactly(noCancelado);
        }
    }

    @Test
    void commentAuthorshipDoesNotKeepReadAccess() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        insertComment(chamado, maria, "Maria olhando", "2026-10-01 09:00:00.000000");
        assertThat(statusOf(maria, "/api/chamados/{id}/comentarios", chamado.getId())).isEqualTo(200);

        jdbcTemplate.update(
                "UPDATE chamado SET atendente_id = ?, status = 'EM_ANDAMENTO' WHERE id = ?",
                joao.usuarioId(), chamado.getId());

        assertThat(statusOf(maria, "/api/chamados/{id}/comentarios", chamado.getId())).isEqualTo(403);
        assertThat(statusOf(joao, "/api/chamados/{id}/comentarios", chamado.getId())).isEqualTo(200);
    }

    @Test
    void forbidsUsersWithoutRelationAndReturnsNotFoundForMissingTicket() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);

        assertThat(statusOf(outroSolicitante, "/api/chamados/{id}/comentarios", chamado.getId()))
                .isEqualTo(403);
        assertThat(statusOf(admin, "/api/chamados/{id}/comentarios", 999_999L)).isEqualTo(404);
        assertThat(statusOf(null, "/api/chamados/{id}/comentarios", chamado.getId())).isEqualTo(401);
    }

    private JsonNode read(Login login, String uri, Long chamadoId) throws Exception {
        MvcResult result = mockMvc.perform(get(uri, chamadoId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private int statusOf(Login login, String uri, Long chamadoId) throws Exception {
        var request = get(uri, chamadoId);
        if (login != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken());
        }
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    private long insertComment(Chamado chamado, Login autor, String texto, String criadoEm) {
        jdbcTemplate.update("""
                INSERT INTO comentario (chamado_id, autor_id, texto, criado_em)
                VALUES (?, ?, ?, ?)
                """, chamado.getId(), autor.usuarioId(), texto, criadoEm);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM comentario", Long.class);
    }

    private static List<Long> ids(JsonNode array) {
        List<Long> ids = new ArrayList<>();
        array.forEach(item -> ids.add(item.path("id").asLong()));
        return ids;
    }

    private static void assertFields(JsonNode node, String... expected) {
        List<String> actual = new ArrayList<>();
        node.fieldNames().forEachRemaining(actual::add);
        assertThat(actual).containsExactlyInAnyOrder(expected);
    }

    private Chamado chamado(StatusChamado status, Login atendente) {
        return chamadoRepository.saveAndFlush(new Chamado(
                "Sem rede",
                "Cabo solto",
                status,
                Prioridade.MEDIA,
                usuarioRepository.findById(dono.usuarioId()).orElseThrow(),
                atendente == null
                        ? null
                        : usuarioRepository.findById(atendente.usuarioId()).orElseThrow(),
                categoria));
    }

    private Login registerAndLogin(String email, String nome, PerfilUsuario perfil)
            throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nome", nome, "email", email, "senha", RAW_PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn();
        Long usuarioId = objectMapper.readTree(registration.getResponse().getContentAsString())
                .path("id")
                .asLong();
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        usuario.setPerfil(perfil);
        usuarioRepository.saveAndFlush(usuario);

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "senha", RAW_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("accessToken")
                .asText();
        return new Login(usuarioId, accessToken);
    }

    private record Login(Long usuarioId, String accessToken) {
    }
}
