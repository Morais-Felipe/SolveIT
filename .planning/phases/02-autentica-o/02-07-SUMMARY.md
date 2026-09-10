---
phase: 02-autentica-o
plan: 07
subsystem: auth
tags: [spring-security, jwt, problem-details, authorization-errors, testcontainers, tdd]
requires:
  - phase: 02-autentica-o
    plan: 06
    provides: Autenticação Bearer stateless com usuário atual do MySQL e endpoint /api/auth/me
provides:
  - Classificação interna de token inválido, expirado e conta inativa sem vazar detalhes do JJWT
  - Respostas 401 RFC 9457 distintas para credencial ausente, inválida, expirada e conta inativa
  - Resposta 403 ACCESS_DENIED pelo mesmo factory e serializador usados no contrato de erros
affects: [02-autentica-o, authorization, frontend-auth, protected-endpoints, api-errors]
actuals:
  tasks: 2
  commits: 4
tech-stack:
  added: []
  patterns: [typed authentication failure, AuthenticationEntryPoint, AccessDeniedHandler, shared ProblemDetail writer, TDD RED-GREEN]
key-files:
  created:
    - solveit-api/src/main/java/com/solveit/security/AuthenticationFailureReason.java
    - solveit-api/src/main/java/com/solveit/security/RestAuthenticationEntryPoint.java
    - solveit-api/src/main/java/com/solveit/security/RestAccessDeniedHandler.java
  modified:
    - solveit-api/src/main/java/com/solveit/security/JwtService.java
    - solveit-api/src/main/java/com/solveit/security/JwtAuthenticationFilter.java
    - solveit-api/src/main/java/com/solveit/exception/ApiErrorCode.java
    - solveit-api/src/main/java/com/solveit/exception/ProblemDetailFactory.java
    - solveit-api/src/main/java/com/solveit/config/SecurityConfig.java
    - solveit-api/src/test/java/com/solveit/security/JwtServiceTest.java
    - solveit-api/src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java
key-decisions:
  - "O JwtService classifica expiração antes da captura ampla de JwtException e entrega ao filtro somente razões internas controladas."
  - "Token ausente permanece anônimo; qualquer Authorization presente fora do formato Bearer aceito é classificado como token inválido."
  - "AuthenticationEntryPoint e AccessDeniedHandler escrevem o mesmo RFC 9457 pelo ProblemDetailFactory, sem usar mensagens das exceções recebidas."
requirements-completed: [AUTH-05]
coverage:
  - id: D1
    description: Falhas de esquema, formato, assinatura, algoritmo, issuer, audience, expiração, subject, usuário e atividade são classificadas com segurança
    requirement: AUTH-05
    verification:
      - kind: unit
        ref: solveit-api/src/test/java/com/solveit/security/JwtServiceTest.java#classifiesExpiredTokenSeparatelyForAuthentication
        status: pass
      - kind: unit
        ref: solveit-api/src/test/java/com/solveit/security/JwtServiceTest.java#classifiesEveryOtherJwtRejectionAsInvalidForAuthentication
        status: pass
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java#classifiesEveryInvalidBearerShapeWithoutAuthenticatingTheRequest
        status: pass
    human_judgment: false
  - id: D2
    description: Token ausente, inválido, expirado e conta inativa recebem 401 com códigos e mensagens D-18 exatos
    requirement: AUTH-05
    verification:
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java#returnsProblemDetailsWhenBearerIsMissing
        status: pass
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java#returnsInvalidProblemDetailsWithoutLeakingBearerInternals
        status: pass
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java#returnsExpiredProblemDetailsForExpiredBearer
        status: pass
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java#returnsInactiveAccountProblemDetailsOnlyAfterValidBearerIdentification
        status: pass
    human_judgment: false
  - id: D3
    description: Identidade autenticada sem permissão recebe 403 ACCESS_DENIED no formato RFC 9457
    requirement: AUTH-05
    verification:
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java#returnsAccessDeniedProblemDetailsForAuthenticatedIdentity
        status: pass
    human_judgment: false
  - id: D4
    description: Corpo e logs de falhas de segurança não expõem Bearer, segredo, SQL, stack ou classes do JJWT
    requirement: AUTH-05
    verification:
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java#returnsInvalidProblemDetailsWithoutLeakingBearerInternals
        status: pass
      - kind: other
        ref: .\solveit-api\mvnw.cmd -f .\solveit-api\pom.xml test
        status: pass
    human_judgment: false
duration: 28m active
completed: 2026-09-10
status: complete
---

# Phase 2 Plan 7: Falhas Bearer e Problem Details de segurança Summary

**Falhas Bearer classificadas internamente e convertidas em respostas 401/403 RFC 9457 estáveis, legíveis e sem vazamento de detalhes técnicos.**

## Performance

- **Duration:** 28 min ativos, com retomada após limite de uso
- **Started:** 2026-09-09T15:22:00-03:00
- **Completed:** 2026-09-10
- **Tasks:** 2
- **Files modified:** 10
- **Tests:** 51 na suíte completa, zero falhas, erros ou skips

## Accomplishments

