package com.solveit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solveit.entity.Categoria;
import com.solveit.entity.Chamado;
import com.solveit.entity.Comentario;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.enums.Prioridade;
import com.solveit.enums.StatusChamado;
import com.solveit.repository.CategoriaRepository;
import com.solveit.repository.ChamadoRepository;
import com.solveit.repository.UsuarioRepository;
import com.solveit.service.ComentarioService;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * COLL-03: nenhuma rota da aplicação edita ou exclui comentários. As tentativas usam um
 * usuário que TEM acesso ao chamado, para que um 403 de segurança não seja confundido
 * com a ausência da operação; depois o comentário é relido no MySQL e pela API.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
class ComentarioImutabilidadeIntegrationTest {

    private static final String RAW_PASSWORD = "senha segura para imutabilidade 123";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_imutabilidade_test")
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
    private Login admin;

    @BeforeEach
    void prepare() throws Exception {
        jdbcTemplate.update("DELETE FROM historico_status");
        jdbcTemplate.update("DELETE FROM comentario");
        jdbcTemplate.update("DELETE FROM chamado");
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();
        dono = registerAndLogin("dono@example.com", "Dono", PerfilUsuario.SOLICITANTE);
        admin = registerAndLogin("admin@example.com", "Admin", PerfilUsuario.ADMIN);
    }

    @Test
    void noAuthenticatedHttpAttemptChangesOrRemovesAComment() throws Exception {
        Categoria categoria = categoriaRepository.saveAndFlush(new Categoria("Rede", null));
        Chamado chamado = chamadoRepository.saveAndFlush(new Chamado(
                "Sem rede", "Cabo solto", StatusChamado.ABERTO, Prioridade.MEDIA,
                usuarioRepository.findById(dono.usuarioId()).orElseThrow(), null, categoria));
        MvcResult criado = mockMvc.perform(post("/api/chamados/{id}/comentarios", chamado.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(dono))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("texto", "Texto original"))))
                .andExpect(status().isCreated())
                .andReturn();
        long comentarioId = objectMapper.readTree(criado.getResponse().getContentAsString())
                .path("id").asLong();
        Map<String, Object> antes = commentRow(comentarioId);

        String colecao = "/api/chamados/" + chamado.getId() + "/comentarios";
        String item = colecao + "/" + comentarioId;
        List<MockHttpServletRequestBuilder> tentativas = List.of(
                put(colecao), patch(colecao), delete(colecao),
                put(item), patch(item), delete(item));

        for (Login login : List.of(dono, admin)) {
            for (MockHttpServletRequestBuilder tentativa : tentativas) {
                int status = mockMvc.perform(tentativa
                                .header(HttpHeaders.AUTHORIZATION, bearer(login))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("texto", "Texto adulterado"))))
                        .andReturn().getResponse().getStatus();
                assertThat(status)
                        .as("tentativa de mutação por %s", login.usuarioId())
                        .isIn(404, 405);
            }
        }

        assertThat(commentRow(comentarioId)).isEqualTo(antes);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM comentario", Long.class))
                .isOne();
        String leitura = mockMvc.perform(get(colecao).header(HttpHeaders.AUTHORIZATION, bearer(dono)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(leitura).contains("Texto original").doesNotContain("Texto adulterado");
    }

    @Test
    void comentarioEntityHasNoPublicSetters() {
        List<String> setters = Arrays.stream(Comentario.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .filter(name -> name.startsWith("set"))
                .toList();

        assertThat(setters).isEmpty();
    }

    @Test
    void comentarioServiceExposesOnlyCreateAndList() {
        List<String> publicos = Arrays.stream(ComentarioService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .toList();

        assertThat(publicos).containsExactlyInAnyOrder("adicionar", "listar");
        assertThat(publicos).noneMatch(nome -> {
            String n = nome.toLowerCase(Locale.ROOT);
            return n.contains("editar") || n.contains("alterar") || n.contains("atualizar")
                    || n.contains("excluir") || n.contains("remover") || n.contains("apagar")
                    || n.contains("update") || n.contains("delete");
        });
    }

    private Map<String, Object> commentRow(long comentarioId) {
        return jdbcTemplate.queryForMap("SELECT * FROM comentario WHERE id = ?", comentarioId);
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private static String bearer(Login login) {
        return "Bearer " + login.accessToken();
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
