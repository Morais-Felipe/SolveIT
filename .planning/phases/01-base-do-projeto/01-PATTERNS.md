# Phase 1: Base do Projeto - Pattern Map

**Mapped:** 2026-09-02  
**Files classified:** 27 paths/groups  
**Code analogs found:** 0 / 27  
**Documentary assignments:** 27 / 27

## Repository Reality

O repositório é greenfield. A inspeção dos arquivos rastreados não encontrou `solveit-api/`, arquivos Java, `pom.xml`, YAML Spring, repositories, migrations SQL ou testes de aplicação. Portanto, nenhum futuro arquivo desta fase deve copiar código local inexistente.

Os análogos abaixo são contratos documentais reais e rastreados. Eles definem nomes, responsabilidades, stack e processo, mas não devem ser apresentados como implementação pronta. A pesquisa da fase orienta a implementação, porém não é usada como “análogo de código”.

## File Classification

| Ticket | New/Modified File | Role | Data Flow | Closest Real Analog | Match Quality |
|---|---|---|---|---|---|
| 1 | `.gitignore` | config | file-I/O | `.gitignore:1-13` | exact existing config |
| 1 | `solveit-api/pom.xml` | config | file-I/O | `AGENTS.md:37-61`, `AGENTS.md:89` | documentary only; no code analog |
| 1 | `solveit-api/mvnw` | utility | file-I/O | `AGENTS.md:89` | documentary only; generated artifact |
| 1 | `solveit-api/mvnw.cmd` | utility | file-I/O | `AGENTS.md:89` | documentary only; generated artifact |
| 1 | `solveit-api/.mvn/wrapper/**` | config | file-I/O | `AGENTS.md:89` | documentary only; generated artifact |
| 1 | `solveit-api/src/main/java/com/solveit/SolveItApplication.java` | config | request-response startup | `SolveIT_GSD_SystemPrompt.md:127-146` | documentary only; no code analog |
| 1 | `solveit-api/src/main/resources/application.yml` | config | file-I/O | `SolveIT_GSD_SystemPrompt.md:127-146`, `01-CONTEXT.md:33` | documentary exact path |
| 1 | `solveit-api/src/main/resources/application-dev.yml` | config | file-I/O | `SolveIT_GSD_SystemPrompt.md:127-146`, `AGENTS.md:92` | documentary exact path |
| 2 | `solveit-api/src/main/java/com/solveit/enums/StatusChamado.java` | model | transform | `SolveIT_GSD_SystemPrompt.md:56-69` | exact domain contract; no code analog |
| 2 | `solveit-api/src/main/java/com/solveit/enums/Prioridade.java` | model | transform | `SolveIT_GSD_SystemPrompt.md:56-69` | exact domain contract; no code analog |
| 2 | `solveit-api/src/main/java/com/solveit/enums/PerfilUsuario.java` | model | transform | `SolveIT_GSD_SystemPrompt.md:37-46` | exact domain contract; no code analog |
| 3 | `solveit-api/src/main/java/com/solveit/entity/Usuario.java` | model | CRUD | `SolveIT_GSD_SystemPrompt.md:37-46` | exact field contract; no code analog |
| 3 | `solveit-api/src/main/java/com/solveit/entity/Categoria.java` | model | CRUD | `SolveIT_GSD_SystemPrompt.md:48-54` | exact field contract; no code analog |
| 3 | `solveit-api/src/main/java/com/solveit/entity/Chamado.java` | model | CRUD | `SolveIT_GSD_SystemPrompt.md:56-69`, `01-CONTEXT.md:25` | exact field contract plus version decision |
| 3 | `solveit-api/src/main/java/com/solveit/entity/Comentario.java` | model | append-only | `SolveIT_GSD_SystemPrompt.md:71-78`, `01-CONTEXT.md:30` | exact field/immutability contract |
| 3 | `solveit-api/src/main/java/com/solveit/entity/HistoricoStatus.java` | model | append-only | `SolveIT_GSD_SystemPrompt.md:80-89`, `01-CONTEXT.md:30` | exact field/immutability contract |
| 4 | `solveit-api/src/main/java/com/solveit/repository/UsuarioRepository.java` | repository | CRUD | `SolveIT_GSD_SystemPrompt.md:127-146`, `AGENTS.md:40` | role contract; no code analog |
| 4 | `solveit-api/src/main/java/com/solveit/repository/CategoriaRepository.java` | repository | CRUD | `SolveIT_GSD_SystemPrompt.md:127-146`, `AGENTS.md:40` | role contract; no code analog |
| 4 | `solveit-api/src/main/java/com/solveit/repository/ChamadoRepository.java` | repository | CRUD | `SolveIT_GSD_SystemPrompt.md:127-146`, `AGENTS.md:40` | role contract; no code analog |
| 4 | `solveit-api/src/main/java/com/solveit/repository/ComentarioRepository.java` | repository | append-only | `SolveIT_GSD_SystemPrompt.md:127-146`, `01-CONTEXT.md:30` | role/immutability contract; no code analog |
| 4 | `solveit-api/src/main/java/com/solveit/repository/HistoricoStatusRepository.java` | repository | append-only | `SolveIT_GSD_SystemPrompt.md:127-146`, `01-CONTEXT.md:30` | role/immutability contract; no code analog |
| 5 | `solveit-api/src/main/resources/db/migration/V1__create_usuario.sql` | migration | batch | `SolveIT_GSD_SystemPrompt.md:37-46`, `AGENTS.md:98-102` | schema contract; no SQL analog |
| 5 | `solveit-api/src/main/resources/db/migration/V2__create_categoria.sql` | migration | batch | `SolveIT_GSD_SystemPrompt.md:48-54`, `AGENTS.md:98-102` | schema contract; no SQL analog |
| 5 | `solveit-api/src/main/resources/db/migration/V3__create_chamado.sql` | migration | batch | `SolveIT_GSD_SystemPrompt.md:56-69`, `AGENTS.md:98-102` | schema contract; no SQL analog |
| 5 | `solveit-api/src/main/resources/db/migration/V4__create_comentario.sql` | migration | batch | `SolveIT_GSD_SystemPrompt.md:71-78`, `AGENTS.md:98-102` | schema contract; no SQL analog |
| 5 | `solveit-api/src/main/resources/db/migration/V5__create_historico_status.sql` | migration | batch | `SolveIT_GSD_SystemPrompt.md:80-89`, `AGENTS.md:98-102` | schema contract; no SQL analog |
| 5 | `solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java` | test | CRUD/startup | `01-CONTEXT.md:34`, `.planning/REQUIREMENTS.md:10-13`, `AGENTS.md:80` | acceptance contract; no test analog |

