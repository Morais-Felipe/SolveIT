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
 * Duas transações MySQL reais disputam o mesmo chamado. A primeira fica aberta até o
 * InnoDB mostrar a segunda esperando o guard; então a primeira confirma e a segunda lê
 * o estado já confirmado, com uma versão que não bate mais.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class ChamadoAtribuicaoConcorrenciaTest {

    private static final long TIMEOUT_SECONDS = 10;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_atribuicao_concorrencia_test")
            .withUsername("solveit")
            .withPassword("solveit-test");

    @Autowired
    ChamadoService chamadoService;

    @Autowired
    ComentarioService comentarioService;

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
    private Usuario solicitante;
    private Usuario atendenteX;
    private Usuario atendenteY;
    private Chamado chamado;

    @BeforeEach
    void prepare() {
        executor = Executors.newFixedThreadPool(2);
        jdbcTemplate.update("DELETE FROM historico_status");
        jdbcTemplate.update("DELETE FROM comentario");
        jdbcTemplate.update("DELETE FROM chamado");
        jdbcTemplate.update("DELETE FROM categoria");
        jdbcTemplate.update("DELETE FROM usuario");
        solicitante = usuarioRepository.saveAndFlush(new Usuario(
                "Solicitante", "solicitante@example.com", "hash", PerfilUsuario.SOLICITANTE));
        atendenteX = usuarioRepository.saveAndFlush(new Usuario(
                "Atendente X", "x@example.com", "hash", PerfilUsuario.ATENDENTE));
        atendenteY = usuarioRepository.saveAndFlush(new Usuario(
                "Atendente Y", "y@example.com", "hash", PerfilUsuario.ATENDENTE));
        Categoria categoria = categoriaRepository.saveAndFlush(new Categoria("Rede", null));
        chamado = chamadoRepository.saveAndFlush(new Chamado(
                "Sem rede", "Cabo solto", StatusChamado.ABERTO, Prioridade.ALTA,
                solicitante, null, categoria));
    }

    @AfterEach
    void stopWorkers() throws Exception {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        executeAsRoot("DROP TRIGGER IF EXISTS trg_teste_falha_historico_atribuicao");
    }

    @Test
    void firstConfirmedAssignmentWinsWhenXArrivesFirst() throws Exception {
        assertSingleWinner(atendenteX, atendenteY);
    }

    @Test
    void firstConfirmedAssignmentWinsWhenYArrivesFirst() throws Exception {
        assertSingleWinner(atendenteY, atendenteX);
    }

    @Test
    void editionConfirmedFirstMakesTheOldAssignmentStale() throws Exception {
        Future<ChamadoDtos.Detalhe> perdedor = holdFirstThenRun(
                () -> editar("Texto confirmado"),
                () -> assumir(atendenteX));

        assertStale(perdedor);
        Map<String, Object> estado = row();
        assertThat(estado.get("titulo")).isEqualTo("Texto confirmado");
        assertThat(estado.get("status")).isEqualTo("ABERTO");
        assertThat(estado.get("atendente_id")).isNull();
        assertThat(((Number) estado.get("version")).longValue()).isEqualTo(1);
        assertThat(historyCount()).isZero();
    }

    @Test
    void assignmentConfirmedFirstMakesTheOldEditionStale() throws Exception {
        Future<ChamadoDtos.Detalhe> perdedor = holdFirstThenRun(
                () -> assumir(atendenteX),
                () -> editar("Texto atrasado"));

        assertStale(perdedor);
        Map<String, Object> estado = row();
        assertThat(estado.get("titulo")).isEqualTo("Sem rede");
        assertThat(estado.get("status")).isEqualTo("EM_ANDAMENTO");
        assertThat(((Number) estado.get("atendente_id")).longValue())
                .isEqualTo(atendenteX.getId());
        assertThat(historyCount()).isOne();
    }

    @Test
    void commentConfirmedFirstDoesNotReserveNorInvalidateTheAssignment() throws Exception {
        // Maria (Y) comenta e segura o guard; João (X) tenta assumir com a versão 0.
        Future<ChamadoDtos.Detalhe> atribuicao = holdFirstThenRun(
                () -> {
                    comentarioService.adicionar(
                            atendenteY.getId(),
                            chamado.getId(),
                            new ComentarioDtos.Criar("Maria olhando", Map.of()));
                    return null;
                },
                () -> assumir(atendenteX));

        ChamadoDtos.Detalhe resultado = atribuicao.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(resultado.atendente().id()).isEqualTo(atendenteX.getId());

        Map<String, Object> estado = row();
        assertThat(estado.get("status")).isEqualTo("EM_ANDAMENTO");
        assertThat(((Number) estado.get("version")).longValue()).isEqualTo(1);
        assertThat(historyCount()).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT autor_id FROM comentario WHERE chamado_id = ?",
                Long.class,
                chamado.getId())).isEqualTo(atendenteY.getId());
    }

    @Test
    void historyFailureRollsBackAttendantStatusAndVersion() throws Exception {
        executeAsRoot("""
                CREATE TRIGGER trg_teste_falha_historico_atribuicao
                BEFORE INSERT ON historico_status
                FOR EACH ROW
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'falha simulada de historico'
                """);

        assertThatThrownBy(() -> assumir(atendenteX)).isInstanceOf(DataAccessException.class);

        Map<String, Object> estado = row();
        assertThat(estado.get("status")).isEqualTo("ABERTO");
        assertThat(estado.get("atendente_id")).isNull();
        assertThat(((Number) estado.get("version")).longValue()).isZero();
        assertThat(historyCount()).isZero();
    }

    private void assertSingleWinner(Usuario primeiro, Usuario segundo) throws Exception {
        Future<ChamadoDtos.Detalhe> perdedor = holdFirstThenRun(
                () -> assumir(primeiro),
                () -> assumir(segundo));

        assertStale(perdedor);
        Map<String, Object> estado = row();
        assertThat(estado.get("status")).isEqualTo("EM_ANDAMENTO");
        assertThat(((Number) estado.get("atendente_id")).longValue()).isEqualTo(primeiro.getId());
        assertThat(((Number) estado.get("version")).longValue()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList(
                "SELECT alterado_por_id FROM historico_status", Long.class))
                .containsExactly(primeiro.getId());
    }

    /**
     * Executa {@code primeira} numa transação que só confirma depois de a
     * {@code segunda} estar comprovadamente na fila do guard. Devolve o resultado da
     * segunda, que sempre roda com a versão 0 vista antes da primeira.
     */
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

    private static void assertStale(Future<ChamadoDtos.Detalhe> perdedor) {
        assertThatThrownBy(() -> perdedor.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ApiErrorCode.STALE_RESOURCE));
    }

    private ChamadoDtos.Detalhe assumir(Usuario atendente) {
        return chamadoService.atribuir(
                atendente.getId(),
                chamado.getId(),
                new ChamadoDtos.Atribuir(atendente.getId(), 0L, null, Map.of()));
    }

    private ChamadoDtos.Detalhe editar(String titulo) {
        return chamadoService.editar(
                solicitante.getId(),
                chamado.getId(),
                new ChamadoDtos.Editar(titulo, "Cabo solto", 0L, Map.of()));
    }

    private Map<String, Object> row() {
        return jdbcTemplate.queryForMap("SELECT * FROM chamado WHERE id = ?", chamado.getId());
    }

    private long historyCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM historico_status", Long.class);
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
