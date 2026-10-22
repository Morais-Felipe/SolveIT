package com.solveit.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
class ChamadoTransicaoIntegrationTest {

    private static final String RAW_PASSWORD = "senha segura para transicao 123";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_chamado_transicao_test")
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
    private Login responsavel;
    private Login outroAtendente;
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
        responsavel = registerAndLogin("resp@example.com", "Responsavel", PerfilUsuario.ATENDENTE);
        outroAtendente = registerAndLogin("outro@example.com", "Outro", PerfilUsuario.ATENDENTE);
        admin = registerAndLogin("admin@example.com", "Admin", PerfilUsuario.ADMIN);
        categoria = categoriaRepository.saveAndFlush(new Categoria("Rede", null));
    }

    @Test
    void responsibleAttendantResolvesWithOneInstantAcrossTicketAndHistory() throws Exception {
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, responsavel);

        change(responsavel, chamado.getId(), payload("RESOLVIDO", 0L, "Cabo trocado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVIDO"))
                .andExpect(jsonPath("$.atendente.id").value(responsavel.usuarioId()))
                .andExpect(jsonPath("$.resolvidoEm").isNotEmpty())
                .andExpect(jsonPath("$.versao").value(1));

        Map<String, Object> estado = row(chamado.getId());
        assertThat(estado.get("resolvido_em"))
                .isNotNull()
                .isEqualTo(estado.get("atualizado_em"));
        List<Map<String, Object>> historico = history(chamado.getId());
        assertThat(historico).hasSize(1);
        Map<String, Object> registro = historico.get(0);
        assertThat(registro.get("status_anterior")).isEqualTo("EM_ANDAMENTO");
        assertThat(registro.get("status_novo")).isEqualTo("RESOLVIDO");
        assertThat(((Number) registro.get("alterado_por_id")).longValue())
                .isEqualTo(responsavel.usuarioId());
        assertThat(registro.get("observacao")).isEqualTo("Cabo trocado");
        assertThat(registro.get("alterado_em")).isEqualTo(estado.get("resolvido_em"));
    }

    @Test
    void adminResolvesTicketAssignedToSomeoneElse() throws Exception {
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, responsavel);

        change(admin, chamado.getId(), payload("RESOLVIDO", 0L, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVIDO"));

        assertThat(((Number) history(chamado.getId()).get(0).get("alterado_por_id")).longValue())
                .isEqualTo(admin.usuarioId());
    }

    @Test
    void forbidsActorsOutsideTheResolutionRule() throws Exception {
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, responsavel);

        // Não enxerga o chamado: 403 antes de qualquer outra verificação.
        change(outroAtendente, chamado.getId(), payload("RESOLVIDO", 99L, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        // Enxerga, mas solicitante não resolve.
        change(dono, chamado.getId(), payload("RESOLVIDO", 0L, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertUnchanged(chamado.getId(), "EM_ANDAMENTO");
    }

    @Test
    void rejectsTransitionsOutsideTheTable() throws Exception {
        Chamado aberto = chamado(StatusChamado.ABERTO, null);
        Chamado emAndamento = chamado(StatusChamado.EM_ANDAMENTO, responsavel);

        change(admin, aberto.getId(), payload("RESOLVIDO", 0L, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        change(admin, emAndamento.getId(), payload("EM_ANDAMENTO", 0L, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        change(admin, emAndamento.getId(), payload("ABERTO", 0L, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        assertUnchanged(aberto.getId(), "ABERTO");
        assertUnchanged(emAndamento.getId(), "EM_ANDAMENTO");
    }

    @Test
    void rejectsStaleVersion() throws Exception {
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, responsavel);
        jdbcTemplate.update("UPDATE chamado SET version = version + 1 WHERE id = ?", chamado.getId());

        change(responsavel, chamado.getId(), payload("RESOLVIDO", 0L, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE"));

        assertThat(row(chamado.getId()).get("status")).isEqualTo("EM_ANDAMENTO");
        assertThat(row(chamado.getId()).get("resolvido_em")).isNull();
        assertThat(history(chamado.getId())).isEmpty();
    }

    @Test
    void rejectsInvalidPayloads() throws Exception {
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, responsavel);

        List<Map<String, Object>> invalidos = List.of(
                without(payload("RESOLVIDO", 0L, null), "status"),
                payload("INEXISTENTE", 0L, null),
                without(payload("RESOLVIDO", 0L, null), "versaoEsperada"),
                payload("RESOLVIDO", -1L, null),
                payload("RESOLVIDO", 0L, "x".repeat(501)),
                with(payload("RESOLVIDO", 0L, null), "resolvidoEm", "2020-01-01T00:00:00"));

        for (Map<String, Object> corpo : invalidos) {
            change(responsavel, chamado.getId(), corpo)
                    .andExpect(status().isBadRequest());
        }
        assertUnchanged(chamado.getId(), "EM_ANDAMENTO");
    }

    @Test
    void returnsNotFoundForMissingTicket() throws Exception {
        change(admin, 999_999L, payload("RESOLVIDO", 0L, null))
                .andExpect(status().isNotFound());
    }

    private void assertUnchanged(Long chamadoId, String status) {
        Map<String, Object> estado = row(chamadoId);
        assertThat(estado.get("status")).isEqualTo(status);
        assertThat(((Number) estado.get("version")).longValue()).isZero();
        assertThat(estado.get("resolvido_em")).isNull();
        assertThat(history(chamadoId)).isEmpty();
    }

    private ResultActions change(Login login, Long chamadoId, Map<String, Object> body)
            throws Exception {
        return mockMvc.perform(patch("/api/chamados/{id}/status", chamadoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private static Map<String, Object> payload(String status, Long versao, String observacao) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
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

    private record Login(Long usuarioId, String accessToken) {
    }
}