- `JwtService` distingue expiração das demais rejeições JWT e o filtro classifica formato, claims, subject, existência e atividade sem expor exceções internas.
- Token ausente, inválido, expirado e de conta inativa retornam 401 com `application/problem+json;charset=UTF-8`, códigos estáveis e textos D-18 exatos.
- Uma identidade válida sem permissão recebe 403 `ACCESS_DENIED` pelo mesmo `ProblemDetailFactory`; o disparo por regra de perfil será provado no Plano 02-08.
- A matriz negativa verifica que corpo e logs não contêm Authorization, Bearer completo, segredo, SQL, stack trace ou classes do JJWT.

## Task Commits

1. **RED — classificação das falhas Bearer:** `b94722c` (`test`)
2. **GREEN — razões internas controladas:** `0557150` (`feat`)
3. **RED — contrato Problem Details 401/403:** `7f7b056` (`test`)
4. **GREEN — handlers REST e writer comum:** `eebe5af` (`feat`)

## Files Created/Modified

- `AuthenticationFailureReason.java` — enum interno para token inválido, expirado e conta inativa.
- `JwtService.java` — retorna claims aceitos ou motivo controlado, capturando expiração antes de `JwtException` ampla.
- `JwtAuthenticationFilter.java` — valida o formato Bearer, recarrega a conta e anexa o motivo seguro à requisição rejeitada.
- `RestAuthenticationEntryPoint.java` — converte ausência ou motivo interno nos quatro contratos 401 D-18.
- `RestAccessDeniedHandler.java` — escreve o contrato 403 `ACCESS_DENIED` sem usar detalhes da exceção.
- `ApiErrorCode.java` — inclui os cinco códigos públicos de segurança omitidos no plano original.
- `ProblemDetailFactory.java` — centraliza status, UTF-8, media type e serialização do Problem Details.
- `SecurityConfig.java` — registra entry point e denied handler na cadeia do Spring Security.
- `JwtServiceTest.java` e `AuthSecurityIntegrationTest.java` — cobrem classificação, respostas e ausência de vazamentos.

## Decisions Made

- O motivo da falha é um enum interno e nunca uma mensagem do parser JWT.
- Cabeçalho ausente segue anônimo para gerar `AUTH_TOKEN_MISSING`; cabeçalho presente com esquema ou forma inválida gera `AUTH_TOKEN_INVALID`.
- Usuário ausente continua indistinguível de token inválido; `AUTH_ACCOUNT_INACTIVE` só aparece após token válido identificar a conta.
- O `ProblemDetailFactory` recebe o `ObjectMapper` configurado pelo Spring e escreve o mesmo wire contract para 401 e 403.

## Deviations from Plan

None — o plano corrigido e aprovado foi executado conforme apresentado.

## Issues Encountered

- O primeiro comando Maven focado precisou ter o argumento `-Dtest` colocado entre aspas porque o PowerShell interpretou a vírgula como sintaxe própria; nenhum teste havia iniciado nessa tentativa.
- O Git manteve avisos informativos de conversão LF para CRLF no Windows.
- O Flyway manteve o aviso conhecido de suporte testado oficialmente até MySQL 8.1; todas as cinco migrations passaram no MySQL 8.4.11.
- O limite de uso interrompeu a conexão com a execução final; os sete relatórios Surefire persistidos comprovaram depois a conclusão dos 51 testes.
- Na retomada, o sandbox apresentou `setup refresh had errors`; as consultas locais de recuperação foram executadas fora dele, sem alterar código ou acessar serviços externos.

## TDD Gate Compliance

- O RED `b94722c` executou 15 testes e falhou em 3 expectativas porque o filtro ainda não fornecia motivo controlado; os 9 testes unitários existentes permaneceram verdes.
- O GREEN `0557150` fez os 11 testes unitários e os 6 testes de integração passarem sem reduzir assertions.
- O RED `7f7b056` executou 11 testes de integração: quatro falharam com corpo/content type ausentes e um pela ausência do denied handler.
- O GREEN `eebe5af` fez os 11 testes de integração passarem com contrato completo e verificações negativas de vazamento.

## Verification

- `mvnw.cmd -Dtest=JwtServiceTest test` — 11 testes, zero falhas, erros ou skips.
- `mvnw.cmd -Dtest=AuthSecurityIntegrationTest test` — 11 testes, zero falhas, erros ou skips.
- `mvnw.cmd test` — 51 testes em 7 relatórios Surefire, zero falhas, erros ou skips.
- MySQL 8.4.11, cinco migrations Flyway, FilterChainProxy, MockMvc, `Clock` controlável, serialização RFC 9457 e captura de logs foram exercitados.

## User Setup Required

Nenhuma configuração nova. O segredo JWT externo exigido pelos planos anteriores permanece necessário fora do perfil `test`.

## Next Phase Readiness

O Plano 02-08 pode adicionar o probe exclusivo de teste para comprovar 401 versus 403 pela cadeia completa, revisar a matriz de autorização e concluir a Fase 2.

## Self-Check: PASSED

- Os três arquivos criados e sete arquivos modificados existem no histórico de implementação.
- Os quatro commits RED/GREEN do Plano 02-07 foram encontrados.
- 51 testes passaram com zero falhas, erros ou skips.
- `AUTH-05` está coberto por classificação, respostas públicas e provas negativas de vazamento.

---
*Phase: 02-autentica-o*
*Completed: 2026-09-10*
