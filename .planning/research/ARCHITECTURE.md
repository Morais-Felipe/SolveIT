# Architecture Research

**Domain:** Web IT help-desk and ticket management system
**Project:** SolveIT
**Researched:** 2026-09-02
**Confidence:** MEDIUM-HIGH (official framework documentation plus locked project constraints)

## Recommended Architecture

Build SolveIT as a **layered modular monolith**: one Spring Boot deployable, one MySQL database, and one React single-page application. This is the simplest architecture that preserves the domain's important boundaries, supports transactional invariants, and remains understandable for a Build-to-Learn project. Do not introduce microservices, a message broker, CQRS, or a generic ACL subsystem in the first release.

### System Overview

```text
┌──────────────────────────────────────────────────────────────┐
│ React SPA                                                    │
│ routes/pages → feature components → API modules → Axios      │
│ AuthContext owns identity/token; URL owns filters/pagination │
└─────────────────────────────┬────────────────────────────────┘
                              │ HTTPS / JSON / Bearer JWT
┌─────────────────────────────▼────────────────────────────────┐
│ Spring Security filter chain                                │
│ JWT authentication → request-level role gate                │
├──────────────────────────────────────────────────────────────┤
│ controller → DTO validation/mapping → service               │
│                 service owns authorization + use cases       │
├──────────────────────────────────────────────────────────────┤
│ entity/enums ← repository (JPA Specifications/projections)   │
│ transactional ticket writes + immutable audit records        │
├───────────────────────┬──────────────────────────────────────┤
│ MySQL 8 / Flyway      │ after-commit domain events           │
└───────────────────────┴──────────────────┬───────────────────┘
                                           ▼
                              async EmailService → SMTP
```

The package layout remains the locked horizontal structure (`config`, `controller`, `dto`, `entity`, `enums`, `exception`, `repository`, `security`, `service`). Domain cohesion is preserved through naming inside those packages: `ChamadoController`, `ChamadoService`, `ChamadoRepository`, and ticket-specific DTOs form a vertical use-case path without changing the prescribed folders.

### Component Responsibilities

| Component | Responsibility | Key boundary |
|-----------|----------------|--------------|
| React pages/routes | Compose role-specific screens, read route params/query filters, render loading/error states | Never decide server authorization |
| React API modules | Axios calls, DTO types, token header, uniform error normalization | No UI state or business rules |
| Security filter chain | Validate JWT, load active user, establish `Authentication`, reject unauthenticated requests | Authentication, not ticket ownership |
| Controllers | HTTP mapping, bean validation, status codes, request/response DTO conversion | Thin; no repositories or transition logic |
| Services | Use-case orchestration, RBAC and ownership checks, transition rules, transaction boundaries, event publication | Authoritative business boundary |
| Repositories | Persistence, filtered/paged reads, locks where required, dashboard projections | No authorization policy |
| Entities/enums | Relational state and small invariant helpers such as allowed transition checks | Never serialize directly to the API |
| MySQL/Flyway | Durable state, foreign keys, uniqueness, indexes, reproducible schema evolution | Schema changes only through migrations |
| Email event listener | Convert committed ticket events into email requests outside the HTTP transaction | Email failure cannot roll back committed ticket state |

## Recommended Project Structure

```text
solveit-api/
└── src/main/java/com/solveit/
    ├── config/         SecurityConfig, MailConfig, AsyncConfig, CorsConfig
    ├── controller/     AuthController, ChamadoController, DashboardController
    ├── dto/            request/response records grouped by entity/use case
    ├── entity/         Usuario, Categoria, Chamado, Comentario, HistoricoStatus
    ├── enums/          PerfilUsuario, Prioridade, StatusChamado
    ├── exception/      domain exceptions + GlobalExceptionHandler
    ├── repository/     JpaRepository, Specification helpers, projections
    ├── security/       JwtFilter, JwtUtil, authenticated principal loader
    └── service/        use cases, authorization helpers, EmailService/listeners

solveit-web/
└── src/
    ├── api/            axiosClient, authApi, chamadosApi, adminApi, dashboardApi
    ├── auth/           AuthContext/AuthProvider, route guards, useAuth
    ├── components/     shared presentational controls and layout
    ├── features/       chamados, comentarios, dashboard, usuarios, categorias
    ├── pages/          route-level composition only
    ├── routes/         router and role-aware route definitions
    └── types/          API DTO contracts and enums
```

