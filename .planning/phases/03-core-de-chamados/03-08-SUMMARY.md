---
phase: 03-core-de-chamados
plan: "08"
subsystem: api
tags: [spring-mvc, spring-security, jpa, pessimistic-locking, concurrency, append-only, testcontainers]

requires:
  - phase: 03-core-de-chamados
    plan: "07"
    provides: Transições manuais (fechar e cancelar) e política de leitura de chamados
provides:
  - POST /api/chamados/{id}/comentarios com autor derivado do UsuarioPrincipal
  - ComentarioService.adicionar sob o guard global, sem alterar o chamado
  - Provas de que comentário não reserva, não conserva acesso e é serializado contra encerramento
affects: [03-09-leitura-comentarios-historico, notificacoes, front-end]

ticket:
  number: 18
  branch: ticket/18-comentarios
  pr: pendente

actuals:
  tasks: 3
  commits: 4
  executor: Claude Code (sem GSD)

tech-stack:
  added: []
  patterns:
    - Comentário trava o chamado só para ler status confirmado; nenhum UPDATE em chamado
    - Acesso de comentário = política de leitura (ChamadoAuthorizationService), sem regra paralela
    - Acesso (403) verificado antes do status encerrado (409), sem revelar encerramento a estranhos

key-files:
  created:
    - solveit-api/src/main/java/com/solveit/controller/ComentarioController.java
    - solveit-api/src/main/java/com/solveit/service/ComentarioService.java
    - solveit-api/src/main/java/com/solveit/dto/comentario/ComentarioDtos.java
    - solveit-api/src/test/java/com/solveit/controller/ComentarioCriacaoIntegrationTest.java
    - solveit-api/src/test/java/com/solveit/service/ComentarioConcorrenciaTest.java
  modified:
    - solveit-api/src/test/java/com/solveit/service/ChamadoAtribuicaoConcorrenciaTest.java

key-decisions:
  - "Sem versão esperada no comando de comentário: ele não altera o chamado; a ordem contra transições vem do guard."
  - "201 sem Location, porque não existe rota individual de comentário."
  - "ComentarioRepository não precisou de alteração (saveAndFlush herdado); Comentario continua sem setters."

requirements-completed: []
requirements-partial:
  - id: COLL-01
    covered: "Comentar com identidade autenticada somente em ABERTO, EM_ANDAMENTO ou RESOLVIDO, validando o estado atual inclusive contra encerramento concorrente."
    remaining: "Leitura das mensagens anteriores em FECHADO/CANCELADO (listagem em 03-09)."
  - id: TICK-12
    covered: "Comentário não sobrescreve nem invalida transições; encerramento confirmado primeiro recusa o comentário."
    remaining: "Devolução administrativa (03-10)."

coverage:
  - id: D1
    description: "D-02/D-12: autor do principal; campos extras 400; matriz de acesso e status; mensagem literal de conversa encerrada; chamado intocado."
    requirement: "COLL-01"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/ComentarioCriacaoIntegrationTest.java"
        status: pass
    human_judgment: false
  - id: D2
    description: "D-03: comentário de Maria não impede atribuição a João com a versão original; Maria perde acesso; perdedor da fila recebe 403."
    requirement: "TICK-06"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/ComentarioCriacaoIntegrationTest.java#commentingDoesNotReserveNorPreserveAccessAfterAnotherAttendantAssumes"
        status: pass
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/service/ChamadoAtribuicaoConcorrenciaTest.java#commentConfirmedFirstDoesNotReserveNorInvalidateTheAssignment"
        status: pass
    human_judgment: false
  - id: D3
    description: "D-18/D-19: comentário × fechar/cancelar nas duas ordens; falha de inserção não deixa linha."
    requirement: "COLL-01, TICK-12"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/service/ComentarioConcorrenciaTest.java"
        status: pass
    human_judgment: false
---

# Plano 03-08 — Comentários auditáveis

## Resultado

`POST /api/chamados/{id}/comentarios` com `{texto}` → 201 `{id, texto, autor:{id,nome}, criadoEm}`.

| Situação | Resposta |
|---|---|
| Acesso atual e status ABERTO/EM_ANDAMENTO/RESOLVIDO | 201 |
| Sem relação atual com o chamado | 403 (antes do status) |
| FECHADO ou CANCELADO | 409 `COMMENTS_CLOSED`, "Este chamado foi encerrado e não aceita novos comentários." |
| Texto vazio, > 10000, ou campo extra (`autorId`…) | 400 |
| Chamado inexistente | 404 |

## Commits

| Commit | Tipo | Descrição |
|---|---|---|
| `c709bd7` | feat | Rota, service e DTOs de comentário |
| `df3bf21` | test | Comentário não reserva nem conserva acesso (HTTP e concorrente) |
| `c9977a2` | test | Comentário × fechar/cancelar nas duas ordens e rollback |

## Verificação

- Suíte completa no MySQL 8.4.11 (Testcontainers): **191/195**.
- As 4 falhas são `AuthHttpErrorIntegrationTest` (loopback do ambiente Windows), reproduzidas na `main`.

## Pendências

- COLL-01 completa com a listagem do 03-09; TICK-12 com a devolução do 03-10.
- Handler genérico de `Exception` → 500; CI.
