---
phase: 02-autentica-o
plan: 06
subsystem: auth
tags: [spring-security, jwt, bearer-filter, security-context, testcontainers, tdd]
requires:
  - phase: 02-autentica-o
    plan: 05
    provides: Login stateless com JWT e resposta uniforme para credenciais inválidas
provides:
  - Autenticação Bearer por requisição com perfil e atividade recarregados do MySQL
  - GET /api/auth/me baseado exclusivamente no ID tipado do principal autenticado
  - Contextos de segurança independentes, sem hash, HttpSession ou cookie
affects: [02-autentica-o, auth-errors, authorization, frontend-auth, protected-endpoints]
actuals:
  tasks: 2
  commits: 4
tech-stack:
  added: []
  patterns: [OncePerRequestFilter, current-state lookup, typed principal, stateless SecurityContext, TDD RED-GREEN]
key-files:
  created:
    - solveit-api/src/main/java/com/solveit/security/JwtAuthenticationFilter.java
    - solveit-api/src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java
  modified:
    - solveit-api/src/main/java/com/solveit/config/SecurityConfig.java
    - solveit-api/src/main/java/com/solveit/security/UsuarioPrincipal.java
    - solveit-api/src/main/java/com/solveit/controller/AuthController.java
    - solveit-api/src/main/java/com/solveit/service/AuthService.java
key-decisions:
  - "O Bearer fornece somente o sub assinado; perfil e atividade são recarregados do banco em toda requisição."
  - "O principal criado pelo filtro não copia o hash e entra em um SecurityContext vazio específico da requisição."
  - "GET /api/auth/me usa somente UsuarioPrincipal.getId(), ignora identificadores do cliente e responde com UsuarioResponse."
requirements-completed: [AUTH-03, AUTH-05]
coverage:
  - id: D1
    description: Bearer válido autentica com perfil e atividade atuais; inatividade, exclusão e expiração bloqueiam o token
    requirement: AUTH-05
    verification:
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java#reloadsProfileAndActiveStateOnEveryRequest
        status: pass
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java#reactivatedAccountCannotReuseAnExpiredToken
        status: pass
    human_judgment: false
  - id: D2
    description: GET /api/auth/me retorna dados atuais e públicos do próprio usuário, sem aceitar ID escolhido pelo cliente
    requirement: AUTH-03
    verification:
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java#returnsOnlyTheAuthenticatedUsersCurrentPublicDataFromMe
        status: pass
    human_judgment: false
  - id: D3
    description: Requisições paralelas mantêm principais independentes e não criam sessão, cookie ou contexto persistente
    requirement: AUTH-05
    verification:
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java#keepsParallelRequestSecurityContextsIndependent
        status: pass
      - kind: integration
        ref: .\solveit-api\mvnw.cmd -f .\solveit-api\pom.xml -q -Dtest=AuthSecurityIntegrationTest test
        status: pass
    human_judgment: false
duration: 28m
completed: 2026-09-09
status: complete
---

# Phase 2 Plan 6: Bearer por requisição e identidade atual Summary

**JWT Bearer transformado em identidade atual do MySQL a cada requisição, com `/api/auth/me` vinculado exclusivamente ao principal autenticado.**

## Performance

- **Duration:** 28 min ativos
- **Started:** 2026-09-09T14:12:00-03:00
- **Completed:** 2026-09-09T14:40:00-03:00
- **Tasks:** 2
- **Files modified:** 6
- **Tests:** 43 na suíte completa, zero falhas, erros ou skips

## Accomplishments

- `JwtAuthenticationFilter` valida o Bearer, converte `sub` em `Long` e recarrega usuário, perfil e atividade do MySQL em toda requisição.
- Conta inativa ou removida deixa de autenticar imediatamente; uma conta reativada recupera apenas token ainda não expirado.
- O principal Bearer nunca recebe o hash da senha e cada chamada usa um `SecurityContext` novo, sem sessão HTTP.
- Requisições paralelas com tokens de usuários diferentes mantêm IDs e authorities separados por thread.
- `GET /api/auth/me` retorna os seis campos públicos atuais do próprio usuário e ignora `userId` forjado na query.

## Task Commits

