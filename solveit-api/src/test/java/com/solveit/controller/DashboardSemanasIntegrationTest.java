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
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Relógio fixo: os limites das janelas são comparados até o microssegundo. */
@Testcontainers
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@Import(DashboardSemanasIntegrationTest.RelogioFixo.class)
class DashboardSemanasIntegrationTest {

    private static final String RAW_PASSWORD = "senha segura para dashboard 123";
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 6, 15, 12, 0);
    private static final int ULTIMA = 7;

    @TestConfiguration
    static class RelogioFixo {
        @Bean
        @Primary
        Clock relogioFixo() {
            return Clock.fixed(AGORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        }
    }

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_dashboard_semanas_test")
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
    private Login atendenteX;
    private Login atendenteY;
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
        atendenteX = registerAndLogin("x@example.com", "Xavier", PerfilUsuario.ATENDENTE);
        atendenteY = registerAndLogin("y@example.com", "Yara", PerfilUsuario.ATENDENTE);
        categoria = categoriaRepository.saveAndFlush(new Categoria("Rede", null));
    }

    @Test
    void alwaysReturnsEightContiguousSevenDayWindowsEndingNow() throws Exception {
        JsonNode semanas = semanas(admin);

        assertThat(semanas.size()).isEqualTo(8);
        assertThat(data(semanas.get(0), "inicio")).isEqualTo(AGORA.minusWeeks(8));
        assertThat(data(semanas.get(ULTIMA), "fim")).isEqualTo(AGORA);
        for (int i = 0; i < semanas.size(); i++) {
            assertThat(data(semanas.get(i), "fim")).isEqualTo(data(semanas.get(i), "inicio").plusWeeks(1));
            assertContagem(semanas.get(i), 0, 0);
            if (i > 0) {
                assertThat(data(semanas.get(i), "inicio")).isEqualTo(data(semanas.get(i - 1), "fim"));
            }
        }
    }

    @Test
    void windowIncludesItsStartAndExcludesItsEnd() throws Exception {
        LocalDateTime divisa = AGORA.minusWeeks(1);
        chamado(StatusChamado.ABERTO, null, divisa, null);
        chamado(StatusChamado.ABERTO, null, divisa.minusNanos(1_000), null);

        JsonNode semanas = semanas(admin);

        assertContagem(semanas.get(ULTIMA), 1, 0);
        assertContagem(semanas.get(ULTIMA - 1), 1, 0);
    }

    @Test
    void ignoresTicketsOutsideTheEightWeeks() throws Exception {
        LocalDateTime inicio = AGORA.minusWeeks(8);
        chamado(StatusChamado.ABERTO, null, inicio, null);
        chamado(StatusChamado.ABERTO, null, inicio.minusNanos(1_000), null);
        chamado(StatusChamado.ABERTO, null, AGORA, null);

        JsonNode semanas = semanas(admin);

        assertContagem(semanas.get(0), 1, 0);
        assertThat(total(semanas, "novos")).isEqualTo(1);
    }

    @Test
    void countsResolutionInTheWeekItHappenedEvenAfterClosing() throws Exception {
        LocalDateTime criado = AGORA.minusWeeks(3).plusDays(1);
        LocalDateTime resolvido = AGORA.minusDays(2);
        chamado(StatusChamado.RESOLVIDO, atendenteX, criado, resolvido);
        chamado(StatusChamado.FECHADO, atendenteX, criado, resolvido);

        JsonNode semanas = semanas(admin);

        assertContagem(semanas.get(ULTIMA - 2), 2, 0);
        assertContagem(semanas.get(ULTIMA), 0, 2);
    }

    @Test
    void cancelledTicketCountsAsNewButNeverAsResolved() throws Exception {
        chamado(StatusChamado.CANCELADO, null, AGORA.minusDays(1), null);

        JsonNode semanas = semanas(admin);

        assertContagem(semanas.get(ULTIMA), 1, 0);
        assertThat(total(semanas, "resolvidos")).isZero();
    }

    @Test
    void attendantOnlyCountsTheQueueAndTheirOwnTickets() throws Exception {
        LocalDateTime criado = AGORA.minusDays(3);
        LocalDateTime resolvido = AGORA.minusDays(1);
        chamado(StatusChamado.ABERTO, null, criado, null);
        chamado(StatusChamado.RESOLVIDO, atendenteX, criado, resolvido);
        chamado(StatusChamado.RESOLVIDO, atendenteY, criado, resolvido);

        assertContagem(semanas(admin).get(ULTIMA), 3, 2);
        assertContagem(semanas(atendenteX).get(ULTIMA), 2, 1);
        assertContagem(semanas(atendenteY).get(ULTIMA), 2, 1);
    }

    private JsonNode semanas(Login login) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/dashboard/resumo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("semanas");
    }

    private static LocalDateTime data(JsonNode semana, String campo) {
        return LocalDateTime.parse(semana.path(campo).asText());
    }

    private static long total(JsonNode semanas, String campo) {
        long soma = 0;
        for (JsonNode semana : semanas) {
            soma += semana.path(campo).asLong();
        }
        return soma;
    }

    private static void assertContagem(JsonNode semana, long novos, long resolvidos) {
        assertThat(semana.path("novos").asLong()).isEqualTo(novos);
        assertThat(semana.path("resolvidos").asLong()).isEqualTo(resolvidos);
    }

    /** As datas são gravadas por SQL: a entidade preenche criadoEm com a hora real. */
    private void chamado(StatusChamado status, Login atendente, LocalDateTime criadoEm, LocalDateTime resolvidoEm) {
        Chamado salvo = chamadoRepository.saveAndFlush(new Chamado(
                "Chamado " + status,
                "Descricao",
                status,
                Prioridade.MEDIA,
                usuarioRepository.findById(solicitante.usuarioId()).orElseThrow(),
                atendente == null
                        ? null
                        : usuarioRepository.findById(atendente.usuarioId()).orElseThrow(),
                categoria));
        jdbcTemplate.update(
                "UPDATE chamado SET criado_em = ?, resolvido_em = ? WHERE id = ?",
                criadoEm,
                resolvidoEm,
                salvo.getId());
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
