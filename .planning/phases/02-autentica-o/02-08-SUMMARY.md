---
phase: 02-autentica-o
plan: 08
subsystem: auth
tags: [spring-security, method-security, authorization, testcontainers, mockmvc, tdd]
requires:
  - phase: 02-autentica-o
    plan: 07
    provides: Autenticação Bearer stateless, perfil atual do MySQL e contratos 401/403 RFC 9457
provides:
  - Method Security habilitado para regras de perfil com @PreAuthorize
  - Prova integrada de autorização por perfil atual e relação persistida no servidor
  - Matriz 401/403/200 que ignora identidade fornecida pelo cliente
affects: [03-core-de-chamados, authorization, protected-endpoints, service-policies]
actuals:
  tokens: 2622
  tasks: 1
  commits: 2
tech-stack:
  added: []
  patterns: [method-level authorization, transactional relationship guard, trusted principal identity, test-only authorization probe, TDD RED-GREEN]
key-files:
  created: []
  modified:
    - solveit-api/src/main/java/com/solveit/config/SecurityConfig.java
    - solveit-api/src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java
key-decisions:
  - "O perfil atual limita quem pode tentar a operação por @PreAuthorize; a relação persistida limita sobre qual registro o usuário pode agir no service transacional."
  - "A identidade usada na autorização vem exclusivamente de UsuarioPrincipal; parâmetros userId fornecidos pelo cliente não participam da decisão."
  - "A Fase 2 habilita e prova o mecanismo com componentes exclusivos de teste; as políticas concretas de Chamado serão definidas e aplicadas na Fase 3."
patterns-established:
  - "Perfil no limite do método: use @PreAuthorize para a regra ampla de papel."
  - "Relação no service: carregue o recurso persistido e compare suas relações com o ID confiável do principal dentro da transação."
requirements-completed: [AUTH-06]
coverage:
  - id: D1
    description: Perfil atual do banco é aplicado pelo Spring Security antes da operação protegida
    requirement: AUTH-06
    verification:
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java#deniesCurrentNonAdminProfileAtTheMethodBoundary
        status: pass
    human_judgment: false
  - id: D2
    description: Guarda transacional valida a relação persistida com o ID do principal e ignora userId forjado
    requirement: AUTH-06
    verification:
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java#deniesAdminWhenPersistedRelationshipDoesNotMatchEvenWithForgedUserId
        status: pass
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java#allowsAdminWhenPersistedRelationshipMatchesAndIgnoresForgedUserId
        status: pass
    human_judgment: false
  - id: D3
    description: Cadeia real distingue anônimo, perfil negado, relação negada e acesso permitido com respostas 401/403/200
    requirement: AUTH-06
    verification:
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java
        status: pass
      - kind: other
        ref: .\solveit-api\mvnw.cmd test
        status: pass
    human_judgment: false
duration: 13m active
completed: 2026-09-10
status: complete
---

# Phase 2 Plan 8: Autorização por perfil e relação Summary

**Method Security ativo e matriz integrada comprovando que perfil atual e relação persistida são impostos pelo servidor sem confiar em identidade enviada pelo cliente.**

## Performance

- **Duration:** 13 min ativos
- **Started:** 2026-09-10T10:02:00-03:00
- **Completed:** 2026-09-10T10:15:22-03:00
- **Tasks:** 1
- **Files modified:** 2
- **Tests:** 55 na suíte completa, zero falhas, erros ou skips

## Accomplishments

- @EnableMethodSecurity ativa regras como @PreAuthorize("hasRole('ADMIN')") sobre métodos protegidos.
- Um controller e um service exclusivos de teste atravessam JWT, FilterChainProxy, Method Security, transação e MySQL para provar perfil e relação sem criar endpoints fictícios em produção.
- A matriz comprova 401 para anônimo, 403 para perfil incorreto, 403 para relação incorreta e 200 quando ambas as condições são satisfeitas.
- O parâmetro userId forjado é ignorado; a decisão usa UsuarioPrincipal.getId() e a relação carregada pelo repositório.

## Task Commits

1. **RED — matriz de autorização server-side:** 5f66860 (test)
2. **GREEN — ativação de Method Security:** 47b1d7d (feat)

## Files Created/Modified

- SecurityConfig.java — habilita a infraestrutura de segurança em métodos do Spring.
- AuthSecurityIntegrationTest.java — adiciona o probe exclusivo da suíte e os quatro cenários de perfil, relação e identidade forjada.
- UsuarioPrincipal.java — revisado durante a execução; já expunha ID, perfil atual e authorities no formato ROLE_<PERFIL>, portanto não precisou ser alterado.

## Decisions Made

- Perfil responde **quem pode tentar** a operação; relação responde **sobre qual registro pode agir**.
- O perfil é aplicado no limite do método pelo Spring Security, enquanto a relação dependente do banco é verificada no service transacional.
- Identificadores enviados pelo cliente são dados não confiáveis e não substituem a identidade autenticada.
- A Fase 2 entrega a fundação de autorização. As relações concretas de solicitante, atendente e administrador sobre Chamado serão implementadas na Fase 3.

## Deviations from Plan

None — o plano aprovado foi executado conforme apresentado. UsuarioPrincipal não exigiu edição porque as capacidades previstas já existiam.

## Issues Encountered

- A primeira tentativa de patch em SecurityConfig.java usou um bloco de imports diferente do arquivo real; o patch foi rejeitado sem modificar o arquivo e reaplicado no contexto correto.
- O Git manteve avisos informativos de conversão LF para CRLF no Windows.
- O Flyway manteve o aviso conhecido de suporte testado oficialmente até MySQL 8.1; todas as cinco migrations passaram no MySQL 8.4.11.
- Contextos antigos de integração emitiram avisos do Hikari ao validar conexões de containers já encerrados; os novos pools iniciaram normalmente e a suíte terminou verde.

## TDD Gate Compliance

- O RED executou 15 testes e falhou somente em deniesCurrentNonAdminProfileAtTheMethodBoundary: esperava 403 e recebeu 200 porque Method Security ainda estava inativo.
- O GREEN adicionou somente @EnableMethodSecurity e fez os mesmos 15 testes passarem sem reduzir assertions.
- A suíte completa passou com 55 testes, confirmando ausência de regressão após a alteração global.

## Verification

- mvnw.cmd -Dtest=AuthSecurityIntegrationTest test — 15 testes, zero falhas, erros ou skips.
- mvnw.cmd test — 55 testes, zero falhas, erros ou skips.
- mvnw.cmd -DskipTests package — JAR executável gerado com sucesso.
- MySQL 8.4.11, cinco migrations Flyway, MockMvc, filtro JWT, Method Security, service transacional e contratos 401/403 foram exercitados.

## User Setup Required

Nenhuma configuração nova. O segredo JWT externo exigido pelos planos anteriores permanece necessário fora do perfil test.

## Next Phase Readiness

Os oito planos da Fase 2 estão implementados. A fase está pronta para a verificação global de sua meta e dos requisitos AUTH-01 a AUTH-06 antes do planejamento da Fase 3.

## Self-Check: PASSED

- Os dois arquivos modificados existem e os componentes de probe permanecem somente em src/test.
- Os commits RED 5f66860 e GREEN 47b1d7d foram encontrados.
- 55 testes e o empacotamento passaram.
- AUTH-06 está coberto por perfil atual, relação persistida, identidade confiável e matriz negativa.

---
*Phase: 02-autentica-o*
*Completed: 2026-09-10*
