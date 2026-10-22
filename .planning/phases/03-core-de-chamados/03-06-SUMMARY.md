---
phase: 03-core-de-chamados
plan: "06"
subsystem: api
tags: [spring-mvc, spring-security, jpa, pessimistic-locking, optimistic-locking, concurrency, history, testcontainers]

requires:
  - phase: 03-core-de-chamados
    plan: "05"
    provides: Política de leitura, edição versionada e lista no escopo
provides:
  - PATCH /api/chamados/{id}/atendente (ATENDENTE para si, ADMIN para qualquer ATENDENTE ativo)
  - Transição ABERTO → EM_ANDAMENTO com um único vencedor
  - HistoricoStatus.transicao com alteradoEm igual ao atualizadoEm do chamado
  - Provas de disputa, edição desatualizada e rollback com MySQL real
affects: [03-07-resolver-fechar-cancelar, 03-08-comentarios, historico, dashboard]

ticket:
  number: 16
  branch: ticket/16-transicoes-status
  pr: pendente

actuals:
  tasks: 2
  commits: 3
  executor: Claude Code (sem GSD)

tech-stack:
  added: []
  patterns:
    - Ator e destino travados por ID crescente antes do chamado
    - Versão conferida antes do estado; o perdedor de uma disputa recebe STALE_RESOURCE
    - Histórico de transição gravado após o flush do chamado, reutilizando atualizadoEm

key-files:
  created:
    - solveit-api/src/test/java/com/solveit/controller/ChamadoAtribuicaoIntegrationTest.java
    - solveit-api/src/test/java/com/solveit/service/ChamadoAtribuicaoConcorrenciaTest.java
  modified:
    - solveit-api/src/main/java/com/solveit/entity/HistoricoStatus.java
    - solveit-api/src/main/java/com/solveit/dto/chamado/ChamadoDtos.java
    - solveit-api/src/main/java/com/solveit/service/ChamadoService.java
    - solveit-api/src/main/java/com/solveit/controller/ChamadoController.java
    - solveit-api/src/test/java/com/solveit/controller/ChamadoEdicaoIntegrationTest.java

key-decisions:
  - "Aprovada: alteradoEm da transição = atualizadoEm do chamado (HistoricoStatus.transicao), para que as duas tabelas registrem o mesmo instante."
  - "Aprovada: chamado já assumido responde 409, não 403; quem perde a disputa acabou de ver o chamado na fila."
  - "Destino inexistente, não ATENDENTE ou inativo responde 409 INVALID_STATE; 404 fica reservado ao chamado do caminho."
  - "O autor do histórico é o ator (ADMIN quando atribui terceiro), não o atendente de destino."
  - "Observação em branco é normalizada para null."

requirements-completed: [TICK-06]
requirements-partial:
  - id: TICK-11
    covered: "Abertura (03-04) e atribuição (03-06) geram exatamente um histórico na mesma transação; rejeições não geram histórico."
    remaining: "Resolver, fechar, cancelar e devoluções administrativas com motivo automático."
  - id: TICK-12
    covered: "Edição (03-05) e atribuição (03-06) não sobrescrevem transição confirmada."
    remaining: "Demais transições de status."

coverage:
  - id: D1
    description: "D-04/D-14: atribuição gera EM_ANDAMENTO e um histórico com ator, observação e instante do chamado."
    requirement: "TICK-06"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/ChamadoAtribuicaoIntegrationTest.java"
        status: pass
    human_judgment: false
  - id: D2
    description: "D-04: duas atribuições simultâneas nas duas ordens deixam um responsável e um histórico."
    requirement: "TICK-12"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/service/ChamadoAtribuicaoConcorrenciaTest.java#firstConfirmedAssignmentWins*"
        status: pass
    human_judgment: false
  - id: D3
    description: "D-16: edição e atribuição nas duas ordens; o perdedor recebe 409 sem sobrescrever."
    requirement: "TICK-12"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/service/ChamadoAtribuicaoConcorrenciaTest.java"
        status: pass
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/ChamadoEdicaoIntegrationTest.java#editionStartedBeforeAssignmentDoesNotOverwriteTheService"
        status: pass
    human_judgment: false
  - id: D4
    description: "D-19: falha real do histórico (trigger MySQL) reverte responsável, status e versão."
    requirement: "TICK-11"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/service/ChamadoAtribuicaoConcorrenciaTest.java#historyFailureRollsBackAttendantStatusAndVersion"
        status: pass
    human_judgment: false
  - id: D5
    description: "D-03: comentário não reserva o chamado."
    requirement: "TICK-06"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/ChamadoAtribuicaoIntegrationTest.java#commentingDoesNotReserveTheTicket"
        status: pass
    human_judgment: false
---

# Plano 03-06 — Assumir e atribuir chamados com um único vencedor

## Resultado

`PATCH /api/chamados/{id}/atendente` com `{atendenteId, versaoEsperada, observacao?}`.

| Situação | Resposta |
|---|---|
| ATENDENTE assume para si / ADMIN atribui ATENDENTE ativo | 200, `EM_ANDAMENTO`, versão +1, um histórico |
| SOLICITANTE, ou ATENDENTE indicando outro ID | 403 |
| Destino inexistente, não ATENDENTE ou inativo | 409 `INVALID_STATE` |
| Chamado fora da fila (assumido, não ABERTO) | 409 `INVALID_STATE` |
| Versão desatualizada | 409 `STALE_RESOURCE` |
| Chamado inexistente | 404 |

## Commits

| Commit | Tipo | Descrição |
|---|---|---|
| `ee54e28` | feat | Rota, service, DTO `Atribuir` e `HistoricoStatus.transicao` |
| `028936f` | test | Disputa nas duas ordens, edição × atribuição, rollback e caso HTTP |

## Observações

- O escopo do plano é somente a atribuição; resolver, fechar e cancelar ficam para os planos seguintes.
- O perdedor de qualquer disputa recebe `STALE_RESOURCE`, porque a versão é conferida antes do estado.
- A suíte completa passou de 10 minutos e terminou em segundo plano; CI reduziria esse custo local.

## Verificação

- Suíte completa no MySQL 8.4.11 (Testcontainers): **157/161**.
- As 4 falhas são `AuthHttpErrorIntegrationTest` (loopback do ambiente Windows), reproduzidas na `main`.

## Pendências

- TICK-11 e TICK-12 continuam parciais até as demais transições.
- Handler genérico de `Exception` → 500 continua pendente.
