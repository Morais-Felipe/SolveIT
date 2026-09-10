---
phase: 02-autentica-o
plan: 02
subsystem: auth
tags: [bean-validation, unicode, problem-details, concurrency, mysql]
requires:
  - phase: 02-autentica-o
    plan: 01
    provides: Cadastro público stateless com BCrypt e MySQL 8.4
provides:
  - Senhas validadas por code points Unicode e bytes UTF-8 sem transformação
  - Erros de cadastro em RFC 9457 Problem Details com códigos estáveis
  - E-mail duplicado determinístico sob concorrência real
  - Logs de integridade sem senha, SQL, constraint ou stack trace
affects: [02-autentica-o, login, jwt, api-errors, frontend]
actuals:
  tasks: 3
  commits: 6
tech-stack:
  added: []
  patterns: [Bean Validation customizada, Problem Details centralizado, classificação específica de constraint, TDD RED-GREEN]
key-files:
  created:
    - solveit-api/src/main/java/com/solveit/dto/validation/ValidPassword.java
    - solveit-api/src/main/java/com/solveit/dto/validation/PasswordValidator.java
    - solveit-api/src/main/java/com/solveit/exception/ApiErrorCode.java
    - solveit-api/src/main/java/com/solveit/exception/EmailAlreadyRegisteredException.java
    - solveit-api/src/main/java/com/solveit/exception/GlobalExceptionHandler.java
    - solveit-api/src/main/java/com/solveit/exception/ProblemDetailFactory.java
  modified:
    - solveit-api/src/main/java/com/solveit/dto/auth/RegisterRequest.java
    - solveit-api/src/main/java/com/solveit/service/AuthService.java
    - solveit-api/src/main/resources/application.yml
    - solveit-api/src/test/java/com/solveit/controller/AuthRegistrationIntegrationTest.java
key-decisions:
  - "Senhas são medidas em code points Unicode e bytes UTF-8, sem trim ou normalização."
  - "existsByEmail é somente pré-checagem; uk_usuario_email no MySQL é a autoridade final de unicidade."
  - "Somente a ConstraintViolationException que identifica exatamente uk_usuario_email é convertida em EMAIL_ALREADY_REGISTERED."
  - "ProblemDetailFactory centraliza type, title, status, detail, instance, code e errors para reutilização futura pela cadeia de segurança."
patterns-established:
  - "Erros públicos são construídos com texto controlado e nunca derivados de mensagens JDBC."
  - "Falhas esperadas de integridade são materializadas com saveAndFlush dentro da fronteira transacional."
requirements-completed: [AUTH-01, AUTH-04]
coverage:
  - id: D1
    description: Senha exige 15 code points, aceita até 72 bytes UTF-8 e preserva o texto original
    requirement: AUTH-04
    verification:
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthRegistrationIntegrationTest.java
        status: pass
    human_judgment: false
  - id: D2
    description: Validação e e-mail duplicado retornam Problem Details 400/409 sem internals
    requirement: AUTH-01
    verification:
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthRegistrationIntegrationTest.java#returnsProblemDetailsForInvalidRegistrationFields
        status: pass
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthRegistrationIntegrationTest.java#returnsConflictProblemDetailsForSequentialDuplicateEmail
        status: pass
    human_judgment: false
  - id: D3
    description: Duas requisições concorrentes produzem um 201, um 409 e uma única linha no MySQL
    requirement: AUTH-01
    verification:
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthRegistrationIntegrationTest.java#resolvesConcurrentDuplicateEmailWithOneCreatedAndOneConflict
        status: pass
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthRegistrationIntegrationTest.java#keepsUnrelatedIntegrityViolationAsSafeInternalError
        status: pass
    human_judgment: false
duration: 24m
completed: 2026-09-06
status: complete
---

# Phase 2 Plan 2: Validação e erros seguros do cadastro Summary

**Cadastro agora valida a senha corretamente, publica erros previsíveis e resolve e-mail duplicado mesmo quando duas requisições chegam juntas.**

## Performance

- **Duration:** 24 min ativos
- **Started:** 2026-09-06T12:28:00-03:00
- **Completed:** 2026-09-06T12:52:00-03:00
- **Tasks:** 3
- **Files modified:** 10
- **Tests:** 18 na suíte completa, zero falhas, erros ou skips

## Accomplishments

- A senha é validada com mínimo de 15 code points Unicode e máximo de 72 bytes UTF-8; espaços, símbolos, acentos e emoji são preservados.
- Falhas de campo retornam `400 application/problem+json` com `VALIDATION_ERROR` e `errors` por campo.
- E-mail duplicado retorna `409` com `EMAIL_ALREADY_REGISTERED` e o detalhe público `E-mail já cadastrado`.
- A corrida de duas requisições é resolvida pela constraint `uk_usuario_email`: exatamente uma criação e um conflito.
- Violações de integridade não relacionadas ao e-mail permanecem `500 INTERNAL_ERROR` e não são classificadas incorretamente.
- Corpos e logs testados não expõem senha, hash BCrypt, SQL, constraint ou stack trace.

