# Project Research Summary

**Project:** SolveIT
**Domain:** Web IT help-desk / ticket management system
**Researched:** 2026-09-02
**Confidence:** MEDIUM-HIGH

## Executive Summary

SolveIT is a greenfield, Build-to-Learn help-desk application whose value is not feature breadth but a correct, understandable ticket lifecycle. Experts would build this scope as a layered modular monolith: one Spring Boot REST API, one MySQL database managed exclusively by Flyway, and one React SPA. The architecture should keep controllers thin, make services authoritative for role- and ticket-level authorization, and model assignment and status changes as explicit transactional commands rather than generic CRUD.

The recommended delivery strategy is dependency-first but outcome-oriented. Establish a reproducible schema and DTO boundaries, secure identity and administration, then prove the complete `ABERTO -> EM_ANDAMENTO -> RESOLVIDO -> FECHADO` path with atomic history before adding SMTP, dashboards, or the full UI. Preserve the locked choices from `PROJECT.md`: Java 17, Spring Boot 3, MySQL 8, Flyway, JWT/BCrypt, JavaMailSender/SMTP, React/Tailwind/React Router/Axios, the prescribed backend packages, immutable comments, and server-side contextual authorization.

The dominant risks are object-level authorization leaks, invalid or concurrent state transitions, divergence between ticket state and audit history, and email work coupled to database transactions. Prevent them with service-layer access policies, an explicit transition table plus optimistic locking, one transaction for mutation and history, DTO-only APIs, MySQL-backed integration tests, and after-commit notification dispatch. Keep the milestone intentionally small: no microservices, broker, configurable workflow, SLA engine, omnichannel intake, CMDB, attachments, or AI until validated demand exists.

## Key Findings

### Recommended Stack

Use the latest compatible patches within the locked major lines and let the Spring Boot BOM manage its ecosystem. Build a synchronous Spring MVC/JPA modular monolith and a separate client-rendered React SPA. Test pure rules quickly, but use the real Spring Security chain and MySQL Testcontainers for boundaries that mocks or H2 cannot prove. Full detail is in [STACK.md](./STACK.md).

**Core technologies:**

- Java 17 + Spring Boot 3.5.16: REST runtime and dependency platform — satisfies the project lock while staying on the maintained Boot 3 line.
- Spring MVC, Spring Data JPA, Spring Security: HTTP, persistence, and authorization — match transactional CRUD without a reactive programming model.
- MySQL 8.4 LTS + Flyway 11.7.2: durable relational state and reproducible schema — Flyway is the sole schema owner; Hibernate uses `ddl-auto=validate`.
- JJWT 0.13.0 + BCrypt: self-issued short-lived JWTs and password hashing — keep parsing/issuance isolated and authorization in Spring Security/services.
- JavaMailSender: required SMTP adapter — place it behind an after-commit notification boundary with finite timeouts.
- React 19.2 + Vite 8 + Tailwind CSS 4: responsive SPA — use Node 22 LTS, React Router declarative mode, and one configured Axios client.
- JUnit/MockMvc/Testcontainers and Vitest/Testing Library: rule and boundary verification — defer Playwright until the vertical workflow exists.

**Critical version requirements:** Stay on Spring Boot 3.x and Java 17; use MySQL 8.4 LTS rather than starting on 8.0; include both `flyway-core` and `flyway-mysql`; align React/React DOM and Tailwind/plugin versions; Vite 8 requires Node 20.19+ or 22.12+.

### Expected Features

SolveIT v1 must deliver the defined ticket system, not a generalized ITSM suite. Correct contextual access, guided transitions, and audit-by-construction are the differentiators. Full prioritization is in [FEATURES.md](./FEATURES.md).

**Must have (table stakes):**

- Registration, login, `/me`, JWT/BCrypt, and three fixed profiles; public registration can create only `SOLICITANTE`.
- Admin-managed users and categories with activation/deactivation rather than destructive deletion.
- Structured ticket creation, role-scoped paged queues, allowed filters, deterministic sorting, and complete detail.
- Assignment, controlled status/cancellation workflow, and editing only while `ABERTO` by the owner or admin.
- Immutable chronological comments and an atomic `HistoricoStatus` record for every status change.
- Email for the five defined event classes, including urgent handling of new `CRITICA` tickets.
- Lean operational summaries/groupings for admins and attendants.
- Responsive role-focused React UI with loading, empty, success, and actionable error states.
- Critical rule, authorization, migration, transaction, and endpoint-security tests.

