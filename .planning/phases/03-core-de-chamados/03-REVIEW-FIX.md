---
phase: 03-core-de-chamados
fixed_at: 2026-09-30T11:35:19.0556238Z
review_path: .planning/phases/03-core-de-chamados/03-REVIEW.md
iteration: 1
findings_in_scope: 2
fixed: 2
skipped: 0
status: all_fixed
---

# Phase 03: Code Review Fix Report

**Fixed at:** 2026-09-30T11:35:19.0556238Z
**Source review:** `.planning/phases/03-core-de-chamados/03-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 2
- Fixed: 2
- Skipped: 0

## Fixed Issues

### CR-01: Direct category lock failures bypass the contractual 503 mapping

**Files modified:** `solveit-api/src/main/java/com/solveit/exception/GlobalExceptionHandler.java`, `solveit-api/src/test/java/com/solveit/controller/CategoriaEdicaoIntegrationTest.java`
**Commit:** `e1307a4`
**Applied fix:** Mapped direct JPA `LockTimeoutException` and `PessimisticLockException` failures to the existing `503 CORE_BUSY` handler. Added an HTTP/MySQL regression that holds the category row lock in a separate transaction, exercises both `PUT` and `PATCH`, rejects exception/SQL leakage, and proves that name, description, active state, and version remain unchanged.
**Fix status:** fixed; requires human verification because this changes exceptional transaction behavior.

### WR-01: Activation validation messages contain literal mojibake

**Files modified:** `solveit-api/src/main/java/com/solveit/dto/categoria/CategoriaDtos.java`, `solveit-api/src/test/java/com/solveit/controller/CategoriaEdicaoIntegrationTest.java`
**Commit:** `51b8c5f`
**Applied fix:** Replaced the three corrupted `AlterarAtivo` validation literals with correct UTF-8 Portuguese and updated the HTTP integration assertion.

## Verification

Verification ran in the isolated worktree `C:/SolveIT/.claude/worktrees/rf-03-20260930-1` against Testcontainers MySQL 8.4.11.

- `CategoriaEdicaoIntegrationTest`: 11 tests, 0 failures, 0 errors, 0 skipped after CR-01.
- `CategoriaEdicaoIntegrationTest#rejectsMissingExpectedVersionForActivationChange`: 1 test, 0 failures, 0 errors, 0 skipped after WR-01.
- Final combined `CategoriaEdicaoIntegrationTest`: 11 tests, 0 failures, 0 errors, 0 skipped.
- The orchestrator independently re-ran the final `CategoriaEdicaoIntegrationTest` from the main workspace: 11 tests, 0 failures, 0 errors, 0 skipped, `BUILD SUCCESS`.
- The first test attempt did not execute because Docker was stopped; after Docker Desktop started, all listed verification runs passed.
- Flyway emitted its known warning that MySQL 8.4 is newer than its declared tested range; all seven migrations validated and applied successfully.

---

_Fixed: 2026-09-30T11:35:19.0556238Z_
_Fixer: the agent (gsd-code-fixer)_
_Iteration: 1_