## Pattern Assignments

### Ticket 1 — Bootstrap

**Apply to:** `pom.xml`, Maven Wrapper, `SolveItApplication.java`, `application.yml`, `application-dev.yml`, and the scoped `.gitignore` update.

**Documentary analogs:**

- `SolveIT_GSD_SystemPrompt.md:127-146` fixes the module name, base package and resource paths.
- `01-CONTEXT.md:17-19` fixes Maven, Java 17, Spring Boot 3, BOM-managed ecosystem versions and the `solveit-api/` boundary.
- `AGENTS.md:37-61` defines the foundation dependencies; `AGENTS.md:89` requires the Maven Wrapper.
- `01-CONTEXT.md:33` and `.gitignore:4-7` establish externalized credentials and the prohibition on committed secrets.

**Pattern to implement:** keep the build minimal to this phase: Spring Web, Data JPA, Flyway core plus MySQL module, Connector/J at runtime, Boot test support and MySQL Testcontainers support. Do not add Security, JWT, Mail, Lombok, MapStruct, H2, frontend or business controllers.

**Configuration contract:** `application.yml` activates `dev`; `application-dev.yml` reads connection values from environment variables and sets Hibernate to validation, never schema creation. Safe local defaults may be versioned; real credentials may not.

**No code to copy:** generate the Spring Boot entry point and Maven Wrapper from standard tooling, then verify the generated result rather than hand-copying an unrelated project.

### Ticket 2 — Enums

**Documentary analog:** `SolveIT_GSD_SystemPrompt.md:37-69`.

```text
PerfilUsuario: SOLICITANTE | ATENDENTE | ADMIN
StatusChamado: ABERTO | EM_ANDAMENTO | RESOLVIDO | FECHADO | CANCELADO
Prioridade: BAIXA | MEDIA | ALTA | CRITICA
```

Each enum is a small standalone type in `com.solveit.enums`. Persist it textually when entities arrive; never depend on ordinal position (`01-CONTEXT.md:24`). There is no local Java enum analog.

### Ticket 3 — Entities

**Documentary analog:** the five field blocks in `SolveIT_GSD_SystemPrompt.md:37-89`, constrained by `01-CONTEXT.md:22-30`.

**Shared entity pattern:**

- explicit `@Entity`/`@Table`, `Long` generated identifiers and explicit column names/nullability/lengths;
- `@Enumerated(EnumType.STRING)` for all enums;
- child-to-parent `@ManyToOne(fetch = LAZY)` relationships, with `Chamado.atendente` as the only nullable relationship in the locked model;
- application/JPA-managed creation/update timestamps, mirrored by database `NOT NULL` constraints where mandatory;
- `@Version` on `Chamado` for optimistic locking;
- no REST DTO behavior, bidirectional collection convenience, delete cascade or orphan removal;
- `Comentario` and `HistoricoStatus` remain append-only by design.

