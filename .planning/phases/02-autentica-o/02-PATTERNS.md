# Phase 2: Autenticação - Pattern Map

**Mapped:** 2026-09-05
**Scope:** `solveit-api/` (somente fontes rastreadas pelo Git)
**Candidate files classified:** 23
**Local analog coverage:** 11/23 por padrão de camada; 12/23 sem análogo local porque as camadas HTTP/security ainda não existem

## Reading Guide

Este mapa separa duas coisas que não devem ser confundidas durante o planejamento:

1. **Padrão comprovado no SolveIT:** código já rastreado no repositório e que deve ser preservado.
2. **Padrão novo fundamentado na pesquisa:** estrutura ainda inexistente, para a qual `02-RESEARCH.md` e as APIs oficiais são a referência, não um arquivo local fictício.

Não há hoje controllers, DTOs, services, filtros, configuração Spring Security nem tratamento global de erros. Logo, `Usuario`, `UsuarioRepository`, os YAMLs, o POM e `PersistenceIntegrationTest` são bons analogs de estilo e integração, mas não analogs funcionais de autenticação.

## Existing Contract That Must Not Drift

### Entidade de usuário

**Source:** `solveit-api/src/main/java/com/solveit/entity/Usuario.java`

O cadastro deve construir a entidade explicitamente, como já permite o construtor, sem Lombok e sem transformar a entidade em contrato REST.

**Constructor and UTC creation pattern** (lines 45-59):

```java
protected Usuario() {
}

public Usuario(String nome, String email, String senha, PerfilUsuario perfil) {
    this.nome = nome;
    this.email = email;
    this.senha = senha;
    this.perfil = perfil;
}

@PrePersist
private void preencherDataDeCriacao() {
    if (criadoEm == null) {
        criadoEm = LocalDateTime.now(ZoneOffset.UTC);
    }
}
```

**Sensitive and current-state fields** (lines 29-43):

```java
@Column(name = "email", nullable = false, unique = true, length = 255)
private String email;

@Column(name = "senha", nullable = false, length = 255)
private String senha;

@Enumerated(EnumType.STRING)
@Column(name = "perfil", nullable = false, length = 20)
private PerfilUsuario perfil;

@Column(name = "ativo", nullable = false)
private Boolean ativo = true;
```

Consequências para a fase:

- `senha` deve receber somente o hash BCrypt antes de `save`.
- DTOs de resposta devem mapear campos explicitamente e nunca serializar `Usuario`.
- O filtro deve ler `ativo` e `perfil` do registro atual em cada requisição protegida.
- Use `Boolean.TRUE.equals(usuario.getAtivo())` na fronteira de segurança para falhar fechado mesmo diante de um objeto inconsistente em teste.

### Perfil canônico

**Source:** `solveit-api/src/main/java/com/solveit/enums/PerfilUsuario.java` (lines 3-7)

```java
public enum PerfilUsuario {
    SOLICITANTE,
    ATENDENTE,
    ADMIN
}
```

O cadastro público atribui `PerfilUsuario.SOLICITANTE` no servidor. `RegisterRequest` não deve possuir campo `perfil`.

### Repositório mínimo

**Source:** `solveit-api/src/main/java/com/solveit/repository/UsuarioRepository.java` (lines 8-12)

```java
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);
}
```

O projeto prefere métodos derivados mínimos. `findById` já é herdado de `JpaRepository`; não crie uma duplicata. `findByEmail` atende ao login depois da normalização, e `existsByEmail` pode antecipar a mensagem do cadastro, mas a constraint do banco continua sendo a autoridade contra corrida.

### Restrição física de unicidade e perfil

**Source:** `solveit-api/src/main/resources/db/migration/V1__create_usuario.sql` (lines 1-14)

