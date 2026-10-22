package com.solveit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.solveit.dto.categoria.CategoriaDtos;
import com.solveit.dto.chamado.ChamadoDtos;
import com.solveit.entity.Categoria;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.enums.Prioridade;
import com.solveit.exception.ApiErrorCode;
import com.solveit.exception.DomainException;
import com.solveit.repository.CategoriaRepository;
import com.solveit.repository.UsuarioRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * As duas escritas passam pelo mesmo guard global, então nunca se intercalam: a que
 * chega primeiro termina (commit) antes de a outra ler a categoria. Cada teste fixa uma
 * ordem segurando a primeira transação aberta até ver, no MySQL, a segunda esperando o lock.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class ChamadoAberturaConcorrenciaTest {

    private static final long TIMEOUT_SECONDS = 10;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_chamado_concorrencia_test")
            .withUsername("solveit")
            .withPassword("solveit-test");

    @Autowired
    ChamadoService chamadoService;

    @Autowired
    CategoriaService categoriaService;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    CategoriaRepository categoriaRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    private ExecutorService executor;
    private Usuario admin;
    private Usuario solicitante;
    private Categoria categoria;

    @BeforeEach
    void prepare() {
        executor = Executors.newFixedThreadPool(2);
        jdbcTemplate.update("DELETE FROM historico_status");
        jdbcTemplate.update("DELETE FROM comentario");
        jdbcTemplate.update("DELETE FROM chamado");
        jdbcTemplate.update("DELETE FROM categoria");
        jdbcTemplate.update("DELETE FROM usuario");
        admin = usuarioRepository.saveAndFlush(
                new Usuario("Admin", "admin@example.com", "hash", PerfilUsuario.ADMIN));
        solicitante = usuarioRepository.saveAndFlush(
                new Usuario("Solicitante", "solicitante@example.com", "hash",
                        PerfilUsuario.SOLICITANTE));
        categoria = categoriaRepository.saveAndFlush(new Categoria("Rede", null));
    }

    @AfterEach
    void stopWorkers() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void deactivationFirstMakesTheOpeningFailWithoutLeavingHistory() throws Exception {
        CountDownLatch deactivated = new CountDownLatch(1);
        CountDownLatch releaseDeactivation = new CountDownLatch(1);

        Future<?> deactivation = executor.submit(() -> inTransaction(() -> {
            desativarCategoria();
            deactivated.countDown();
            await(releaseDeactivation);
        }));
        await(deactivated);

        Future<ChamadoDtos.Detalhe> opening = executor.submit(this::abrirChamado);
        waitUntilSomeoneWaitsForALock();

        releaseDeactivation.countDown();
        deactivation.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThatThrownBy(() -> opening.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ApiErrorCode.INVALID_STATE));
        assertThat(count("chamado")).isZero();
        assertThat(count("historico_status")).isZero();
        assertThat(categoriaAtiva()).isFalse();
    }

    @Test
    void openingFirstIsPreservedWhenTheCategoryIsDeactivatedAfterwards() throws Exception {
        CountDownLatch opened = new CountDownLatch(1);
        CountDownLatch releaseOpening = new CountDownLatch(1);

        Future<?> opening = executor.submit(() -> inTransaction(() -> {
            abrirChamado();
            opened.countDown();
            await(releaseOpening);
        }));
        await(opened);

        Future<?> deactivation = executor.submit(this::desativarCategoria);
        waitUntilSomeoneWaitsForALock();

        releaseOpening.countDown();
        opening.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        deactivation.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(count("chamado")).isOne();
        assertThat(count("historico_status")).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT categoria_id FROM chamado", Long.class)).isEqualTo(categoria.getId());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status_novo FROM historico_status", String.class)).isEqualTo("ABERTO");
        assertThat(categoriaAtiva()).isFalse();
    }

    private ChamadoDtos.Detalhe abrirChamado() {
        return chamadoService.abrir(solicitante.getId(), new ChamadoDtos.Criar(
                "Sem rede",
                "Cabo desconectado.",
                Prioridade.ALTA,
                categoria.getId(),
                Map.of()));
    }

    private CategoriaDtos.Resposta desativarCategoria() {
        return categoriaService.alterarAtivo(
                admin.getId(),
                categoria.getId(),
                new CategoriaDtos.AlterarAtivo(false, categoria.getVersion()));
    }

    private void inTransaction(ThrowingRunnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            try {
                work.run();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    /**
     * Prova, pelo próprio InnoDB, que a segunda transação chegou ao guard e está
     * bloqueada, em vez de supor isso por tempo de espera.
     */
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

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("Worker did not reach the expected point");
        }
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private boolean categoriaAtiva() {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT ativo FROM categoria WHERE id = ?", Boolean.class, categoria.getId()));
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
