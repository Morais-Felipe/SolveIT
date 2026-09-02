# Pitfalls Research

**Domain:** Web IT help-desk / ticket management system (Spring Boot 3, Spring Security, JPA, MySQL 8, Flyway, JWT, SMTP, React)
**Researched:** 2026-09-02
**Confidence:** MEDIUM — domain invariants come directly from the project specification; framework guidance is based on official documentation, but the configured documentation providers were unavailable during this run.

## Critical Pitfalls

### Pitfall 1: Role checks without ticket-level authorization (IDOR)

**What goes wrong:**
A logged-in requester changes `/chamados/{id}` and reads another requester's ticket, comments, or status history. An attendant sees or changes tickets outside the intended queue, or resolves a ticket assigned to somebody else. Hiding UI links does not protect direct API calls.

**Why it happens:**
Endpoint rules such as `hasRole("SOLICITANTE")` answer only *what role is this user?*, not *is this user related to this ticket and allowed to perform this action?* Generic `findById` repository calls make it easy to forget ownership checks on detail and nested endpoints.

**How to avoid:**
Define one authorization policy per operation in the service layer. Load the authenticated user from the security context, never from a client-supplied `solicitanteId`. Enforce ownership/assignment on ticket detail, edit, status, comment, and history endpoints. Prefer scoped queries such as “ticket by id and requester id” where they simplify proof of access; retain method/route role rules as the outer gate. Return a consistent 404 or 403 policy that does not leak ticket existence. Add negative integration tests for every role-resource combination.

**Warning signs:**
- Services accept an actor/user ID from request JSON.
- Controllers call `repository.findById(id)` and immediately map the entity.
- Security tests cover unauthenticated access but not cross-user access.
- Comments/history have weaker checks than the parent ticket.
- “The frontend does not show that button” appears in a security rationale.

**Phase to address:**
Phase 2 establishes authenticated identity and method authorization; Phase 3 implements object-level policies alongside each ticket operation; Phase 7 performs a route-by-role-by-relationship security matrix review.

---

### Pitfall 2: Status changes bypass the state machine

**What goes wrong:**
Tickets jump from `ABERTO` to `RESOLVIDO`, closed tickets are reopened, a requester closes another requester's ticket, or assignment changes without the required automatic transition to `EM_ANDAMENTO`. Different endpoints implement contradictory rules.

**Why it happens:**
Status is treated as a freely editable field or a generic CRUD patch. Transition, actor, ownership, assignment, and side effects are split across controllers and duplicated `if` statements.

**How to avoid:**
Model status changes as named domain operations, not arbitrary entity updates. Keep a single transition policy in the service/domain layer that validates current status, target status, actor role, ownership/assignment, and operation-specific preconditions. Do not bind request DTOs directly onto entities. Use optimistic locking (`@Version`) or equivalent concurrency control so two actors cannot both transition from a stale state. Test every allowed edge and representative forbidden edge in the transition graph.

**Warning signs:**
- A generic update DTO contains `status`, `solicitante`, or audit timestamps.
- Multiple controllers call `chamado.setStatus(...)`.
- Rules depend only on target status and ignore current status.
- Two rapid requests can both succeed from the same original state.
- Assignment and status update are separate HTTP/service operations.

**Phase to address:**
Phase 1 defines enums and optional versioning; Phase 3 implements the centralized state machine and concurrency tests before email or UI depends on it.

---

### Pitfall 3: Ticket state and audit history diverge

**What goes wrong:**
The ticket shows a new status without a `HistoricoStatus` record, or the history records a change that rolled back or never became current. Actor, previous status, or timestamp is incorrect, making the audit trail untrustworthy.

**Why it happens:**
The ticket save and history insert occur in different transactions, history is added by controllers, exceptions are swallowed, or bulk update queries bypass the domain service. Mutable comments or cascade deletes further undermine audit evidence.

**How to avoid:**
Put status validation, ticket mutation, derived timestamps (`resolvidoEm`), and history insertion in one `@Transactional` service method. Derive `statusAnterior` from the locked/loaded persisted entity and the actor from the security context. Make comments append-only at both API and service boundaries. Avoid bulk status updates. Add rollback tests that force history persistence failure and verify neither record commits; add invariant tests comparing each transition with exactly one history row.

