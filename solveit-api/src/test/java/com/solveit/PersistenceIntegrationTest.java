package com.solveit;

import com.solveit.entity.Categoria;
import com.solveit.entity.Chamado;
import com.solveit.entity.Comentario;
import com.solveit.entity.HistoricoStatus;
import com.solveit.entity.Usuario;
import com.solveit.enums.PerfilUsuario;
import com.solveit.enums.Prioridade;
import com.solveit.enums.StatusChamado;
import com.solveit.repository.CategoriaRepository;
import com.solveit.repository.ChamadoRepository;
import com.solveit.repository.ComentarioRepository;
import com.solveit.repository.HistoricoStatusRepository;
import com.solveit.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.PersistenceContext;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PersistenceIntegrationTest {

    private static final Set<String> DOMAIN_TABLES = Set.of(
            "usuario", "categoria", "chamado", "comentario", "historico_status");

    private static final Set<String> NAMED_CONSTRAINTS = Set.of(
            "uk_usuario_email", "uk_categoria_nome",
            "ck_usuario_perfil", "ck_chamado_status", "ck_chamado_prioridade",
            "ck_historico_status_anterior", "ck_historico_status_novo",
            "fk_chamado_solicitante", "fk_chamado_atendente", "fk_chamado_categoria",
            "fk_comentario_chamado", "fk_comentario_autor",
            "fk_historico_chamado", "fk_historico_alterado_por");

    private static final Set<String> SECONDARY_INDEXES = Set.of(
            "idx_chamado_solicitante_criado", "idx_chamado_atendente_status",
            "idx_chamado_status_prioridade", "idx_chamado_categoria",
            "idx_comentario_chamado_criado", "idx_comentario_autor",
            "idx_historico_chamado_alterado", "idx_historico_alterado_por");

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_test")
            .withUsername("solveit")
            .withPassword("solveit-test");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Flyway flyway;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    CategoriaRepository categoriaRepository;

    @Autowired
    ChamadoRepository chamadoRepository;

    @Autowired
    ComentarioRepository comentarioRepository;

    @Autowired
    HistoricoStatusRepository historicoStatusRepository;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    PlatformTransactionManager transactionManager;

    @PersistenceContext
    EntityManager entityManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void clearDomainData() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        jdbcTemplate.update("DELETE FROM historico_status");
        jdbcTemplate.update("DELETE FROM comentario");
        jdbcTemplate.update("DELETE FROM chamado");
        jdbcTemplate.update("DELETE FROM categoria");
        jdbcTemplate.update("DELETE FROM usuario");
    }

    @Test
    @Order(1)
    void concurrentSpringContextsMigrateAnEmptyDatabaseExactlyOnce() throws Exception {
        flywayForContainer().clean();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()",
                Integer.class)).isZero();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<ConfigurableApplicationContext> boot = () -> {
            ready.countDown();
            if (!start.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent Spring contexts were not ready");
            }
            return startIsolatedContext();
        };

        Future<ConfigurableApplicationContext> first = executor.submit(boot);
        Future<ConfigurableApplicationContext> second = executor.submit(boot);
        ConfigurableApplicationContext firstContext = null;
        ConfigurableApplicationContext secondContext = null;
        try {
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            firstContext = first.get(120, TimeUnit.SECONDS);
            secondContext = second.get(120, TimeUnit.SECONDS);
            assertThat(firstContext.isActive()).isTrue();
            assertThat(secondContext.isActive()).isTrue();
        } finally {
            close(firstContext);
            close(secondContext);
            executor.shutdownNow();
        }

        assertThat(successfulMigrationVersions()).containsExactly("1", "2", "3", "4", "5");
        assertThat(jdbcTemplate.queryForList("""
                SELECT version, COUNT(*) AS version_count
                FROM flyway_schema_history
                WHERE success = TRUE
                GROUP BY version
                """))
                .hasSize(5)
                .allSatisfy(row ->
                        assertThat(((Number) row.get("version_count")).intValue()).isOne());
        assertThat(domainTableNames()).containsExactlyInAnyOrderElementsOf(DOMAIN_TABLES);
    }

    @Test
    @Order(2)
    void schemaMetadataMatchesTheApprovedPhysicalContract() {
        List<Map<String, Object>> tables = jdbcTemplate.queryForList("""
                SELECT table_name, engine, table_collation
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('usuario', 'categoria', 'chamado', 'comentario', 'historico_status')
                ORDER BY table_name
                """);
        assertThat(tables).hasSize(5).allSatisfy(table -> {
            assertThat(table.get("engine")).isEqualTo("InnoDB");
            assertThat(table.get("table_collation")).isEqualTo("utf8mb4_0900_ai_ci");
        });

        List<Map<String, Object>> constraints = jdbcTemplate.queryForList("""
                SELECT table_name, constraint_name, constraint_type
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name IN ('usuario', 'categoria', 'chamado', 'comentario', 'historico_status')
                """);
        assertThat(constraints)
                .filteredOn(row -> "PRIMARY KEY".equals(row.get("constraint_type")))
                .hasSize(5)
                .allSatisfy(row -> assertThat(row.get("constraint_name")).isEqualTo("PRIMARY"));
        assertThat(constraints)
                .extracting(row -> (String) row.get("constraint_name"))
                .containsAll(NAMED_CONSTRAINTS);

        List<String> indexes = jdbcTemplate.queryForList("""
                SELECT DISTINCT index_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name IN ('usuario', 'categoria', 'chamado', 'comentario', 'historico_status')
                """, String.class);
        assertThat(indexes).containsAll(SECONDARY_INDEXES);

        List<String> deleteRules = jdbcTemplate.queryForList("""
                SELECT delete_rule
                FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE()
                """, String.class);
        assertThat(deleteRules).hasSize(7).containsOnly("RESTRICT");

        assertThat(columnNames("usuario"))
                .containsExactly("id", "nome", "email", "senha", "perfil", "ativo", "criado_em");
        assertThat(columnNames("categoria"))
                .containsExactly("id", "nome", "descricao", "ativo");
        assertThat(columnNames("chamado")).containsExactly(
                "id", "titulo", "descricao", "status", "prioridade", "solicitante_id",
                "atendente_id", "categoria_id", "criado_em", "atualizado_em", "resolvido_em", "version");
        assertThat(columnNames("comentario"))
                .containsExactly("id", "chamado_id", "autor_id", "texto", "criado_em");
        assertThat(columnNames("historico_status")).containsExactly(
                "id", "chamado_id", "status_anterior", "status_novo",
                "alterado_por_id", "alterado_em", "observacao");

        assertColumn("usuario", "email", "varchar", 255L, "NO");
        assertColumn("usuario", "perfil", "varchar", 20L, "NO");
        assertColumn("categoria", "descricao", "text", 65_535L, "YES");
        assertColumn("chamado", "titulo", "varchar", 200L, "NO");
        assertColumn("chamado", "status", "varchar", 20L, "NO");
        assertColumn("chamado", "prioridade", "varchar", 10L, "NO");
        assertColumn("chamado", "atendente_id", "bigint", null, "YES");
        assertColumn("chamado", "resolvido_em", "datetime", null, "YES");
        assertColumn("chamado", "version", "bigint", null, "NO");
        assertColumn("historico_status", "observacao", "varchar", 500L, "YES");
    }

    @Test
    @Order(3)
    void repositoriesPersistFlushClearAndReloadTheUnicodeDomainGraph() {
        transactionTemplate.executeWithoutResult(status -> {
            Usuario solicitante = usuarioRepository.save(new Usuario(
                    "João da Silva", uniqueEmail("solicitante"), "hash-temporario",
                    PerfilUsuario.SOLICITANTE));
            Categoria categoria = categoriaRepository.save(new Categoria("Impressão 🖨️", null));
            Chamado chamado = chamadoRepository.save(new Chamado(
                    "Falha no relatório çãõ", "Descrição com acentos e emoji 🚀",
                    StatusChamado.ABERTO, Prioridade.ALTA, solicitante, null, categoria));
            Comentario primeiro = comentarioRepository.save(
                    new Comentario(chamado, solicitante, "Primeiro comentário — análise iniciada"));
            Comentario segundo = comentarioRepository.save(
                    new Comentario(chamado, solicitante, "Segundo comentário ✓"));
            HistoricoStatus historico = historicoStatusRepository.save(new HistoricoStatus(
                    chamado, StatusChamado.ABERTO, StatusChamado.EM_ANDAMENTO,
                    solicitante, null));

            entityManager.flush();
            Long chamadoId = chamado.getId();
            Long solicitanteId = solicitante.getId();
            Long categoriaId = categoria.getId();
            entityManager.clear();

            Chamado reloaded = chamadoRepository.findById(chamadoId).orElseThrow();
            assertThat(reloaded.getTitulo()).isEqualTo("Falha no relatório çãõ");
            assertThat(reloaded.getDescricao()).isEqualTo("Descrição com acentos e emoji 🚀");
            assertThat(reloaded.getStatus()).isEqualTo(StatusChamado.ABERTO);
            assertThat(reloaded.getPrioridade()).isEqualTo(Prioridade.ALTA);
            assertThat(reloaded.getSolicitante().getId()).isEqualTo(solicitanteId);
            assertThat(reloaded.getCategoria().getId()).isEqualTo(categoriaId);
            assertThat(reloaded.getAtendente()).isNull();
            assertThat(reloaded.getResolvidoEm()).isNull();
            assertThat(reloaded.getVersion()).isZero();
            assertThat(categoriaRepository.findById(categoriaId).orElseThrow().getDescricao()).isNull();

            List<Comentario> ordered = comentarioRepository.findAll(
                    Sort.by(Sort.Order.asc("criadoEm"), Sort.Order.asc("id")));
            assertThat(ordered).extracting(Comentario::getId)
                    .containsExactly(primeiro.getId(), segundo.getId());
            assertThat(ordered.get(0).getChamado().getId()).isEqualTo(chamadoId);
            assertThat(ordered.get(0).getAutor().getId()).isEqualTo(solicitanteId);

            HistoricoStatus history = historicoStatusRepository.findById(historico.getId()).orElseThrow();
            assertThat(history.getStatusAnterior()).isEqualTo(StatusChamado.ABERTO);
            assertThat(history.getStatusNovo()).isEqualTo(StatusChamado.EM_ANDAMENTO);
            assertThat(history.getObservacao()).isNull();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM chamado WHERE id = ?", String.class, chamadoId))
                    .isEqualTo("ABERTO");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT prioridade FROM chamado WHERE id = ?", String.class, chamadoId))
                    .isEqualTo("ALTA");
        });
    }

    @Test
    @Order(4)
    void databaseRejectsDuplicatesRequiredNullsInvalidEnumsAndOrphanReferences() {
        String email = uniqueEmail("duplicado");
        insertUser("Pessoa válida", email, "SOLICITANTE");

        assertThatThrownBy(() -> insertUser("Outra pessoa", email, "SOLICITANTE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO usuario (nome, email, senha, perfil, ativo, criado_em)
                VALUES (NULL, ?, 'hash', 'SOLICITANTE', TRUE, UTC_TIMESTAMP(6))
                """, uniqueEmail("sem-nome")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUser(
                "Perfil inválido", uniqueEmail("perfil-invalido"), "SUPER_USUARIO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO chamado (
                    titulo, descricao, status, prioridade, solicitante_id, atendente_id,
                    categoria_id, criado_em, atualizado_em, resolvido_em, version)
                VALUES ('Órfão', 'Sem pais', 'ABERTO', 'BAIXA', -1, NULL, -1,
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), NULL, 0)
                """))
                .isInstanceOf(DataIntegrityViolationException.class);

        Usuario user = usuarioRepository.saveAndFlush(new Usuario(
                "Referenciado", uniqueEmail("referenciado"), "hash", PerfilUsuario.SOLICITANTE));
        Categoria category = categoriaRepository.saveAndFlush(
                new Categoria("Categoria referenciada", null));
        Chamado ticket = chamadoRepository.saveAndFlush(new Chamado(
                "Chamado válido", "Contrato FK", StatusChamado.ABERTO, Prioridade.MEDIA,
                user, null, category));
        assertThatThrownBy(() ->
                jdbcTemplate.update("DELETE FROM usuario WHERE id = ?", user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(chamadoRepository.existsById(ticket.getId())).isTrue();

        assertThat(Arrays.stream(Comentario.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().startsWith("set"))).isTrue();
        assertThat(Arrays.stream(HistoricoStatus.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().startsWith("set"))).isTrue();
    }

    @Test
    @Order(5)
    void savingTheSameMutableEntityUpdatesOneRowInsteadOfDuplicatingIt() {
        long before = usuarioRepository.count();
        Usuario user = usuarioRepository.saveAndFlush(new Usuario(
                "Nome inicial", uniqueEmail("idempotente"), "hash", PerfilUsuario.ADMIN));
        Long id = user.getId();
        user.setNome("Nome atualizado");
        Usuario updated = usuarioRepository.saveAndFlush(user);

        assertThat(updated.getId()).isEqualTo(id);
        assertThat(usuarioRepository.count()).isEqualTo(before + 1);
        assertThat(usuarioRepository.findById(id).orElseThrow().getNome())
                .isEqualTo("Nome atualizado");
        assertThat(usuarioRepository.existsByEmail(user.getEmail())).isTrue();
        assertThat(usuarioRepository.findByEmail(user.getEmail())).isPresent();
    }

    @Test
    @Order(6)
    void staleConcurrentTicketUpdateFailsAndVersionIncrements() {
        Usuario user = usuarioRepository.saveAndFlush(new Usuario(
                "Concorrência", uniqueEmail("locking"), "hash", PerfilUsuario.ATENDENTE));
        Categoria category = categoriaRepository.saveAndFlush(
                new Categoria("Locking", "Teste de versão"));
        Chamado persisted = chamadoRepository.saveAndFlush(new Chamado(
                "Impressora", "Sem papel", StatusChamado.ABERTO, Prioridade.BAIXA,
                user, null, category));
        Long id = persisted.getId();
        long initialVersion = persisted.getVersion();

        EntityManager firstManager = entityManagerFactory.createEntityManager();
        EntityManager secondManager = entityManagerFactory.createEntityManager();
        EntityTransaction firstTransaction = firstManager.getTransaction();
        EntityTransaction secondTransaction = secondManager.getTransaction();
        try {
            firstTransaction.begin();
            secondTransaction.begin();
            Chamado firstCopy = firstManager.find(Chamado.class, id);
            Chamado staleCopy = secondManager.find(Chamado.class, id);
            firstCopy.setTitulo("Atualização confirmada");
            staleCopy.setTitulo("Atualização obsoleta");
            firstTransaction.commit();
            assertThatThrownBy(secondTransaction::commit)
                    .isInstanceOfAny(
                            jakarta.persistence.RollbackException.class,
                            jakarta.persistence.OptimisticLockException.class);
        } finally {
            rollbackIfActive(firstTransaction);
            rollbackIfActive(secondTransaction);
            firstManager.close();
            secondManager.close();
        }

        Chamado reloaded = chamadoRepository.findById(id).orElseThrow();
        assertThat(reloaded.getTitulo()).isEqualTo("Atualização confirmada");
        assertThat(reloaded.getVersion()).isEqualTo(initialVersion + 1);
    }

    @Test
    @Order(7)
    void repeatedMigrationAndHibernateValidationDoNotChangeSchemaOrHistory() {
        List<Map<String, Object>> historyBefore = migrationHistorySnapshot();
        List<String> metadataBefore = metadataSignature();

        MigrateResult migrateResult = flyway.migrate();
        assertThat(migrateResult.migrationsExecuted).isZero();
        try (ConfigurableApplicationContext context = startIsolatedContext()) {
            assertThat(context.isActive()).isTrue();
        }

        assertThat(migrationHistorySnapshot()).isEqualTo(historyBefore);
        assertThat(metadataSignature()).isEqualTo(metadataBefore);
    }

    private Flyway flywayForContainer() {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
    }

    private ConfigurableApplicationContext startIsolatedContext() {
        return new SpringApplicationBuilder(SolveItApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.profiles.active=test",
                        "spring.datasource.url=" + MYSQL.getJdbcUrl(),
                        "spring.datasource.username=" + MYSQL.getUsername(),
                        "spring.datasource.password=" + MYSQL.getPassword(),
                        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                        "spring.datasource.hikari.maximum-pool-size=2",
                        "spring.flyway.enabled=true",
                        "spring.flyway.locations=classpath:db/migration",
                        "spring.jpa.open-in-view=false",
                        "spring.jpa.hibernate.ddl-auto=validate",
                        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
                        "spring.main.banner-mode=off")
                .run();
    }

    private List<String> successfulMigrationVersions() {
        return jdbcTemplate.queryForList("""
                SELECT version
                FROM flyway_schema_history
                WHERE success = TRUE
                ORDER BY installed_rank
                """, String.class);
    }

    private Set<String> domainTableNames() {
        return Set.copyOf(jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('usuario', 'categoria', 'chamado', 'comentario', 'historico_status')
                """, String.class));
    }

    private List<String> columnNames(String tableName) {
        return jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ?
                ORDER BY ordinal_position
                """, String.class, tableName);
    }

    private void assertColumn(
            String tableName, String columnName, String dataType, Long maximumLength, String nullable) {
        Map<String, Object> column = jdbcTemplate.queryForMap("""
                SELECT data_type, character_maximum_length, is_nullable
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """, tableName, columnName);
        assertThat(column.get("data_type")).isEqualTo(dataType);
        assertThat(column.get("character_maximum_length")).isEqualTo(maximumLength);
        assertThat(column.get("is_nullable")).isEqualTo(nullable);
    }

    private List<Map<String, Object>> migrationHistorySnapshot() {
        return jdbcTemplate.queryForList("""
                SELECT installed_rank, version, description, type, checksum, success
                FROM flyway_schema_history
                ORDER BY installed_rank
                """);
    }

    private List<String> metadataSignature() {
        List<String> signature = new ArrayList<>();
        signature.addAll(jdbcTemplate.queryForList("""
                SELECT CONCAT('TABLE|', table_name, '|', engine, '|', table_collation)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('usuario', 'categoria', 'chamado', 'comentario', 'historico_status')
                """, String.class));
        signature.addAll(jdbcTemplate.queryForList("""
                SELECT CONCAT('CONSTRAINT|', table_name, '|', constraint_name, '|', constraint_type)
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name IN ('usuario', 'categoria', 'chamado', 'comentario', 'historico_status')
                """, String.class));
        signature.addAll(jdbcTemplate.queryForList("""
                SELECT CONCAT('INDEX|', table_name, '|', index_name, '|', seq_in_index, '|', column_name)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name IN ('usuario', 'categoria', 'chamado', 'comentario', 'historico_status')
                """, String.class));
        signature.sort(String::compareTo);
        return signature;
    }

    private void insertUser(String name, String email, String profile) {
        jdbcTemplate.update("""
                INSERT INTO usuario (nome, email, senha, perfil, ativo, criado_em)
                VALUES (?, ?, 'hash', ?, TRUE, UTC_TIMESTAMP(6))
                """, name, email, profile);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "+" + UUID.randomUUID() + "@solveit.local";
    }

    private void rollbackIfActive(EntityTransaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }

    private void close(ConfigurableApplicationContext context) {
        if (context != null) {
            context.close();
        }
    }
}
