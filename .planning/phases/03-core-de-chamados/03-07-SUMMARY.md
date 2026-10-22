---
phase: 03-core-de-chamados
plan: "07"
subsystem: api
tags: [spring-mvc, spring-security, jpa, state-machine, pessimistic-locking, optimistic-locking, concurrency, history, testcontainers]

requires:
  - phase: 03-core-de-chamados
    plan: "06"
    provides: Atribuição ABERTO → EM_ANDAMENTO e HistoricoStatus.transicao
provides:
  - PATCH /api/chamados/{id}/status para resolver, fechar e cancelar
  - ChamadoTransitionPolicy com a tabela explícita de transições manuais
  - Chamado.transicionar com instante único para atualizadoEm, resolvidoEm e histórico
  - Matriz unitária exaustiva (150 combinações) contra oráculo independente
  - Provas de disputa resolver × cancelar e rollback completo com MySQL real
affects: [03-08-comentarios, 03-10-devolucao-administrativa, dashboard, notificacoes]

ticket:
  number: 17
  branch: ticket/17-resolver-fechar-cancelar
  pr: ver STATE.md (aberto e integrado com autorização conjunta de 2026-10-22)

actuals:
  tasks: 3
  commits: 4
  executor: Claude Code (sem GSD)

tech-stack:
  added: []
  patterns:
    - Tabela Map<(origem, destino), regra> — linha ausente 409, regra recusada 403
    - Ordem leitura (403) → versão (409 STALE) → tabela (409/403)
    - Instante do Clock truncado em microssegundos aplicado por Chamado.transicionar
    - Campo @Transient atualizacaoDatada impede o @PreUpdate de sobrescrever o instante

key-files:
  created:
    - solveit-api/src/main/java/com/solveit/service/ChamadoTransitionPolicy.java
    - solveit-api/src/test/java/com/solveit/controller/ChamadoTransicaoIntegrationTest.java
    - solveit-api/src/test/java/com/solveit/service/ChamadoTransitionPolicyTest.java
    - solveit-api/src/test/java/com/solveit/service/ChamadoTransicaoConcorrenciaTest.java
  modified:
    - solveit-api/src/main/java/com/solveit/entity/Chamado.java
    - solveit-api/src/main/java/com/solveit/dto/chamado/ChamadoDtos.java
    - solveit-api/src/main/java/com/solveit/service/ChamadoService.java
    - solveit-api/src/main/java/com/solveit/controller/ChamadoController.java

key-decisions:
  - "Aprovada: ordem leitura → versão → tabela. Leitura é condição necessária, não suficiente; quem não vê o chamado recebe 403 antes de qualquer revelação de versão ou estado."
  - "Aprovada: um instante do Clock UTC define atualizadoEm, resolvidoEm (na resolução) e alteradoEm do histórico."
  - "Correção registrada: o @PreUpdate original sempre sobrescrevia atualizadoEm; foi preciso o campo @Transient atualizacaoDatada."
  - "Tabela: EM_ANDAMENTO→RESOLVIDO (ADMIN ou ATENDENTE responsável); RESOLVIDO→FECHADO e ABERTO→CANCELADO (SOLICITANTE dono); EM_ANDAMENTO→CANCELADO (ADMIN). Nenhuma linha sai de FECHADO/CANCELADO; ABERTO→EM_ANDAMENTO só pela atribuição."
  - "eSolicitanteDono exige perfil SOLICITANTE: atendente que abriu o chamado não herda o papel."

requirements-completed: [TICK-07, TICK-08, TICK-09]
requirements-partial:
  - id: TICK-10
    covered: "Transições não previstas e reabertura de FECHADO/CANCELADO são rejeitadas."
    remaining: "EM_ANDAMENTO → ABERTO por desativação/retirada de perfil do atendente (03-10)."
  - id: TICK-11
    covered: "Abertura, atribuição, resolução, fechamento e cancelamento geram exatamente um histórico; rejeições não geram."
    remaining: "Devoluções administrativas com motivo automático obrigatório (03-10)."
  - id: TICK-12
    covered: "Abertura, edição, atribuição e todas as transições manuais recusam versão antiga sem sobrescrever."
    remaining: "Devolução administrativa concorrente (03-10)."

coverage:
  - id: D1
    description: "Resolução com instante único em chamado e histórico; ADMIN registrado como ator."
    requirement: "TICK-07"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/ChamadoTransicaoIntegrationTest.java"
        status: pass
    human_judgment: false
  - id: D2
    description: "Fechar preserva resolvidoEm/criadoEm; cancelamentos mantêm resolvidoEm nulo; ADMIN e atendentes não herdam decisões do solicitante."
    requirement: "TICK-08, TICK-09"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/ChamadoTransicaoIntegrationTest.java"
        status: pass
    human_judgment: false
  - id: D3
    description: "D-08: 150 combinações origem × destino × ator iguais ao oráculo; estados finais nunca saem; nada reabre."
    requirement: "TICK-10"
    verification:
      - kind: unit
        ref: "solveit-api/src/test/java/com/solveit/service/ChamadoTransitionPolicyTest.java"
        status: pass
    human_judgment: false
  - id: D4
    description: "D-16/D-19: resolver × cancelar nas duas ordens deixa um resultado e um histórico; trigger reverte status, datas e versão."
    requirement: "TICK-11, TICK-12"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/service/ChamadoTransicaoConcorrenciaTest.java"
        status: pass
    human_judgment: false
---

# Plano 03-07 — Resolver, fechar e cancelar

## Resultado

`PATCH /api/chamados/{id}/status` com `{status, versaoEsperada, observacao?}`.

| De → Para | Quem | Datas |
|---|---|---|
| EM_ANDAMENTO → RESOLVIDO | ADMIN ou ATENDENTE responsável | `resolvidoEm` = instante da transição |
| RESOLVIDO → FECHADO | SOLICITANTE dono | `resolvidoEm` preservado |
| ABERTO → CANCELADO | SOLICITANTE dono | `resolvidoEm` nulo |
| EM_ANDAMENTO → CANCELADO | ADMIN | `resolvidoEm` nulo |

Qualquer outra combinação: 409 `INVALID_STATE`. Ator sem regra: 403. Quem não lê o chamado: 403 antes de tudo.

## Commits

| Commit | Tipo | Descrição |
|---|---|---|
| `18c9c5b` | feat | Rota, tabela (só resolução), `Chamado.transicionar` e instante único |
| `6bcc190` | feat | Fechar e cancelar; matriz unitária de 150 combinações |
| `9781af0` | test | Resolver × cancelar nas duas ordens e rollback de status/datas/versão |

## Observações

- A matriz unitária foi executada vermelha antes de completar a tabela: 18 divergências, exatamente as 3 transições ausentes × 6 atores.
- Push, PR e merge foram feitos com autorização conjunta explícita do responsável, dada em 2026-10-22 antes do término da suíte, condicionada a nenhuma falha além das 4 de loopback.

## Verificação

- Suíte completa no MySQL 8.4.11 (Testcontainers): **174/178**.
- As 4 falhas são `AuthHttpErrorIntegrationTest` (loopback do ambiente Windows), reproduzidas na `main`.

## Pendências

- TICK-10, TICK-11 e TICK-12 dependem da devolução administrativa do plano 03-10.
- Handler genérico de `Exception` → 500; CI.