**Warning signs:**
- `save(chamado)` and `save(historico)` live in different public services without one transaction boundary.
- History is created asynchronously.
- There are update/delete endpoints for comments or history.
- Catch blocks log persistence errors and continue.
- Dashboard counts disagree with history-derived events.

**Phase to address:**
Phase 1 creates FK, non-null, and index constraints; Phase 3 makes transitions atomic and tests rollback; Phase 7 adds audit-integrity verification.

---

### Pitfall 4: SMTP is coupled to the database transaction

**What goes wrong:**
A slow or unavailable SMTP server holds database locks, makes an otherwise valid status change fail, or sends an email for a transaction that later rolls back. Retrying the API can create duplicate messages or duplicate business actions.

**Why it happens:**
`JavaMailSender.send` is called inside the transactional ticket method and email success is treated as part of the domain commit. Plain asynchronous calls can be just as unsafe if they run before commit or events are lost on restart.

**How to avoid:**
Commit the domain transaction first, then dispatch notification work from an after-commit event. For this learning MVP, a transactional event listener plus explicit failure logging/retry can be adequate if lost email is an accepted limitation. If delivery reliability is a requirement, introduce a small outbox table written in the same transaction, processed with retry and idempotency. Give each logical notification a deduplication key. Never roll back ticket state solely because SMTP failed.

**Warning signs:**
- SMTP calls occur in a method annotated `@Transactional`.
- API latency tracks mail-server latency.
- Mail exceptions return 500 after the database already changed, or undo the change.
- There is no record/metric of pending or failed notifications.
- Repeating a request sends the same email again.

**Phase to address:**
Phase 3 defines domain events at mutation points; Phase 4 implements after-commit delivery, failure policy, and idempotency; a durable outbox is a phase-specific research flag if guaranteed delivery is required.

---

### Pitfall 5: JWT validation is incomplete or identity becomes stale

**What goes wrong:**
Tokens are accepted without strict signature/expiry validation, secrets are weak or committed, role claims are trusted after an administrator changes the user's role, or deactivated users retain access until a long-lived token expires. Browser token handling exposes credentials to XSS, and CORS is mistaken for an authorization boundary.

**Why it happens:**
JWT is viewed as encryption or a session database. Hand-written parsing accepts claims before verification; keys, algorithms, expiration, clock behavior, and logout/revocation semantics are left implicit.

**How to avoid:**
Use a maintained JWT library's signed-token parser; pin the expected algorithm/key and validate signature and expiration before using claims. Generate a sufficiently strong secret outside source control. Keep access tokens short-lived. On each request, load the current user (or validate a version/active-state claim) so deactivation takes effect according to an explicit policy. Public registration must hard-code `SOLICITANTE`; ignore any submitted role. Decide browser storage deliberately: an HttpOnly/Secure/SameSite cookie needs CSRF protection, while JavaScript-accessible storage increases XSS impact. Log authentication failures without logging tokens.

**Warning signs:**
- The signing secret appears in Git or frontend environment files.
- The register request accepts `perfil`.
- Code decodes Base64 payloads without a verified parser.
- Tokens have no expiry or remain valid for days.
- A disabled user's existing token still authorizes sensitive actions without an explicit decision.
- JWTs appear in URLs or logs.

**Phase to address:**
Phase 2 must define and test token validation, key configuration, registration role, expiry, deactivation behavior, and 401/403 semantics; Phase 6 implements the selected browser strategy; Phase 7 performs adversarial JWT tests.

---

### Pitfall 6: JPA entities are exposed as the REST model

**What goes wrong:**
JSON serialization recurses through bidirectional relationships, triggers lazy-loading failures or N+1 queries, leaks password hashes and internal fields, or lets clients overwrite relationships and audit data. Response shape changes accidentally when entity mappings change.

**Why it happens:**
Returning entities looks faster than designing DTOs. Jackson annotations are then used reactively to suppress cycles, mixing persistence concerns with the API contract.

