---
phase: 03-core-de-chamados
plan: "02"
subsystem: api
tags: [spring-mvc, spring-security, jpa, flyway, mysql, optimistic-locking, pessimistic-locking, testcontainers]

requires:
  - phase: 03-core-de-chamados
    plan: "01"
    provides: Catálogo protegido de categorias e protocolo compartilhado de coordenação das escritas
provides:
  - Edição ADMIN de categoria com versão esperada e conflito stale controlado
  - Ativação e desativação lógica sem exclusão nem quebra de chamados existentes
  - Migração V7 com categoria.version e upgrade V6 para V7 preservando dados
affects: [03-03-usuarios, 03-04-chamados, 03-05-consulta-chamados, categorias, core-write-protocol]

actuals:
  tokens: 10024
  tasks: 2
  commits: 5

tech-stack:
  added: []
  patterns:
    - Formulários mutáveis enviam versaoEsperada e recebem 409 quando o estado mudou
    - Escritas de categoria seguem guard global, ator atual, registro bloqueado, comparação de versão e flush
    - Operação sem mudança valida a versão mas não produz UPDATE nem incremento artificial

key-files:
  created:
    - solveit-api/src/main/resources/db/migration/V7__version_categoria.sql
    - solveit-api/src/test/java/com/solveit/controller/CategoriaEdicaoIntegrationTest.java
  modified:
    - solveit-api/src/main/java/com/solveit/entity/Categoria.java
    - solveit-api/src/main/java/com/solveit/dto/categoria/CategoriaDtos.java
    - solveit-api/src/main/java/com/solveit/service/CategoriaService.java
    - solveit-api/src/main/java/com/solveit/controller/CategoriaController.java
    - solveit-api/src/test/java/com/solveit/controller/CategoriaIntegrationTest.java
    - solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java

key-decisions:
  - "Categoria usa @Version sobre BIGINT NOT NULL DEFAULT 0; o cliente recebe versao e envia versaoEsperada."
  - "PUT e PATCH compartilham o protocolo guard global → ADMIN atual → categoria com PESSIMISTIC_WRITE → comparação de versão."
  - "Desativação é exclusivamente lógica; no-op retorna 200 com a mesma versão e nenhuma relação é removida."

patterns-established:
  - "Versioned admin command: DTO exige versaoEsperada não negativa; service compara após locks e devolve STALE_RESOURCE em conflito."
  - "Logical deactivation: altera somente ativo, preserva identidade, nome e chaves estrangeiras e filtra o registro do catálogo ativo."

requirements-completed: [ADMN-05, ADMN-06]

coverage:
  - id: D1
    description: "ADMIN edita nome e descrição de categoria com unicidade e controle de versão; payload antigo não sobrescreve mudança confirmada."
    requirement: "ADMN-05"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/CategoriaEdicaoIntegrationTest.java#editing, uniqueness, validation and stale-version scenarios"
        status: pass
    human_judgment: false
  - id: D2
    description: "ADMIN ativa ou desativa categoria sem exclusão; catálogo reflete o estado, no-op preserva a versão e chamados continuam vinculados."
    requirement: "ADMN-06"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/CategoriaEdicaoIntegrationTest.java#activation, deactivation, no-op and ticket-relationship scenarios"
        status: pass
    human_judgment: false
  - id: D3
    description: "Banco existente na V6 recebe categoria.version=0 pela V7 sem perder nome, descrição ou estado ativo."
    requirement: "ADMN-06"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java#versionSixDatabaseUpgradesToVersionSevenWithoutLosingCategoryData"
        status: pass
    human_judgment: false

duration: 43min
completed: 2026-09-29
status: complete
---

# Phase 3 Plan 2: Edição e ativação de categorias Summary

**Categorias editáveis e ativáveis por ADMIN com concorrência versionada, desativação lógica e upgrade V6→V7 comprovado no MySQL 8.4**

## Performance

