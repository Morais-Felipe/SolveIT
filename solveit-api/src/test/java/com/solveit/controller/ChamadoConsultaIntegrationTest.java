package com.solveit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class ChamadoConsultaIntegrationTest {

    private static final String RAW_PASSWORD = "senha segura para consulta 123";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_chamado_consulta_test")
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

    private Login solicitanteA;
    private Login solicitanteB;
    private Login atendenteX;
    private Login atendenteY;
    private Login admin;

    /** A: ABERTO na fila. */
    private Chamado filaDeA;
    /** A: EM_ANDAMENTO com X. */
    private Chamado andamentoDeAComX;
    /** B: FECHADO com X (encerrado, continua de X). */
    private Chamado fechadoDeBComX;
    /** B: EM_ANDAMENTO com Y. */
    private Chamado andamentoDeBComY;

    @BeforeEach
    void prepare() throws Exception {
        jdbcTemplate.update("DELETE FROM historico_status");
        jdbcTemplate.update("DELETE FROM comentario");
        jdbcTemplate.update("DELETE FROM chamado");
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();

        solicitanteA = registerAndLogin("a@example.com", "Solicitante A", PerfilUsuario.SOLICITANTE);
        solicitanteB = registerAndLogin("b@example.com", "Solicitante B", PerfilUsuario.SOLICITANTE);
        atendenteX = registerAndLogin("x@example.com", "Atendente X", PerfilUsuario.ATENDENTE);
        atendenteY = registerAndLogin("y@example.com", "Atendente Y", PerfilUsuario.ATENDENTE);
        admin = registerAndLogin("admin@example.com", "Admin", PerfilUsuario.ADMIN);

        Categoria categoria = categoriaRepository.saveAndFlush(new Categoria("Rede", null));
        filaDeA = chamado("Fila de A", StatusChamado.ABERTO, solicitanteA, null, categoria);
        andamentoDeAComX = chamado(
                "Andamento de A", StatusChamado.EM_ANDAMENTO, solicitanteA, atendenteX, categoria);
        fechadoDeBComX = chamado(
                "Fechado de B", StatusChamado.FECHADO, solicitanteB, atendenteX, categoria);
        andamentoDeBComY = chamado(
                "Andamento de B", StatusChamado.EM_ANDAMENTO, solicitanteB, atendenteY, categoria);
    }

    @Test
    void requiresBearerToReadTicket() throws Exception {
        mockMvc.perform(get("/api/chamados/{id}", filaDeA.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
    }

    @Test
    void appliesTheRelationalAccessMatrixToTicketDetail() throws Exception {
        Map<Login, Map<Chamado, Integer>> matriz = new LinkedHashMap<>();
        matriz.put(solicitanteA, expected(200, 200, 403, 403));
        matriz.put(solicitanteB, expected(403, 403, 200, 200));
        matriz.put(atendenteX, expected(200, 200, 200, 403));
        matriz.put(atendenteY, expected(200, 403, 403, 200));
        matriz.put(admin, expected(200, 200, 200, 200));

        for (Map.Entry<Login, Map<Chamado, Integer>> linha : matriz.entrySet()) {
            for (Map.Entry<Chamado, Integer> celula : linha.getValue().entrySet()) {
                int status = statusOf(linha.getKey(), celula.getKey().getId());
                assertThat(status)
                        .as("%s lendo '%s'", linha.getKey().email(), celula.getKey().getTitulo())
                        .isEqualTo(celula.getValue());
            }
        }
    }

    @Test
    void returnsFullDetailWithoutCredentials() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/chamados/{id}", andamentoDeAComX.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(atendenteX)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(andamentoDeAComX.getId()))
                .andExpect(jsonPath("$.titulo").value("Andamento de A"))
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$.solicitante.id").value(solicitanteA.usuarioId()))
                .andExpect(jsonPath("$.solicitante.nome").value("Solicitante A"))
                .andExpect(jsonPath("$.atendente.id").value(atendenteX.usuarioId()))
                .andExpect(jsonPath("$.atendente.nome").value("Atendente X"))
                .andExpect(jsonPath("$.categoria.nome").value("Rede"))
                .andExpect(jsonPath("$.versao").value(0))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("senha")
                .doesNotContain("$2")
                .doesNotContain("@example.com");
    }

    @Test
    void returnsNotFoundForMissingTicketToEveryProfile() throws Exception {
        for (Login login : List.of(solicitanteA, atendenteX, admin)) {
            assertThat(statusOf(login, 999_999L)).isEqualTo(404);
        }
    }

    @Test
    void currentRelationIsTheOnlyAuthority() throws Exception {
        assertThat(statusOf(atendenteX, andamentoDeAComX.getId())).isEqualTo(200);

        // Reatribuído a Y: X deixa de ter relação atual com o chamado.
        jdbcTemplate.update(
                "UPDATE chamado SET atendente_id = ? WHERE id = ?",
                atendenteY.usuarioId(), andamentoDeAComX.getId());
        assertThat(statusOf(atendenteX, andamentoDeAComX.getId())).isEqualTo(403);
        assertThat(statusOf(atendenteY, andamentoDeAComX.getId())).isEqualTo(200);

        // X rebaixado a SOLICITANTE: chamado encerrado que era dele deixa de ser visível.
        assertThat(statusOf(atendenteX, fechadoDeBComX.getId())).isEqualTo(200);
        changeProfile(atendenteX.usuarioId(), PerfilUsuario.SOLICITANTE);
        assertThat(statusOf(atendenteX, fechadoDeBComX.getId())).isEqualTo(403);
        assertThat(statusOf(atendenteX, filaDeA.getId())).isEqualTo(403);
    }

    @Test
    void commentAuthorshipDoesNotGrantAccess() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO comentario (chamado_id, autor_id, texto, criado_em)
                VALUES (?, ?, 'Comentario antigo de B', UTC_TIMESTAMP(6))
                """, filaDeA.getId(), solicitanteB.usuarioId());

        assertThat(statusOf(solicitanteB, filaDeA.getId())).isEqualTo(403);
    }

    @Test
    void queueTicketLeavesTheQueueOnceAssigned() throws Exception {
        assertThat(statusOf(atendenteY, filaDeA.getId())).isEqualTo(200);

        jdbcTemplate.update(
                "UPDATE chamado SET atendente_id = ?, status = 'EM_ANDAMENTO' WHERE id = ?",
                atendenteX.usuarioId(), filaDeA.getId());

        assertThat(statusOf(atendenteY, filaDeA.getId())).isEqualTo(403);
        assertThat(statusOf(atendenteX, filaDeA.getId())).isEqualTo(200);
    }

    private Map<Chamado, Integer> expected(int fila, int andamentoA, int fechadoB, int andamentoB) {
        Map<Chamado, Integer> esperado = new LinkedHashMap<>();
        esperado.put(filaDeA, fila);
        esperado.put(andamentoDeAComX, andamentoA);
        esperado.put(fechadoDeBComX, fechadoB);
        esperado.put(andamentoDeBComY, andamentoB);
        return esperado;
    }

    private int statusOf(Login login, Long chamadoId) throws Exception {
        return mockMvc.perform(get("/api/chamados/{id}", chamadoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private Chamado chamado(
            String titulo,
            StatusChamado status,
            Login solicitante,
            Login atendente,
            Categoria categoria) {
        return chamadoRepository.saveAndFlush(new Chamado(
                titulo,
                "Descricao de " + titulo,
                status,
                Prioridade.MEDIA,
                usuarioRepository.findById(solicitante.usuarioId()).orElseThrow(),
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
        changeProfile(usuarioId, perfil);

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "senha", RAW_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("accessToken")
                .asText();
        return new Login(usuarioId, email, accessToken);
    }

    private void changeProfile(Long usuarioId, PerfilUsuario perfil) {
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        usuario.setPerfil(perfil);
        usuarioRepository.saveAndFlush(usuario);
    }

    private static String bearer(Login login) {
        return "Bearer " + login.accessToken();
    }

    private record Login(Long usuarioId, String email, String accessToken) {
    }
}