**How to avoid:**
Use explicit request and response DTOs. Never serialize `Usuario.senha`. Map only fields the caller may see, with role-aware detail where necessary. Fetch associations intentionally using projections/entity graphs/query methods and map inside the transaction. Avoid a blanket “fix” of eager relationships or disabling lazy-loading errors. Keep request DTOs operation-specific so assignment, ownership, status, and timestamps cannot be mass-assigned.

**Warning signs:**
- Controllers return `ResponseEntity<Chamado>` or `Page<Chamado>`.
- Entities contain a growing web of `@JsonIgnore` and managed/back-reference annotations.
- SQL logs show one query per ticket row.
- Responses unexpectedly include password hashes or full user objects.
- `spring.jpa.open-in-view` is relied on to make serialization work.

**Phase to address:**
Phase 1 establishes entity boundaries; Phase 3 introduces explicit DTO mapping and query shapes before endpoints proliferate; Phase 5 uses projections for dashboard queries.

---

### Pitfall 7: Flyway and Hibernate disagree about schema ownership

**What goes wrong:**
Local environments work because Hibernate silently created/changed tables, while clean or production databases fail. Applied migration files are edited, causing checksum validation errors or—worse—environmental schema drift. Enum/string lengths, defaults, indexes, and foreign keys differ from JPA expectations.

**Why it happens:**
DDL generation and versioned migrations are both enabled as authorities. Developers repair checksums or edit old migrations instead of appending a new migration. Tests do not start from an empty schema.

**How to avoid:**
Make Flyway the sole schema mutator and use Hibernate schema validation (`ddl-auto=validate`), not update/create, outside disposable experiments. Treat applied `V...` migrations as immutable; add a new migration for every change. Run Flyway validation and a clean-schema startup test in CI. Use database constraints for unique email/category, non-null fields, FKs, and relevant indexes. Document MySQL enum strategy (prefer stable string columns plus application enums) and timestamp/time-zone conventions.

**Warning signs:**
- `ddl-auto=update` appears with Flyway enabled.
- Developers manually alter their local database.
- The same migration has different checksums across branches/environments.
- CI reuses a long-lived database and never tests migration from empty.
- JPA starts only after changing validation to `none`.

**Phase to address:**
Phase 1 must settle schema ownership, migration immutability, constraints, and clean-schema verification; every later phase appends migrations rather than editing history.

---

### Pitfall 8: Filtering and pagination leak data or become unstable

**What goes wrong:**
Filters are applied after fetching, requester scoping is forgotten for one filter combination, pages contain duplicates/missing rows, arbitrary sort fields expose internals, or joins inflate counts. Unbounded endpoints exhaust memory and dashboard totals disagree with ticket lists.

**Why it happens:**
Optional filters produce ad hoc branching repository methods. Authorization is added after query construction. Sorting lacks a deterministic tiebreaker, and entities with collection joins are paged directly.

**How to avoid:**
Build one composable query/specification whose mandatory authorization predicate is applied before optional filters. Always use bounded page size and an allowlist of sort keys; add `id` as a deterministic tiebreaker. Return a stable page DTO rather than framework/entity serialization. Index common predicates deliberately (`status`, `prioridade`, category, assignee, requester, created timestamp), guided by `EXPLAIN` after representative data exists. Use dedicated aggregate queries/projections for dashboards and test every role with filter combinations.

**Warning signs:**
- `findAll()` followed by Java stream filtering.
- `size` has no maximum.
- Client-controlled property names flow directly into `Sort`.
- Adding a filter changes what the same role is allowed to see.
- Page totals are wrong when comments/history are joined.
- Results reorder between identical requests.

**Phase to address:**
Phase 3 designs secure paginated ticket queries and indexes; Phase 5 creates purpose-built dashboard aggregates; Phase 7 verifies query plans only after realistic seed volume exists.

---

### Pitfall 9: A learning project becomes an architecture showcase

**What goes wrong:**
The developer spends more time on microservices, brokers, generic repositories, elaborate domain frameworks, refresh-token infrastructure, or premature caching than on understanding the ticket lifecycle. Complexity hides rather than teaches Spring transactions, security, JPA, and SQL.

**Why it happens:**
Production patterns are adopted without the scale or reliability requirement that justifies them, and abstractions are introduced before repetition is understood.