```sql
CREATE TABLE usuario (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nome VARCHAR(120) NOT NULL,
    email VARCHAR(255) NOT NULL,
    senha VARCHAR(255) NOT NULL,
    perfil VARCHAR(20) NOT NULL,
    ativo BIT NOT NULL DEFAULT b'1',
    criado_em DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_usuario_email UNIQUE (email),
    CONSTRAINT ck_usuario_perfil CHECK (
        perfil IN ('SOLICITANTE', 'ATENDENTE', 'ADMIN')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

Nenhuma migração nova é esperada para a autenticação: o schema já comporta o incremento. Não altere `V1`; se uma necessidade física real surgir, ela exigirá uma nova migration versionada.

## File Classification

Os nomes abaixo são a materialização mais direta da estrutura proposta em `02-RESEARCH.md`; nomes internos permanecem discricionários, mas as responsabilidades não devem ser fundidas.

| New/Modified File | Role | Data Flow | Closest Tracked Analog | Match Quality |
|---|---|---|---|---|
| `solveit-api/pom.xml` | config/dependencies | build | o próprio POM | exact |
| `solveit-api/src/main/resources/application.yml` | config | request-response bootstrap | `application-dev.yml` | role-match |
| `.../config/JwtProperties.java` | config | transform | `application-dev.yml` | partial |
| `.../config/SecurityConfig.java` | config | request-response | none | no analog |
| `.../controller/AuthController.java` | controller | request-response | none | no analog |
| `.../dto/auth/RegisterRequest.java` | model/DTO | request-response | `Usuario.java` only for field types | partial |
| `.../dto/auth/LoginRequest.java` | model/DTO | request-response | `Usuario.java` only for field types | partial |
| `.../dto/auth/TokenResponse.java` | model/DTO | request-response | none | no analog |
| `.../dto/auth/UsuarioResponse.java` | model/DTO | transform | `Usuario.java` as source model | partial |
| `.../dto/validation/ValidPassword.java` | validation | transform | none | no analog |
| `.../dto/validation/PasswordValidator.java` | validation | transform | none | no analog |
| `.../service/AuthService.java` | service | CRUD/request-response | `UsuarioRepository.java` as persistence boundary | partial |
| `.../security/UsuarioPrincipal.java` | model/security | transform | `Usuario.java` as source model | partial |
| `.../security/UsuarioDetailsService.java` | service/security | CRUD | `UsuarioRepository.java` | role-match |
| `.../security/JwtService.java` | service/security | transform | none | no analog |
| `.../security/JwtAuthenticationFilter.java` | middleware | request-response | none | no analog |
| `.../security/RestAuthenticationEntryPoint.java` | middleware/error | request-response | none | no analog |
| `.../security/RestAccessDeniedHandler.java` | middleware/error | request-response | none | no analog |
| `.../exception/ApiErrorCode.java` | model/error | transform | enums such as `PerfilUsuario.java` | role-match |
| `.../exception/ProblemDetailFactory.java` | utility/error | transform | none | no analog |
| `.../exception/GlobalExceptionHandler.java` | middleware/error | request-response | none | no analog |
| `.../exception/EmailAlreadyRegisteredException.java` | model/error | request-response | none | no analog |
| `.../src/test/java/com/solveit/...` authentication tests | test | request-response + CRUD | `PersistenceIntegrationTest.java` | role-match |

`...` above means `solveit-api/src/main/java/com/solveit` unless the row identifies the test tree. The planner may divide the last row into focused tests such as `AuthServiceTest`, `JwtServiceTest` and `AuthSecurityIntegrationTest`.

## Pattern Assignments

### `pom.xml` (dependency configuration)

**Analog:** `solveit-api/pom.xml`

**Boot BOM pattern** (lines 7-12, 24-45):

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.16</version>
    <relativePath/>
</parent>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

Apply to:

- Add Boot-managed `spring-boot-starter-security`, `spring-boot-starter-validation` and test-scoped `spring-security-test` without explicit versions.
- Add JJWT `jjwt-api` at compile scope and `jjwt-impl`/`jjwt-jackson` at runtime scope, all at the single explicit JJWT version established by research.
- Preserve Java 17, Boot 3.5.16, MySQL/Flyway and existing Testcontainers dependencies.

### `JwtProperties.java` and `application.yml` (externalized configuration)

**Analog:** `solveit-api/src/main/resources/application.yml` and `application-dev.yml`

**Environment fallback pattern** (`application.yml` lines 1-5):

```yaml
spring:
  application:
    name: solveit-api
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
```

**Secrets supplied only by environment** (`application-dev.yml` lines 1-5):

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/solveit?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

Apply to:

- Bind issuer, audience, access-token TTL and Base64 secret to a typed, validated properties class.
- Safe identifiers/duration may have documented defaults; the signing secret must not have a committed real/default credential.
- Every test context that starts the full application must supply deterministic JWT test properties. This includes the pre-existing `PersistenceIntegrationTest`, whose context will otherwise begin validating the new properties.
- Keep the one-hour public contract synchronized with `expiresIn`; derive both token expiration and response value from the same configured duration.

### DTOs and `AuthController.java` (explicit HTTP boundary)

**Partial analog:** `Usuario.java` for canonical field names/types. There is no existing controller or DTO to copy.

Use Java records for immutable request/response DTOs if the planner adopts the stack recommendation, but keep mapping explicit. The response mapper must select only:

```text
id, nome, email, perfil, ativo, criadoEm
```

Do not expose `senha`, accept `perfil` in public registration, or return a JWT from registration. Controller responsibilities should stay limited to validation, HTTP status/location semantics, service delegation and DTO mapping; normalization, password hashing and duplicate handling belong to the service/domain boundary.

Follow `02-RESEARCH.md` for Spring MVC annotations and Problem Details because the repository contains no trustworthy local HTTP precedent.

### Password validation (DTO validation, transform)

**Analog:** none.

The implementation needs a dedicated validator because built-in `@Size` counts Java characters differently from the required BCrypt byte limit. The validator must:

- preserve the password exactly (no `trim`, lowercase or Unicode normalization);
- require at least 15 Unicode code points;
- allow at most 72 bytes after UTF-8 encoding;
- attach the failure to the `senha` field so Problem Details can populate `errors`.

Do not use `@NotBlank` for the password: it changes the accepted character set and usually applies whitespace semantics prohibited by D-05.

### `AuthService.java` (transactional orchestration)

**Partial analog:** `UsuarioRepository.java` and `Usuario.java`.

The existing persistence flow in `PersistenceIntegrationTest.java` lines 254-276 demonstrates explicit construction, repository save, flush and reload:

```java
Usuario solicitante = usuarioRepository.save(new Usuario(
        "João da Silva", uniqueEmail("solicitante"), "hash-temporario",
        PerfilUsuario.SOLICITANTE));
