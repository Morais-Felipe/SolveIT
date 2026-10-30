package com.solveit.service.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solveit.entity.Categoria;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.repository.CategoriaRepository;
import com.solveit.repository.UsuarioRepository;
import java.util.Map;
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

/**
 * MAIL-06: com o envio habilitado e nenhum servidor SMTP na porta configurada, a
 * operação continua respondendo e gravando normalmente; a falha fica só no log.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "solveit.mail.enabled=true",
        "solveit.mail.from=solveit@teste.local",
        "spring.mail.host=localhost",
        "spring.mail.port=3999"
})
@AutoConfigureMockMvc
class NotificacaoSmtpIndisponivelIntegrationTest {

    private static final String RAW_PASSWORD = "senha segura para smtp fora 123";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_smtp_indisponivel_test")
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

    @Test
    void smtpFailureDoesNotUndoNorFailTheOperation() throws Exception {
        String adminToken = registerAndLogin("admin@teste.local", "Admin", PerfilUsuario.ADMIN);
        String solicitanteToken =
                registerAndLogin("sol@teste.local", "Solicitante", PerfilUsuario.SOLICITANTE);
        Categoria categoria = categoriaRepository.saveAndFlush(new Categoria("Rede", null));
        assertThat(adminToken).isNotBlank();

        mockMvc.perform(post("/api/chamados")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + solicitanteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "titulo", "Sem rede",
                                "descricao", "Cabo solto",
                                "prioridade", "CRITICA",
                                "categoriaId", categoria.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ABERTO"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chamado", Long.class))
                .isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM historico_status", Long.class))
                .isOne();
    }

    private String registerAndLogin(String email, String nome, PerfilUsuario perfil)
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
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .path("accessToken")
                .asText();
    }
}
