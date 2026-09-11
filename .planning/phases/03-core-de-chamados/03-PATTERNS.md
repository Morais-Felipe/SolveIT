# Phase 3: Core de Chamados - Pattern Map

**Mapped:** 2026-09-11
**Scope:** backend only; proposed filenames below are planning assignments, not implemented files.
**Tracked-source check:** analogs verified with `git -c safe.directory=C:/SolveIT ls-files solveit-api/src`.

## File Classification

Paths below are relative to `solveit-api/src/main/java/com/solveit/`, except migrations and tests. Grouped rows identify each proposed file with the same responsibility. Final DTO and test names belong to the plans.

| New/modified file | Role | Data flow | Closest analog | Match quality |
|---|---|---|---|---|
| `controller/UsuarioController.java`, `controller/CategoriaController.java`, `controller/ChamadoController.java`, `controller/ComentarioController.java`, `controller/HistoricoStatusController.java` | controller | request-response | `controller/AuthController.java` | role-match |
| `service/UsuarioService.java`, `service/CategoriaService.java`, `service/ChamadoService.java`, `service/ComentarioService.java`, `service/HistoricoStatusService.java` | service | CRUD | `service/AuthService.java` | role-match |
| `service/ChamadoAuthorizationService.java` | service | request-response | authorization probe in `src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java` | partial; test-only example |
| Administrative user request/response and preview/confirmation DTOs in `dto/usuario/` | model | transform | explicit mapping in `service/AuthService.java` | partial; preview has no analog |
| Category request/response DTOs in `dto/categoria/` | model | transform | explicit mapping in `service/AuthService.java` | partial |
| Ticket create/edit/transition/detail/list DTOs in `dto/chamado/` | model | transform | `controller/AuthController.java` validation boundary and `service/AuthService.java` mapping | partial |
| Comment create/read DTOs in `dto/comentario/`; history read DTO in `dto/historico/` | model | transform | `service/AuthService.java` mapping | partial |
| `entity/Usuario.java`, `entity/Categoria.java` version additions | model | CRUD | `entity/Chamado.java` | exact for version mapping |
| `entity/Chamado.java`, `entity/HistoricoStatus.java`, `entity/Comentario.java` domain adjustments | model | CRUD | `entity/Chamado.java` | role-match; preserve immutable comment API |
| `repository/UsuarioRepository.java`, `repository/CategoriaRepository.java`, `repository/ChamadoRepository.java`, `repository/ComentarioRepository.java`, `repository/HistoricoStatusRepository.java` query extensions | model | CRUD | no inspected strong lock/specification analog | research required |
| `repository/ChamadoSpecifications.java` | utility | CRUD | none | new pattern |
| Coordination entity/repository/service, e.g. `entity/CoordenacaoEscrita.java`, `repository/CoordenacaoEscritaRepository.java`, `service/CoordenacaoEscritaService.java` | model/service | batch | `entity/Chamado.java` mapping and `service/AuthService.java` transaction shape only | partial; lock protocol new |
| `exception/GlobalExceptionHandler.java`, `exception/ApiErrorCode.java`, new domain/conflict exceptions | middleware/model | request-response | `exception/GlobalExceptionHandler.java` | exact for handler shape |
| New additive SQL migration(s) under `src/main/resources/db/migration/` | migration | batch | none among extracted analogs | research required |
| New MVC/integration suites for users, categories, tickets, comments/history | test | request-response | `src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java` | role-match |
| Policy unit tests and two-transaction MySQL concurrency tests; persistence regression updates | test | CRUD/batch | security test provides assertions only | partial; database race harness new |

## Pattern Assignments

### Controllers: imports, validation and authenticated identity

**Analog:** `solveit-api/src/main/java/com/solveit/controller/AuthController.java`, lines 7–16, 29–33, 40–43.

```java
import com.solveit.security.UsuarioPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

@PostMapping("/register")
@ResponseStatus(HttpStatus.CREATED)
public UsuarioResponse register(@Valid @RequestBody RegisterRequest request) {
    return authService.register(request);
}

@GetMapping("/me")
public UsuarioResponse me(@AuthenticationPrincipal UsuarioPrincipal principal) {
    return authService.me(principal.getId());
}
```

Copy constructor injection (lines 23–27), thin delegation and explicit DTOs. New protected commands pass the principal ID to the service; never accept actor/author identity from the request. Do not copy the public access semantics of registration/login.

### Services: transaction boundary, normalization and response mapping

**Analog:** `solveit-api/src/main/java/com/solveit/service/AuthService.java`, lines 23–24, 48–56, 97–112.

```java
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public UsuarioResponse register(RegisterRequest request) {
    String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
    if (usuarioRepository.existsByEmail(normalizedEmail)) {
        throw new EmailAlreadyRegisteredException();
    }
    String passwordHash = passwordEncoder.encode(request.senha());
    // Remaining registration implementation is specific to authentication.
```

```java
private static UsuarioResponse toResponse(Usuario usuario) {
    return new UsuarioResponse(
            usuario.getId(), usuario.getNome(), usuario.getEmail(),
            usuario.getPerfil(), usuario.getAtivo(), usuario.getCriadoEm());
}
```

Reuse explicit mapping without password/hash, BCrypt encoder injection, and `@Transactional(readOnly = true)` for reads. New write methods must acquire coordination and reload the actor as described in RESEARCH before mutable decisions. Existing registration is not evidence that a pre-check alone prevents races: lines 64–71 flush and translate only the known uniqueness violation, then rethrow all others. Never continue after failed persistence in a rollback-only transaction.

