---
phase: 02-autentica-o
plan: 03
subsystem: auth
tags: [jwt, jjwt, hs256, configuration-properties, clock]
requires:
  - phase: 02-autentica-o
    plan: 02
    provides: Cadastro validado, erros seguros e configuração Spring Security stateless
provides:
  - Configuração JWT tipada que falha na inicialização para segredo ausente, inválido ou fraco
  - Emissão e validação de JWT HS256 com issuer, audience e TTL configuráveis
  - Payload mínimo com sub, iat, exp, iss e aud
  - Relógio UTC injetável para expiração determinística
affects: [02-autentica-o, login, bearer-filter, api-security, frontend]
actuals:
  tokens: 4658
  tasks: 2
  commits: 3
tech-stack:
  added: [JJWT 0.13.0]
  patterns: [ConfigurationProperties validado, startup fail-closed, algoritmo JWT em allowlist, Clock injetável, TDD RED-GREEN]
key-files:
  created:
    - solveit-api/src/main/java/com/solveit/config/JwtProperties.java
    - solveit-api/src/main/java/com/solveit/security/JwtService.java
    - solveit-api/src/test/java/com/solveit/config/JwtPropertiesContextTest.java
    - solveit-api/src/test/java/com/solveit/security/JwtServiceTest.java
    - solveit-api/src/test/resources/application-test.yml
  modified:
    - solveit-api/pom.xml
    - solveit-api/src/main/java/com/solveit/config/SecurityConfig.java
    - solveit-api/src/main/resources/application.yml
key-decisions:
  - "O segredo JWT é Base64 externo, decodifica para no mínimo 32 bytes e nunca possui default versionado."
  - "O parser aceita somente HS256 e exige issuer e audience configurados antes de entregar as claims."
  - "O payload contém apenas sub, iat, exp, iss e aud; perfil, estado e dados pessoais permanecem fora do token."
  - "Clock.systemUTC é um bean da aplicação e pode ser substituído por Clock fixo nos testes."
patterns-established:
  - "Configurações de segurança são ligadas a objetos tipados e validadas durante a criação do contexto Spring."
  - "JWT recebido é tratado como entrada não confiável e validado por chave, algoritmo e claims registrados."
requirements-completed: [AUTH-02, AUTH-05]
coverage:
  - id: D1
    description: Configuração JWT válida é ligada e segredo ausente, Base64 inválido ou chave curta impedem a inicialização
    requirement: AUTH-05
    verification:
      - kind: unit
        ref: solveit-api/src/test/java/com/solveit/config/JwtPropertiesContextTest.java
        status: pass
    human_judgment: false
  - id: D2
    description: JwtService emite HS256 com claims mínimas e TTL configurado usando Clock injetável
    requirement: AUTH-02
    verification:
      - kind: unit
        ref: solveit-api/src/test/java/com/solveit/security/JwtServiceTest.java#issuesHs256TokenWithOnlyApprovedClaimsAndConfiguredTtl
        status: pass
      - kind: unit
        ref: solveit-api/src/test/java/com/solveit/security/JwtServiceTest.java#usesConfiguredTtlInsteadOfASecondHardCodedDuration
        status: pass
    human_judgment: false
  - id: D3
    description: Parser rejeita chave, assinatura, algoritmo, issuer, audience, expiração e formato inválidos
    requirement: AUTH-05
    verification:
      - kind: unit
        ref: solveit-api/src/test/java/com/solveit/security/JwtServiceTest.java
        status: pass
      - kind: integration
        ref: .\solveit-api\mvnw.cmd -f .\solveit-api\pom.xml -q test
        status: pass
    human_judgment: false
duration: 20m
completed: 2026-09-08
status: complete
---

# Phase 2 Plan 3: Fundação criptográfica JWT Summary

**JWT HS256 temporário e mínimo, com segredo externo validado, parser restrito e expiração determinística por Clock.**

## Performance

- **Duration:** 20 min ativos
- **Started:** 2026-09-08T12:45:00-03:00
- **Completed:** 2026-09-08T13:05:00-03:00
- **Tasks:** 2
- **Files modified:** 8
- **Tests:** 31 na suíte completa, zero falhas, erros ou skips

## Accomplishments

- A aplicação exige segredo Base64 externo com pelo menos 32 bytes decodificados e falha durante o startup para configuração insegura.
- `JwtService` emite e valida tokens HS256 com `sub`, `iat`, `exp`, `iss` e `aud=solveit-api`.
- O parser rejeita algoritmo diferente, assinatura ou chave incorreta, issuer/audience inesperados, token expirado e formato malformado.
- Nome, e-mail, perfil e estado ativo não entram no token; o estado atual continuará vindo do banco em cada requisição protegida.
- O access token usa TTL configurável de uma hora e não introduz refresh token, cookie ou sessão HTTP.

## Task Commits