## Task Commits

1. **Task 1 RED — fronteiras Unicode/UTF-8:** `e2dbac7` (`test`)
2. **Task 1 GREEN — validador customizado:** `a77a332` (`feat`)
3. **Task 2 RED — contrato Problem Details:** `9e53207` (`test`)
4. **Task 2 GREEN — factory e advice:** `6d6da2e` (`feat`)
5. **Task 3 RED — concorrência e classificação:** `a5e680e` (`test`)
6. **Task 3 GREEN — flush, constraint específica e logs seguros:** `c4d2398` (`feat`)

## Files Created/Modified

- `ValidPassword.java` e `PasswordValidator.java` — contrato de senha como Bean Validation reutilizável.
- `RegisterRequest.java` — aplica `@ValidPassword` sem `@NotBlank` ou transformação da senha.
- `ApiErrorCode.java` — códigos públicos estáveis e URNs de tipo.
- `ProblemDetailFactory.java` — fábrica sem estado para a estrutura RFC 9457.
- `GlobalExceptionHandler.java` — converte validação, conflito e integridade desconhecida em respostas controladas.
- `EmailAlreadyRegisteredException.java` — falha de negócio independente de JDBC.
- `AuthService.java` — pré-checagem normalizada, `saveAndFlush` e classificação exata de `uk_usuario_email`.
- `application.yml` — silencia somente o logger JDBC bruto que expunha mensagens de constraint.
- `AuthRegistrationIntegrationTest.java` — cobre fronteiras, respostas, concorrência MySQL e não vazamento.

## Decisions Made

- `String.length()` não foi usado para o mínimo porque um emoji pode ocupar duas unidades UTF-16; `codePointCount` representa corretamente os caracteres Unicode do contrato.
- O limite máximo é medido depois da codificação UTF-8 porque BCrypt recebe bytes e rejeita entradas acima de 72 bytes.
- A pré-checagem `existsByEmail` evita trabalho desnecessário no caminho comum, mas não substitui a constraint única sob concorrência.
- A classificação consulta `ConstraintViolationException.getConstraintName()` e compara somente o nome simples `uk_usuario_email`; mensagens genéricas de “duplicate” não são aceitas.
- O logger `org.hibernate.engine.jdbc.spi.SqlExceptionHelper` foi desligado pontualmente; o handler mantém sinal operacional com `Request failed with code INTERNAL_ERROR`, sem anexar a exceção.

## Deviations from Plan

- `solveit-api/src/main/resources/application.yml` não constava na lista de arquivos do plano. Ele foi alterado porque o teste RED demonstrou que o Hibernate registrava SQL e o nome da constraint antes que o service pudesse tratar a exceção. A mudança é uma correção de segurança necessária para cumprir a exigência explícita de logs seguros.

## Issues Encountered

- Dentro do sandbox, o Maven Wrapper encontrou primeiro um erro ao inspecionar o diretório `.m2` e depois `AccessDeniedException` ao acessar um JAR. A execução fora do sandbox, já prevista pelo diagnóstico da fase, confirmou que Wrapper, cache Maven e Docker funcionam normalmente.
- O Spring ainda informa uma senha de desenvolvimento gerada enquanto não existe o `UserDetailsService`; form login e HTTP Basic permanecem desabilitados. O plano de login substituirá essa configuração.
- O Flyway mantém o aviso conhecido de suporte testado oficialmente até MySQL 8.1; as cinco migrations passaram no MySQL 8.4.11.

## TDD Gate Compliance

- Cada uma das três tarefas possui um commit RED que falhou pela ausência do comportamento planejado.
- Cada RED é seguido por um commit GREEN que fez os mesmos testes passarem sem reduzir as expectativas.
- A suíte final executou 18 testes com zero falhas, erros ou skips.

## Verification

- `mvnw.cmd -q '-Dtest=AuthRegistrationIntegrationTest' test` — passou, 10 testes.
- `mvnw.cmd -q test` — passou, 18 testes em 3 relatórios Surefire.
- Concorrência executada contra MySQL 8.4.11 real via Testcontainers.
- Nenhuma migration foi criada ou alterada.

## User Setup Required

None.

## Next Phase Readiness

O contrato de entrada e de erros do cadastro está pronto. O Plano 02-03 pode construir a fundação criptográfica JWT sem misturar validação, concorrência ou representação de erro.

## Self-Check: PASSED

- 10 arquivos da entrega encontrados no diff do plano.
- 6 commits RED/GREEN encontrados no histórico.
- Suíte completa passou com 18 testes.

---
*Phase: 02-autentica-o*
*Completed: 2026-09-06*