**Should have (competitive within this project):**

- One central transition/actor policy reflected consistently in API tests and UI actions.
- Audit-by-construction: ticket state and history cannot diverge; comments remain append-only.
- Focused experiences for requester, attendant, and administrator without separate applications.
- Explicit criticality alerts and an incremental implementation that teaches REST, Spring, transactions, JPA, and relational design.

**Defer (v2+ or explicit post-validation scope):**

- Password recovery, notification delivery/preferences, exports, and cross-ticket references belong in v1.x only after evidence and a scope decision.
- SLA calendars/escalation, attachments, knowledge base, omnichannel intake, generic automation, CMDB/ITSM expansion, and AI belong in v2+.
- Reopening terminal tickets, editing/deleting comments, public privileged registration, configurable workflows, and native mobile are explicit anti-features for v1.

### Architecture Approach

Use a layered modular monolith that retains the locked horizontal backend packages (`config`, `controller`, `dto`, `entity`, `enums`, `exception`, `repository`, `security`, `service`) while keeping vertical cohesion through domain-specific names. Services own use cases, authorization, transitions, transactions, and event publication; repositories own persistence and scoped queries; controllers only adapt HTTP; entities never cross the REST boundary. The React SPA groups feature code behind shared auth, routing, and API modules. Full detail is in [ARCHITECTURE.md](./ARCHITECTURE.md).

**Major components:**

1. React routes/pages/features — compose role-specific journeys; URL owns queue filters and pagination.
2. Axios API/auth modules — stable DTO exchange, fixed base URL, token handling, cancellation, and error normalization.
3. Spring Security chain — authenticate strict JWTs and load the current active user; coarse route gates only.
4. Controllers and DTOs — validate/map HTTP inputs and outputs without persistence or business logic leakage.
5. Transactional services — enforce role plus ownership/assignment, named state transitions, concurrency control, audit writes, and events.
6. Repositories/entities/MySQL/Flyway — perform scoped paged reads, aggregate projections, constrained relational writes, and immutable migrations.
7. After-commit email listener — deliver bounded best-effort SMTP work without affecting the committed ticket transaction.

**Key patterns:** explicit state machine; defense-in-depth authorization; command/query separation without CQRS infrastructure; optimistic locking by default; atomic ticket/history transactions; DTO projections and database-side filters; after-commit domain events; append-only comments/history.

### Critical Pitfalls

Full prevention and recovery guidance is in [PITFALLS.md](./PITFALLS.md).

1. **Role-only authorization causes IDOR** — enforce an operation-specific service policy for role and ticket relationship, scope list queries in SQL, and test every wrong-owner/wrong-assignee combination.
2. **Generic status updates bypass workflow** — expose named domain commands, use a single transition table, derive actors from authentication, and reject stale concurrent writes with `@Version` or a justified lock.
3. **Ticket and audit history diverge** — mutate status/timestamps and insert exactly one history row inside one transaction; force persistence failures in rollback tests.
4. **SMTP corrupts transaction semantics** — publish only committed domain events, send outside the transaction, bound timeouts/retries, and decide explicitly whether best-effort loss is acceptable or an outbox is required.
5. **JWT identity or API models become unsafe** — validate signature/algorithm/expiry, externalize secrets, define deactivation and browser transport semantics, hard-code public roles, and expose DTOs rather than JPA entities.

## Implications for Roadmap

Based on the combined research, use seven phases. Each phase should remain explanatory and demonstrable, preserving the project rule to plan before implementation and work incrementally.

### Phase 1: Data and API Foundation

**Rationale:** Every feature depends on relational invariants, migrations, DTO boundaries, errors, and concurrency metadata. Retrofitting these after endpoints exist is expensive and risky.
**Delivers:** Spring Boot skeleton, MySQL 8.4/Flyway setup, enums/entities/repositories, numbered schema with constraints/indexes, DTO/error envelope, clean-schema integration test, and chosen optimistic-lock field.
**Addresses:** Core data model and reproducible persistence foundation.
**Avoids:** Flyway/Hibernate drift, entity serialization, enum ordinals, missing constraints, and late concurrency retrofits.

### Phase 2: Identity, Authorization, and Administration

