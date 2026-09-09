---
phase: 02-autentica-o
plan: 05
subsystem: auth
tags: [spring-security, problem-details, anti-enumeration, secure-logging, tdd]
requires:
  - phase: 02-autentica-o
    plan: 04
    provides: Login stateless pelo provider com emissão JWT após autenticação
provides:
  - Resposta 401 RFC 9457 idêntica para usuário inexistente, senha incorreta e conta inativa
  - Código público AUTH_CREDENTIALS_INVALID com mensagem controlada
  - Provas de ausência de token, sessão, cookie e material sensível em respostas e logs
affects: [02-autentica-o, bearer-filter, auth-errors, frontend-login, production-security]
actuals:
  tasks: 1
  commits: 4
tech-stack:
  added: []
  patterns: [exception translation, public error boundary, parameterized integration test, TDD RED-GREEN]
key-files:
  created:
    - solveit-api/src/main/java/com/solveit/exception/InvalidCredentialsException.java
  modified:
    - .planning/phases/02-autentica-o/02-05-PLAN.md
    - solveit-api/src/main/java/com/solveit/exception/ApiErrorCode.java
    - solveit-api/src/main/java/com/solveit/exception/GlobalExceptionHandler.java
    - solveit-api/src/main/java/com/solveit/service/AuthService.java
    - solveit-api/src/test/java/com/solveit/controller/AuthLoginIntegrationTest.java
key-decisions:
  - "Toda AuthenticationException do login é convertida para uma única InvalidCredentialsException, sem inspeção pública da causa."
  - "O contrato público usa 401, application/problem+json, AUTH_CREDENTIALS_INVALID e a mensagem E-mail ou senha inválidos."
  - "Tentativas inválidas não geram log detalhado; os testes proíbem credenciais, hash, segredo, token, SQL, stack e classes internas."
  - "Rate limiting permanece fora da Fase 2, mas deve ser decidido e implementado antes de exposição à internet."
patterns-established:
  - "Falhas internas de autenticação são traduzidas em uma exceção de domínio antes da fronteira HTTP."
  - "Matrizes de segurança usam testes parametrizados para aplicar exatamente o mesmo contrato a todas as causas."
requirements-completed: [AUTH-02, AUTH-04]
coverage:
  - id: D1
    description: Usuário inexistente, senha incorreta e conta inativa retornam o mesmo 401 RFC 9457 e o mesmo conjunto de campos
    requirement: AUTH-02
    verification:
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthLoginIntegrationTest.java#returnsTheSameSafeProblemForEveryInvalidCredential
        status: pass
    human_judgment: false
  - id: D2
    description: Falhas de login não emitem token, não criam sessão/cookie e não expõem credenciais ou detalhes internos em resposta/log
    requirement: AUTH-04
    verification:
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthLoginIntegrationTest.java#returnsTheSameSafeProblemForEveryInvalidCredential
        status: pass
      - kind: integration
        ref: .\solveit-api\mvnw.cmd -f .\solveit-api\pom.xml -q test
        status: pass
    human_judgment: false
duration: 20m
completed: 2026-09-09
status: complete
---

# Phase 2 Plan 5: Falhas uniformes de login Summary

**Login resistente à enumeração de contas por meio de um único 401 Problem Details para todas as falhas de credencial.**

## Performance

- **Duration:** 20 min ativos
- **Started:** 2026-09-09T13:30:00-03:00
- **Completed:** 2026-09-09T13:50:00-03:00
- **Tasks:** 1
- **Files modified:** 6
- **Tests:** 38 na suíte completa, zero falhas, erros ou skips

## Accomplishments

- E-mail inexistente, senha errada e conta inativa agora retornam exatamente `401 AUTH_CREDENTIALS_INVALID`.
- As três respostas usam `application/problem+json`, seis campos públicos idênticos e a mensagem “E-mail ou senha inválidos.”
- `AuthService` converte a classe-base `AuthenticationException` sem examinar ou revelar a causa.
- Falhas não emitem JWT, não criam cookie/sessão e não registram corpo, e-mail, senha, hash, segredo ou detalhes internos.
- O caminho feliz do Plano 04 continua autenticando pelo provider, apagando credenciais e emitindo o token somente após sucesso.

## Task Commits

1. **Correção de escopo aprovada — incluir ApiErrorCode:** `adc385f` (`docs`)
2. **RED — matriz de credenciais inválidas:** `056809b` (`test`)
3. **RED — mídia RFC 9457 explícita:** `1e3a3ed` (`test`)
4. **GREEN — tradução uniforme para 401:** `fd5fdf2` (`feat`)

## Files Created/Modified

