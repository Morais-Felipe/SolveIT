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
class UsuarioOperacaoIntegrationTest {

    private static final String RAW_PASSWORD = "senha segura para operacao 123";
    private static final String MOTIVO_DESATIVACAO = "Retorno à fila por desativação do atendente";
    private static final String MOTIVO_PERFIL = "Retorno à fila por mudança de perfil do atendente";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_usuario_operacao_test")
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

    private Login admin;
    private Login solicitante;
    private Login atendente;
    private Login outroAtendente;
    private Categoria categoria;

    @BeforeEach
    void prepare() throws Exception {
        jdbcTemplate.update("DELETE FROM historico_status");
        jdbcTemplate.update("DELETE FROM comentario");
        jdbcTemplate.update("DELETE FROM chamado");
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();

        admin = registerAndLogin("admin@example.com", "Admin", PerfilUsuario.ADMIN);
        solicitante = registerAndLogin("sol@example.com", "Solicitante", PerfilUsuario.SOLICITANTE);
        atendente = registerAndLogin("at@example.com", "Atendente", PerfilUsuario.ATENDENTE);
        outroAtendente = registerAndLogin("at2@example.com", "Outro", PerfilUsuario.ATENDENTE);
        categoria = categoriaRepository.saveAndFlush(new Categoria("Rede", null));
    }