### Structure Rationale

- Keep the prescribed backend packages because they expose the basic Spring layers clearly. Prevent a “god service” by splitting services by use case when a domain grows (`ChamadoQueryService`, `ChamadoCommandService`, `ChamadoAuthorizationService`) while keeping them under `service/`.
- Group frontend code by feature after the shared infrastructure boundary. This keeps ticket UI, hooks, and view-specific helpers together without coupling them to Axios configuration.
- Request and response DTOs are separate from JPA entities. This prevents lazy-loading leaks, recursive JSON graphs, accidental password exposure, and API contracts changing whenever persistence mappings change.

## Architectural Patterns

### 1. Transactional Service Use Cases

Every command that changes a ticket is one public service operation. For assignment, the same `@Transactional` method validates the actor and target attendant, loads the ticket, changes attendant and status, and inserts `HistoricoStatus`. Any exception rolls the whole use case back.

```java
@Transactional
public ChamadoResponse atribuir(Long chamadoId, Long atendenteId, UsuarioPrincipal actor) {
    Chamado chamado = chamadoRepository.findForUpdateById(chamadoId)
        .orElseThrow(() -> new ResourceNotFoundException("Chamado"));
    authorization.assertPodeAtribuir(actor, chamado, atendenteId);
    chamado.atribuir(atendenteId); // ABERTO -> EM_ANDAMENTO
    historicoRepository.save(HistoricoStatus.daMudanca(chamado, actor.id()));
    events.publishEvent(ChamadoAtribuido.from(chamado));
    return mapper.toResponse(chamado);
}
```

The example uses a write lock conceptually. Implement either pessimistic locking on the ticket lookup or an optimistic `@Version` column before exposing concurrent assignment/status actions. Without concurrency control, two valid requests can both inspect stale state and produce a lost update or contradictory history. For this small command-oriented domain, optimistic locking is the normal default; a short pessimistic lock is reasonable for assignment if contention testing shows repeated conflicts.

### 2. Explicit State Machine as Domain Policy

Represent allowed edges explicitly rather than accepting an arbitrary target enum. The service evaluates both transition and actor relationship:

```text
ABERTO --assign--> EM_ANDAMENTO --resolve--> RESOLVIDO --close--> FECHADO
   └--owner cancel--> CANCELADO       EM_ANDAMENTO --admin cancel--> CANCELADO
```

Use named command methods (`atribuir`, `resolver`, `fechar`, `cancelar`) internally even if the public API retains a generic `PATCH /status`. This makes actor rules and timestamp side effects visible and testable. `resolvidoEm` is set only on entry to `RESOLVIDO`; terminal states have no outgoing transitions.

### 3. Defense-in-Depth Authorization

Use three checks, each for a different concern:

1. The security configuration requires authentication and provides coarse route gates.
2. Method/service authorization restricts roles (`SOLICITANTE`, `ATENDENTE`, `ADMIN`).
3. The service loads the ticket and verifies the relationship: requester owns it, attendant is assigned to it, or actor is admin.

Never accept `solicitanteId`, author identity, or “isAdmin” from a request body. Derive the actor from Spring Security's authenticated principal. List queries must be scoped in the database (for example, requester sees `solicitante_id = actor.id`) rather than fetching all tickets and filtering in Java.

### 4. After-Commit Asynchronous Notifications

The command service publishes a small immutable event containing IDs and stable display data. An `@TransactionalEventListener(phase = AFTER_COMMIT)` listener submits email work through a bounded task executor. This avoids sending email for a transaction that later rolls back and keeps SMTP latency outside the API response.

