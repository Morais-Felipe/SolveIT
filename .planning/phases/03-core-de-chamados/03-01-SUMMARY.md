---
phase: 03-core-de-chamados
plan: "01"
subsystem: api
tags: [spring-mvc, spring-security, jpa, flyway, mysql, pessimistic-locking, testcontainers]

requires:
  - phase: 02-autenticacao
    provides: JWT Bearer, UsuarioPrincipal, autorização por perfil e Problem Details
provides:
  - Catálogo autenticado de categorias ativas com paginação estável e detalhe autorizado
  - Cadastro ADMIN de categoria com unicidade, validação e erros controlados
  - Coordenação persistente de escritas com timeout, autoridade atual e rollback único JDBC/JPA
  - Migração V6 com singleton core_write_guard e upgrade V5 para V6 preservando dados
affects: [03-02-categorias, 03-03-usuarios, 03-04-chamados, core-write-protocol]

actuals:
  tokens: 13739
  tasks: 3
  commits: 6

tech-stack:
  added: []
  patterns:
    - Service público transacional adquire o guard antes de ler estado mutável
    - Autoridade do ator é recarregada com PESSIMISTIC_WRITE após o guard
    - Timeout de row lock é temporário por sessão e restaurado antes de devolver a conexão ao pool

key-files:
  created:
    - solveit-api/src/main/java/com/solveit/controller/CategoriaController.java
    - solveit-api/src/main/java/com/solveit/dto/categoria/CategoriaDtos.java
    - solveit-api/src/main/java/com/solveit/service/CategoriaService.java
    - solveit-api/src/main/java/com/solveit/service/CoreWriteCoordinator.java
    - solveit-api/src/main/java/com/solveit/exception/DomainException.java
    - solveit-api/src/main/resources/db/migration/V6__create_core_write_guard.sql
    - solveit-api/src/test/java/com/solveit/controller/CategoriaIntegrationTest.java
    - solveit-api/src/test/java/com/solveit/service/CoreWriteCoordinatorIntegrationTest.java
  modified:
    - solveit-api/src/main/java/com/solveit/repository/CategoriaRepository.java
    - solveit-api/src/main/java/com/solveit/exception/ApiErrorCode.java
    - solveit-api/src/main/java/com/solveit/exception/GlobalExceptionHandler.java
    - solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java

key-decisions:
  - "Todas as escritas curtas da Fase 3 compartilham a linha singleton core_write_guard e mantêm o lock até commit ou rollback."
  - "O timeout nativo de row lock é definido temporariamente na sessão JDBC e restaurado em finally; setQueryTimeout permanece como segunda barreira."
  - "A criação de categoria recarrega o usuário atual depois do guard e classifica duplicidade somente pela constraint uk_categoria_nome."

patterns-established:
  - "Core write protocol: @Transactional(timeout=10) → adquirir guard → recarregar ator bloqueado → autorizar → alterar → flush → commit."
  - "Controlled domain errors: DomainException leva código/status/textos fixos; detalhes JDBC nunca atravessam a API."

requirements-completed: [ADMN-04, ADMN-05]

coverage:
  - id: D1
    description: "Usuário autenticado lista categorias ativas com paginação estável e consulta detalhes conforme atividade e perfil."
    requirement: "ADMN-04"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/CategoriaIntegrationTest.java#GET catalog and detail scenarios"
        status: pass
    human_judgment: false
  - id: D2
    description: "ADMIN cria categoria ativa de nome único; perfis indevidos, forma inválida e duplicidade recebem contratos HTTP controlados."
    requirement: "ADMN-05"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/CategoriaIntegrationTest.java#POST creation scenarios"
        status: pass
    human_judgment: false
  - id: D3
    description: "A migração V6 preserva bancos V5 e o coordenador serializa escritas, expira esperas, relê autoridade e reverte JDBC/JPA juntos."
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java#V5 to V6 upgrade"
        status: pass
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/service/CoreWriteCoordinatorIntegrationTest.java#concurrency and rollback scenarios"
        status: pass
    human_judgment: false

duration: 4h 36m
completed: 2026-09-24
status: complete
---

# Phase 3 Plan 1: API protegida de categorias Summary

**Catálogo e cadastro de categorias protegidos por JWT, com coordenação transacional persistente e provas reais de upgrade, concorrência e rollback no MySQL 8.4**

## Performance

- **Duration:** 4h 36m
- **Started:** 2026-09-24T11:03:00-03:00
- **Completed:** 2026-09-24T15:39:00-03:00
- **Tasks:** 3
- **Files modified:** 12

## Accomplishments