- **Duration:** 43 min
- **Started:** 2026-09-29T11:29:42-03:00
- **Completed:** 2026-09-29T12:13:08-03:00
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- Entregues `PUT /api/categorias/{id}` e `PATCH /api/categorias/{id}/ativo`, ambos restritos a ADMIN e protegidos por versão esperada.
- Desativação remove a categoria somente do catálogo ativo; registro, nome e `chamado.categoria_id` permanecem íntegros.
- Comprovados no-op sem incremento, conflito stale sem sobrescrita, unicidade entre categorias e upgrade V6→V7 com backfill `version=0`.

## Task Commits

Cada tarefa foi registrada em commits TDD atômicos:

1. **Tarefa 1: Editar categoria com versão real** — `cc62a30` (RED), `7151693` (GREEN)
2. **Tarefa 2: Ativar/desativar preservando dados e prova de upgrade** — `08484c2` (RED), `92d1879` (GREEN)

**Checkpoint entre tarefas:** `f511a5f` (WIP documentado)

**Plan metadata:** este commit documental.

## Files Created/Modified

- `V7__version_categoria.sql` e `Categoria.java` — coluna versionada aditiva e `@Version` sem setter.
- `CategoriaDtos.java`, `CategoriaController.java` e `CategoriaService.java` — contratos versionados, rotas ADMIN e protocolo transacional compartilhado.
- `CategoriaEdicaoIntegrationTest.java` — dez cenários HTTP/MySQL de edição, ativação, autorização, validação, no-op, stale e vínculo.
- `CategoriaIntegrationTest.java` — respostas anteriores atualizadas para incluir a versão real.
- `PersistenceIntegrationTest.java` — sete migrações, metadado da coluna e upgrade V6→V7 com dados existentes.

## Decisions Made

- A versão da categoria é autoridade do servidor e nunca recebe setter. O cliente apenas ecoa `versaoEsperada`; a comparação ocorre depois do guard e do lock do registro.
- Edição e alteração de atividade reutilizam os mesmos helpers privados de autorização, lock e versão para impedir divergência entre rotas.
- No-op ainda valida autoridade, existência e versão, mas retorna antes de `saveAndFlush`; assim um comando atual é idempotente sem ocultar um comando stale.
- Categoria nunca é apagada por esse fluxo. Desativação altera somente `ativo`, preservando nomes consultáveis e todas as chaves estrangeiras.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- O Docker Desktop estava indisponível no início da Tarefa 1; depois de iniciar o daemon, todos os ciclos RED/GREEN foram repetidos contra MySQL 8.4.11.
- A primeira versão da prova V6→V7 tratou a coluna MySQL `BOOLEAN` como `Number`; a asserção foi corrigida para `Boolean` antes do commit RED, sem mudar o comportamento exigido.
- A suíte completa emite avisos de pools Hikari de contextos anteriores tentando reconectar após o descarte de containers. Os relatórios Surefire confirmaram 90 testes, zero falhas, zero erros e zero ignorados.
- O Flyway gerenciado alerta que MySQL 8.4 é mais novo que sua faixa declarada de testes. V1–V7, validação Hibernate, upgrade e idempotência passaram no MySQL 8.4.11.

## User Setup Required

None - no external service configuration required.

## Ticket / Branch / PR

- **Plano:** 03-02
- **Ticket/branch:** `ticket/12-edicao-categorias`
- **PR:** [#14 — Plano 03-02: editar e ativar/desativar categorias](https://github.com/Morais-Felipe/SolveIT/pull/14)

## Next Phase Readiness

- ADMN-05 e ADMN-06 estão completos e o contrato de versão de categoria está pronto para consumo pelos próximos planos.
- O plano 03-03 só deve iniciar depois de push, PR e integração do ticket 12, cada ação com sua aprovação separada.
- Nenhum bloqueio técnico novo foi identificado; permanecem as preocupações de roadmap já registradas para fases futuras.

## Self-Check: PASSED

- Os 8 arquivos do plano existem e os quatro commits TDD, além do checkpoint, estão presentes.
- Verificação do plano: 18 testes, zero falhas/erros/ignorados.
- Regressão direta do catálogo: 10 testes, zero falhas/erros/ignorados.
- Regressão completa do back-end: 90 testes, zero falhas/erros/ignorados.

---
*Phase: 03-core-de-chamados*
*Completed: 2026-09-29*