**Rationale:** Domain operations cannot be safely exposed until the server has a trusted actor and reusable role/object authorization seams; users and categories supply valid workflow references.
**Delivers:** Registration/login/me, BCrypt, strict JWT filter and current-active-user loading, route/method gates, authorization helpers, admin user/category CRUD plus activation, and negative security tests.
**Addresses:** Secure identity, fixed profiles, user/category administration, and authenticated category reads.
**Avoids:** Privilege escalation, stale disabled-user access, client-supplied actors/roles, secret leakage, and UI-only authorization.

### Phase 3: Ticket Lifecycle and Collaboration

**Rationale:** This is the core value and highest-risk domain work. Deliver it as a vertical path before integrations: create, list, view, assign, resolve, close, with audit integrity; then add editing, cancellation, and comments using the same policies.
**Delivers:** Role-scoped paged/filterable queues, ticket detail/edit, explicit assignment and transition commands, cancellations, immutable comments, atomic history, stable sorting, optimistic concurrency, and rollback/authorization/query tests.
**Addresses:** Opening, querying, filtering, assignment, workflow, comments, history, and complete ticket detail.
**Avoids:** IDOR, invalid transitions, mass assignment, lost updates, audit divergence, unbounded/in-memory filtering, N+1 responses, and unstable pages.

### Phase 4: Notification Delivery

**Rationale:** Email must react to already-correct committed domain events; building it earlier couples uncertain rules to an unreliable external boundary.
**Delivers:** Five defined email event flows, urgent critical-ticket notification, after-commit listener, bounded executor, SMTP adapter/timeouts, failure visibility, bounded retry/deduplication behavior, and SMTP integration tests.
**Addresses:** Required event notifications without changing ticket correctness.
**Avoids:** SMTP inside DB transactions, mail about rolled-back changes, API latency tied to SMTP, invisible failures, and uncontrolled duplicates.

### Phase 5: Operational Dashboard

**Rationale:** Indicators are trustworthy only after workflow, role scope, status timestamps, categories, and assignees are stable.
**Delivers:** Lean summary and groupings by category, attendant, and priority using repository aggregate projections, with access rules and reconciliation against equivalent filtered lists.
**Addresses:** Required admin/attendant operational indicators.
**Avoids:** Loading full entity sets to count, inconsistent metric definitions, unauthorized global views, and premature analytics infrastructure.

### Phase 6: Role-Focused React Application

**Rationale:** A stable API reduces simultaneous Spring/React learning risk and UI contract churn. Implement screens in the same vertical order as proven backend capabilities.
**Delivers:** Vite/Tailwind shell, auth/session/bootstrap, declarative routing, one Axios client, requester/attendant/admin journeys, URL-backed queues, ticket timeline/actions, dashboards, responsive layouts, and loading/empty/error feedback.
**Addresses:** Complete responsive web experience tailored to all three profiles.
**Avoids:** Treating route guards as security, unsafe token forwarding, duplicated fetch logic, invalid UI actions, filter loss, optimistic false success, and duplicate submits.

### Phase 7: Cross-Stack Hardening and Acceptance

**Rationale:** Cross-cutting failures appear only when the complete system is exercised adversarially and at representative volume.
**Delivers:** Route-by-role-by-relationship matrix, JWT negative suite, clean MySQL migration/startup verification, transaction/concurrency/query-count tests, SMTP outage cases, realistic query-plan checks, and a small Playwright set for each critical role journey.
**Addresses:** Critical testing and endpoint security review required by `PROJECT.md`.
**Avoids:** Happy-path-only confidence, authorization gaps on nested routes, migration drift, performance surprises, token leakage, and regressions across layers.

### Phase Ordering Rationale

- Schema and trusted identity precede operations because authorization needs persistent role and ticket relationships.
- Phase 3 proves the core value before breadth: assignment is inseparable from `EM_ANDAMENTO`, and every transition is inseparable from history.
- Email and dashboards consume stable events/data; neither should define or precede workflow semantics.
- The full UI follows a tested API but mirrors its vertical slices, keeping client permissions advisory and server rules authoritative.
- Hardening closes cross-phase gaps after real flows exist; critical unit/integration tests still ship within each earlier phase.

### Research Flags

Phases likely needing `$gsd-plan-phase --research-phase <N>`:

- **Phase 2:** Decide JWT browser transport, CSRF/XSS tradeoff, access-token lifetime, and exact deactivation semantics before locking the API/client contract.
- **Phase 3:** Confirm the complete actor/transition matrix and choose optimistic versus selective pessimistic locking before status endpoints are planned.
- **Phase 4:** Decide whether notification loss after process crash is acceptable. If not, research a transactional outbox and idempotent worker; do not add a broker by default.
- **Phase 5:** Resolve whether attendant dashboard aggregates are global or limited to the current attendant and define “active” counts precisely.
- **Phase 6:** Re-check current npm patches/security advisories and organizational browser requirements when scaffolding begins.

