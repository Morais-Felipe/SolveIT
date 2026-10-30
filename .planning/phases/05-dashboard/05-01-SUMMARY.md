---
phase: 05-dashboard
plan: "01"
subsystem: api
tags: [criteria-api, aggregation, specifications, hibernate-statistics]

requires:
  - phase: 04-notificacoes-email
    plan: "01"
    provides: Back-end de chamados completo e CI
provides:
  - GET /api/dashboard/resumo para ADMIN e ATENDENTE
  - Agregados no MySQL reaproveitando o predicado de visibilidade da lista
affects: [06-front-end]

ticket:
  number: 24
  branch: ticket/24-dashboard
  pr: ver STATE.md

actuals:
  tasks: 2
  commits: 3
  executor: Claude Code (sem GSD)

tech-stack:
  added: []
  patterns:
    - Criteria API com CriteriaQuery<Tuple>, COUNT e GROUP BY sobre Specification.toPredicate
    - Estatísticas do Hibernate em teste para provar que nenhuma entidade é carregada

key-files:
  created:
    - solveit-api/src/main/java/com/solveit/controller/DashboardController.java
    - solveit-api/src/main/java/com/solveit/service/DashboardService.java
    - solveit-api/src/main/java/com/solveit/repository/DashboardRepository.java
    - solveit-api/src/main/java/com/solveit/dto/dashboard/DashboardDtos.java
    - solveit-api/src/test/java/com/solveit/controller/DashboardIntegrationTest.java
  modified:
    - README.md

key-decisions:
  - "D-1 aprovada: escopo dos agregados = ChamadoSpecifications.visivelPara(usuario); resolve a pendência da Fase 5 no STATE."
  - "D-2 aprovada: carga ativa = chamados EM_ANDAMENTO por atendente responsável."
  - "D-3 aprovada: crítica aberta = status ABERTO e prioridade CRITICA."
  - "D-4 aprovada: por categoria conta todos os chamados visíveis, de qualquer status, inclusive em categoria desativada."

requirements-completed: [DASH-01, DASH-02, DASH-03, DASH-04, DASH-05]
requirements-partial: []

coverage:
  - id: D1
    description: "Números exatos para ADMIN e ATENDENTE; SOLICITANTE 403; sem token 401; escopo vazio com zeros e as quatro prioridades."
    requirement: "DASH-01..05"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/DashboardIntegrationTest.java"
        status: pass
    human_judgment: false
  - id: D2
    description: "Critério 3: cada indicador igual ao totalElements de GET /api/chamados com o filtro equivalente, para ADMIN e dois atendentes."
    requirement: "DASH-05"
    verification:
      - kind: integration
        ref: "DashboardIntegrationTest#everyIndicatorReconcilesWithTheEquivalentListQuery"
        status: pass
    human_judgment: false
  - id: D3
    description: "Critério 4: nenhuma entidade Chamado carregada e no máximo 6 statements na requisição."
    requirement: "DASH-05"
    verification:
      - kind: integration
        ref: "DashboardIntegrationTest#aggregatesAreComputedInTheDatabaseWithoutLoadingTickets"
        status: pass
    human_judgment: false
---

# Plano 05-01 — Dashboard

## Resultado

`GET /api/dashboard/resumo` → `{totais, porCategoria, cargaPorAtendente, abertosPorPrioridade}`, calculado em 4 consultas de agregação no MySQL, no escopo de leitura do perfil.

## Commits

| Commit | Tipo | Descrição |
|---|---|---|
| `83a4438` | docs | Plano 05-01 |
| `eea7214` | feat | Rota, service, repository de agregados, DTOs e testes |
| docs | docs | README, SUMMARY e documentos de planejamento |

## Verificação

- `DashboardIntegrationTest` 6/6 no MySQL 8.4.11 (Testcontainers); vermelho completo antes da implementação.
- Suíte completa verificada pelo CI do PR.
