---
phase: 03-core-de-chamados
reviewed: 2026-09-30T11:05:25Z
depth: standard
files_reviewed: 8
files_reviewed_list:
  - solveit-api/src/main/resources/db/migration/V7__version_categoria.sql
  - solveit-api/src/main/java/com/solveit/entity/Categoria.java
  - solveit-api/src/main/java/com/solveit/dto/categoria/CategoriaDtos.java
  - solveit-api/src/main/java/com/solveit/service/CategoriaService.java
  - solveit-api/src/main/java/com/solveit/controller/CategoriaController.java
  - solveit-api/src/test/java/com/solveit/controller/CategoriaEdicaoIntegrationTest.java
  - solveit-api/src/test/java/com/solveit/controller/CategoriaIntegrationTest.java
  - solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java
findings:
  critical: 1
  warning: 1
  info: 0
  total: 2
status: issues_found
---

# Phase 03: Code Review Report

**Reviewed:** 2026-09-30T11:05:25Z
**Depth:** standard
**Files Reviewed:** 8
**Status:** issues_found

## Summary

The category edit/activation flow correctly applies the global write guard, reloads the current actor, locks the category, compares the expected version, and preserves existing ticket relationships. The migration is additive and the persistence tests cover the V6-to-V7 backfill. However, the new direct JPA row-lock path has an unmapped timeout/deadlock failure mode, and the activation DTO exposes corrupted Portuguese validation messages that its integration test currently accepts.

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: Direct category lock failures bypass the contractual 503 mapping

**File:** `C:/SolveIT/solveit-api/src/main/java/com/solveit/service/CategoriaService.java:150`

**Issue:** `entityManager.refresh(categoria, LockModeType.PESSIMISTIC_WRITE)` invokes the shared `EntityManager` directly. If this lock times out or is selected as a deadlock victim, JPA may throw `jakarta.persistence.LockTimeoutException` or `jakarta.persistence.PessimisticLockException`. Spring's shared-entity-manager proxy rethrows the target exception; it does not apply the persistence-exception translation that repository proxies provide. The global handler currently maps only Spring's `PessimisticLockingFailureException`, `QueryTimeoutException`, and `TransactionTimedOutException`, so this new failure path can return an uncontrolled `500` instead of the plan's required `503` with `CORE_BUSY`. Existing concurrency coverage exercises the JDBC guard timeout, not contention on this direct category row lock.

**Fix:** Map both JPA lock exceptions to the existing controlled busy response (or catch and translate them at the service boundary), and add an HTTP integration test that holds the category row lock from a separate transaction and verifies rollback plus `503 CORE_BUSY` for `PUT`/`PATCH`.

```java
@ExceptionHandler({
        jakarta.persistence.LockTimeoutException.class,
        jakarta.persistence.PessimisticLockException.class,
        PessimisticLockingFailureException.class,
        QueryTimeoutException.class,
        TransactionTimedOutException.class
})
public ResponseEntity<ProblemDetail> handleCoreBusy(HttpServletRequest request) {
    // existing CORE_BUSY response
}
```

## Warnings

### WR-01: Activation validation messages contain literal mojibake

**File:** `C:/SolveIT/solveit-api/src/main/java/com/solveit/dto/categoria/CategoriaDtos.java:40-43`

**Issue:** The three messages in `AlterarAtivo` contain literal corrupted sequences (`Ã©`, `Ã£`) instead of Portuguese characters. Clients therefore receive unreadable validation feedback. `CategoriaEdicaoIntegrationTest.java:360` asserts the corrupted value, so the current test locks in the defect instead of detecting it.

**Fix:** Replace the literals with correctly encoded UTF-8 text and update the integration assertion.

```java
public record AlterarAtivo(
        @NotNull(message = "O estado ativo é obrigatório")
        Boolean ativo,
        @NotNull(message = "A versão esperada é obrigatória")
        @PositiveOrZero(message = "A versão esperada não pode ser negativa")
        Long versaoEsperada) {
}
```

---

_Reviewed: 2026-09-30T11:05:25Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