**How to avoid:**
Keep one Spring Boot application and one MySQL database. Organize by the agreed layers, with thin controllers and cohesive services. Add a pattern only when a concrete requirement or measured failure demands it. Prefer explicit transition code, DTOs, SQL migrations, and tests that make learning visible. Record deferred production hardening (durable outbox, refresh-token rotation, caching) rather than silently building it now.

**Warning signs:**
- Infrastructure appears before the first end-to-end ticket flow.
- Interfaces have one implementation “for flexibility.”
- A generic base CRUD service fights ticket-specific rules.
- Phase estimates are dominated by tooling rather than user-visible behavior.
- The developer cannot explain transaction or authorization behavior without the framework abstraction.

**Phase to address:**
All phases, enforced through narrow vertical outcomes and explicit deferrals; roadmap review should reject infrastructure without a current requirement.

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Return JPA entities from controllers | Less mapping code | Data leaks, recursion, unstable API, N+1 queries | Never for authenticated domain entities |
| Put authorization only in controllers/UI | Fast demo | Alternate endpoints bypass rules; hard-to-test policy | Never |
| Generic `PATCH`/CRUD service for tickets | Fewer methods | Invalid transitions and mass assignment | Never |
| Send SMTP synchronously in transaction | Simple sequencing | Locks, rollback ambiguity, duplicates | Never for the final design |
| After-commit in-memory email without outbox | Small implementation | Email may be lost on process crash | Acceptable for MVP only if explicitly documented and monitored |
| `ddl-auto=update` with Flyway | Easy local iteration | Irreproducible schemas and migration drift | Only in a throwaway spike, never shared environments |
| Store access token in localStorage | Easy Axios integration | XSS can exfiltrate bearer token | Only with explicit risk acceptance for a learning-only deployment; prefer safer cookie design for public deployment |
| Add indexes for every column upfront | Feels performance-ready | Slower writes, unused complexity | Never blindly; add from query patterns and plans |
| Build microservices/message broker | “Production-like” portfolio | Operational and conceptual overload | Not for this milestone |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| SMTP / JavaMailSender | Treat mail delivery as part of the DB transaction | Publish after commit; define retry/failure policy; use an outbox only if durable delivery is required |
| MySQL + Flyway | Let Hibernate mutate schema or edit applied migrations | Flyway owns DDL; append immutable migrations; validate on clean schema |
| React + JWT | Treat route guards/CORS as security and leak bearer tokens | Server enforces every rule; choose token storage/CSRF model explicitly; redact tokens from logs |
| Axios interceptors | Create refresh/retry loops or attach token to unintended origins | One configured API client, bounded 401 behavior, fixed base URL, no credential forwarding cross-origin |
| JPA + Jackson | Serialize lazy bidirectional graphs | DTOs/projections with intentional fetch plans |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| N+1 mapping of requester, category, assignee | SQL query count grows per ticket row | DTO projection/entity graph; query-count integration test | Visible at tens to hundreds of rows per page |
| Unbounded ticket/comment/history lists | Slow responses and memory spikes | Enforced page limits; chronological indexed pagination | Hundreds to thousands of records per tenant/user |
| In-memory optional filtering | High memory, wrong totals, authorization gaps | Database-side composed predicates | Hundreds of tickets; correctness can fail immediately |
| Offset pagination on deep operational queues | Increasing latency for late pages | Start with bounded offset; consider keyset pagination only when measured | Usually tens/hundreds of thousands of rows with deep offsets |
| Dashboard computed by loading entities | CPU/memory spikes and N+1 queries | SQL aggregate projections with scope predicates | Thousands of tickets or frequent refresh |
| Missing indexes on foreign keys/filter columns | Full scans and lock pressure | Index based on list/dashboard queries; verify with `EXPLAIN` | Often noticeable at tens of thousands of tickets |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Role-only authorization | HIGH: cross-user ticket/comment/history exposure | Object-level ownership/assignment policy on every operation |
| Accept requester/actor/role from client | HIGH: impersonation or privilege escalation | Derive actor from authenticated principal; hard-code public registration role |
| Allow nested endpoints without parent access check | HIGH: audit/comment disclosure | Authorize through the parent ticket before query/response |
| Long-lived JWT unaffected by deactivation | HIGH: removed user retains access | Short expiry plus current active-state/version validation policy |
| Log tokens, passwords, ticket descriptions, or SMTP secrets | HIGH: credential and sensitive-data leakage | Structured redaction and safe error messages |
| Client-supplied sort/property paths | MEDIUM: information probing or expensive queries | Sort-field allowlist and bounded pagination |
| Distinguish forbidden ticket from nonexistent inconsistently | MEDIUM: ID enumeration | Adopt and test a consistent 403/404 disclosure policy |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| UI offers actions invalid for current role/status | Confusing failures and mistrust | Drive enabled actions from current state/role, while keeping server checks authoritative |
| Optimistic success before transaction commits | UI shows a transition/email outcome that failed | Update UI from confirmed API response; show mail as non-blocking notification state if exposed |
| Filters reset unexpectedly or pages reorder | Users lose queue position and miss tickets | URL-backed filters, deterministic sorting, clear reset, retained pagination state |
| Status names without transition explanation | Requesters do not know next step | Show timeline, actor, timestamp, and allowed next action in plain language |
| Generic 403/500 toast | Users cannot correct input or understand ownership rule | Stable API error codes mapped to role-appropriate messages |
| No double-submit protection | Duplicate comments or repeated actions | Disable while pending and make critical operations idempotent/concurrency-safe |

