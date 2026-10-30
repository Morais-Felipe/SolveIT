package com.solveit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureMockMvc
class DashboardIntegrationTest {

    private static final String RAW_PASSWORD = "senha segura para dashboard 123";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_dashboard_test")
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

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Login admin;
    private Login solicitante;
    private Login atendenteX;
    private Login atendenteY;
    private Categoria rede;
    private Categoria hardware;

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
        rede = categoriaRepository.saveAndFlush(new Categoria("Rede", null));
        hardware = categoriaRepository.saveAndFlush(new Categoria("Hardware", null));

        chamado(StatusChamado.ABERTO, Prioridade.CRITICA, rede, null);
        chamado(StatusChamado.ABERTO, Prioridade.ALTA, rede, null);
        chamado(StatusChamado.ABERTO, Prioridade.MEDIA, hardware, null);
        chamado(StatusChamado.EM_ANDAMENTO, Prioridade.ALTA, rede, atendenteX);
        chamado(StatusChamado.EM_ANDAMENTO, Prioridade.BAIXA, hardware, atendenteX);
        chamado(StatusChamado.EM_ANDAMENTO, Prioridade.MEDIA, rede, atendenteY);
        chamado(StatusChamado.RESOLVIDO, Prioridade.MEDIA, hardware, atendenteX);
        chamado(StatusChamado.FECHADO, Prioridade.CRITICA, rede, atendenteY);
        chamado(StatusChamado.CANCELADO, Prioridade.BAIXA, rede, null);
        // Categoria desativada depois de ter chamados continua contando (D-4).
        jdbcTemplate.update("UPDATE categoria SET ativo = FALSE WHERE id = ?", hardware.getId());
    }

    @Test
    void adminSeesTheWholeOperation() throws Exception {
        JsonNode resumo = resumo(admin);

        assertThat(resumo.path("totais").path("aberto").asLong()).isEqualTo(3);
        assertThat(resumo.path("totais").path("emAndamento").asLong()).isEqualTo(3);
        assertThat(resumo.path("totais").path("resolvido").asLong()).isEqualTo(1);
        assertThat(resumo.path("totais").path("criticaAberta").asLong()).isEqualTo(1);

        assertThat(resumo.path("porCategoria").size()).isEqualTo(2);
        assertItem(resumo.path("porCategoria").get(0), "categoriaId", rede.getId(), "Rede", "quantidade", 6);
        assertItem(resumo.path("porCategoria").get(1), "categoriaId", hardware.getId(), "Hardware", "quantidade", 3);

        assertThat(resumo.path("cargaPorAtendente").size()).isEqualTo(2);
        assertItem(resumo.path("cargaPorAtendente").get(0),
                "atendenteId", atendenteX.usuarioId(), "Xavier", "emAndamento", 2);
        assertItem(resumo.path("cargaPorAtendente").get(1),
                "atendenteId", atendenteY.usuarioId(), "Yara", "emAndamento", 1);

        assertPrioridades(resumo, 0, 1, 1, 1);
    }

    @Test
    void attendantSeesOnlyTheQueueAndTheirOwnTickets() throws Exception {
        JsonNode resumo = resumo(atendenteX);

        assertThat(resumo.path("totais").path("aberto").asLong()).isEqualTo(3);
        assertThat(resumo.path("totais").path("emAndamento").asLong()).isEqualTo(2);
        assertThat(resumo.path("totais").path("resolvido").asLong()).isEqualTo(1);
        assertThat(resumo.path("totais").path("criticaAberta").asLong()).isEqualTo(1);

        // Empate em 3: desempate pelo nome.
        assertItem(resumo.path("porCategoria").get(0), "categoriaId", hardware.getId(), "Hardware", "quantidade", 3);
        assertItem(resumo.path("porCategoria").get(1), "categoriaId", rede.getId(), "Rede", "quantidade", 3);

        assertThat(resumo.path("cargaPorAtendente").size()).isEqualTo(1);
        assertItem(resumo.path("cargaPorAtendente").get(0),
                "atendenteId", atendenteX.usuarioId(), "Xavier", "emAndamento", 2);
        assertThat(resumo.toString()).doesNotContain("Yara");
    }

    @Test
    void everyIndicatorReconcilesWithTheEquivalentListQuery() throws Exception {
        for (Login login : List.of(admin, atendenteX, atendenteY)) {
            JsonNode resumo = resumo(login);

            assertThat(resumo.path("totais").path("aberto").asLong())
                    .isEqualTo(listTotal(login, "status", "ABERTO"));
            assertThat(resumo.path("totais").path("emAndamento").asLong())
                    .isEqualTo(listTotal(login, "status", "EM_ANDAMENTO"));
            assertThat(resumo.path("totais").path("resolvido").asLong())
                    .isEqualTo(listTotal(login, "status", "RESOLVIDO"));
            assertThat(resumo.path("totais").path("criticaAberta").asLong())
                    .isEqualTo(listTotal(login, "status", "ABERTO", "prioridade", "CRITICA"));

            for (JsonNode item : resumo.path("porCategoria")) {
                assertThat(item.path("quantidade").asLong()).isEqualTo(
                        listTotal(login, "categoriaId", item.path("categoriaId").asText()));
            }
            for (JsonNode item : resumo.path("cargaPorAtendente")) {
                assertThat(item.path("emAndamento").asLong()).isEqualTo(listTotal(login,
                        "atendenteId", item.path("atendenteId").asText(),
                        "status", "EM_ANDAMENTO"));
            }
            for (Prioridade prioridade : Prioridade.values()) {
                assertThat(resumo.path("abertosPorPrioridade").path(prioridade.name()).asLong())
                        .isEqualTo(listTotal(login, "status", "ABERTO", "prioridade", prioridade.name()));
            }
        }
    }

    @Test
    void aggregatesAreComputedInTheDatabaseWithoutLoadingTickets() throws Exception {
        Statistics estatisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();

        resumo(admin);

        assertThat(estatisticas.getEntityStatistics(Chamado.class.getName()).getLoadCount()).isZero();
        assertThat(estatisticas.getPrepareStatementCount()).isLessThanOrEqualTo(6);
    }

    @Test
    void emptyScopeReturnsZerosAndAllPriorities() throws Exception {
        jdbcTemplate.update("DELETE FROM chamado");

        JsonNode resumo = resumo(atendenteX);

        assertThat(resumo.path("totais").path("aberto").asLong()).isZero();
        assertThat(resumo.path("porCategoria")).isEmpty();
        assertThat(resumo.path("cargaPorAtendente")).isEmpty();
        assertPrioridades(resumo, 0, 0, 0, 0);
    }

    @Test
    void requesterIsForbiddenAndAnonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + solicitante.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode resumo(Login login) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/dashboard/resumo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long listTotal(Login login, String... params) throws Exception {
        var request = get("/api/chamados")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken());
        for (int i = 0; i < params.length; i += 2) {
            request.queryParam(params[i], params[i + 1]);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("totalElements").asLong();
    }

    private static void assertItem(
            JsonNode item, String idField, Long id, String nome, String countField, long quantidade) {
        assertThat(item.path(idField).asLong()).isEqualTo(id);
        assertThat(item.path("nome").asText()).isEqualTo(nome);
        assertThat(item.path(countField).asLong()).isEqualTo(quantidade);
    }

    private static void assertPrioridades(JsonNode resumo, long baixa, long media, long alta, long critica) {
        JsonNode prioridades = resumo.path("abertosPorPrioridade");
        assertThat(prioridades.path("BAIXA").asLong()).isEqualTo(baixa);
        assertThat(prioridades.path("MEDIA").asLong()).isEqualTo(media);
        assertThat(prioridades.path("ALTA").asLong()).isEqualTo(alta);
        assertThat(prioridades.path("CRITICA").asLong()).isEqualTo(critica);
        assertThat(prioridades.size()).isEqualTo(4);
    }

    private void chamado(StatusChamado status, Prioridade prioridade, Categoria categoria, Login atendente) {
        chamadoRepository.saveAndFlush(new Chamado(
                "Chamado " + status + " " + prioridade,
                "Descricao",
                status,
                prioridade,
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
