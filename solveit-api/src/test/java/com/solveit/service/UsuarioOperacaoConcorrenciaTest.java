package com.solveit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.solveit.dto.usuario.UsuarioDtos;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.exception.ApiErrorCode;
import com.solveit.exception.DomainException;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Dois ADMINs tentam, ao mesmo tempo, operações que somadas deixariam o sistema sem
 * administrador. O guard enfileira as duas; a segunda enxerga o estado confirmado pela
 * primeira e é recusada. Em todos os casos sobra exatamente um ADMIN ativo.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class UsuarioOperacaoConcorrenciaTest {

    private static final long TIMEOUT_SECONDS = 10;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_usuario_operacao_concorrencia_test")
            .withUsername("solveit")
            .withPassword("solveit-test");

    @Autowired
    UsuarioService usuarioService;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    private ExecutorService executor;
    private Usuario adminA;
    private Usuario adminB;

    @BeforeEach
    void prepare() {
        executor = Executors.newFixedThreadPool(2);
        jdbcTemplate.update("DELETE FROM historico_status");
        jdbcTemplate.update("DELETE FROM comentario");
        jdbcTemplate.update("DELETE FROM chamado");
        jdbcTemplate.update("DELETE FROM categoria");
        jdbcTemplate.update("DELETE FROM usuario");
        adminA = usuarioRepository.saveAndFlush(
                new Usuario("Admin A", "a@example.com", "hash", PerfilUsuario.ADMIN));
        adminB = usuarioRepository.saveAndFlush(
                new Usuario("Admin B", "b@example.com", "hash", PerfilUsuario.ADMIN));
    }

    @AfterEach
    void stopWorkers() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void mutualDeactivationLeavesTheFirstWinnerAsTheOnlyAdmin() throws Exception {
        Future<Object> segunda = holdFirstThenRun(
                () -> desativar(adminA, adminB),
                () -> desativar(adminB, adminA));

        // B foi desativado pela primeira operação e já não pode agir.
        assertRejectedWith(segunda, ApiErrorCode.AUTH_ACCOUNT_INACTIVE);
        assertThat(ativo(adminA)).isTrue();
        assertThat(ativo(adminB)).isFalse();
        assertThat(adminsAtivos()).isOne();
    }

    @Test
    void simultaneousSelfDeactivationKeepsOneAdmin() throws Exception {
        Future<Object> segunda = holdFirstThenRun(
                () -> desativar(adminA, adminA),
                () -> desativar(adminB, adminB));

        assertRejectedWith(segunda, ApiErrorCode.LAST_ACTIVE_ADMIN);
        assertThat(ativo(adminA)).isFalse();
        assertThat(ativo(adminB)).isTrue();
        assertThat(adminsAtivos()).isOne();
    }

    private Object desativar(Usuario ator, Usuario alvo) {
        Long versao = jdbcTemplate.queryForObject(
                "SELECT version FROM usuario WHERE id = ?", Long.class, alvo.getId());
        return usuarioService.alterarOperacao(
                ator.getId(),
                alvo.getId(),
                new UsuarioDtos.AlterarOperacao(PerfilUsuario.ADMIN, false, versao, Map.of()));
    }

    private boolean ativo(Usuario usuario) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT ativo FROM usuario WHERE id = ?", Boolean.class, usuario.getId()));
    }

    private long adminsAtivos() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usuario WHERE perfil = 'ADMIN' AND ativo = TRUE", Long.class);
    }

    private static void assertRejectedWith(Future<Object> segunda, ApiErrorCode codigo) {
        assertThatThrownBy(() -> segunda.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(codigo));
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
}