Phases with standard patterns (skip research-phase unless requirements change):

- **Phase 1:** Spring Boot/Flyway/MySQL foundation and Testcontainers validation are well documented.
- **Phase 7:** The required verification matrix is already concrete; planning should translate it into executable tests rather than repeat ecosystem research.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | MEDIUM-HIGH | Locked majors and architecture fit are strong; official docs and registries support versions, but exact patches are time-sensitive. |
| Features | MEDIUM-HIGH | SolveIT v1 scope is explicit and high-confidence; market classification is based on several official vendor sources without direct user research. |
| Architecture | MEDIUM-HIGH | Official Spring/React patterns align with the domain and locked constraints; several operational choices remain intentionally undecided. |
| Pitfalls | MEDIUM-HIGH | Core risks follow directly from project invariants and established security/transaction guidance; provider availability reduced verification depth in that research run. |

**Overall confidence:** MEDIUM-HIGH

### Gaps to Address

- **JWT browser/session model:** Select bearer storage versus secure cookie and document CSRF, XSS, logout, expiry, refresh, and deactivation behavior in Phase 2 planning.
- **Concurrency policy:** Confirm `@Version` as the default and identify any assignment operation that warrants a pessimistic lock through tests.
- **Notification durability:** Obtain explicit acceptance of best-effort after-commit delivery or plan an outbox; define retry count, deduplication key, and failure visibility.
- **Dashboard scope and metric definitions:** Clarify global versus assigned-only attendant data and the exact status set used for “active” totals.
- **Pagination scope discrepancy:** Research recommends all ticket lists be bounded and stable, while feature notes describe broad pagination as potentially v1.x; requirements should make safe bounded pagination part of v1 for operational endpoints.
- **Deployment/security context:** Production origin, HTTPS termination, secret storage, SMTP provider, and browser support are not yet defined; validate before deployment work.
- **Product validation:** No SolveIT user interviews or usage data exist. Treat every post-v1 feature as a hypothesis, not roadmap commitment.

## Sources

### Primary (HIGH confidence)

- [PROJECT.md](../PROJECT.md) — locked scope, roles, workflow, stack, architecture, audit invariants, and Build-to-Learn process.
- `SolveIT_GSD_SystemPrompt.md` — source routes, entities, transitions, notifications, and initial build order as cited by the research files.

### Secondary (MEDIUM confidence)

- [Spring Boot 3.5 documentation](https://docs.spring.io/spring-boot/3.5/) — system requirements, dependency management, SQL, mail, and Testcontainers.
- [Spring Security documentation](https://docs.spring.io/spring-security/reference/) — filter-chain, method authorization, and MockMvc security testing.
- [Spring Framework transaction documentation](https://docs.spring.io/spring-framework/reference/data-access/transaction.html) — service transactions and transaction-bound events.
- [Spring Data JPA documentation](https://docs.spring.io/spring-data/jpa/reference/) — specifications, paging, query methods, and projections.
- [MySQL 8.4 documentation](https://dev.mysql.com/doc/refman/8.4/en/) and [MySQL lifecycle notice](https://www.mysql.com/support/eol-notice.html) — LTS choice and 8.0 support transition.
- [Flyway documentation](https://documentation.red-gate.com/flyway) — immutable migration history and validation.
- [JJWT project documentation](https://github.com/jwtk/jjwt) and [RFC 8725](https://www.rfc-editor.org/rfc/rfc8725.html) — dependency shape and JWT security practices.
- [OWASP JWT Cheat Sheet for Java](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html) — browser/token threat guidance.
- [React](https://react.dev/), [Vite](https://vite.dev/), [Tailwind CSS](https://tailwindcss.com/), [React Router](https://reactrouter.com/), and [Axios](https://axios-http.com/) official documentation — SPA tooling, state, routing, styling, and HTTP client patterns.
- Atlassian Jira Service Management, Zendesk, and Freshdesk official product documentation — table-stakes versus advanced help-desk capabilities.

### Tertiary (LOW confidence)

- Post-v1 demand assumptions — password recovery, exports, notification preferences, SLA, knowledge, attachments, omnichannel, automation, CMDB, and AI require user or operational validation.

---
*Research completed: 2026-09-02*
*Ready for roadmap: yes*