## "Looks Done But Isn't" Checklist

- [ ] **Authorization:** Every ticket, comment, and history route has negative tests for wrong role, wrong requester, and wrong assignee—not just an authenticated happy path.
- [ ] **State machine:** Every allowed edge succeeds and forbidden edge fails; assignment atomically produces `EM_ANDAMENTO` and a history row.
- [ ] **Audit:** Forced history persistence failure rolls back the ticket change; comments have no update/delete route or service method.
- [ ] **JWT:** Expired, malformed, wrong-signature, disabled-user, and privilege-escalation registration cases are tested; secrets are externalized.
- [ ] **Persistence:** A clean database migrates and starts with Hibernate validation; no applied migration needs editing.
- [ ] **Serialization:** No endpoint returns an entity or password hash; SQL query count remains bounded for a ticket page.
- [ ] **Email:** SMTP outage does not undo ticket state; failures and retries are visible; duplicate-event behavior is tested.
- [ ] **Pagination:** Page size is capped, sort fields allowlisted, ordering deterministic, and role scope remains correct under every filter.
- [ ] **Dashboard:** Aggregates use the intended active/status definitions and match equivalent filtered lists.
- [ ] **Learning scope:** The end-to-end ticket lifecycle works before optional infrastructure or abstractions are introduced.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Authorization leak | HIGH | Disable affected endpoint if needed; add centralized/scoped policy; audit access logs; add matrix tests; assess exposed data |
| Invalid state transitions | HIGH | Freeze generic updates; codify transition table; repair inconsistent rows with reviewed migration; add optimistic locking/tests |
| Audit divergence | HIGH | Identify source of truth; reconcile rows with a one-off audited migration; make mutation+history atomic; prohibit bypass paths |
| SMTP coupling | MEDIUM | Move send after commit; add event identifiers/failure store; introduce outbox only if loss cannot be accepted |
| JWT design flaw/key exposure | HIGH | Rotate signing key, invalidate tokens, shorten TTL, fix strict parser and current-user validation; scrub logs/history where possible |
| Entity serialization | MEDIUM | Introduce DTOs endpoint-by-endpoint; deny sensitive fields immediately; add intentional fetch plans and response contract tests |
| Migration drift | HIGH | Compare schema/history across environments; create forward corrective migration; rebuild disposable databases; never rewrite applied history |
| Slow/incorrect filtering | MEDIUM | Move predicates to DB; make authorization mandatory; cap pages; add indexes after `EXPLAIN`; regression-test totals/order |
| Overcomplex architecture | MEDIUM | Stop feature work; delete or quarantine unused abstractions; restore modular monolith boundaries; document explicit deferrals |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Flyway/Hibernate schema drift | Phase 1 — Base | Empty MySQL schema migrates; app starts with `ddl-auto=validate`; Flyway validate passes |
| Entity serialization and unsafe request binding | Phases 1 and 3 — Base/Core | Controllers expose DTOs only; password never appears; bounded query-count test |
| JWT validation/deactivation mistakes | Phase 2 — Authentication | Negative token suite plus public-role escalation and disabled-user tests |
| Role-only/object authorization leak | Phases 2 and 3; audit in 7 | Route × role × ownership/assignment matrix is green |
| Invalid state transitions/concurrent updates | Phase 3 — Core | Transition-table tests; two stale concurrent updates cannot both commit |
| Audit inconsistency | Phase 3 — Core | Forced failure rolls back both ticket and history; exactly one history row per transition |
| Filtering/pagination leakage and instability | Phase 3 — Core | Filter combinations preserve scope; bounded size, allowlisted sort, stable order and totals |
| SMTP/transaction coupling | Phase 4 — Email | SMTP failure leaves committed state intact; rollback emits no mail; duplicate handling verified |
| Inefficient/incorrect dashboard aggregation | Phase 5 — Dashboard | Aggregate queries match reference filters and query plans are reviewed with realistic data |
| JWT browser handling and UI-only authorization | Phase 6 — Frontend | Direct API calls remain protected; token/CSRF model is tested; no token in URL/log |
| Cross-cutting gaps | Phase 7 — Quality | Security matrix, clean migration, rollback, query-count, and representative-volume tests all pass |
| Learning-project overcomplexity | Every phase | Each phase produces a demonstrable user/domain outcome; optional infrastructure requires a recorded need |