For the educational MVP, in-process events are sufficient, but they are **best effort**: a process crash after commit and before send can lose mail. Record failures with event type/ticket ID and retry a bounded number of times. If reliable delivery becomes a requirement, add a transactional outbox table and worker in a later phase; do not add a broker first.

### 5. Query/Command Separation Without CQRS Infrastructure

Keep mutations as entity-loading service commands. Optimize read endpoints independently with DTO projections, `Pageable`, and JPA `Specification` predicates for optional ticket filters. Dashboard repositories should return aggregate projections directly from SQL/JPA queries, not load ticket collections and count in Java.

## Data Flow

### Authenticated Request

```text
React action
  → feature API module
  → Axios client adds Bearer token
  → JwtFilter validates token and active user
  → controller validates request DTO
  → service authorizes role + ownership
  → repository query/write
  → service maps response DTO
  → controller returns JSON/error contract
```

### Status Update and History

```text
PATCH status
  → load ticket with concurrency protection
  → verify actor relationship and allowed edge
  → mutate status/timestamps
  → INSERT HistoricoStatus
  → publish notification event
  → COMMIT ticket + history atomically
  → AFTER_COMMIT listener queues email
```

The transaction includes only database work. SMTP must never run inside it.

### React State Ownership

| State | Owner |
|-------|-------|
| Authenticated user/token/session bootstrap | `AuthProvider`/auth module |
| Current route and ticket filters/page | React Router URL/search params |
| Form inputs and modal state | Local component/feature state |
| Ticket lists/details returned by API | Page/feature query hook; refresh after mutation |
| Permissions shown in UI | Derived from `/auth/me` plus ticket DTO; convenience only |

Avoid a global reducer for all server data. Context is appropriate for authentication; route-level hooks are adequate for the initial app. Centralize loading cancellation/stale-response protection in hooks or route loaders rather than duplicating raw `useEffect` fetching in every component.

## Integration Points

| Boundary | Communication | Design rule |
|----------|---------------|-------------|
| React ↔ Spring API | Versionable JSON DTOs over HTTPS | Stable error envelope; pagination metadata; CORS restricted by environment |
| Security ↔ services | Authenticated principal/ID passed into use case | Domain service rechecks current active user and ownership |
| Services ↔ repositories | Direct interfaces within one process | Transactions begin in services, not controllers |
| Ticket service ↔ history | Direct repository write in same transaction | No status change can bypass history creation |
| Ticket/comment services ↔ email | Application event after database commit | Bounded executor, logs/metrics, retry policy |
| App ↔ MySQL | JPA plus Flyway-managed schema | `ddl-auto=validate`; migrations own DDL |

## Database Architecture Notes

- Add indexes aligned with access paths: `usuario(email)` unique; `categoria(nome)` unique; tickets on `(solicitante_id, criado_em)`, `(atendente_id, status)`, `(status, prioridade)`, and `categoria_id`; comments/history on `(chamado_id, criado_em/alterado_em)`.
- Foreign keys protect references. Deactivation is preferred to deleting users/categories referenced by audit records.
- Enums may be stored as readable strings, with application validation and migration-reviewed changes. Never persist Java enum ordinals.
- Comments and history expose insert/read operations only; omit update/delete routes and services. Database permissions can be tightened later, but the application boundary is required now.
- Ticket list queries are always paged and have a stable secondary sort (`criadoEm DESC, id DESC`) to avoid page drift.

## Scaling Considerations

| Scale | Architecture adjustment |
|-------|-------------------------|
| 0–1k users | Single Spring instance, MySQL, in-process after-commit email executor; observe slow queries before tuning |
| 1k–100k users | Multiple stateless API instances, pooled DB connections, composite indexes verified by query plans, durable outbox email worker, cache only stable reference data |
| 100k+ users | Read replicas/analytics store for dashboards, partition/retention strategy for audit volume, broker only if measured async workload justifies it; split services by operational pressure, not entity names |

Likely first bottlenecks are unpaged/filter-heavy ticket queries and SMTP throughput, not Java service CPU. Dashboard aggregation is the next candidate; optimize projections/indexes before introducing a separate analytics system.

## Anti-Patterns to Avoid