- Entregues `GET /api/categorias`, `GET /api/categorias/{id}` e `POST /api/categorias` com DTOs explícitos, paginação, validação e autorização no servidor.
- Estabelecido o protocolo compartilhado de escritas da Fase 3 com guard MySQL, recarga da autoridade atual, timeout finito e falhas 409/503 controladas.
- Comprovados upgrade V5→V6, aplicação V1..V6 em banco vazio, serialização de workers, revogação durante espera e rollback atômico JDBC/JPA.

## Task Commits

Cada tarefa foi registrada em commits atômicos:

1. **Tarefa 1: Ler o catálogo real com Bearer** — `d15bab9` (RED), `d12b0ed` (GREEN)
2. **Tarefa 2: Cadastrar categoria e controlar falhas pela fronteira comum** — `7dff219` (RED), `908b21d` (GREEN)
3. **Tarefa 3: Provar upgrade e coordenação sem alterar migrações aplicadas** — `1a9980e` (correção encontrada pela prova), `4a03788` (provas de integração)

**Plan metadata:** este commit documental.

## Files Created/Modified

- `CategoriaController.java`, `CategoriaService.java`, `CategoriaDtos.java` e `CategoriaRepository.java` — API fina, regras transacionais e consultas do catálogo.
- `CoreWriteCoordinator.java` e `V6__create_core_write_guard.sql` — linha singleton, lock pessimista e timeout restaurável.
- `DomainException.java`, `ApiErrorCode.java` e `GlobalExceptionHandler.java` — fronteira controlada para conflitos, indisponibilidade e demais erros de domínio futuros.
- `CategoriaIntegrationTest.java` — 10 cenários HTTP com filtro Bearer e MySQL reais.
- `PersistenceIntegrationTest.java` e `CoreWriteCoordinatorIntegrationTest.java` — 13 cenários de migração, metadados, concorrência, timeout e rollback.

## Decisions Made

- O guard global aprovado foi mantido como fundação simples e observável para as escritas curtas da fase; cada service futuro deve seguir a mesma ordem de locks.
- `PreparedStatement#setQueryTimeout(5)` sozinho não encerrou de forma confiável a espera por row lock no Connector/J. O coordenador agora aplica `innodb_lock_wait_timeout=5` apenas na sessão emprestada e restaura o valor original em `finally`.
- A identidade usada na escrita continua sendo somente o ID do principal; perfil e atividade são relidos do banco depois que o guard é adquirido.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Timeout JDBC não interrompia espera por row lock**
- **Found during:** Tarefa 3, cenário de expiração de espera.
- **Issue:** `setQueryTimeout(5)` deixou o worker bloqueado além do prazo e não oferecia a garantia de disponibilidade prevista.
- **Fix:** Aplicar timeout nativo do InnoDB na sessão da mesma conexão e restaurar o valor anterior em `finally`, mantendo `setQueryTimeout` como defesa adicional.
- **Files modified:** `solveit-api/src/main/java/com/solveit/service/CoreWriteCoordinator.java`.
- **Verification:** cinco testes do coordenador e a suíte completa de 80 testes passaram.
- **Committed in:** `1a9980e`.

---

**Total deviations:** 1 auto-fixed (1 bug de confiabilidade).
**Impact on plan:** A correção preserva o protocolo aprovado, não adiciona dependências e torna o timeout de lock verificável no MySQL real.

## Issues Encountered

- O Docker Desktop estava inicialmente indisponível; após o daemon ficar pronto, todos os ciclos RED/GREEN foram repetidos contra MySQL 8.4.11 e somente evidências válidas foram aceitas.
- A execução conjunta das classes Testcontainers emite avisos do Hikari durante o teardown de containers anteriores; os relatórios Surefire confirmaram 80 testes, zero falhas, zero erros e zero ignorados.
- O Flyway gerenciado alerta que MySQL 8.4 é mais novo que a faixa testada por essa versão. As seis migrações e a validação Hibernate passaram repetidamente no MySQL 8.4.11.

## User Setup Required

None - no external service configuration required.

## Ticket / Branch / PR

- **Plano:** 03-01
- **Ticket/branch:** `ticket/11-categorias-api`
- **PR:** pendente; push ainda não autorizado

## Next Phase Readiness

- O protocolo de coordenação está pronto para ser consumido pelo plano 03-02.
- O próximo plano só deve iniciar depois de push, PR e integração do ticket 11, cada ação com sua aprovação separada.
- ADMN-04 e ADMN-05 permanecem pendentes no quadro global porque também são declarados pelos planos 03-05 e 03-02, respectivamente.

## Self-Check: PASSED

- 12 arquivos do plano existem e os seis commits locais estão presentes.
- Verificação do plano: 23 testes, zero falhas/erros/ignorados.
- Regressão completa do back-end: 80 testes, zero falhas/erros/ignorados.
- Gates de schema drift, codebase drift e UI safety não bloquearam a wave.

---
*Phase: 03-core-de-chamados*
*Completed: 2026-09-24*
