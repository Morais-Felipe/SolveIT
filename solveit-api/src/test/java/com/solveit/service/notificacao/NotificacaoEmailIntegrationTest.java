package com.solveit.service.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.solveit.dto.chamado.ChamadoDtos;
import com.solveit.entity.Categoria;
import com.solveit.entity.Chamado;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.enums.Prioridade;
import com.solveit.enums.StatusChamado;
import com.solveit.repository.CategoriaRepository;
import com.solveit.repository.ChamadoRepository;
import com.solveit.repository.UsuarioRepository;
import com.solveit.service.ChamadoService;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Os e-mails saem de uma thread separada depois do commit; por isso o teste espera a
 * chegada com prazo. Ausência de e-mail é verificada com uma janela curta de espera.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "solveit.mail.enabled=true",
        "solveit.mail.from=solveit@teste.local",
        "spring.mail.host=localhost",
        "spring.mail.port=3025"
})
@AutoConfigureMockMvc
class NotificacaoEmailIntegrationTest {

    private static final String RAW_PASSWORD = "senha segura para notificacao 123";
    private static final long ESPERA_MS = 5_000;
    private static final long JANELA_SEM_EMAIL_MS = 1_500;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_notificacao_test")
            .withUsername("solveit")
            .withPassword("solveit-test");

    @RegisterExtension
    static final GreenMailExtension GREEN_MAIL = new GreenMailExtension(ServerSetupTest.SMTP);

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

    @Autowired
    private ChamadoService chamadoService;

    private Login admin1;
    private Login admin2;
    private Login solicitante;
    private Login atendente;
    private Categoria categoria;

    @BeforeEach
    void prepare() throws Exception {
        jdbcTemplate.update("DELETE FROM historico_status");
        jdbcTemplate.update("DELETE FROM comentario");
        jdbcTemplate.update("DELETE FROM chamado");
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();

        admin1 = registerAndLogin("admin1@teste.local", "Admin 1", PerfilUsuario.ADMIN);
        admin2 = registerAndLogin("admin2@teste.local", "Admin 2", PerfilUsuario.ADMIN);
        Login adminInativo = registerAndLogin("inativo@teste.local", "Inativo", PerfilUsuario.ADMIN);
        jdbcTemplate.update("UPDATE usuario SET ativo = FALSE WHERE id = ?", adminInativo.usuarioId());
        solicitante = registerAndLogin("sol@teste.local", "Solicitante", PerfilUsuario.SOLICITANTE);
        atendente = registerAndLogin("at@teste.local", "Atendente", PerfilUsuario.ATENDENTE);
        categoria = categoriaRepository.saveAndFlush(new Categoria("Rede", null));
        GREEN_MAIL.purgeEmailFromAllMailboxes();
    }

    // ---- MAIL-01 / MAIL-02 / MAIL-06 ------------------------------------------------

    @Test
    void openingNotifiesEveryActiveAdmin() throws Exception {
        long id = abrir("ALTA");

        List<MimeMessage> emails = aguardar(2);
        assertThat(destinatarios(emails))
                .containsExactlyInAnyOrder("admin1@teste.local", "admin2@teste.local");
        assertThat(emails.get(0).getSubject()).isEqualTo("[SolveIT] #" + id + " Sem rede");
        assertThat(emails.get(0).getFrom()[0].toString()).isEqualTo("solveit@teste.local");
        assertThat(corpo(emails.get(0))).contains("Solicitante").contains("ALTA").contains("Rede");
    }

    @Test
    void criticalOpeningIsMarkedUrgent() throws Exception {
        long id = abrir("CRITICA");

        for (MimeMessage email : aguardar(2)) {
            assertThat(email.getSubject()).isEqualTo("[URGENTE] [SolveIT] #" + id + " Sem rede");
        }
    }

    @Test
    void rejectedOpeningSendsNothing() throws Exception {
        jdbcTemplate.update("UPDATE categoria SET ativo = FALSE WHERE id = ?", categoria.getId());

        send(post("/api/chamados"), solicitante, Map.of(
                "titulo", "Sem rede", "descricao", "Cabo solto",
                "prioridade", "ALTA", "categoriaId", categoria.getId()))
                .andExpect(status().isConflict());

        assertNenhumEmail();
    }

