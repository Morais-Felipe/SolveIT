package com.solveit.service;

import com.solveit.dto.categoria.CategoriaDtos;
import com.solveit.entity.Categoria;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.exception.ApiErrorCode;
import com.solveit.exception.DomainException;
import com.solveit.repository.CategoriaRepository;
import com.solveit.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(CoreWriteCoordinatorIntegrationTest.ProbeConfiguration.class)
class CoreWriteCoordinatorIntegrationTest {

    private static final long FUTURE_TIMEOUT_SECONDS = 10;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_coordinator_test")
            .withUsername("solveit")
            .withPassword("solveit-test");

    @Autowired
    CoreWriteCoordinator coordinator;

    @Autowired
    CoordinatorProbe probe;

    @Autowired
    CategoriaRepository categoriaRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private ExecutorService executor;

    @BeforeEach
    void clearData() {
        executor = Executors.newFixedThreadPool(2);
        jdbcTemplate.update("DELETE FROM categoria");
        jdbcTemplate.update("DELETE FROM usuario");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM core_write_guard WHERE id = 1", Integer.class)).isOne();
    }

    @AfterEach
    void stopWorkers() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void rejectsAcquisitionWithoutCallerTransaction() {
        assertThatThrownBy(coordinator::adquirir)
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void serializesTwoWritesUntilTheFirstTransactionCommits() throws Exception {
        CountDownLatch firstAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondAttempted = new CountDownLatch(1);
        CountDownLatch secondAcquired = new CountDownLatch(1);

        Future<Long> first = executor.submit(() ->
                probe.createWhileHoldingGuard("Primeira categoria", firstAcquired, releaseFirst));
        assertThat(firstAcquired.await(5, TimeUnit.SECONDS)).isTrue();

        Future<Long> second = executor.submit(() ->
                probe.createAfterSignal("Segunda categoria", secondAttempted, secondAcquired));
        assertThat(secondAttempted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(secondAcquired.await(500, TimeUnit.MILLISECONDS)).isFalse();

        releaseFirst.countDown();
        Long firstId = first.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Long secondId = second.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(firstId).isNotEqualTo(secondId);
        assertThat(categoriaRepository.findAll())
                .extracting(Categoria::getNome)
                .containsExactlyInAnyOrder("Primeira categoria", "Segunda categoria");
    }

    @Test
    void rollsBackJdbcAndJpaWorkTogetherAndReleasesTheGuard() {
        String jdbcName = "JDBC rollback " + UUID.randomUUID();
        String jpaName = "JPA rollback " + UUID.randomUUID();

        assertThatThrownBy(() -> probe.writeWithJdbcAndJpaThenFail(jdbcName, jpaName))
                .isInstanceOf(ExpectedRollbackException.class);

        assertThat(categoriaRepository.existsByNome(jdbcName)).isFalse();
        assertThat(categoriaRepository.existsByNome(jpaName)).isFalse();

        Long persistedId = probe.create("Categoria apos rollback");
        assertThat(categoriaRepository.findById(persistedId)).isPresent();
    }

    @Test
    void expiresAWaitingAcquisitionWithoutLeakingTheGuard() throws Exception {
        CountDownLatch holderAcquired = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        Future<Void> holder = executor.submit(() -> {
            probe.holdGuard(holderAcquired, releaseHolder);
            return null;
        });
        assertThat(holderAcquired.await(5, TimeUnit.SECONDS)).isTrue();

        Future<Throwable> waiter = executor.submit(() -> captureFailure(
                () -> probe.create("Categoria que deve expirar")));
        Throwable timeoutFailure = waiter.get(8, TimeUnit.SECONDS);

        assertThat(timeoutFailure).isInstanceOf(PessimisticLockingFailureException.class);
        assertThat(categoriaRepository.existsByNome("Categoria que deve expirar")).isFalse();

        releaseHolder.countDown();
        holder.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Long persistedId = probe.create("Categoria apos timeout");
        assertThat(categoriaRepository.findById(persistedId)).isPresent();
    }

    @Test
    void revokedAdministratorWaitingForTheGuardCannotWrite() throws Exception {
        Usuario administrator = usuarioRepository.saveAndFlush(new Usuario(
                "Administrador concorrente",
                "admin-coordinator+" + UUID.randomUUID() + "@solveit.local",
                "hash",
                PerfilUsuario.ADMIN));
        CountDownLatch holderAcquired = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        CountDownLatch creationAttempted = new CountDownLatch(1);
        Future<Void> holder = executor.submit(() -> {
            probe.holdGuard(holderAcquired, releaseHolder);
            return null;
        });
        assertThat(holderAcquired.await(5, TimeUnit.SECONDS)).isTrue();

        Future<Throwable> creation = executor.submit(() -> captureFailure(() ->
                probe.createCategoryAfterSignal(administrator.getId(), creationAttempted)));
        assertThat(creationAttempted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> creation.get(500, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);

        assertThat(jdbcTemplate.update(
                "UPDATE usuario SET ativo = FALSE WHERE id = ?", administrator.getId())).isOne();
        releaseHolder.countDown();

        Throwable failure = creation.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        holder.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(failure).isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.getCode()).isEqualTo(ApiErrorCode.AUTH_ACCOUNT_INACTIVE));
        assertThat(categoriaRepository.existsByNome("Categoria de ator revogado")).isFalse();
    }

    private static Throwable captureFailure(ThrowingAction action) {
        try {
            action.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {

        @Bean
        CoordinatorProbe coordinatorProbe(
                CoreWriteCoordinator coordinator,
                CategoriaRepository categoriaRepository,
                CategoriaService categoriaService,
                JdbcTemplate jdbcTemplate) {
            return new CoordinatorProbe(
                    coordinator, categoriaRepository, categoriaService, jdbcTemplate);
        }
    }

    static class CoordinatorProbe {

        private final CoreWriteCoordinator coordinator;
        private final CategoriaRepository categoriaRepository;
        private final CategoriaService categoriaService;
        private final JdbcTemplate jdbcTemplate;

        CoordinatorProbe(
                CoreWriteCoordinator coordinator,
                CategoriaRepository categoriaRepository,
                CategoriaService categoriaService,
                JdbcTemplate jdbcTemplate) {
            this.coordinator = coordinator;
            this.categoriaRepository = categoriaRepository;
            this.categoriaService = categoriaService;
            this.jdbcTemplate = jdbcTemplate;
        }

        @Transactional(timeout = 15)
        public Long createWhileHoldingGuard(
                String name, CountDownLatch acquired, CountDownLatch release) {
            coordinator.adquirir();
            acquired.countDown();
            await(release, "The first guarded transaction was not released");
            return categoriaRepository.saveAndFlush(new Categoria(name, null)).getId();
        }

        @Transactional(timeout = 10)
        public Long createAfterSignal(
                String name, CountDownLatch attempted, CountDownLatch acquired) {
            attempted.countDown();
            coordinator.adquirir();
            acquired.countDown();
            return categoriaRepository.saveAndFlush(new Categoria(name, null)).getId();
        }

        @Transactional(timeout = 10)
        public Long create(String name) {
            coordinator.adquirir();
            return categoriaRepository.saveAndFlush(new Categoria(name, null)).getId();
        }

        @Transactional(timeout = 15)
        public void holdGuard(CountDownLatch acquired, CountDownLatch release) {
            coordinator.adquirir();
            acquired.countDown();
            await(release, "The guarded transaction was not released");
        }

        @Transactional(timeout = 10)
        public void writeWithJdbcAndJpaThenFail(String jdbcName, String jpaName) {
            coordinator.adquirir();
            jdbcTemplate.update("""
                    INSERT INTO categoria (nome, descricao, ativo)
                    VALUES (?, 'Criada por JDBC', TRUE)
                    """, jdbcName);
            categoriaRepository.saveAndFlush(new Categoria(jpaName, "Criada por JPA"));
            throw new ExpectedRollbackException();
        }

        public CategoriaDtos.Resposta createCategoryAfterSignal(
                Long administratorId, CountDownLatch attempted) {
            attempted.countDown();
            return categoriaService.criar(
                    administratorId,
                    new CategoriaDtos.Criar("Categoria de ator revogado", null));
        }

        private static void await(CountDownLatch latch, String message) {
            try {
                if (!latch.await(12, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(message);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Worker interrupted while holding the guard", exception);
            }
        }
    }

    static final class ExpectedRollbackException extends RuntimeException {
    }
}