entityManager.flush();
Long solicitanteId = solicitante.getId();
entityManager.clear();
```

Adapt, do not copy literally: normalize e-mail, validate raw password, BCrypt-encode it, force `SOLICITANTE`, persist inside a transaction and map to a public DTO. The `"hash-temporario"` string is only old persistence-test data and is not a password-storage pattern.

For duplicate registration, `existsByEmail` is an early check only. Also translate the database's `DataIntegrityViolationException` from `uk_usuario_email` into the same stable 409 contract, as the current test proves the database is authoritative (`PersistenceIntegrationTest.java` lines 311-325).

For login, delegate password verification to `AuthenticationManager`; collapse nonexistent e-mail, wrong password and inactive account into the same 401 result. Issue the JWT only after an active authenticated identity exists.

### `UsuarioPrincipal.java` and `UsuarioDetailsService.java`

**Partial analog:** `Usuario.java` plus `UsuarioRepository.java`.

Create a security-specific principal instead of making `Usuario` implement `UserDetails`. It should carry only the values needed during authentication/authorization, expose current authorities from `PerfilUsuario`, and implement credential erasure if it temporarily holds the hash.

The loader should normalize the login e-mail consistently and use:

```java
Optional<Usuario> findByEmail(String email);
```

Do not return different public failure reasons from the loader. The authenticated request path must use the JWT `sub` plus inherited `findById`, not trust a profile claim.

### `JwtService.java` (token transform)

**Analog:** none.

Use the JJWT 0.13 example in `02-RESEARCH.md`, including fixed HS256, Base64-decoded key material of at least 32 bytes, `sub`, `iat`, `exp`, `iss` and the research-approved technical `aud=solveit-api`. Inject `Clock` so expiration tests do not sleep or depend on wall time. Do not put name, e-mail, profile or active state in the token.

Return a typed parse outcome or narrowly classified exceptions so the filter can preserve the required distinction between expired, invalid and signed-token/inactive-account failures. Never log token content.

### `JwtAuthenticationFilter.java` and `SecurityConfig.java`

**Analog:** none.

Follow the security flow from `02-RESEARCH.md`:

1. Accept only an exact Bearer header shape.
2. Missing token remains anonymous so the protected-route rule produces `AUTH_TOKEN_MISSING`.
3. Verify signature, algorithm, issuer, audience and expiry.
4. Convert `sub` to `Long`, load `Usuario` by ID, and reject absent/inactive accounts.
5. Build a fresh `SecurityContext` with authorities from the current database profile.
6. Configure `SessionCreationPolicy.STATELESS`, public POST matchers only for register/login, `/api/auth/me` authenticated, and `anyRequest().authenticated()`.
7. Disable form login and HTTP Basic. Disable CSRF only for the locked bearer-header/no-cookie contract and leave the rationale in code or plan notes.

The root application at `SolveItApplication.java` lines 1-10 is in package `com.solveit`, so components under `com.solveit.config`, `controller`, `exception`, `security` and `service` are discovered by normal component scanning; do not add manual scan lists.

### Problem Details components

**Analog:** none.

One stateless factory/writer should build RFC 9457 bodies containing stable `code` and optional field `errors`. Reuse it from:

- `GlobalExceptionHandler` for controller validation and domain conflicts;
- `RestAuthenticationEntryPoint` for 401 responses raised by/after the filter;
- `RestAccessDeniedHandler` for authenticated 403 responses.

Do not expect `@RestControllerAdvice` to catch exceptions raised before `DispatcherServlet`. Ensure `Content-Type: application/problem+json`, include request instance safely, and never serialize exception messages that may contain SQL, class names, secrets or JWT material.

### Authentication tests

**Analog:** `solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java`

**Real MySQL/context pattern** (lines 59-90):

```java
@Testcontainers
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class PersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("solveit_test")
            .withUsername("solveit")
            .withPassword("solveit-test");
}
```

**Assertion style** (lines 311-325):

```java
assertThatThrownBy(() -> insertUser("Outra pessoa", email, "SOLICITANTE"))
        .isInstanceOf(DataIntegrityViolationException.class);