    @Test
    void rolledBackOpeningSendsNothing() throws Exception {
        executeAsRoot("""
                CREATE TRIGGER trg_teste_falha_historico_email
                BEFORE INSERT ON historico_status
                FOR EACH ROW
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'falha simulada de historico'
                """);
        try {
            assertThatThrownBy(() -> chamadoService.abrir(
                    solicitante.usuarioId(),
                    new ChamadoDtos.Criar("Sem rede", "Cabo solto", Prioridade.ALTA,
                            categoria.getId(), Map.of())))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            executeAsRoot("DROP TRIGGER IF EXISTS trg_teste_falha_historico_email");
        }

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chamado", Long.class)).isZero();
        assertNenhumEmail();
    }

    // ---- MAIL-03 ---------------------------------------------------------------------

    @Test
    void adminAssignmentNotifiesAttendantAndRequester() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);

        send(patch("/api/chamados/{id}/atendente", chamado.getId()), admin1,
                Map.of("atendenteId", atendente.usuarioId(), "versaoEsperada", 0))
                .andExpect(status().isOk());

        assertThat(destinatarios(aguardar(2)))
                .containsExactlyInAnyOrder("at@teste.local", "sol@teste.local");
    }

    @Test
    void selfAssignmentDoesNotNotifyTheActor() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);

        send(patch("/api/chamados/{id}/atendente", chamado.getId()), atendente,
                Map.of("atendenteId", atendente.usuarioId(), "versaoEsperada", 0))
                .andExpect(status().isOk());

        assertThat(destinatarios(aguardar(1))).containsExactly("sol@teste.local");
        assertNenhumEmailAlemDe(1);
    }

    // ---- MAIL-04 ---------------------------------------------------------------------

    @Test
    void statusChangesNotifyTheRequesterButNotTheActor() throws Exception {
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, atendente);

        send(patch("/api/chamados/{id}/status", chamado.getId()), atendente,
                Map.of("status", "RESOLVIDO", "versaoEsperada", 0, "observacao", "Cabo trocado"))
                .andExpect(status().isOk());
        List<MimeMessage> resolvido = aguardar(1);
        assertThat(destinatarios(resolvido)).containsExactly("sol@teste.local");
        assertThat(corpo(resolvido.get(0)))
                .contains("EM_ANDAMENTO").contains("RESOLVIDO").contains("Cabo trocado");

        GREEN_MAIL.purgeEmailFromAllMailboxes();
        send(patch("/api/chamados/{id}/status", chamado.getId()), solicitante,
                Map.of("status", "FECHADO", "versaoEsperada", 1))
                .andExpect(status().isOk());
        assertNenhumEmail();
    }

    @Test
    void returningToTheQueueNotifiesTheRequester() throws Exception {
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, atendente);
        Long versao = usuarioRepository.findById(atendente.usuarioId()).orElseThrow().getVersion();

        send(patch("/api/usuarios/{id}/operacao", atendente.usuarioId()), admin1,
                Map.of("perfil", "ATENDENTE", "ativo", false, "versaoEsperada", versao))
                .andExpect(status().isOk());

        List<MimeMessage> emails = aguardar(1);
        assertThat(destinatarios(emails)).containsExactly("sol@teste.local");
        assertThat(emails.get(0).getSubject()).contains("#" + chamado.getId());
        assertThat(corpo(emails.get(0))).contains("ABERTO");
    }

    @Test
    void rejectedTransitionSendsNothing() throws Exception {
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, atendente);

        send(patch("/api/chamados/{id}/status", chamado.getId()), solicitante,
                Map.of("status", "RESOLVIDO", "versaoEsperada", 0))
                .andExpect(status().isForbidden());

        assertNenhumEmail();
    }

    // ---- MAIL-05 ---------------------------------------------------------------------

    @Test
    void commentNotifiesTheOtherInvolvedPeople() throws Exception {
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, atendente);

        send(post("/api/chamados/{id}/comentarios", chamado.getId()), solicitante,
                Map.of("texto", "Ainda sem conexão à rede"))
                .andExpect(status().isCreated());
        List<MimeMessage> doSolicitante = aguardar(1);
        assertThat(destinatarios(doSolicitante)).containsExactly("at@teste.local");
        assertThat(corpo(doSolicitante.get(0))).contains("Solicitante").contains("Ainda sem conexão à rede");

        GREEN_MAIL.purgeEmailFromAllMailboxes();
        send(post("/api/chamados/{id}/comentarios", chamado.getId()), admin1,
                Map.of("texto", "Acompanhando"))
                .andExpect(status().isCreated());
        assertThat(destinatarios(aguardar(2)))
                .containsExactlyInAnyOrder("sol@teste.local", "at@teste.local");
    }

    @Test
    void commentRecipientsAreNotDuplicated() throws Exception {
        // O mesmo usuário é solicitante e atendente do chamado (perfil trocado depois).
        Usuario ambos = usuarioRepository.findById(atendente.usuarioId()).orElseThrow();
        Chamado chamado = chamadoRepository.saveAndFlush(new Chamado(
                "Sem rede", "Cabo solto", StatusChamado.EM_ANDAMENTO, Prioridade.MEDIA,
                ambos, ambos, categoria));

        send(post("/api/chamados/{id}/comentarios", chamado.getId()), admin1,
                Map.of("texto", "Verificando"))
                .andExpect(status().isCreated());

        assertThat(destinatarios(aguardar(1))).containsExactly("at@teste.local");
        assertNenhumEmailAlemDe(1);
    }

    @Test
    void rejectedCommentSendsNothing() throws Exception {
        Chamado chamado = chamado(StatusChamado.FECHADO, atendente);

        send(post("/api/chamados/{id}/comentarios", chamado.getId()), solicitante,
                Map.of("texto", "Tarde demais"))
                .andExpect(status().isConflict());

        assertNenhumEmail();
    }

    // ---- apoio -----------------------------------------------------------------------

    private long abrir(String prioridade) throws Exception {
        MvcResult result = send(post("/api/chamados"), solicitante, Map.of(
                        "titulo", "Sem rede",
                        "descricao", "Cabo solto",
                        "prioridade", prioridade,
                        "categoriaId", categoria.getId()))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private List<MimeMessage> aguardar(int quantidade) {
        assertThat(GREEN_MAIL.waitForIncomingEmail(ESPERA_MS, quantidade))
                .as("esperava %d e-mail(s)", quantidade)
                .isTrue();
        return Arrays.asList(GREEN_MAIL.getReceivedMessages());
    }

    private static void assertNenhumEmail() {
        assertThat(GREEN_MAIL.waitForIncomingEmail(JANELA_SEM_EMAIL_MS, 1)).isFalse();
    }

    private static void assertNenhumEmailAlemDe(int quantidade) {
        assertThat(GREEN_MAIL.waitForIncomingEmail(JANELA_SEM_EMAIL_MS, quantidade + 1)).isFalse();
    }

    private static List<String> destinatarios(List<MimeMessage> emails) throws Exception {
        List<String> todos = new ArrayList<>();
        for (MimeMessage email : emails) {
            for (var endereco : email.getRecipients(Message.RecipientType.TO)) {
                todos.add(endereco.toString());
            }
        }
        return todos;
    }

    private static String corpo(MimeMessage email) throws Exception {
        return email.getContent().toString();
    }

    private Chamado chamado(StatusChamado status, Login responsavel) {
        return chamadoRepository.saveAndFlush(new Chamado(
                "Sem rede",
                "Cabo solto",
                status,
                Prioridade.MEDIA,
                usuarioRepository.findById(solicitante.usuarioId()).orElseThrow(),
                responsavel == null
                        ? null
                        : usuarioRepository.findById(responsavel.usuarioId()).orElseThrow(),
                categoria));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Login login, Map<String, ?> body)
            throws Exception {
        return mockMvc.perform(request
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private static void executeAsRoot(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                        MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
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
