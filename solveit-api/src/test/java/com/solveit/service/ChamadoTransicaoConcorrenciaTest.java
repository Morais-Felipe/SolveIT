package com.solveit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.solveit.dto.chamado.ChamadoDtos;
import com.solveit.entity.Categoria;
import com.solveit.entity.Chamado;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.enums.Prioridade;
import com.solveit.enums.StatusChamado;
import com.solveit.exception.ApiErrorCode;
import com.solveit.exception.DomainException;
import com.solveit.repository.CategoriaRepository;
import com.solveit.repository.ChamadoRepository;
import com.solveit.repository.UsuarioRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Resolver (atendente responsável) e cancelar (ADMIN) disputam o mesmo chamado
 * EM_ANDAMENTO com a mesma versão. A primeira transação só confirma depois de o InnoDB
 * mostrar a segunda na fila do guard; a segunda lê o estado confirmado e é recusada.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class ChamadoTransicaoConcorrenciaTest {

    private static final long TIMEOUT_SECONDS = 10;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_transicao_concorrencia_test")
            .withUsername("solveit")
            .withPassword("solveit-test");

    @Autowired
    ChamadoService chamadoService;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    CategoriaRepository categoriaRepository;

    @Autowired
    ChamadoRepository chamadoRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    private ExecutorService executor;
    private Usuario responsavel;
    private Usuario admin;
    private Chamado chamado;

    @BeforeEach
    void prepare() {
        executor = Executors.newFixedThreadPool(2);
        jdbcTemplate.update("DELETE FROM historico_status");
        jdbcTemplate.update("DELETE FROM comentario");
        jdbcTemplate.update("DELETE FROM chamado");
        jdbcTemplate.update("DELETE FROM categoria");
        jdbcTemplate.update("DELETE FROM usuario");
        Usuario solicitante = usuarioRepository.saveAndFlush(new Usuario(
                "Solicitante", "solicitante@example.com", "hash", PerfilUsuario.SOLICITANTE));
        responsavel = usuarioRepository.saveAndFlush(new Usuario(
                "Responsavel", "resp@example.com", "hash", PerfilUsuario.ATENDENTE));
        admin = usuarioRepository.saveAndFlush(new Usuario(
                "Admin", "admin@example.com", "hash", PerfilUsuario.ADMIN));
        Categoria categoria = categoriaRepository.saveAndFlush(new Categoria("Rede", null));
        chamado = chamadoRepository.saveAndFlush(new Chamado(
                "Sem rede", "Cabo solto", StatusChamado.EM_ANDAMENTO, Prioridade.ALTA,
                solicitante, responsavel, categoria));
    }

    @AfterEach
    void stopWorkers() throws Exception {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        executeAsRoot("DROP TRIGGER IF EXISTS trg_teste_falha_historico_transicao");
    }

    @Test
    void resolutionConfirmedFirstMakesCancellationStale() throws Exception {
        Future<ChamadoDtos.Detalhe> perdedora = holdFirstThenRun(this::resolver, this::cancelar);

        assertStale(perdedora);
        Map<String, Object> estado = row();
        assertThat(estado.get("status")).isEqualTo("RESOLVIDO");
        assertThat(estado.get("resolvido_em")).isNotNull().isEqualTo(estado.get("atualizado_em"));
        assertThat(((Number) estado.get("version")).longValue()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList(
                "SELECT status_novo FROM historico_status", String.class))
                .containsExactly("RESOLVIDO");
    }

    @Test
    void cancellationConfirmedFirstMakesResolutionStale() throws Exception {
        Future<ChamadoDtos.Detalhe> perdedora = holdFirstThenRun(this::cancelar, this::resolver);

        assertStale(perdedora);
        Map<String, Object> estado = row();
        assertThat(estado.get("status")).isEqualTo("CANCELADO");
        assertThat(estado.get("resolvido_em")).isNull();
        assertThat(((Number) estado.get("version")).longValue()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList(
                "SELECT status_novo FROM historico_status", String.class))
                .containsExactly("CANCELADO");
    }

    @Test
    void historyFailureRollsBackStatusDatesAndVersion() throws Exception {
        Map<String, Object> antes = row();
        executeAsRoot("""
                CREATE TRIGGER trg_teste_falha_historico_transicao
                BEFORE INSERT ON historico_status
                FOR EACH ROW
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'falha simulada de historico'
                """);

        assertThatThrownBy(this::resolver).isInstanceOf(DataAccessException.class);

        Map<String, Object> depois = row();
        assertThat(depois.get("status")).isEqualTo("EM_ANDAMENTO");
        assertThat(depois.get("resolvido_em")).isNull();
        assertThat(depois.get("atualizado_em")).isEqualTo(antes.get("atualizado_em"));
        assertThat(((Number) depois.get("version")).longValue()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM historico_status", Long.class)).isZero();
    }

    private ChamadoDtos.Detalhe resolver() {
        return chamadoService.alterarStatus(
                responsavel.getId(),
                chamado.getId(),
                new ChamadoDtos.AlterarStatus(StatusChamado.RESOLVIDO, 0L, null, Map.of()));
    }

    private ChamadoDtos.Detalhe cancelar() {
        return chamadoService.alterarStatus(
                admin.getId(),
                chamado.getId(),
                new ChamadoDtos.AlterarStatus(StatusChamado.CANCELADO, 0L, null, Map.of()));
    }

    private Future<ChamadoDtos.Detalhe> holdFirstThenRun(
            Callable<ChamadoDtos.Detalhe> primeira,
            Callable<ChamadoDtos.Detalhe> segunda) throws Exception {
        CountDownLatch primeiraExecutada = new CountDownLatch(1);
        CountDownLatch liberarPrimeira = new CountDownLatch(1);

        Future<?> vencedora = executor.submit(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    try {
                        primeira.call();
                        primeiraExecutada.countDown();
                        await(liberarPrimeira);
                    } catch (RuntimeException exception) {
                        throw exception;
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                }));
        await(primeiraExecutada);

        Future<ChamadoDtos.Detalhe> perdedora = executor.submit(segunda);
        waitUntilSomeoneWaitsForALock();

        liberarPrimeira.countDown();
        vencedora.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return perdedora;
    }

    private static void assertStale(Future<ChamadoDtos.Detalhe> perdedora) {
        assertThatThrownBy(() -> perdedora.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ApiErrorCode.STALE_RESOURCE));
    }

    private Map<String, Object> row() {
        return jdbcTemplate.queryForMap("SELECT * FROM chamado WHERE id = ?", chamado.getId());
    }

    private static void waitUntilSomeoneWaitsForALock() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        try (Connection connection = DriverManager.getConnection(
                        MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
                Statement statement = connection.createStatement()) {
            while (System.nanoTime() < deadline) {
                try (ResultSet waits = statement.executeQuery(
                        "SELECT COUNT(*) FROM performance_schema.data_lock_waits")) {
                    waits.next();
                    if (waits.getInt(1) > 0) {
                        return;
                    }
                }
                Thread.sleep(25);
            }
        }
        throw new AssertionError("The second transaction never waited for the guard");
    }

    private static void executeAsRoot(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                        MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("Worker did not reach the expected point");
        }
    }
}