### Entities: versions, associations and UTC dates

**Analog:** `solveit-api/src/main/java/com/solveit/entity/Chamado.java`, lines 46–56, 67–72, 91–105.

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "solicitante_id", nullable = false)
private Usuario solicitante;

@Version
@Column(name = "version", nullable = false)
private Long version;

protected Chamado() {
}
```

Copy version mapping to Usuario/Categoria with matching additive DDL. Expose expected version through DTOs and compare it; do not set entity version from client input. Existing callbacks use `LocalDateTime.now(ZoneOffset.UTC)` (line 93). Keep explicit constructors/accessors and lazy associations; map DTOs while the read transaction remains open. History's nullable previous state is new and must match migration constraints.

### Errors: central Problem Details

**Analog:** `solveit-api/src/main/java/com/solveit/exception/GlobalExceptionHandler.java`, lines 78–90.

```java
@ExceptionHandler(EmailAlreadyRegisteredException.class)
public ResponseEntity<ProblemDetail> handleEmailAlreadyRegistered(
        HttpServletRequest request) {
    ProblemDetail problem = problemDetailFactory.create(
            HttpStatus.CONFLICT,
            ApiErrorCode.EMAIL_ALREADY_REGISTERED,
            "Conflito de cadastro",
            "E-mail já cadastrado",
            requestUri(request),
            null);
    return problemResponse(HttpStatus.CONFLICT, problem);
}
```

Add specific conflict/not-found/domain handlers through this factory. Preserve validation field errors (lines 31–47), safe messages and stable enum codes. Do not expose SQL, exception messages, password values or tokens. Rejected actions must roll back without inserting status history.

### Authorization and test contract

**Analog:** `solveit-api/src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java`, lines 726–750. This is a test probe, not a production ticket policy.

```java
@GetMapping("/api/test/authorization/{resourceId}")
@PreAuthorize("hasRole('ADMIN')")
public Map<String, Long> authorize(
        @PathVariable Long resourceId,
        @AuthenticationPrincipal UsuarioPrincipal principal) {
    Long resourceOwnerId = authorizationProbeService.authorize(principal, resourceId);
```

```java
if (!resourceOwner.getId().equals(principal.getId())) {
    throw new AccessDeniedException("Authenticated user is not related to the resource");
}
```

Copy the separation between method role checks and persisted relationship checks, not the probe's owner-only rule. Real ticket policy must implement all approved roles and the available queue. The service must also revalidate current actor state under coordination for writes.

The same test file, lines 480–485, demonstrates hostile identity parameters through real HTTP security:

```java
MvcResult result = mockMvc.perform(get(
                "/api/test/authorization/{resourceId}", resourceOwner.usuarioId())
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.accessToken())
        .queryParam("userId", resourceOwner.usuarioId().toString()))
    .andExpect(status().isForbidden())
    .andReturn();
```

Use real register/login tokens, wrong role, wrong relation, inactive actor and forged ID scenarios. Existing lines 626–648 verify status/content type, complete Problem Details fields and absence of secrets/SQL. New concurrency tests need independent database transactions, bounded coordination and final-state/history assertions; the security filter barrier alone does not test database locking.

## Shared Patterns

- **Authentication:** existing controller principal ID, then current persisted actor; JWT Bearer remains unchanged, without cookies/refresh tokens.
- **Validation:** `@Valid @RequestBody`; requests whitelist editable fields; normalized limits must match persistence constraints.
- **Transactions:** public proxied service methods own the atomic write; entity update and history insert belong together. Use RESEARCH's coordination protocol, not a controller transaction.
- **Responses:** explicit Java DTO mapping; no entity serialization or credentials.
- **Errors:** centralized factory and enum codes; stale requests become controlled conflicts; authorization remains 401/403 as established.
- **Testing:** reuse real security chain patterns; extend MySQL coverage for actual constraints and competing operations.

## No Analog Found

| New behavior/files | Reason and planner direction |
|---|---|
| Global coordination entity/repository/service | No established persistent write guard. Use researched lock ordering and require every Phase 3 write path to participate. |
| Exact administrative preview/confirmation DTOs | No existing bulk confirmation protocol. Validate action, target version and complete sorted ID/version set, including empty sets. |
| `ChamadoSpecifications.java` and paginated visibility | No extracted specification analog. Use Spring Data 3.5 APIs from RESEARCH; mandatory visibility AND every optional filter. |
| Additive history/version/coordination migrations | Existing schema is a baseline, not permission to modify V1–V5. Add migration(s), test fresh database and upgrade. |
| Concurrent MySQL scenario tests | Existing HTTP security tests do not prove last-admin or assignment/withdrawal atomicity. Build two-transaction tests with finite timeouts. |
| First-admin operational documentation | New documented procedure, not a public endpoint or startup promotion. Operational execution requires its own approval. |

## Metadata

**Analog search scope:** tracked `solveit-api/src/main` and `solveit-api/src/test`.
**Strong analogs extracted:** 5 (controller, service, entity, handler, security integration test).
**Coverage:** all feature/file families implied by CONTEXT and RESEARCH classified; final per-plan filenames are proposals.
**Pattern extraction date:** 2026-09-11.
**Write scope:** this document only; no source modification or commit.