## Roadmap Research Flags

- **Phase 2:** Decide browser JWT transport and deactivation semantics before frontend work. Cookie-based JWT changes CSRF requirements; JavaScript-accessible bearer storage changes XSS risk.
- **Phase 3:** Treat the transition table, actor policy, and concurrency strategy as a design checkpoint before implementing status endpoints.
- **Phase 4:** Confirm whether notification loss on process crash is acceptable. If not, research and plan a transactional outbox; do not smuggle it into the MVP without a reliability decision.
- **Phase 5:** Define whether attendant dashboard data is global or scoped to the current attendant; the source specification is explicit on endpoint roles but ambiguous on data scope.
- **Phase 7:** Use MySQL-backed integration tests for transactions/migrations; mocks alone cannot verify rollback, constraints, locking, or query behavior.

## Sources

- [SolveIT project definition](../../.planning/PROJECT.md) — primary source for roles, workflow, audit invariant, stack, and learning constraints (HIGH for project intent).
- [SolveIT source system prompt](../../SolveIT_GSD_SystemPrompt.md) — primary source for routes, transitions, entities, notifications, and original phase order (HIGH for project intent).
- [Spring Security: Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html) — official framework guidance on service-layer authorization (MEDIUM in this run; provider lookup unavailable).
- [Spring Framework: Transaction-bound Events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html) — official guidance for transaction-phase event listeners (MEDIUM).
- [Spring Framework: Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html) — official transaction boundary guidance (MEDIUM).
- [Spring Data JPA: Specifications](https://docs.spring.io/spring-data/jpa/reference/jpa/specifications.html) and [Paging and Sorting](https://docs.spring.io/spring-data/commons/reference/repositories/core-extensions.html) — official composable query and pagination guidance (MEDIUM).
- [Spring Boot: SQL Databases / Open EntityManager in View](https://docs.spring.io/spring-boot/reference/data/sql.html) — official JPA configuration context (MEDIUM).
- [Redgate Flyway: Validate](https://documentation.red-gate.com/fd/validate-277578898.html) and [Schema History Table](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/flyway-schema-history-table) — official migration checksum/history behavior (MEDIUM).
- [JJWT README](https://github.com/jwtk/jjwt/blob/master/README.adoc) — library-maintainer parsing/signature/expiration usage (MEDIUM).
- [RFC 8725: JSON Web Token Best Current Practices](https://www.rfc-editor.org/rfc/rfc8725.html) — standards guidance for algorithm verification and JWT threats (MEDIUM).
- [OWASP JSON Web Token Cheat Sheet for Java](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html) — browser storage, token sidejacking, and Java implementation risks (MEDIUM).

---
*Pitfalls research for: SolveIT web IT help-desk system*
*Researched: 2026-09-02*