```

Apply to:

- Keep JUnit Jupiter + AssertJ naming and arrange behavior around externally visible outcomes.
- Use fast unit tests for normalization, password boundary, BCrypt behavior, JWT claims/expiry and service decisions.
- Use `@SpringBootTest` + MockMvc + the real Spring Security filter chain for registration/login/`me`, 401/403 and the negative token matrix.
- Reuse the existing shared MySQL 8.4 Testcontainers convention instead of H2.
- Keep tests deterministic: inject `Clock`, use unique e-mails and explicitly clean persisted users when test isolation requires it.
- Do not copy `@TestMethodOrder` unless a test genuinely validates a sequence; authentication tests should normally be independent.

## Shared Patterns

### Explicit Java over code generation

**Sources:** all tracked Java domain files.

Use explicit constructors, accessors and mapper methods. Do not introduce Lombok or MapStruct in this phase.

### Minimal repositories and service-owned rules

**Source:** `UsuarioRepository.java`.

Repositories express persistence queries; services own normalization, hashing, duplicate translation, credential semantics and authorization decisions. Controllers must not compare passwords or make final access decisions.

### Flyway owns DDL; Hibernate validates

**Source:** `application-dev.yml` lines 6-16.

```yaml
flyway:
  enabled: true
  locations: classpath:db/migration
jpa:
  open-in-view: false
  hibernate:
    ddl-auto: validate
