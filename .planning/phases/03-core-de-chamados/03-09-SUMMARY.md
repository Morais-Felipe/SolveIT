---
phase: 03-core-de-chamados
plan: "09"
subsystem: api
tags: [spring-mvc, spring-security, spring-data-jpa, entity-graph, append-only, testcontainers]

requires:
  - phase: 03-core-de-chamados
    plan: "08"
    provides: Criação de comentários e política de leitura de chamados
provides:
  - GET /api/chamados/{id}/comentarios em ordem cronológica
  - GET /api/chamados/{id}/historico completo em ordem cronológica
  - Prova HTTP e por reflexão de que comentários não são editados nem excluídos
affects: [03-10-devolucao-administrativa, front-end, dashboard]

ticket:
  number: 19
  branch: ticket/19-leitura-comentarios-historico
  pr: pendente

actuals:
  tasks: 3
  commits: 4
  executor: Claude Code (sem GSD)

tech-stack:
  added: []
  patterns:
    - Consultas derivadas OrderBy...AscIdAsc com @EntityGraph do autor
    - Leituras com a mesma política do detalhe do chamado (ChamadoAuthorizationService)
    - Histórico sem paginação, conforme COLL-04, e sem reconstrução a partir do estado atual

key-files:
  created:
    - solveit-api/src/main/java/com/solveit/controller/HistoricoStatusController.java
    - solveit-api/src/main/java/com/solveit/service/HistoricoStatusService.java
    - solveit-api/src/main/java/com/solveit/dto/historico/HistoricoStatusDtos.java
    - solveit-api/src/test/java/com/solveit/controller/ComentarioHistoricoConsultaIntegrationTest.java
    - solveit-api/src/test/java/com/solveit/controller/ComentarioImutabilidadeIntegrationTest.java
  modified:
    - solveit-api/src/main/java/com/solveit/controller/ComentarioController.java
    - solveit-api/src/main/java/com/solveit/service/ComentarioService.java
    - solveit-api/src/main/java/com/solveit/repository/ComentarioRepository.java
    - solveit-api/src/main/java/com/solveit/repository/HistoricoStatusRepository.java

key-decisions:
  - "Comentários e histórico retornam arrays completos, sem paginação."
  - "Somente {id, nome} do autor é exposto; nada de e-mail ou credenciais."
  - "A imutabilidade é provada com usuários que TÊM acesso, para que 403 não mascare a ausência de rota; aceitas 404 ou 405 do Spring MVC."
  - "ComentarioDtos não precisou de alteração; o mapeamento virou um toResposta compartilhado entre criação e listagem."

requirements-completed: [COLL-01, COLL-02, COLL-03, COLL-04]
requirements-partial: []

coverage:
  - id: D1
    description: "D-15/COLL-02: comentários cronológicos com desempate por id; vazio []; encerrados legíveis; autoria não conserva acesso."
    requirement: "COLL-01, COLL-02"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/ComentarioHistoricoConsultaIntegrationTest.java#comment scenarios"
        status: pass
    human_judgment: false
  - id: D2
    description: "COLL-04: histórico de fluxo real (abrir, atribuir, recusa, resolver, fechar) igual aos IDs do MySQL; cancelado legível; desempate por id; mesmo acesso do chamado."
    requirement: "COLL-04"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/ComentarioHistoricoConsultaIntegrationTest.java#history scenarios"
        status: pass
    human_judgment: false
  - id: D3
    description: "COLL-03: 12 tentativas PUT/PATCH/DELETE autenticadas respondem 404/405; linha e GET inalterados; sem setters nem métodos de mutação."
    requirement: "COLL-03"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/ComentarioImutabilidadeIntegrationTest.java"
        status: pass
    human_judgment: false
---

# Plano 03-09 — Ler comentários e histórico, e provar imutabilidade

## Resultado

| Rota | Resposta | Ordem |
|---|---|---|
| `GET /api/chamados/{id}/comentarios` | `[{id, texto, autor:{id,nome}, criadoEm}]` | `criadoEm`, `id` |
| `GET /api/chamados/{id}/historico` | `[{id, statusAnterior, statusNovo, alteradoPor:{id,nome}, alteradoEm, observacao}]` | `alteradoEm`, `id` |

Ambas: 200 (inclusive `[]`), 403 sem relação atual, 404 chamado inexistente, 401 sem token. Nenhuma rota edita ou exclui comentários.

## Commits

| Commit | Tipo | Descrição |
|---|---|---|
| `dab941b` | feat | Listagem de comentários |
| `6de0785` | feat | Histórico completo |
| `7e045cd` | test | Imutabilidade por HTTP e reflexão |

## Observações

- **Tarefa 1 sem execução vermelha:** o Docker estava desligado; a implementação foi escrita antes de os testes poderem rodar. O teste de ordem insere IDs fora da ordem cronológica, o que detectaria ordenação errada.
- **Tarefa 3 não tem vermelho possível:** prova a ausência de rotas que nunca existiram; o peso está na releitura do estado.
- O temor de que a falta do handler genérico produzisse 401/500 em rotas inexistentes não se confirmou: o Spring MVC respondeu 404/405.

## Verificação

- Suíte completa no MySQL 8.4.11 (Testcontainers): **203/207**.
- As 4 falhas são `AuthHttpErrorIntegrationTest` (loopback do ambiente Windows), reproduzidas na `main`.

## Pendências

- Plano 03-10 (devolução administrativa) fecha TICK-10, TICK-11 e TICK-12 e encerra a Fase 3.
- Handler genérico de `Exception` → 500; CI.