1. **Task 1 — dependências e configuração JWT validada:** `b13b1cc` (`feat`)
2. **Task 2 RED — contrato de emissão e parsing:** `2b512c0` (`test`)
3. **Task 2 GREEN — implementação HS256:** `c60cada` (`feat`)

## Files Created/Modified

- `pom.xml` — declara `jjwt-api` em compile e `jjwt-impl`/`jjwt-jackson` em runtime.
- `JwtProperties.java` — liga secret, issuer, audience e TTL e aplica validação fail-closed.
- `application.yml` — externaliza as quatro propriedades JWT sem segredo padrão.
- `application-test.yml` — fornece uma fixture Base64 pública somente para testes.
- `JwtPropertiesContextTest.java` — prova um contexto válido e três configurações inválidas.
- `JwtService.java` — emite e valida JWT com HS256, claims registradas e Clock.
- `JwtServiceTest.java` — cobre claims, TTL e a matriz criptográfica negativa.
- `SecurityConfig.java` — fornece o bean `Clock.systemUTC()` usado pelo serviço.

## Decisions Made

- HS256 é fixado em uma allowlist de um único algoritmo; o valor `alg` recebido no token não escolhe o algoritmo aceito pela API.
- A audiência técnica `solveit-api` é emitida e exigida para evitar reutilização acidental do token em outro serviço.
- A chave é criada por `Keys.hmacShaKeyFor`, e o parser/assinatura ficam sob responsabilidade do JJWT em vez de código HMAC próprio.
- O serviço retorna as `Claims` já verificadas; o futuro filtro converterá `sub` para `Long` e consultará o usuário atual no banco.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Bean de Clock adicionado à configuração de segurança**
- **Found during:** Task 2 (Emitir e validar JWT HS256 com Clock)
- **Issue:** O plano exigia injeção de `Clock`, mas não listava onde o bean de produção seria criado.
- **Fix:** `SecurityConfig` passou a fornecer `Clock.systemUTC()`.
- **Files modified:** `solveit-api/src/main/java/com/solveit/config/SecurityConfig.java`
- **Verification:** A suíte completa iniciou os contextos Spring e executou 31 testes.
- **Committed in:** `c60cada`

---

**Total deviations:** 1 auto-fixed (1 missing critical).
**Impact on plan:** A alteração é necessária para cumprir a injeção prevista e não muda o contrato HTTP nem a estratégia Bearer aprovada.

## Issues Encountered

- `DurationMin` não está disponível no Spring Boot 3.5.16 usado pelo projeto. A duração positiva passou a ser validada por `@AssertTrue` com `Duration.isZero()` e `isNegative()`.
- O parser auxiliar do primeiro teste usou inicialmente o relógio real e considerou expirada a fixture temporal. Ele passou a receber o mesmo Clock fixo do cenário.
- O Flyway mantém o aviso conhecido de suporte testado oficialmente até MySQL 8.1; as cinco migrations passaram no MySQL 8.4.11.
- O Spring ainda informa uma senha de desenvolvimento até o `UserDetailsService` real ser introduzido pelo plano de login.

## TDD Gate Compliance

- O commit RED `2b512c0` compilou a API pública e executou 9 testes que falharam por ausência do comportamento, sem erro de sintaxe ou importação.
- O commit GREEN `c60cada` fez os mesmos 9 testes passarem sem reduzir as expectativas.
- Nenhum refactor separado foi necessário após o GREEN.

## Verification

- `mvnw.cmd -q -Dtest=JwtPropertiesContextTest test` — passou, 4 testes.
- `mvnw.cmd -q -Dtest=JwtServiceTest test` — passou, 9 testes.
- `mvnw.cmd -q -Dtest=JwtPropertiesContextTest,JwtServiceTest test` — passou, 13 testes.
- `mvnw.cmd -q test` — passou, 31 testes em 5 relatórios Surefire.
- MySQL 8.4.11 e as cinco migrations Flyway foram exercitados pela suíte completa.

## User Setup Required

- Fora do perfil `test`, definir `SOLVEIT_JWT_SECRET` com Base64 que decodifique para pelo menos 32 bytes.
- `SOLVEIT_JWT_ISSUER`, `SOLVEIT_JWT_AUDIENCE` e `SOLVEIT_JWT_ACCESS_TOKEN_TTL` são opcionais; os padrões são `solveit-api`, `solveit-api` e `1h`.

## Next Phase Readiness

O núcleo JWT está pronto para o Plano 02-04 comprovar credenciais pelo Spring Security e emitir `accessToken` somente após login válido. O transporte continua Bearer stateless, sem cookie, refresh token ou armazenamento React nesta fase.

## Self-Check: PASSED

- 8 arquivos da entrega encontrados no diff do plano.
- Commits de configuração, RED e GREEN encontrados no histórico.
- 31 testes passaram com zero falhas, erros ou skips.
- Nenhum segredo real ou valor padrão de segredo foi versionado.

---
*Phase: 02-autentica-o*
*Completed: 2026-09-08*
