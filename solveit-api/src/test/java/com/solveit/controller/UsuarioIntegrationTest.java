package com.solveit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.repository.UsuarioRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
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
class UsuarioIntegrationTest {

    private static final String RAW_PASSWORD = "senha segura para usuarios 123";
    private static final String NEW_PASSWORD = "outra senha forte para usuarios 456";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_usuario_test")
            .withUsername("solveit")
            .withPassword("solveit-test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        usuarioRepository.deleteAll();
    }

    @Test
    void requiresBearerToListUsers() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
    }

    @Test
    void forbidsNonAdminProfilesFromListingAndReadingUsers() throws Exception {
        LoginResult solicitante = registerAndLogin("solicitante@example.com", "Solicitante");
        LoginResult atendente = registerAndLogin("atendente@example.com", "Atendente");
        changeProfile(atendente.usuarioId(), PerfilUsuario.ATENDENTE);

        for (LoginResult login : List.of(solicitante, atendente)) {
            mockMvc.perform(get("/api/usuarios")
                            .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

            mockMvc.perform(get("/api/usuarios/{id}", solicitante.usuarioId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(login)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Test
    void listsAllUsersOrderedByIdWithStablePaginationAndNoCredentials() throws Exception {
        LoginResult admin = registerAndLogin("admin@example.com", "Admin");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);
        LoginResult segundo = registerAndLogin("segundo@example.com", "Segundo");
        LoginResult inativo = registerAndLogin("inativo@example.com", "Inativo");
        deactivate(inativo.usuarioId());

        MvcResult firstPage = mockMvc.perform(get("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .queryParam("page", "0")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(admin.usuarioId()))
                .andExpect(jsonPath("$.items[0].nome").value("Admin"))
                .andExpect(jsonPath("$.items[0].email").value("admin@example.com"))
                .andExpect(jsonPath("$.items[0].perfil").value("ADMIN"))
                .andExpect(jsonPath("$.items[0].ativo").value(true))
                .andExpect(jsonPath("$.items[0].criadoEm").isNotEmpty())
                .andExpect(jsonPath("$.items[1].id").value(segundo.usuarioId()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andReturn();

        assertResponseFields(firstPage, "items", "page", "size", "totalElements", "totalPages");
        JsonNode firstItem = objectMapper.readTree(firstPage.getResponse().getContentAsString())
                .path("items")
                .get(0);
        assertItemFields(firstItem, "id", "nome", "email", "perfil", "ativo", "criadoEm", "versao");

        mockMvc.perform(get("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .queryParam("page", "1")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(inativo.usuarioId()))
                .andExpect(jsonPath("$.items[0].ativo").value(false))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void returnsAnEmptyPageBeyondTheLastPage() throws Exception {
        LoginResult admin = registerAndLogin("admin-vazio@example.com", "Admin Vazio");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);

        mockMvc.perform(get("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .queryParam("page", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(5))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void rejectsInvalidPaginationParameters() throws Exception {
        LoginResult admin = registerAndLogin("admin-pagina@example.com", "Admin Pagina");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);

        for (String[] params : List.of(
                new String[] {"page", "-1"},
                new String[] {"size", "0"},
                new String[] {"size", "101"})) {
            mockMvc.perform(get("/api/usuarios")
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                            .queryParam(params[0], params[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors." + params[0]).exists());
        }
    }

    @Test
    void returnsUserDetailsForAdminWithoutCredentials() throws Exception {
        LoginResult admin = registerAndLogin("admin-detalhe@example.com", "Admin Detalhe");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);
        LoginResult alvo = registerAndLogin("alvo@example.com", "Alvo");

        MvcResult result = mockMvc.perform(get("/api/usuarios/{id}", alvo.usuarioId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alvo.usuarioId()))
                .andExpect(jsonPath("$.nome").value("Alvo"))
                .andExpect(jsonPath("$.email").value("alvo@example.com"))
                .andExpect(jsonPath("$.perfil").value("SOLICITANTE"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn();

        assertResponseFields(result, "id", "nome", "email", "perfil", "ativo", "criadoEm", "versao");
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(RAW_PASSWORD)
                .doesNotContain("$2");
    }

    @Test
    void returnsNotFoundForMissingUser() throws Exception {
        LoginResult admin = registerAndLogin("admin-ausente@example.com", "Admin Ausente");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);

        mockMvc.perform(get("/api/usuarios/{id}", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void appliesProfileChangesBetweenRequestsWithoutNewToken() throws Exception {
        LoginResult admin = registerAndLogin("admin-rebaixado@example.com", "Admin Rebaixado");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);

        mockMvc.perform(get("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());

        changeProfile(admin.usuarioId(), PerfilUsuario.ATENDENTE);

        mockMvc.perform(get("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminCreatesActiveUsersForEveryProfileWithBcryptHash() throws Exception {
        LoginResult admin = registerAndLogin("admin-cria@example.com", "Admin Cria");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);

        for (PerfilUsuario perfil : PerfilUsuario.values()) {
            String email = "novo-" + perfil.name().toLowerCase() + "@example.com";
            MvcResult result = mockMvc.perform(post("/api/usuarios")
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "nome", "  Novo " + perfil.name() + "  ",
                                    "email", email,
                                    "senha", NEW_PASSWORD,
                                    "perfil", perfil.name()))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.nome").value("Novo " + perfil.name()))
                    .andExpect(jsonPath("$.email").value(email))
                    .andExpect(jsonPath("$.perfil").value(perfil.name()))
                    .andExpect(jsonPath("$.ativo").value(true))
                    .andExpect(jsonPath("$.versao").value(0))
                    .andReturn();

            assertResponseFields(
                    result, "id", "nome", "email", "perfil", "ativo", "criadoEm", "versao");
            Long id = objectMapper.readTree(result.getResponse().getContentAsString())
                    .path("id")
                    .asLong();
            assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                    .isEqualTo("/api/usuarios/" + id);
            Usuario stored = usuarioRepository.findById(id).orElseThrow();
            assertThat(stored.getSenha()).startsWith("$2").isNotEqualTo(NEW_PASSWORD);
            assertThat(passwordEncoder.matches(NEW_PASSWORD, stored.getSenha())).isTrue();
            assertThat(login(email, NEW_PASSWORD)).isEqualTo(200);
        }
    }

    @Test
    void createdUserIsAlwaysActiveEvenWhenClientSendsInactive() throws Exception {
        LoginResult admin = registerAndLogin("admin-ativo@example.com", "Admin Ativo");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);

        mockMvc.perform(post("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Forcado Ativo",
                                "email", "forcado@example.com",
                                "senha", NEW_PASSWORD,
                                "perfil", "ATENDENTE",
                                "ativo", false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void normalizesEmailAndRejectsDuplicatesWithoutCreatingAnotherUser() throws Exception {
        LoginResult admin = registerAndLogin("admin-dup@example.com", "Admin Dup");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);

        mockMvc.perform(post("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Normalizado",
                                "email", "  Normalizado@Example.COM ",
                                "senha", NEW_PASSWORD,
                                "perfil", "SOLICITANTE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("normalizado@example.com"));
        long total = usuarioRepository.count();

        MvcResult duplicate = mockMvc.perform(post("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Duplicado",
                                "email", "NORMALIZADO@example.com",
                                "senha", NEW_PASSWORD,
                                "perfil", "ADMIN"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andReturn();

        assertThat(usuarioRepository.count()).isEqualTo(total);
        assertThat(duplicate.getResponse().getContentAsString())
                .doesNotContain("uk_usuario_email")
                .doesNotContain(NEW_PASSWORD);
    }

    @Test
    void rejectsInvalidCreationPayloads() throws Exception {
        LoginResult admin = registerAndLogin("admin-invalido@example.com", "Admin Invalido");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);
        long total = usuarioRepository.count();

        List<Map<String, Object>> payloads = List.of(
                Map.of("nome", "Sem Perfil", "email", "sem-perfil@example.com",
                        "senha", NEW_PASSWORD),
                Map.of("nome", "Perfil Falso", "email", "perfil-falso@example.com",
                        "senha", NEW_PASSWORD, "perfil", "ROOT"),
                Map.of("nome", "Senha Curta", "email", "senha-curta@example.com",
                        "senha", "curta", "perfil", "SOLICITANTE"),
                Map.of("nome", " ", "email", "nome-vazio@example.com",
                        "senha", NEW_PASSWORD, "perfil", "SOLICITANTE"),
                Map.of("nome", "Email Ruim", "email", "nao-e-email",
                        "senha", NEW_PASSWORD, "perfil", "SOLICITANTE"));

        for (Map<String, Object> payload : payloads) {
            mockMvc.perform(post("/api/usuarios")
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(payload)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(usuarioRepository.count()).isEqualTo(total);
    }

    @Test
    void forbidsNonAdminFromCreatingAndEditingUsers() throws Exception {
        LoginResult atendente = registerAndLogin("atendente-cria@example.com", "Atendente");
        changeProfile(atendente.usuarioId(), PerfilUsuario.ATENDENTE);
        Usuario alvo = usuarioRepository.findById(atendente.usuarioId()).orElseThrow();

        mockMvc.perform(post("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(atendente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Escalada",
                                "email", "escalada@example.com",
                                "senha", NEW_PASSWORD,
                                "perfil", "ADMIN"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(put("/api/usuarios/{id}", alvo.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(atendente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Outro Nome",
                                "email", "atendente-cria@example.com",
                                "versaoEsperada", alvo.getVersion()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(usuarioRepository.existsByEmail("escalada@example.com")).isFalse();
        assertThat(usuarioRepository.findById(alvo.getId()).orElseThrow().getNome())
                .isEqualTo("Atendente");
    }

    @Test
    void publicRegistrationStillCreatesOnlySolicitante() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Tentou Admin",
                                "email", "tentou-admin@example.com",
                                "senha", RAW_PASSWORD,
                                "perfil", "ADMIN"))))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("id")
                .asLong();
        assertThat(usuarioRepository.findById(id).orElseThrow().getPerfil())
                .isEqualTo(PerfilUsuario.SOLICITANTE);
    }

    @Test
    void editsOnlyPersonalDataWithCurrentVersionAndPreservesPasswordWhenAbsent()
            throws Exception {
        LoginResult admin = registerAndLogin("admin-edita@example.com", "Admin Edita");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);
        LoginResult alvoLogin = registerAndLogin("alvo-edita@example.com", "Alvo Edita");
        changeProfile(alvoLogin.usuarioId(), PerfilUsuario.ATENDENTE);
        Usuario antes = usuarioRepository.findById(alvoLogin.usuarioId()).orElseThrow();

        mockMvc.perform(put("/api/usuarios/{id}", antes.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "  Nome Corrigido ",
                                "email", " Corrigido@Example.com",
                                "versaoEsperada", antes.getVersion()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Nome Corrigido"))
                .andExpect(jsonPath("$.email").value("corrigido@example.com"))
                .andExpect(jsonPath("$.perfil").value("ATENDENTE"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.versao").value(antes.getVersion() + 1));

        Usuario depois = usuarioRepository.findById(antes.getId()).orElseThrow();
        assertThat(depois.getSenha()).isEqualTo(antes.getSenha());
        assertThat(depois.getPerfil()).isEqualTo(PerfilUsuario.ATENDENTE);
        assertThat(login("corrigido@example.com", RAW_PASSWORD)).isEqualTo(200);
    }

    @Test
    void replacesPasswordWhenProvided() throws Exception {
        LoginResult admin = registerAndLogin("admin-senha@example.com", "Admin Senha");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);
        LoginResult alvoLogin = registerAndLogin("alvo-senha@example.com", "Alvo Senha");
        Usuario alvo = usuarioRepository.findById(alvoLogin.usuarioId()).orElseThrow();

        mockMvc.perform(put("/api/usuarios/{id}", alvo.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Alvo Senha",
                                "email", "alvo-senha@example.com",
                                "senha", NEW_PASSWORD,
                                "versaoEsperada", alvo.getVersion()))))
                .andExpect(status().isOk());

        assertThat(login("alvo-senha@example.com", NEW_PASSWORD)).isEqualTo(200);
        assertThat(login("alvo-senha@example.com", RAW_PASSWORD)).isEqualTo(401);
    }

    @Test
    void rejectsStaleVersionWithoutChangingTheUser() throws Exception {
        LoginResult admin = registerAndLogin("admin-stale@example.com", "Admin Stale");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);
        LoginResult alvoLogin = registerAndLogin("alvo-stale@example.com", "Alvo Stale");
        Usuario alvo = usuarioRepository.findById(alvoLogin.usuarioId()).orElseThrow();
        long versaoVista = alvo.getVersion();

        mockMvc.perform(put("/api/usuarios/{id}", alvo.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Primeira Edicao",
                                "email", "alvo-stale@example.com",
                                "versaoEsperada", versaoVista))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/usuarios/{id}", alvo.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Edicao Atrasada",
                                "email", "alvo-stale@example.com",
                                "versaoEsperada", versaoVista))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE"));

        assertThat(usuarioRepository.findById(alvo.getId()).orElseThrow().getNome())
                .isEqualTo("Primeira Edicao");
    }

    @Test
    void rejectsProfileOrActivityChangesThroughDataEdition() throws Exception {
        LoginResult admin = registerAndLogin("admin-atalho@example.com", "Admin Atalho");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);
        LoginResult alvoLogin = registerAndLogin("alvo-atalho@example.com", "Alvo Atalho");
        Usuario alvo = usuarioRepository.findById(alvoLogin.usuarioId()).orElseThrow();

        for (Map.Entry<String, Object> proibido : List.<Map.Entry<String, Object>>of(
                Map.entry("perfil", "ADMIN"),
                Map.entry("ativo", false))) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("nome", "Alvo Atalho");
            payload.put("email", "alvo-atalho@example.com");
            payload.put("versaoEsperada", alvo.getVersion());
            payload.put(proibido.getKey(), proibido.getValue());

            mockMvc.perform(put("/api/usuarios/{id}", alvo.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(payload)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors." + proibido.getKey()).exists());
        }

        Usuario depois = usuarioRepository.findById(alvo.getId()).orElseThrow();
        assertThat(depois.getPerfil()).isEqualTo(PerfilUsuario.SOLICITANTE);
        assertThat(depois.getAtivo()).isTrue();
        assertThat(depois.getVersion()).isEqualTo(alvo.getVersion());
    }

    @Test
    void rejectsInvalidEditionPayloads() throws Exception {
        LoginResult admin = registerAndLogin("admin-put-invalido@example.com", "Admin Put");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);
        Usuario alvo = usuarioRepository.findById(admin.usuarioId()).orElseThrow();

        List<Map<String, Object>> payloads = List.of(
                Map.of("nome", "Sem Versao", "email", "admin-put-invalido@example.com"),
                Map.of("nome", "Senha Curta", "email", "admin-put-invalido@example.com",
                        "senha", "curta", "versaoEsperada", alvo.getVersion()),
                Map.of("nome", " ", "email", "admin-put-invalido@example.com",
                        "versaoEsperada", alvo.getVersion()));

        for (Map<String, Object> payload : payloads) {
            mockMvc.perform(put("/api/usuarios/{id}", alvo.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(payload)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void rejectsEditionToAnEmailAlreadyInUse() throws Exception {
        LoginResult admin = registerAndLogin("admin-email@example.com", "Admin Email");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);
        registerAndLogin("ocupado@example.com", "Ocupado");
        LoginResult alvoLogin = registerAndLogin("livre@example.com", "Livre");
        Usuario alvo = usuarioRepository.findById(alvoLogin.usuarioId()).orElseThrow();

        mockMvc.perform(put("/api/usuarios/{id}", alvo.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Livre",
                                "email", "OCUPADO@example.com",
                                "versaoEsperada", alvo.getVersion()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

        assertThat(usuarioRepository.findById(alvo.getId()).orElseThrow().getEmail())
                .isEqualTo("livre@example.com");
    }

    @Test
    void returnsNotFoundWhenEditingMissingUser() throws Exception {
        LoginResult admin = registerAndLogin("admin-put-404@example.com", "Admin Put 404");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);

        mockMvc.perform(put("/api/usuarios/{id}", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Fantasma",
                                "email", "fantasma@example.com",
                                "versaoEsperada", 0))))
                .andExpect(status().isNotFound());
    }

    @Test
    void concurrentPublicAndAdminRegistrationOfSameEmailCreatesExactlyOneUser()
            throws Exception {
        LoginResult admin = registerAndLogin("admin-corrida@example.com", "Admin Corrida");
        changeProfile(admin.usuarioId(), PerfilUsuario.ADMIN);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int rodada = 0; rodada < 5; rodada++) {
                String email = "corrida-" + rodada + "@example.com";
                CountDownLatch largada = new CountDownLatch(1);

                Callable<Integer> publico = () -> {
                    largada.await(10, TimeUnit.SECONDS);
                    return mockMvc.perform(post("/api/auth/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(new RegisterPayload(
                                            "Publico", email, RAW_PASSWORD))))
                            .andReturn().getResponse().getStatus();
                };
                Callable<Integer> administrativo = () -> {
                    largada.await(10, TimeUnit.SECONDS);
                    return mockMvc.perform(post("/api/usuarios")
                                    .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(Map.of(
                                            "nome", "Administrativo",
                                            "email", email,
                                            "senha", NEW_PASSWORD,
                                            "perfil", "ATENDENTE"))))
                            .andReturn().getResponse().getStatus();
                };

                Future<Integer> primeiro = executor.submit(publico);
                Future<Integer> segundo = executor.submit(administrativo);
                largada.countDown();

                assertThat(List.of(
                                primeiro.get(60, TimeUnit.SECONDS),
                                segundo.get(60, TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder(201, 409);
                assertThat(usuarioRepository.findByEmail(email)).isPresent();
                assertThat(usuarioRepository.findAll().stream()
                                .filter(usuario -> usuario.getEmail().equals(email))
                                .count())
                        .isOne();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private String json(Object payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private int login(String email, String senha) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginPayload(email, senha))))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private LoginResult registerAndLogin(String email, String nome) throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                nome,
                                email,
                                RAW_PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn();
        Long usuarioId = objectMapper.readTree(registration.getResponse().getContentAsString())
                .path("id")
                .asLong();

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(
                                email,
                                RAW_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("accessToken")
                .asText();
        return new LoginResult(usuarioId, accessToken);
    }

    private void changeProfile(Long usuarioId, PerfilUsuario perfil) {
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        usuario.setPerfil(perfil);
        usuarioRepository.saveAndFlush(usuario);
    }

    private void deactivate(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        usuario.setAtivo(false);
        usuarioRepository.saveAndFlush(usuario);
    }

    private void assertResponseFields(MvcResult result, String... expectedFields) throws Exception {
        assertItemFields(
                objectMapper.readTree(result.getResponse().getContentAsString()),
                expectedFields);
    }

    private static void assertItemFields(JsonNode node, String... expectedFields) {
        List<String> actualFields = new ArrayList<>();
        node.fieldNames().forEachRemaining(actualFields::add);
        assertThat(actualFields).containsExactlyInAnyOrder(expectedFields);
    }

    private static String bearer(LoginResult login) {
        return "Bearer " + login.accessToken();
    }

    private record RegisterPayload(String nome, String email, String senha) {
    }

    private record LoginPayload(String email, String senha) {
    }

    private record LoginResult(Long usuarioId, String accessToken) {
    }
}