- `02-05-PLAN.md` — inclui `ApiErrorCode.java` na lista de arquivos e na leitura obrigatória.
- `InvalidCredentialsException.java` — representa uma única falha pública de credencial.
- `ApiErrorCode.java` — adiciona o código estável `AUTH_CREDENTIALS_INVALID`.
- `GlobalExceptionHandler.java` — produz o Problem Details controlado com status 401.
- `AuthService.java` — converte somente falhas do `AuthenticationManager`; emissão JWT permanece depois do sucesso.
- `AuthLoginIntegrationTest.java` — executa a mesma prova para as três causas e captura respostas/logs.
- `UsuarioDetailsService.java` foi inspecionado e preservado porque já usa mensagem interna genérica sem e-mail.

## Decisions Made

- A fronteira pública não diferencia `BadCredentialsException`, `DisabledException` nem usuário inexistente.
- O handler não usa a mensagem ou a causa da exceção para montar a resposta; título e detalhe são constantes do contrato.
- Falhas normais de login não são registradas individualmente com detalhes sensíveis. Observabilidade futura deve usar métricas agregadas e dados não identificáveis.
- `401` representa ausência de identidade válida; `403` continuará reservado a uma identidade reconhecida sem permissão.

## Security Risk Before Production

O endpoint público continua sujeito a credential stuffing e brute force. Conforme `02-RESEARCH.md — Open Questions (RESOLVED)`, rate limiting está explicitamente fora da implementação da Fase 2 para evitar introduzir silenciosamente biblioteca ou estado distribuído. O controle deve ser escolhido, implementado e verificado antes de expor o SolveIT à internet. Esta entrega cobre o subconjunto aplicável do ASVS 5.0 L1 e não declara conformidade integral.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] ApiErrorCode ausente da lista original de arquivos**
- **Found before:** Task 1
- **Issue:** O plano exigia `AUTH_CREDENTIALS_INVALID`, mas não listava o enum que materializa códigos públicos.
- **Fix:** Após aprovação explícita, o plano passou a incluir o arquivo em `files_modified`, `<files>` e `<read_first>`.
- **Committed in:** `adc385f`

**2. [Rule 2 - Missing Critical] Content-Type não estava afirmado no primeiro teste RED**
- **Found during:** revisão entre RED e GREEN
- **Issue:** O contrato RFC 9457 também exige `application/problem+json`; a execução RED já mostrava `Content type = null`.
- **Fix:** O teste passou a exigir mídia compatível antes do GREEN.
- **Committed in:** `1e3a3ed`

---

**Total deviations:** 2 auto-fixed (2 missing critical).
**Impact on plan:** Ambos os ajustes tornam verificáveis contratos já aprovados, sem ampliar o comportamento do produto.

## Issues Encountered

- O RED recebeu `401` vazio nas três causas; os testes falharam corretamente pela ausência de `type`, corpo e Content-Type RFC 9457.
- O Git detectou proprietários diferentes no `.git`; os comandos usaram `-c safe.directory=C:/SolveIT` por invocação, sem modificar configuração global.
- `init execute-phase` voltou a reportar `agent_runtime: claude`; a tarefa sequencial não dependeu desse campo e terminou normalmente.
- O Flyway mantém o aviso conhecido de suporte testado oficialmente até MySQL 8.1; todas as cinco migrations passaram no MySQL 8.4.11.
- A suíte completa exibiu logs dos pools Hikari tentando reconectar após o encerramento de contêineres anteriores. Os bancos ativos, 38 testes e migrations concluíram normalmente.

## TDD Gate Compliance

- O commit RED `056809b` executou 7 testes: os 4 anteriores passaram e os 3 novos falharam no mesmo ponto porque o 401 ainda tinha corpo vazio.
- O commit `1e3a3ed` tornou explícita a mídia RFC 9457 que também estava ausente na saída RED.
- O commit GREEN `fd5fdf2` fez as 7 execuções passarem sem reduzir expectativas e sem criar ramificações por causa.

## Verification

- `mvnw.cmd -q "-Dtest=AuthLoginIntegrationTest" test` — RED: 7 testes, 3 falhas esperadas pela ausência do Problem Details.
- `mvnw.cmd -q "-Dtest=AuthLoginIntegrationTest" test` — GREEN: 7 testes, zero falhas, erros ou skips.
- `mvnw.cmd -q test` — passou, 38 testes em 6 relatórios Surefire.
- MySQL 8.4.11, cinco migrations Flyway, provider Spring Security, MockMvc e captura de logs foram exercitados.

## User Setup Required

Nenhuma configuração nova. O segredo JWT externo exigido pelos Planos 03/04 permanece necessário fora do perfil `test`.

## Next Phase Readiness

O Plano 02-06 pode consumir o Bearer em cada requisição, recarregar perfil/estado atual do banco e implementar `GET /api/auth/me`. Erros específicos de token e acesso serão completados nos Planos 07 e 08.

## Self-Check: PASSED

- Nova exceção e quatro arquivos modificados encontrados no histórico de implementação.
- Commits de escopo, RED, refinamento do contrato e GREEN encontrados.
- 38 testes passaram com zero falhas, erros ou skips.
- Nenhum refresh token, cookie, sessão ou rate limiting foi introduzido.

---
*Phase: 02-autentica-o*
*Completed: 2026-09-09*
