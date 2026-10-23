package com.solveit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.solveit.dto.chamado.ChamadoDtos;
import com.solveit.dto.comentario.ComentarioDtos;
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
 * Comentário e encerramento passam pelo mesmo guard. Quem confirma primeiro decide:
 * comentário antes → ele fica e o encerramento segue; encerramento antes → o comentário
 * vê o status confirmado e recebe COMMENTS_CLOSED.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class ComentarioConcorrenciaTest {

    private static final long TIMEOUT_SECONDS = 10;
    private static final String MENSAGEM_ENCERRADO =
            "Este chamado foi encerrado e não aceita novos comentários.";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_comentario_concorrencia_test")
            .withUsername("solveit")
            .withPassword("solveit-test");

    @Autowired
    ComentarioService comentarioService;

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
    private Usuario dono;
    private Usuario responsavel;
    private Categoria categoria;

    @BeforeEach
    void prepare() {
        executor = Executors.newFixedThreadPool(2);
        jdbcTemplate.update("DELETE FROM historico_status");
        jdbcTemplate.update("DELETE FROM comentario");
        jdbcTemplate.update("DELETE FROM chamado");
        jdbcTemplate.update("DELETE FROM categoria");
        jdbcTemplate.update("DELETE FROM usuario");
        dono = usuarioRepository.saveAndFlush(new Usuario(
                "Dono", "dono@example.com", "hash", PerfilUsuario.SOLICITANTE));
        responsavel = usuarioRepository.saveAndFlush(new Usuario(
                "Responsavel", "resp@example.com", "hash", PerfilUsuario.ATENDENTE));
        categoria = categoriaRepository.saveAndFlush(new Categoria("Rede", null));
    }

    @AfterEach
    void stopWorkers() throws Exception {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        executeAsRoot("DROP TRIGGER IF EXISTS trg_teste_falha_comentario");
    }

    @Test
    void commentConfirmedBeforeClosingIsKeptAndClosingSucceeds() throws Exception {
        Chamado chamado = chamado(StatusChamado.RESOLVIDO, responsavel);

        Future<Object> fechamento = holdFirstThenRun(
                () -> comentar(responsavel, chamado),
                () -> encerrar(chamado, StatusChamado.FECHADO));
        fechamento.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertFinal(chamado, "FECHADO", 1, 1);
    }

    @Test
    void closingConfirmedFirstRejectsTheComment() throws Exception {
        Chamado chamado = chamado(StatusChamado.RESOLVIDO, responsavel);

        Future<Object> comentario = holdFirstThenRun(
                () -> encerrar(chamado, StatusChamado.FECHADO),
                () -> comentar(responsavel, chamado));

        assertCommentsClosed(comentario);
        assertFinal(chamado, "FECHADO", 0, 1);
    }

    @Test
    void commentConfirmedBeforeCancellingIsKeptAndCancellingSucceeds() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);

        Future<Object> cancelamento = holdFirstThenRun(
                () -> comentar(dono, chamado),
                () -> encerrar(chamado, StatusChamado.CANCELADO));
        cancelamento.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertFinal(chamado, "CANCELADO", 1, 1);
    }

    @Test
    void cancellingConfirmedFirstRejectsTheComment() throws Exception {
        Chamado chamado = chamado(StatusChamado.ABERTO, null);

        Future<Object> comentario = holdFirstThenRun(
                () -> encerrar(chamado, StatusChamado.CANCELADO),
                () -> comentar(dono, chamado));

        assertCommentsClosed(comentario);
        assertFinal(chamado, "CANCELADO", 0, 1);
    }

    @Test
    void failedCommentInsertLeavesNoRowAndTicketUntouched() throws Exception {
        Chamado chamado = chamado(StatusChamado.EM_ANDAMENTO, responsavel);
        Map<String, Object> antes = row(chamado);
        executeAsRoot("""
                CREATE TRIGGER trg_teste_falha_comentario
                BEFORE INSERT ON comentario
                FOR EACH ROW
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'falha simulada de comentario'
                """);

        assertThatThrownBy(() -> comentar(responsavel, chamado))
                .isInstanceOf(DataAccessException.class);

        assertThat(count("comentario")).isZero();
        assertThat(count("historico_status")).isZero();
        assertThat(row(chamado)).isEqualTo(antes);
    }

    private Object comentar(Usuario autor, Chamado chamado) {
        return comentarioService.adicionar(
                autor.getId(),
                chamado.getId(),
                new ComentarioDtos.Criar("Comentario concorrente", Map.of()));
    }

    /** Fechar e cancelar ABERTO são decisões do solicitante dono. */
    private Object encerrar(Chamado chamado, StatusChamado destino) {
        return chamadoService.alterarStatus(
                dono.getId(),
                chamado.getId(),
                new ChamadoDtos.AlterarStatus(destino, 0L, null, Map.of()));
    }

    private void assertFinal(Chamado chamado, String status, long comentarios, long historicos) {
        Map<String, Object> estado = row(chamado);
        assertThat(estado.get("status")).isEqualTo(status);
        assertThat(((Number) estado.get("version")).longValue()).isEqualTo(1);
        assertThat(count("comentario")).isEqualTo(comentarios);
        assertThat(count("historico_status")).isEqualTo(historicos);
    }

    private static void assertCommentsClosed(Future<Object> comentario) {
        assertThatThrownBy(() -> comentario.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOfSatisfying(DomainException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ApiErrorCode.COMMENTS_CLOSED);
                    assertThat(exception.getDetail()).isEqualTo(MENSAGEM_ENCERRADO);
                });
    }

    private Future<Object> holdFirstThenRun(Callable<Object> primeira, Callable<Object> segunda)
            throws Exception {
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

        Future<Object> perdedora = executor.submit(segunda);
        waitUntilSomeoneWaitsForALock();

        liberarPrimeira.countDown();
        vencedora.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return perdedora;
    }

    private Chamado chamado(StatusChamado status, Usuario atendente) {
        return chamadoRepository.saveAndFlush(new Chamado(
                "Sem rede", "Cabo solto", status, Prioridade.MEDIA, dono, atendente, categoria));
    }

    private Map<String, Object> row(Chamado chamado) {
        return jdbcTemplate.queryForMap("SELECT * FROM chamado WHERE id = ?", chamado.getId());
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
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