    @Test
    void deactivatingAnAttendantReturnsOngoingTicketsToTheQueueWithHistory() throws Exception {
        Chamado andamento1 = chamado(StatusChamado.EM_ANDAMENTO, atendente);
        Chamado andamento2 = chamado(StatusChamado.EM_ANDAMENTO, atendente);
        Chamado resolvido = chamado(StatusChamado.RESOLVIDO, atendente);
        Chamado deOutro = chamado(StatusChamado.EM_ANDAMENTO, outroAtendente);

        operar(admin, atendente, "ATENDENTE", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.id").value(atendente.usuarioId()))
                .andExpect(jsonPath("$.usuario.ativo").value(false))
                .andExpect(jsonPath("$.usuario.perfil").value("ATENDENTE"))
                .andExpect(jsonPath("$.chamadosDevolvidos").value(2));

        for (Chamado devolvido : List.of(andamento1, andamento2)) {
            Map<String, Object> estado = row(devolvido);
            assertThat(estado.get("status")).isEqualTo("ABERTO");
            assertThat(estado.get("atendente_id")).isNull();
            assertThat(estado.get("resolvido_em")).isNull();
            assertThat(((Number) estado.get("version")).longValue()).isEqualTo(1);
            List<Map<String, Object>> historico = history(devolvido);
            assertThat(historico).hasSize(1);
            assertThat(historico.get(0).get("status_anterior")).isEqualTo("EM_ANDAMENTO");
            assertThat(historico.get(0).get("status_novo")).isEqualTo("ABERTO");
            assertThat(((Number) historico.get(0).get("alterado_por_id")).longValue())
                    .isEqualTo(admin.usuarioId());
            assertThat(historico.get(0).get("observacao")).isEqualTo(MOTIVO_DESATIVACAO);
            assertThat(historico.get(0).get("alterado_em")).isEqualTo(estado.get("atualizado_em"));
        }
        assertThat(row(resolvido).get("status")).isEqualTo("RESOLVIDO");
        assertThat(((Number) row(resolvido).get("atendente_id")).longValue())
                .isEqualTo(atendente.usuarioId());
        assertThat(history(resolvido)).isEmpty();
        assertThat(row(deOutro).get("status")).isEqualTo("EM_ANDAMENTO");
        assertThat(history(deOutro)).isEmpty();
    }

    @Test
    void returnedTicketsReachTheQueueAndTheDeactivatedTokenStopsWorking() throws Exception {
        Chamado andamento = chamado(StatusChamado.EM_ANDAMENTO, atendente);
        assertThat(detailStatus(outroAtendente, andamento)).isEqualTo(403);

        operar(admin, atendente, "ATENDENTE", false).andExpect(status().isOk());

        assertThat(detailStatus(outroAtendente, andamento)).isEqualTo(200);
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + atendente.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void removingTheAttendantProfileUsesItsOwnReason() throws Exception {
        Chamado andamento = chamado(StatusChamado.EM_ANDAMENTO, atendente);

        operar(admin, atendente, "SOLICITANTE", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.perfil").value("SOLICITANTE"))
                .andExpect(jsonPath("$.usuario.ativo").value(true))
                .andExpect(jsonPath("$.chamadosDevolvidos").value(1));

        assertThat(history(andamento).get(0).get("observacao")).isEqualTo(MOTIVO_PERFIL);
        assertThat(row(andamento).get("status")).isEqualTo("ABERTO");
    }

    @Test
    void changesWithoutOngoingWorkTouchNoTicket() throws Exception {
        operar(admin, solicitante, "ATENDENTE", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.perfil").value("ATENDENTE"))
                .andExpect(jsonPath("$.chamadosDevolvidos").value(0));

        operar(admin, outroAtendente, "ATENDENTE", false).andExpect(status().isOk());
        operar(admin, outroAtendente, "ATENDENTE", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.ativo").value(true))
                .andExpect(jsonPath("$.chamadosDevolvidos").value(0));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM historico_status", Long.class)).isZero();
    }

    @Test
    void protectsTheLastActiveAdmin() throws Exception {
        operar(admin, admin, "ADMIN", false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ACTIVE_ADMIN"));
        operar(admin, admin, "ATENDENTE", true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ACTIVE_ADMIN"));

        Login segundo = registerAndLogin("admin2@example.com", "Admin 2", PerfilUsuario.ADMIN);
        operar(admin, segundo, "ADMIN", false).andExpect(status().isOk());
        operar(admin, admin, "ADMIN", false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ACTIVE_ADMIN"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usuario WHERE perfil = 'ADMIN' AND ativo = TRUE",
                Long.class)).isOne();
    }

    @Test
    void rejectsNonAdminsStaleVersionsNoOpsAndInvalidBodies() throws Exception {
        Chamado andamento = chamado(StatusChamado.EM_ANDAMENTO, atendente);

        operar(atendente, atendente, "ATENDENTE", false)
                .andExpect(status().isForbidden());
        operar(solicitante, atendente, "ATENDENTE", false)
                .andExpect(status().isForbidden());

        send(admin, atendente.usuarioId(), body("ATENDENTE", false, 99L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE"));
        operar(admin, atendente, "ATENDENTE", true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        for (Map<String, Object> invalido : List.of(
                without(body("ATENDENTE", false, 0L), "perfil"),
                without(body("ATENDENTE", false, 0L), "ativo"),
                without(body("ATENDENTE", false, 0L), "versaoEsperada"),
                body("ROOT", false, 0L),
                with(body("ATENDENTE", false, 0L), "email", "novo@example.com"))) {
            send(admin, atendente.usuarioId(), invalido).andExpect(status().isBadRequest());
        }
        send(admin, 999_999L, body("ATENDENTE", false, 0L)).andExpect(status().isNotFound());

        assertThat(row(andamento).get("status")).isEqualTo("EM_ANDAMENTO");
        Usuario intacto = usuarioRepository.findById(atendente.usuarioId()).orElseThrow();
        assertThat(intacto.getAtivo()).isTrue();
        assertThat(intacto.getPerfil()).isEqualTo(PerfilUsuario.ATENDENTE);
    }

    @Test
    void manualStatusRouteStillCannotReturnTicketsToOpen() throws Exception {
        Chamado andamento = chamado(StatusChamado.EM_ANDAMENTO, atendente);

        mockMvc.perform(patch("/api/chamados/{id}/status", andamento.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "ABERTO", "versaoEsperada", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    private ResultActions operar(Login ator, Login alvo, String perfil, boolean ativo)
            throws Exception {
        Long versao = usuarioRepository.findById(alvo.usuarioId()).orElseThrow().getVersion();
        return send(ator, alvo.usuarioId(), body(perfil, ativo, versao));
    }

    private ResultActions send(Login ator, Long alvoId, Map<String, Object> corpo)
            throws Exception {
        return mockMvc.perform(patch("/api/usuarios/{id}/operacao", alvoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ator.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(corpo)));
    }

    private static Map<String, Object> body(String perfil, boolean ativo, Long versao) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("perfil", perfil);
        corpo.put("ativo", ativo);
        corpo.put("versaoEsperada", versao);
        return corpo;
    }

    private static Map<String, Object> with(Map<String, Object> corpo, String key, Object value) {
        corpo.put(key, value);
        return corpo;
    }

    private static Map<String, Object> without(Map<String, Object> corpo, String key) {
        corpo.remove(key);
        return corpo;
    }

    private int detailStatus(Login login, Chamado chamado) throws Exception {
        return mockMvc.perform(get("/api/chamados/{id}", chamado.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andReturn().getResponse().getStatus();
    }

    private Chamado chamado(StatusChamado status, Login responsavel) {
        return chamadoRepository.saveAndFlush(new Chamado(
                "Sem rede",
                "Cabo solto",
                status,
                Prioridade.MEDIA,
                usuarioRepository.findById(solicitante.usuarioId()).orElseThrow(),
                usuarioRepository.findById(responsavel.usuarioId()).orElseThrow(),
                categoria));
    }

    private Map<String, Object> row(Chamado chamado) {
        return jdbcTemplate.queryForMap("SELECT * FROM chamado WHERE id = ?", chamado.getId());
    }

    private List<Map<String, Object>> history(Chamado chamado) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM historico_status WHERE chamado_id = ? ORDER BY id", chamado.getId());
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private Login registerAndLogin(String email, String nome, PerfilUsuario perfil)
            throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", nome, "email", email, "senha", RAW_PASSWORD))))
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
                        .content(json(Map.of("email", email, "senha", RAW_PASSWORD))))
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
