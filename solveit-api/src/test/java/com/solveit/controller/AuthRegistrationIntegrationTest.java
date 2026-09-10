package com.solveit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
@ExtendWith(OutputCaptureExtension.class)
class AuthRegistrationIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_auth_test")
            .withUsername("solveit")
            .withPassword("solveit-test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private UsuarioRepository usuarioRepository;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @BeforeEach
    void cleanDatabase() {
        usuarioRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    void registersAnActiveApplicantWithNormalizedEmailAndOnlyPublicFields() throws Exception {
        String rawPassword = "  senha segura 123!  ";

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Felipe Iniciante",
                                  "email": "  Felipe@Example.COM  ",
                                  "senha": "  senha segura 123!  ",
                                  "perfil": "ADMIN"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Felipe Iniciante"))
                .andExpect(jsonPath("$.email").value("felipe@example.com"))
                .andExpect(jsonPath("$.perfil").value("SOLICITANTE"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.criadoEm").isNotEmpty())
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        List<String> responseFields = new ArrayList<>();
        response.fieldNames().forEachRemaining(responseFields::add);
        assertThat(responseFields).containsExactlyInAnyOrder(
                "id", "nome", "email", "perfil", "ativo", "criadoEm");

        Usuario persisted = usuarioRepository.findByEmail("felipe@example.com").orElseThrow();
        assertThat(persisted.getPerfil()).isEqualTo(PerfilUsuario.SOLICITANTE);
        assertThat(persisted.getAtivo()).isTrue();
        assertThat(persisted.getSenha()).isNotEqualTo(rawPassword).startsWith("$2");
        assertThat(new BCryptPasswordEncoder().matches(rawPassword, persisted.getSenha())).isTrue();
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(springSecurityFilterChain).isNotNull();
    }

    @Test
    void keepsOnlyPostRegistrationPublicAndDoesNotCreateAnHttpSession() throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Segunda pessoa",
                                  "email": "segunda@example.com",
                                  "senha": "outra senha segura 123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult protectedGet = mockMvc.perform(get("/api/auth/register"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult protectedPost = mockMvc.perform(post("/api/not-public"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(registration.getRequest().getSession(false)).isNull();
        assertThat(protectedGet.getRequest().getSession(false)).isNull();
        assertThat(protectedPost.getRequest().getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsPasswordWithFewerThanFifteenUnicodeCodePoints() throws Exception {
        String password = "🔐".repeat(14);

        assertThat(password.codePointCount(0, password.length())).isEqualTo(14);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                "Senha curta",
                                "curta@example.com",
                                password))))
                .andExpect(status().isBadRequest());

        assertThat(usuarioRepository.count()).isZero();
    }

    @Test
    void acceptsExactlyFifteenUnicodeCodePointsIncludingEmoji() throws Exception {
        String password = "🔐".repeat(15);

        assertThat(password.codePointCount(0, password.length())).isEqualTo(15);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                "Senha Unicode",
                                "unicode@example.com",
                                password))))
                .andExpect(status().isCreated());

        Usuario persisted = usuarioRepository.findByEmail("unicode@example.com").orElseThrow();
        assertThat(new BCryptPasswordEncoder().matches(password, persisted.getSenha())).isTrue();
    }

    @Test
    void acceptsPasswordWithExactlySeventyTwoUtf8Bytes() throws Exception {
        String password = "a".repeat(72);

        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(72);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                "Senha no limite",
                                "limite@example.com",
                                password))))
                .andExpect(status().isCreated());

        Usuario persisted = usuarioRepository.findByEmail("limite@example.com").orElseThrow();
        assertThat(new BCryptPasswordEncoder().matches(password, persisted.getSenha())).isTrue();
    }

    @Test
    void rejectsPasswordWithSeventyThreeUtf8Bytes() throws Exception {
        String password = "a".repeat(71) + "é";

        assertThat(password.codePointCount(0, password.length())).isEqualTo(72);
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(73);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                "Senha longa",
                                "longa@example.com",
                                password))))
                .andExpect(status().isBadRequest());

        assertThat(usuarioRepository.count()).isZero();
    }

    @Test
    void returnsProblemDetailsForInvalidRegistrationFields() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                "Senha inválida",
                                "invalida@example.com",
                                "curta"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:solveit:error:validation-error"))
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Um ou mais campos estão inválidos."))
                .andExpect(jsonPath("$.instance").value("/api/auth/register"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.senha").value(
                        "A senha deve ter no mínimo 15 caracteres e no máximo 72 bytes UTF-8"))
                .andReturn();

        assertSafeErrorBody(result, "curta");
    }

    @Test
    void rejectsMalformedEmailAfterRemovingExternalSpaces() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                "E-mail inválido",
                                "  not-an-email  ",
                                "senha segura para validar"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.email").value(
                        "O e-mail deve ter um formato válido"));

        assertThat(usuarioRepository.count()).isZero();
    }

    @Test
    void rejectsNameAndEmailBeyondDatabaseLimits() throws Exception {
        String oversizedName = "n".repeat(121);
        String oversizedEmail = "user@"
                + "a".repeat(63) + "."
                + "b".repeat(63) + "."
                + "c".repeat(63) + "."
                + "d".repeat(59);
        assertThat(oversizedEmail).hasSize(256);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                oversizedName,
                                oversizedEmail,
                                "senha segura para limites"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.nome").value(
                        "O nome deve ter no máximo 120 caracteres"))
                .andExpect(jsonPath("$.errors.email").value(
                        "O e-mail deve ter no máximo 255 caracteres"));

        assertThat(usuarioRepository.count()).isZero();
    }

    @Test
    void returnsConflictProblemDetailsForSequentialDuplicateEmail() throws Exception {
        String password = "senha segura duplicada 123!";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                "Primeira pessoa",
                                "duplicada@example.com",
                                password))))
                .andExpect(status().isCreated());

        MvcResult duplicate = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                "Segunda pessoa",
                                "  DUPLICADA@EXAMPLE.COM  ",
                                password))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:solveit:error:email-already-registered"))
                .andExpect(jsonPath("$.title").value("Conflito de cadastro"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("E-mail já cadastrado"))
                .andExpect(jsonPath("$.instance").value("/api/auth/register"))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andReturn();

        assertThat(usuarioRepository.count()).isEqualTo(1);
        assertSafeErrorBody(duplicate, password);
    }

    @Test
    void resolvesConcurrentDuplicateEmailWithOneCreatedAndOneConflict(
            CapturedOutput output) throws Exception {
        String email = "concorrente@example.com";
        String password = "senha concorrente segura 123!";
        CyclicBarrier bothPreChecksReached = new CyclicBarrier(2);

        doAnswer(invocation -> {
            bothPreChecksReached.await(10, TimeUnit.SECONDS);
            return false;
        }).when(usuarioRepository).existsByEmail(eq(email));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> first = executor.submit(() -> performRegistration(
                    "Primeira concorrente", email, password));
            Future<MvcResult> second = executor.submit(() -> performRegistration(
                    "Segunda concorrente", email, password));

            List<MvcResult> results = List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS));

            assertThat(results)
                    .extracting(result -> result.getResponse().getStatus())
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(usuarioRepository.count()).isEqualTo(1);

            MvcResult conflict = results.stream()
                    .filter(result -> result.getResponse().getStatus() == 409)
                    .findFirst()
                    .orElseThrow();
            JsonNode problem = objectMapper.readTree(conflict.getResponse().getContentAsString());
            assertThat(problem.path("code").asText()).isEqualTo("EMAIL_ALREADY_REGISTERED");
            assertSafeErrorBody(conflict, password);
            assertSafeLog(output, password);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void keepsUnrelatedIntegrityViolationAsSafeInternalError() throws Exception {
        String password = "senha interna segura 123!";
        DataIntegrityViolationException unrelatedViolation =
                new DataIntegrityViolationException("ck_usuario_ativo");
        doThrow(unrelatedViolation).when(usuarioRepository).save(any(Usuario.class));
        doThrow(unrelatedViolation).when(usuarioRepository).saveAndFlush(any(Usuario.class));

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                "Falha interna",
                                "interna@example.com",
                                password))))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Não foi possível concluir a operação."))
                .andReturn();

        assertSafeErrorBody(result, password, "ck_usuario_ativo");
    }

    private MvcResult performRegistration(String nome, String email, String password)
            throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(
                                nome, email, password))))
                .andReturn();
    }

    private static void assertSafeErrorBody(MvcResult result, String... sensitiveValues)
            throws Exception {
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(sensitiveValues)
                .doesNotContain(
                        "$2",
                        "Exception",
                        "java.",
                        "org.",
                        "SELECT ",
                        "INSERT ",
                        "uk_usuario_email",
                        "stackTrace");
    }

    private static void assertSafeLog(CapturedOutput output, String... sensitiveValues) {
        List<String> forbiddenValues = new ArrayList<>(Arrays.asList(sensitiveValues));
        forbiddenValues.addAll(List.of(
                "$2",
                "uk_usuario_email",
                "insert into usuario",
                "DataIntegrityViolationException",
                "ConstraintViolationException"));

        assertThat(output.getAll()).doesNotContain(forbiddenValues.toArray(String[]::new));
    }

    private record RegisterPayload(String nome, String email, String senha) {
    }
}