```

Authentication should require no schema edit. Preserve `open-in-view: false`, so mapping required fields should happen while the service has the data it needs and must not rely on lazy access in controllers.

### UTC and one source of time

**Sources:** `Usuario.java` lines 55-59 and `application-dev.yml` lines 13-18.

Persisted timestamps and Jackson are already UTC-oriented. JWT timestamps should use an injected `Clock`; do not use independent `Instant.now()` calls for issue and expiry if that can create drift or flaky assertions.

### Security fails closed

**Source:** phase decisions and `02-RESEARCH.md`; no existing code analog.

Only two exact POST routes are public. Every other route is authenticated by default. `401` means no valid current identity; `403` means a valid identity lacks permission. Profile authorization comes from the current database row, and future resource-relationship checks remain in transactional services.

## No Analog Found

| File/Concern | Role | Data Flow | Planner Source Instead | Reason |
|---|---|---|---|---|
| `SecurityConfig.java` | config | request-response | `02-RESEARCH.md` Pattern 1 | No Spring Security code exists. |
| `AuthController.java` | controller | request-response | CONTEXT D-01..D-04, D-14, D-16..D-19 | No MVC controller exists. |
| `JwtService.java` | security service | transform | research JJWT 0.13 code example | No token code exists. |
| `JwtAuthenticationFilter.java` | middleware | request-response | research Patterns 1 and 3 | No servlet filter exists. |
| security error handlers | middleware/error | request-response | research Pattern 5 | Filter-chain errors cannot copy controller advice. |
| Problem Details factory/advice | error boundary | request-response | research Pattern 5 + RFC 9457 | No application error contract exists. |
| password Bean Validation | validation | transform | CONTEXT D-05 + research Pitfall 1 | No validation code exists; built-in `@Size` is insufficient. |

## Integration Risks for the Planner

1. **Adding Spring Security changes the whole application immediately.** Before a custom chain exists, Boot applies default security. Keep dependency, configuration and a smoke test in the same executable increment so the application is never left ambiguously locked.
2. **Validated JWT properties affect old context tests.** `PersistenceIntegrationTest` starts the complete Spring application and isolated contexts. Supply test JWT properties everywhere those contexts boot, without committing a production-like secret.
3. **Duplicate e-mail has a concurrency path.** `existsByEmail` alone is race-prone; the named database constraint must still map to the same 409 Problem Detail without leaking SQL.
4. **The collation is case- and accent-insensitive.** Lowercasing/trim makes application lookup deterministic, while MySQL may consider additional Unicode variants equal. Treat any actual uniqueness violation as the same public duplicate outcome.
5. **BCrypt's upper boundary is bytes, not Java `String.length()`.** Exercise ASCII, emoji/multibyte, exactly-72-byte and over-72-byte cases; preserve password whitespace.
6. **Security exceptions have two delivery paths.** MVC advice cannot be the only formatter; filter/authorization failures need entry point and denied handler backed by the same Problem Detail factory.
7. **Current state defeats stale authorization.** A cryptographically valid token is insufficient: database absence/inactivity must reject it, while reactivation restores only an unexpired token and profile changes take effect on the next request.
8. **Login must not enumerate accounts.** Unknown e-mail, wrong password and inactive account require identical status/code/message; avoid obvious timing branches where practical.
9. **AUTH-06 spans future resources.** This phase should install and test the server-side principal/profile foundation, not invent fake production ticket routes. Relationship guards become concrete when Phase 3 introduces ticket operations.
10. **Rate limiting remains an explicit production risk.** It is not authorized by current scope. Record the omission; do not silently add distributed state or a library.
11. **BCrypt work factor needs measurement.** Treat strength 12 as a hypothesis. Benchmark 10/11/12 on target hardware and document the selected cost before fixing it in configuration/tests.
12. **Never log credentials.** Avoid request-body logging on auth endpoints and do not include bearer values, raw passwords, hashes or signing keys in exceptions or test diagnostics.

## Recommended Planning Boundaries

The repository has no same-layer analogs for security, so the safest teaching sequence follows dependency direction:

1. **Contracts and password storage:** dependencies, externalized properties, DTO validation, registration service and focused tests.
2. **Credential verification and JWT:** principal/details loader, authentication manager, BCrypt, JWT service and deterministic unit tests.
3. **Stateless request authentication:** filter, fail-closed chain and `/me`, verified through MockMvc and real persistence.
4. **Unified failure contract and security matrix:** Problem Details across MVC/filter boundaries, duplicate concurrency and negative 401/403/current-state cases.

Each boundary should end with a runnable verification command and a pedagogical explanation of the new Spring concept before implementation.

## Metadata

**Analog search scope:** all tracked files under `solveit-api/`
**Tracked sources inspected:** `pom.xml`, `SolveItApplication.java`, `Usuario.java`, `PerfilUsuario.java`, `UsuarioRepository.java`, `application.yml`, `application-dev.yml`, `V1__create_usuario.sql`, `PersistenceIntegrationTest.java`
**Strong analogs used:** 5 (`Usuario.java`, `UsuarioRepository.java`, `application-dev.yml`, `pom.xml`, `PersistenceIntegrationTest.java`)
**Supplementary contracts:** 4 (`SolveItApplication.java`, `PerfilUsuario.java`, `application.yml`, `V1__create_usuario.sql`)
**Pattern extraction date:** 2026-09-05
**Source-tracking gate:** every cited repository path returned by `git ls-files -- solveit-api`

