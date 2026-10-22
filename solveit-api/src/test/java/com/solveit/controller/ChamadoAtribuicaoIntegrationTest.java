package com.solveit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.ResultActions;
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
class ChamadoAtribuicaoIntegrationTest {

    private static final String RAW_PASSWORD = "senha segura para atribuicao 123";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_chamado_atribuicao_test")
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

    private Login solicitante;
    private Login atendenteX;
    private Login atendenteY;
    private Login admin;
    private Categoria categoria;

    @BeforeEach
    void prepare() throws Exception {
        jdbcTemplate.update("DELETE FROM historico_status");
        jdbcTemplate.update("DELETE FROM comentario");
        jdbcTemplate.update("DELETE FROM chamado");
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();

        solicitante = registerAndLogin("solicitante@example.com", "Solicitante",
                PerfilUsuario.SOLICITANTE);
        atendenteX = registerAndLogin("x@example.com", "Atendente X", PerfilUsuario.ATENDENTE);
        atendenteY = registerAndLogin("y@example.com", "Atendente Y", PerfilUsuario.ATENDENTE);
        admin = registerAndLogin("admin@example.com", "Admin", PerfilUsuario.ADMIN);
        categoria = categoriaRepository.saveAndFlush(new Categoria("Rede", null));
    }

    @Test
    void attendantAssumesQueueTicketWithOneHistoryAtTheSameInstant() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);

        assign(atendenteX, chamado.getId(), payload(atendenteX.usuarioId(), 0L, "Vou verificar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$.atendente.id").value(atendenteX.usuarioId()))
                .andExpect(jsonPath("$.atendente.nome").value("Atendente X"))
                .andExpect(jsonPath("$.resolvidoEm").isEmpty())
                .andExpect(jsonPath("$.versao").value(1));

        List<Map<String, Object>> historico = history(chamado.getId());
        assertThat(historico).hasSize(1);
        Map<String, Object> registro = historico.get(0);
        assertThat(registro.get("status_anterior")).isEqualTo("ABERTO");
        assertThat(registro.get("status_novo")).isEqualTo("EM_ANDAMENTO");
        assertThat(((Number) registro.get("alterado_por_id")).longValue())
                .isEqualTo(atendenteX.usuarioId());
        assertThat(registro.get("observacao")).isEqualTo("Vou verificar");
        assertThat(registro.get("alterado_em"))
                .isNotNull()
                .isEqualTo(row(chamado.getId()).get("atualizado_em"));
    }

    @Test
    void adminAssignsAnotherAttendantAndIsRecordedAsTheActor() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);

        assign(admin, chamado.getId(), payload(atendenteY.usuarioId(), 0L, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atendente.id").value(atendenteY.usuarioId()));

        Map<String, Object> registro = history(chamado.getId()).get(0);
        assertThat(((Number) registro.get("alterado_por_id")).longValue())
                .isEqualTo(admin.usuarioId());
        assertThat(registro.get("observacao")).isNull();
    }

    @Test
    void assignedTicketLeavesOtherQueuesAndStaysWithTheWinner() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        assertThat(detailStatus(atendenteY, chamado.getId())).isEqualTo(200);

        assign(atendenteX, chamado.getId(), payload(atendenteX.usuarioId(), 0L, null))
                .andExpect(status().isOk());

        assertThat(detailStatus(atendenteY, chamado.getId())).isEqualTo(403);
        assertThat(detailStatus(atendenteX, chamado.getId())).isEqualTo(200);
        assertThat(detailStatus(solicitante, chamado.getId())).isEqualTo(200);
        assertThat(listIds(atendenteY)).doesNotContain(chamado.getId());
        assertThat(listIds(atendenteX)).contains(chamado.getId());
    }

    @Test
    void forbidsRequestersAndAttendantsAssigningSomeoneElse() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);

        assign(solicitante, chamado.getId(), payload(atendenteX.usuarioId(), 0L, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assign(atendenteX, chamado.getId(), payload(atendenteY.usuarioId(), 0L, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertUnchanged(chamado.getId());
    }

    @Test
    void rejectsDestinationsThatAreNotActiveAttendants() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        Login inativo = registerAndLogin("inativo@example.com", "Inativo", PerfilUsuario.ATENDENTE);
        jdbcTemplate.update("UPDATE usuario SET ativo = FALSE WHERE id = ?", inativo.usuarioId());

        for (Long destino : List.of(
                inativo.usuarioId(), solicitante.usuarioId(), admin.usuarioId(), 999_999L)) {
            assign(admin, chamado.getId(), payload(destino, 0L, null))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        }
        assertUnchanged(chamado.getId());
    }

    @Test
    void rejectsTicketsThatAreNotInTheQueue() throws Exception {
        Chamado assumido = chamado(StatusChamado.EM_ANDAMENTO, atendenteX);
        Chamado cancelado = chamado(StatusChamado.CANCELADO, null);
        Chamado abertoComResponsavel = chamado(StatusChamado.ABERTO, atendenteX);

        for (Chamado chamado : List.of(assumido, cancelado, abertoComResponsavel)) {
            assign(admin, chamado.getId(), payload(atendenteY.usuarioId(), 0L, null))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_STATE"));
            assertUnchanged(chamado.getId());
        }
    }

    @Test
    void rejectsStaleVersion() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        jdbcTemplate.update("UPDATE chamado SET version = version + 1 WHERE id = ?", chamado.getId());

        assign(atendenteX, chamado.getId(), payload(atendenteX.usuarioId(), 0L, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE"));

        assertThat(row(chamado.getId()).get("atendente_id")).isNull();
        assertThat(history(chamado.getId())).isEmpty();
    }

    @Test
    void rejectsInvalidPayloads() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);

        List<Map<String, Object>> invalidos = List.of(
                without(payload(atendenteX.usuarioId(), 0L, null), "atendenteId"),
                payload(0L, 0L, null),
                without(payload(atendenteX.usuarioId(), 0L, null), "versaoEsperada"),
                payload(atendenteX.usuarioId(), -1L, null),
                payload(atendenteX.usuarioId(), 0L, "x".repeat(501)),
                with(payload(atendenteX.usuarioId(), 0L, null), "status", "RESOLVIDO"));

        for (Map<String, Object> corpo : invalidos) {
            assign(atendenteX, chamado.getId(), corpo)
                    .andExpect(status().isBadRequest());
        }
        assertUnchanged(chamado.getId());
    }

    @Test
    void returnsNotFoundForMissingTicket() throws Exception {
        assign(admin, 999_999L, payload(atendenteX.usuarioId(), 0L, null))
                .andExpect(status().isNotFound());
    }

    @Test
    void commentingDoesNotReserveTheTicket() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        jdbcTemplate.update("""
                INSERT INTO comentario (chamado_id, autor_id, texto, criado_em)
                VALUES (?, ?, 'Eu olho isso depois', UTC_TIMESTAMP(6))
                """, chamado.getId(), atendenteY.usuarioId());

        assign(atendenteX, chamado.getId(), payload(atendenteX.usuarioId(), 0L, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atendente.id").value(atendenteX.usuarioId()));
    }

    private void assertUnchanged(Long chamadoId) {
        Map<String, Object> atual = row(chamadoId);
        assertThat(((Number) atual.get("version")).longValue()).isZero();
        assertThat(history(chamadoId)).isEmpty();
    }

    private ResultActions assign(Login login, Long chamadoId, Map<String, Object> body)
            throws Exception {
        return mockMvc.perform(patch("/api/chamados/{id}/atendente", chamadoId)
                .header(HttpHeaders.AUTHORIZATION, bearer(login))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private int detailStatus(Login login, Long chamadoId) throws Exception {
        return mockMvc.perform(get("/api/chamados/{id}", chamadoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andReturn().getResponse().getStatus();
    }

    private List<Long> listIds(Login login) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/chamados")
                        .header(HttpHeaders.AUTHORIZATION, bearer(login))
                        .queryParam("size", "100"))
                .andExpect(status().isOk())
                .andReturn();
        List<Long> ids = new ArrayList<>();
        objectMapper.readTree(result.getResponse().getContentAsString())
                .path("items")
                .forEach(item -> ids.add(item.path("id").asLong()));
        return ids;
    }

    private static Map<String, Object> payload(Long atendenteId, Long versao, String observacao) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("atendenteId", atendenteId);
        body.put("versaoEsperada", versao);
        if (observacao != null) {
            body.put("observacao", observacao);
        }
        return body;
    }

    private static Map<String, Object> with(Map<String, Object> body, String key, Object value) {
        body.put(key, value);
        return body;
    }

    private static Map<String, Object> without(Map<String, Object> body, String key) {
        body.remove(key);
        return body;
    }

    private Chamado chamado(StatusChamado status, Login responsavel) {
        return chamadoRepository.saveAndFlush(new Chamado(
                "Sem rede",
                "Cabo desconectado",
                status,
                Prioridade.MEDIA,
                usuarioRepository.findById(solicitante.usuarioId()).orElseThrow(),
                responsavel == null
                        ? null
                        : usuarioRepository.findById(responsavel.usuarioId()).orElseThrow(),
                categoria));
    }

    private Map<String, Object> row(Long chamadoId) {
        return jdbcTemplate.queryForMap("SELECT * FROM chamado WHERE id = ?", chamadoId);
    }

    private List<Map<String, Object>> history(Long chamadoId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM historico_status WHERE chamado_id = ? ORDER BY id", chamadoId);
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

    private static String bearer(Login login) {
        return "Bearer " + login.accessToken();
    }

    private record Login(Long usuarioId, String accessToken) {
    }
}