### Business Rules in Controllers

Controllers can be bypassed by another caller and are difficult to test transactionally. Put transition, ownership, history, and event rules in services; controllers only adapt HTTP.

### UI-Only Authorization or Post-Query Filtering

Hiding a button does not protect an endpoint, and filtering an unrestricted result after loading can leak counts/data and waste memory. Enforce object access in services and repository predicates.

### Status Write Followed by Independent History Write

Two repository operations without one transaction permit a changed status with no audit record. Make both part of the same service transaction and test rollback behavior.

### Sending Email Before Commit

SMTP can be slow or unavailable, extends lock duration, and can notify users about rolled-back changes. Publish a transaction-bound event and send only after commit.

### Returning JPA Entities to React

This couples HTTP to persistence and risks lazy-loading/recursive serialization and sensitive fields. Map to purpose-specific DTOs.

### Premature Distributed Architecture

Microservices add network failure, distributed transactions, duplicated authorization, and deployment overhead without solving an MVP constraint. Preserve clean service/repository/event seams inside the monolith so extraction remains possible if evidence later supports it.

## Sensible Build Order

1. **Schema and domain foundation:** enums, Flyway tables/constraints/indexes, entities and repositories. Add the concurrency field/strategy now because retrofitting it after workflows exist is riskier; introduce DTOs and the HTTP error envelope with the first endpoint-bearing phases.
2. **Authentication and authorization skeleton:** JWT, BCrypt, active-user loading, security configuration, authenticated principal, service authorization helpers, and endpoint-security tests.
3. **Reference administration:** categories and users. These provide valid actors/categories for ticket workflows.
4. **Vertical ticket core:** open/list/detail first, then edit; all responses use DTOs and all lists are scoped/paged.
5. **Transactional workflow:** assignment, status transitions, immutable history, concurrency tests, rollback tests. This is the highest-risk phase and should be proven before email or dashboards.
6. **Comments:** immutable insert/read with participant authorization, reusing the same access policy.
7. **Email boundary:** domain events, after-commit listener, bounded async executor, SMTP adapter, failure observability; decide whether best-effort is acceptable before launch.
8. **Dashboard queries:** aggregate projections after real status/ownership semantics stabilize.
9. **React application:** establish API/auth/router shell, then deliver vertical screens in backend capability order. Backend can be exercised first with integration tests/API client, reducing simultaneous learning risk.
10. **Hardening:** full endpoint authorization matrix, transaction/concurrency integration tests, query-plan review, responsive UX, and end-to-end flows per role.

## Sources

- [Spring Framework: Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html) — declarative transaction boundary (official, MEDIUM confidence after provider classification)
- [Spring Framework: Transaction-bound Events](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/event.html) — `AFTER_COMMIT` listener semantics (official, MEDIUM)
- [Spring Framework: ApplicationContext Events](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html) — synchronous default and `@Async` option (official, MEDIUM)
- [Spring Security: Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html) — service/method authorization (official, MEDIUM)
- [Spring Security: Authorization Architecture](https://docs.spring.io/spring-security/reference/servlet/authorization/architecture.html) — request/method authorization and domain-aware decisions (official, MEDIUM)
- [Spring Data JPA: Specifications](https://docs.spring.io/spring-data/jpa/reference/jpa/specifications.html) — composable filters, paging, projections (official, MEDIUM)
- [Spring Data JPA: Query Methods](https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html) — paging and sorting (official, MEDIUM)
- [Spring Data JPA: Projections](https://docs.spring.io/spring-data/jpa/reference/repositories/projections.html) — DTO/read-model optimization (official, MEDIUM)
- [React: Scaling Up with Reducer and Context](https://react.dev/learn/scaling-up-with-reducer-and-context) — focused shared client state (official, MEDIUM)
- [React: You Might Not Need an Effect](https://react.dev/learn/you-might-not-need-an-effect) — fetch race and state-ownership guidance (official, MEDIUM)

---
*Architecture research for SolveIT; optimized for correctness, pedagogy, and incremental delivery rather than speculative scale.*