There is no local JPA entity analog. The planner must make the physical names and lengths explicit once in Ticket 3 so Ticket 5 can mirror them exactly.

### Ticket 4 — Repositories

**Documentary analogs:** `SolveIT_GSD_SystemPrompt.md:127-146` assigns the package; `AGENTS.md:40` selects Spring Data JPA.

Create five interfaces extending `JpaRepository<Entity, Long>`. Keep the surface minimal: identity/uniqueness lookups needed by the next phases are acceptable, but ticket filtering, Specifications, projections and dashboard aggregates belong to the phase that consumes them. Do not create a generic base repository or custom delete/update methods for append-only records.

There is no local repository analog and no implementation class should be invented.

### Ticket 5 — Flyway and Integrated Proof

**Documentary analogs:** `AGENTS.md:98-102`, `01-CONTEXT.md:22-25`, `.planning/REQUIREMENTS.md:10-13`.

Apply one immutable migration per table in dependency order: `usuario`, `categoria`, `chamado`, `comentario`, `historico_status`. SQL must mirror the Ticket 3 contract and include PKs, required FKs, uniqueness, nullability, enum text checks and indexes that support the planned relationship/filter paths. Use restrictive delete behavior for audit data; do not use `ON DELETE CASCADE` for comments or status history.

The integration test starts a fresh MySQL 8.4 container, lets Flyway apply V1–V5, lets Hibernate validate with `ddl-auto=validate`, then persists, flushes, clears and reloads a minimal domain graph. It must also prove textual enums, uniqueness/nullability/FKs, optimistic versioning and the absence of H2. Docker unavailable is a blocking preflight, not a silently skipped success.

There is no local SQL migration or Testcontainers test analog.

## Shared Patterns

### Flyway owns the schema

**Source:** `AGENTS.md:98-102`  
**Apply to:** YAML, all entities, all migrations, integration test.

Flyway creates and evolves the schema; JPA annotations mirror the same contract; Hibernate only validates. Never introduce `ddl-auto=update`, `create`, `create-drop`, `schema.sql` or `data.sql` as a workaround.

### Audit preservation

**Source:** `01-CONTEXT.md:30` and `SolveIT_GSD_SystemPrompt.md:93-104`  
**Apply to:** `Comentario`, `HistoricoStatus`, their repositories and V4/V5.

Comments and history are append-only. Avoid repository methods, cascades and foreign-key actions that create an update/delete path for them.

### Minimal phase boundary

**Source:** `01-CONTEXT.md:7-9`, `01-CONTEXT.md:86-88`  
**Apply to:** all five tickets.

Do not create frontend, authentication/security/JWT, mail, DTOs, exception handlers, services, controllers or business endpoints in Phase 1. Empty placeholder packages are not deliverables.

### Sequential ticket workflow

**Source:** `docs/GIT-WORKFLOW.md:9-35` and `SolveIT_GSD_SystemPrompt.md:213-220`  
**Apply to:** planning and execution.

Produce one plan per ticket, in the fixed order 1→5. Each ticket uses its own `ticket/NN-descricao-curta` branch and reaches an approval checkpoint before any code for that ticket and again before advancing. Ticket 3 and Ticket 4 use compilation-focused gates because the complete Flyway schema appears only in Ticket 5; do not weaken `ddl-auto=validate` to make the intermediate state start.

## No Code Analog Found

| File group | Reason | Planner fallback |
|---|---|---|
| Bootstrap/build files | No Spring Boot module exists | Follow tracked stack/path decisions and standard generators |
| Three enum files | No Java source exists | Copy only the locked constants from the domain document |
| Five entity files | No JPA model exists | Derive mapping from the tracked field/relationship contract |
| Five repository files | No Spring Data interface exists | Use the minimal standard `JpaRepository` boundary |
| Five Flyway migrations | No SQL exists | Mirror the approved Ticket 3 physical contract exactly |
| Integration test | No test source exists | Implement the MySQL/Testcontainers acceptance contract |

## Planner Guardrails

- Exactly five sequential plans/tickets: bootstrap, enums, entities, repositories, Flyway plus integrated test.
- Close physical table/column names, string lengths, collation and MySQL 8.4 image tag before SQL is written; do not let entity and migration plans choose independently.
- A local `compose.yaml` is not part of the canonical file list. Add it only if Ticket 1 explicitly chooses Docker Compose as the approved development-MySQL mechanism.
- Generated wrapper files must remain generated; do not hand-author binary/bootstrap internals.
- The repository currently has unrelated planning work in progress. Preserve it and never revert it.

## Metadata

**Analog search scope:** all tracked files under `C:/projetos`; application-code candidates under `solveit-api/`  
**Tracked-source gate:** all paths named as analogs above are git-tracked; ignored runtime/plugin mirrors were excluded  
**Application source found:** none  
**Pattern extraction date:** 2026-09-02
