package com.solveit.service;

import com.solveit.entity.Usuario;
import com.solveit.exception.ApiErrorCode;
import com.solveit.exception.DomainException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CoreWriteCoordinator {

    private static final int LOCK_WAIT_TIMEOUT_SECONDS = 5;
    private static final String LOCK_SQL =
            "SELECT id FROM core_write_guard WHERE id = 1 FOR UPDATE";

    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    public CoreWriteCoordinator(JdbcTemplate jdbcTemplate, EntityManager entityManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void adquirir() {
        Long guardId = jdbcTemplate.execute((ConnectionCallback<Long>) connection -> {
            int previousLockWaitTimeout;
            try (Statement readTimeout = connection.createStatement();
                    var resultSet = readTimeout.executeQuery(
                            "SELECT @@SESSION.innodb_lock_wait_timeout")) {
                resultSet.next();
                previousLockWaitTimeout = resultSet.getInt(1);
            }

            try {
                try (Statement setTimeout = connection.createStatement()) {
                    setTimeout.execute(
                            "SET SESSION innodb_lock_wait_timeout = "
                                    + LOCK_WAIT_TIMEOUT_SECONDS);
                }
                try (PreparedStatement statement = connection.prepareStatement(LOCK_SQL)) {
                    statement.setQueryTimeout(LOCK_WAIT_TIMEOUT_SECONDS);
                    try (var resultSet = statement.executeQuery()) {
                        return resultSet.next() ? resultSet.getLong("id") : null;
                    }
                }
            } finally {
                try (Statement restoreTimeout = connection.createStatement()) {
                    restoreTimeout.execute(
                            "SET SESSION innodb_lock_wait_timeout = "
                                    + previousLockWaitTimeout);
                }
            }
        });

        if (!Long.valueOf(1L).equals(guardId)) {
            throw new DomainException(
                    ApiErrorCode.CORE_BUSY,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Operação temporariamente indisponível",
                    "Não foi possível iniciar a operação. Tente novamente.");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Usuario carregarUsuarioAtual(Long usuarioId) {
        Usuario usuario = entityManager.find(
                Usuario.class,
                usuarioId,
                LockModeType.PESSIMISTIC_WRITE);
        if (usuario == null) {
            throw new DomainException(
                    ApiErrorCode.AUTH_TOKEN_INVALID,
                    HttpStatus.UNAUTHORIZED,
                    "Autenticação inválida",
                    "Sua autenticação não é válida. Entre novamente.");
        }
        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new DomainException(
                    ApiErrorCode.AUTH_ACCOUNT_INACTIVE,
                    HttpStatus.UNAUTHORIZED,
                    "Conta inativa",
                    "Sua conta está inativa. Entre em contato com um administrador.");
        }
        return usuario;
    }
}