1. **RED — comportamento Bearer por requisição:** `98eecae` (`test`)
2. **GREEN — filtro com estado atual e contexto stateless:** `568d074` (`feat`)
3. **RED — contrato de GET /api/auth/me:** `8a933b1` (`test`)
4. **GREEN — consulta do usuário autenticado:** `5590ca3` (`feat`)

## Files Created/Modified

- `JwtAuthenticationFilter.java` — lê Bearer, valida JWT e cria autenticação com estado atual do usuário.
- `AuthSecurityIntegrationTest.java` — cobre fluxo register-login-bearer, mutações entre requisições, expiração, paralelismo e `/me`.
- `SecurityConfig.java` — posiciona o filtro JWT antes de `UsernamePasswordAuthenticationFilter`.
- `UsuarioPrincipal.java` — oferece criação sem credenciais para o caminho Bearer.
- `AuthController.java` — expõe `GET /api/auth/me` por `@AuthenticationPrincipal`.
- `AuthService.java` — busca o usuário pelo ID do principal e centraliza o mapeamento para `UsuarioResponse`.

## Decisions Made

- O JWT continua contendo somente identidade técnica; authorities nunca são lidas de claims.
- Falhas de parsing, subject inválido, usuário ausente ou inativo não criam autenticação. O Plano 02-07 classificará cada resposta pública.
- O filtro cria um principal sem credenciais e um contexto vazio, deixando o ciclo de limpeza por requisição sob a cadeia stateless do Spring Security.
- `/me` não declara parâmetro de ID e consulta o registro pelo `Long` fornecido pelo principal autenticado.

## Deviations from Plan

None — o plano foi executado conforme aprovado.

## Issues Encountered

- O primeiro comando de leitura usou uma raiz Java presumida sem `solveit-api`; a leitura falhou sem alterar arquivos e foi repetida pelos caminhos encontrados com `rg --files`.
- O Git manteve avisos informativos de conversão LF para CRLF no Windows.
- O Flyway mantém o aviso conhecido de suporte testado oficialmente até MySQL 8.1; todas as cinco migrations passaram no MySQL 8.4.11.
- A suíte completa exibiu tentativas de reconexão de pools Hikari pertencentes a containers anteriores já encerrados; os pools ativos e os 43 testes concluíram normalmente.
- `init execute-phase` voltou a reportar `agent_runtime: claude`; a execução inline não dependeu desse campo.

## TDD Gate Compliance

- O RED `98eecae` compilou e executou quatro testes: três falharam com `401` em vez de `200` e um encontrou principal nulo porque ainda não havia filtro Bearer.
- O GREEN `568d074` fez os quatro cenários passarem; a prova adicional de usuário removido também passou.
- O RED `8a933b1` autenticou o Bearer e falhou com `404` porque `/api/auth/me` ainda não existia.
- O GREEN `5590ca3` fez o contrato `/me` passar sem reduzir assertions.

## Verification

- `mvnw.cmd -q -Dtest=AuthSecurityIntegrationTest test` — GREEN: 5 testes, zero falhas, erros ou skips.
- `mvnw.cmd -q test` — 43 testes em 7 relatórios Surefire, zero falhas, erros ou skips.
- MySQL 8.4.11, cinco migrations Flyway, FilterChainProxy, MockMvc, Clock controlável e chamadas paralelas foram exercitados.

## User Setup Required

Nenhuma configuração nova. O segredo JWT externo exigido pelos planos anteriores permanece necessário fora do perfil `test`.

## Next Phase Readiness

O Plano 02-07 pode classificar e padronizar em Problem Details as recusas por token ausente, inválido, expirado e conta inativa. `AUTH-05` permanece globalmente pendente até esse contrato negativo ser concluído.

## Self-Check: PASSED

- Os dois arquivos criados e quatro arquivos modificados existem no histórico de implementação.
- Os quatro commits RED/GREEN do Plano 02-06 foram encontrados.
- 43 testes passaram com zero falhas, erros ou skips.
- Nenhum cookie, refresh token, sessão HTTP ou authority derivada do JWT foi introduzido.

---
*Phase: 02-autentica-o*
*Completed: 2026-09-09*
