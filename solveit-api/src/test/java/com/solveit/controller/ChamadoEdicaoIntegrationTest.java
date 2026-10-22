package com.solveit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class ChamadoEdicaoIntegrationTest {

    private static final String RAW_PASSWORD = "senha segura para edicao 123";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_chamado_edicao_test")
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
    private Login atendente;
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
        atendente = registerAndLogin("atendente@example.com", "Atendente", PerfilUsuario.ATENDENTE);
        admin = registerAndLogin("admin@example.com", "Admin", PerfilUsuario.ADMIN);
        categoria = categoriaRepository.saveAndFlush(new Categoria("Rede", null));
    }

    @Test
    void ownerEditsOnlyTitleAndDescriptionOfOpenTicket() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        Map<String, Object> antes = row(chamado.getId());
        long historicos = count("historico_status");

        edit(dono, chamado.getId(), payload("  Titulo corrigido  ", "Nova descricao", 0L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Titulo corrigido"))
                .andExpect(jsonPath("$.descricao").value("Nova descricao"))
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.prioridade").value("ALTA"))
                .andExpect(jsonPath("$.versao").value(1));

        Map<String, Object> depois = row(chamado.getId());
        assertThat(depois.get("titulo")).isEqualTo("Titulo corrigido");
        assertThat(depois.get("descricao")).isEqualTo("Nova descricao");
        for (String inalterado : List.of(
                "status", "prioridade", "solicitante_id", "atendente_id", "categoria_id",
                "criado_em", "resolvido_em")) {
            assertThat(depois.get(inalterado)).as(inalterado).isEqualTo(antes.get(inalterado));
        }
        assertThat(((Number) depois.get("version")).longValue()).isEqualTo(1);
        assertThat(count("historico_status")).isEqualTo(historicos);
    }

    @Test
    void adminEditsAnyOpenTicket() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);

        edit(admin, chamado.getId(), payload("Ajuste do admin", "Texto revisado", 0L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Ajuste do admin"));
    }

    @Test
    void forbidsAttendantsAndOtherRequesters() throws Exception {
        Chamado naFila = chamado(StatusChamado.ABERTO, null);
        Chamado atribuido = chamado(StatusChamado.ABERTO, atendente);

        for (Map.Entry<Login, Chamado> tentativa : List.of(
                Map.entry(outroSolicitante, naFila),
                Map.entry(atendente, naFila),
                Map.entry(atendente, atribuido))) {
            edit(tentativa.getKey(), tentativa.getValue().getId(), payload("Invasao", "x", 0L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        assertThat(row(naFila.getId()).get("titulo")).isEqualTo("Titulo original");
        assertThat(row(atribuido.getId()).get("titulo")).isEqualTo("Titulo original");
    }

    @Test
    void rejectsEditionOutsideOpenStatus() throws Exception {
        for (StatusChamado status : List.of(
                StatusChamado.EM_ANDAMENTO, StatusChamado.RESOLVIDO,
                StatusChamado.FECHADO, StatusChamado.CANCELADO)) {
            Chamado chamado = chamado(status, status == StatusChamado.CANCELADO ? null : atendente);

            for (Login login : List.of(dono, admin)) {
                edit(login, chamado.getId(), payload("Tarde demais", "x", 0L))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("INVALID_STATE"));
            }
            assertThat(row(chamado.getId()).get("titulo")).isEqualTo("Titulo original");
        }
    }

    @Test
    void rejectsStaleVersionAfterAChangePersistedElsewhere() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        jdbcTemplate.update(
                "UPDATE chamado SET titulo = 'Mudanca confirmada', version = version + 1 WHERE id = ?",
                chamado.getId());

        edit(dono, chamado.getId(), payload("Formulario antigo", "x", 0L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE"));

        assertThat(row(chamado.getId()).get("titulo")).isEqualTo("Mudanca confirmada");
    }

    @Test
    void editionStartedBeforeAssignmentDoesNotOverwriteTheService() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        Map<String, Object> atribuicao = new LinkedHashMap<>();
        atribuicao.put("atendenteId", atendente.usuarioId());
        atribuicao.put("versaoEsperada", 0L);

        mockMvc.perform(patch("/api/chamados/{id}/atendente", chamado.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + atendente.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(atribuicao)))
                .andExpect(status().isOk());

        edit(dono, chamado.getId(), payload("Formulario aberto antes", "x", 0L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE"));

        Map<String, Object> depois = row(chamado.getId());
        assertThat(depois.get("titulo")).isEqualTo("Titulo original");
        assertThat(depois.get("status")).isEqualTo("EM_ANDAMENTO");
        assertThat(((Number) depois.get("atendente_id")).longValue())
                .isEqualTo(atendente.usuarioId());
    }

    @Test
    void rejectsInvalidOrServerOwnedFields() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);

        List<Map<String, Object>> invalidos = List.of(
                without(payload("Titulo", "Descricao", 0L), "versaoEsperada"),
                payload("Titulo", "Descricao", -1L),
                payload("   ", "Descricao", 0L),
                payload("x".repeat(201), "Descricao", 0L),
                payload("Titulo", " ", 0L),
                with(payload("Titulo", "Descricao", 0L), "status", "FECHADO"),
                with(payload("Titulo", "Descricao", 0L), "prioridade", "BAIXA"),
                with(payload("Titulo", "Descricao", 0L), "categoriaId", 99),
                with(payload("Titulo", "Descricao", 0L), "atendenteId", atendente.usuarioId()));

        for (Map<String, Object> corpo : invalidos) {
            edit(dono, chamado.getId(), corpo)
                    .andExpect(status().isBadRequest());
        }
        Map<String, Object> depois = row(chamado.getId());
        assertThat(depois.get("titulo")).isEqualTo("Titulo original");
        assertThat(((Number) depois.get("version")).longValue()).isZero();
    }

    @Test
    void returnsNotFoundForMissingTicket() throws Exception {
        edit(admin, 999_999L, payload("Fantasma", "x", 0L))
                .andExpect(status().isNotFound());
    }

    private ResultActions edit(Login login, Long chamadoId, Map<String, Object> body)
            throws Exception {
        return mockMvc.perform(put("/api/chamados/{id}", chamadoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private static Map<String, Object> payload(String titulo, String descricao, Long versao) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("titulo", titulo);
        body.put("descricao", descricao);
        body.put("versaoEsperada", versao);
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
                "Titulo original",
                "Descricao original",
                status,
                Prioridade.ALTA,
                usuarioRepository.findById(dono.usuarioId()).orElseThrow(),
                responsavel == null
                        ? null
                        : usuarioRepository.findById(responsavel.usuarioId()).orElseThrow(),
                categoria));
    }

    private Map<String, Object> row(Long chamadoId) {
        return jdbcTemplate.queryForMap("SELECT * FROM chamado WHERE id = ?", chamadoId);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
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
