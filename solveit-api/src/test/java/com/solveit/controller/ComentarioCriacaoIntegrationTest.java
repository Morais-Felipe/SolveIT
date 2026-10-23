package com.solveit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class ComentarioCriacaoIntegrationTest {

    private static final String RAW_PASSWORD = "senha segura para comentarios 123";
    private static final String MENSAGEM_ENCERRADO =
            "Este chamado foi encerrado e não aceita novos comentários.";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_comentario_test")
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
        outroSolicitante = registerAndLogin("outro@example.com", "Outro", PerfilUsuario.SOLICITANTE);
        responsavel = registerAndLogin("resp@example.com", "Responsavel", PerfilUsuario.ATENDENTE);
        outroAtendente = registerAndLogin("at2@example.com", "Atendente 2", PerfilUsuario.ATENDENTE);
        admin = registerAndLogin("admin@example.com", "Admin", PerfilUsuario.ADMIN);
        categoria = categoriaRepository.saveAndFlush(new Categoria("Rede", null));
    }

    @Test
    void requiresBearer() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);

        mockMvc.perform(post("/api/chamados/{id}/comentarios", chamado.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("texto", "Oi"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
    }

    @Test
    void ownerCommentsWithIdentityFromThePrincipalAndTicketIsUntouched() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);
        Map<String, Object> antes = row(chamado.getId());

        MvcResult result = comment(dono, chamado.getId(), Map.of("texto", "  Ainda sem rede  "))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.texto").value("Ainda sem rede"))
                .andExpect(jsonPath("$.autor.id").value(dono.usuarioId()))
                .andExpect(jsonPath("$.autor.nome").value("Dono"))
                .andExpect(jsonPath("$.criadoEm").isNotEmpty())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertFields(body, "id", "texto", "autor", "criadoEm");
        assertFields(body.path("autor"), "id", "nome");

        List<Map<String, Object>> comentarios = comments(chamado.getId());
        assertThat(comentarios).hasSize(1);
        assertThat(((Number) comentarios.get(0).get("autor_id")).longValue())
                .isEqualTo(dono.usuarioId());
        assertThat(row(chamado.getId())).isEqualTo(antes);
        assertThat(historyCount()).isZero();
    }

    @Test
    void everyoneWithCurrentAccessCommentsWhileTheConversationIsOpen() throws Exception {
        Chamado naFila = chamado(StatusChamado.ABERTO, null);
        Chamado emAndamento = chamado(StatusChamado.EM_ANDAMENTO, responsavel);
        Chamado resolvido = chamado(StatusChamado.RESOLVIDO, responsavel);

        List<Map.Entry<Login, Chamado>> permitidos = List.of(
                Map.entry(outroAtendente, naFila),
                Map.entry(admin, naFila),
                Map.entry(responsavel, emAndamento),
                Map.entry(dono, emAndamento),
                Map.entry(admin, emAndamento),
                Map.entry(responsavel, resolvido),
                Map.entry(dono, resolvido));

        for (Map.Entry<Login, Chamado> caso : permitidos) {
            comment(caso.getKey(), caso.getValue().getId(), Map.of("texto", "Comentario"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.autor.id").value(caso.getKey().usuarioId()));
        }
        assertThat(commentCount()).isEqualTo(permitidos.size());
        assertThat(historyCount()).isZero();
    }

    @Test
    void forbidsUsersWithoutCurrentRelation() throws Exception {
        Chamado aberto = chamado(StatusChamado.ABERTO, null);
        Chamado emAndamento = chamado(StatusChamado.EM_ANDAMENTO, responsavel);

        comment(outroSolicitante, aberto.getId(), Map.of("texto", "Intromissao"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        comment(outroAtendente, emAndamento.getId(), Map.of("texto", "Intromissao"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(commentCount()).isZero();
    }

    @Test
    void closedConversationsRejectCommentsWithTheApprovedMessage() throws Exception {
        Chamado fechado = chamado(StatusChamado.FECHADO, responsavel);
        Chamado cancelado = chamado(StatusChamado.CANCELADO, null);

        for (Map.Entry<Login, Chamado> caso : List.of(
                Map.entry(dono, fechado),
                Map.entry(admin, fechado),
                Map.entry(responsavel, fechado),
                Map.entry(dono, cancelado),
                Map.entry(admin, cancelado))) {
            comment(caso.getKey(), caso.getValue().getId(), Map.of("texto", "Tarde demais"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("COMMENTS_CLOSED"))
                    .andExpect(jsonPath("$.detail").value(MENSAGEM_ENCERRADO));
        }
        assertThat(commentCount()).isZero();
    }

    @Test
    void accessIsCheckedBeforeTheClosedState() throws Exception {
        Chamado fechado = chamado(StatusChamado.FECHADO, responsavel);

        comment(outroSolicitante, fechado.getId(), Map.of("texto", "Curioso"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidTextAndClientChosenAuthor() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);

        Map<String, Object> autorForjado = new LinkedHashMap<>();
        autorForjado.put("texto", "Assinado por outro");
        autorForjado.put("autorId", admin.usuarioId());

        List<Map<String, Object>> invalidos = List.of(
                Map.of("texto", "   "),
                Map.of("texto", "x".repeat(10_001)),
                Map.of(),
                autorForjado,
                Map.of("texto", "ok", "status", "FECHADO"));

        for (Map<String, Object> corpo : invalidos) {
            comment(dono, chamado.getId(), corpo)
                    .andExpect(status().isBadRequest());
        }
        assertThat(commentCount()).isZero();
    }

    @Test
    void acceptsTheMaximumTextLength() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);

        comment(dono, chamado.getId(), Map.of("texto", "x".repeat(10_000)))
                .andExpect(status().isCreated());
    }

    @Test
    void returnsNotFoundForMissingTicket() throws Exception {
        comment(admin, 999_999L, Map.of("texto", "Fantasma"))
                .andExpect(status().isNotFound());
    }

    private ResultActions comment(Login login, Long chamadoId, Map<String, ?> body)
            throws Exception {
        return mockMvc.perform(post("/api/chamados/{id}/comentarios", chamadoId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
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

    private List<Map<String, Object>> comments(Long chamadoId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM comentario WHERE chamado_id = ? ORDER BY id", chamadoId);
    }

    private long commentCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM comentario", Long.class);
    }

    private long historyCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM historico_status", Long.class);
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private static void assertFields(JsonNode node, String... expected) {
        List<String> actual = new ArrayList<>();
        node.fieldNames().forEachRemaining(actual::add);
        assertThat(actual).containsExactlyInAnyOrder(expected);
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
