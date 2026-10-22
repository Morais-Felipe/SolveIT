---
phase: 03-core-de-chamados
plan: "05"
subsystem: api
tags: [spring-mvc, spring-security, spring-data-jpa, specifications, pagination, optimistic-locking, testcontainers]

requires:
  - phase: 03-core-de-chamados
    plan: "04"
    provides: Abertura de chamado, DTO Detalhe e rejeição de campos fora do contrato
provides:
  - Política única de leitura de chamados (ChamadoAuthorizationService)
  - GET /api/chamados/{id} com 403 fora do escopo e 404 inexistente
  - GET /api/chamados paginado, filtrado e ordenado no banco dentro do escopo
  - Categoria inativa legível por ADMIN ou por quem vê chamado vinculado
  - PUT /api/chamados/{id} para título e descrição de chamado ABERTO com versão esperada
affects: [03-06-transicoes, 03-07-comentarios, categorias, dashboard, front-end]

ticket:
  number: 15
  branch: ticket/15-consulta-chamados
  pr: pendente

actuals:
  tasks: 3
  commits: 4
  executor: Claude Code (sem GSD)

decisions-human:
  - id: checkpoint-categoria-inativa
    choice: linked-visible-ticket
    decided: 2026-10-22
    summary: "Categoria inativa por ID: 200 para ADMIN ou para quem hoje vê ao menos um chamado vinculado; 403 para os demais; 404 se inexistente. Não amplia acesso a chamados."

tech-stack:
  added: []
  patterns:
    - Mesma regra de acesso em Java (podeAcessar) e em SQL (ChamadoSpecifications.visivelPara)
    - Filtros opcionais retornam null e são combinados por AND com a visibilidade
    - Ordenação por lista fechada de campos com id como desempate na mesma direção
    - EntityGraph de relações to-one na página para evitar N+1 sem perder paginação SQL

key-files:
  created:
    - solveit-api/src/main/java/com/solveit/service/ChamadoAuthorizationService.java
    - solveit-api/src/main/java/com/solveit/repository/ChamadoSpecifications.java
    - solveit-api/src/test/java/com/solveit/controller/ChamadoConsultaIntegrationTest.java
    - solveit-api/src/test/java/com/solveit/controller/ChamadoEdicaoIntegrationTest.java
  modified:
    - solveit-api/src/main/java/com/solveit/repository/ChamadoRepository.java
    - solveit-api/src/main/java/com/solveit/service/ChamadoService.java
    - solveit-api/src/main/java/com/solveit/service/CategoriaService.java
    - solveit-api/src/main/java/com/solveit/controller/ChamadoController.java
    - solveit-api/src/main/java/com/solveit/dto/chamado/ChamadoDtos.java

key-decisions:
  - "D-01: ADMIN vê todos; SOLICITANTE vê os próprios; ATENDENTE vê fila (ABERTO sem responsável) e atribuídos a ele, inclusive encerrados."
  - "D-10: perfil e relação são relidos do banco a cada requisição; reatribuição, rebaixamento e autoria de comentário não preservam nem concedem acesso."
  - "switch exaustivo sobre PerfilUsuario sem default: um perfil novo não compila até ter regra de leitura."
  - "Edição verifica papel/relação antes de versão e estado, para que 409 não revele o status de chamado alheio."
  - "Edição de texto não é transição de status e não gera HistoricoStatus."

requirements-completed: [TICK-02, TICK-03, TICK-04, TICK-05, ADMN-04]
requirements-partial:
  - id: TICK-12
    covered: "Edição com versão esperada rejeita formulário desatualizado após alteração confirmada em outra transação."
    remaining: "Corridas entre transições de status (planos 03-06 em diante)."

coverage:
  - id: D1
    description: "Matriz 5 perfis × 4 chamados no detalhe; relação atual é a única autoridade."
    requirement: "TICK-04"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/ChamadoConsultaIntegrationTest.java#detail scenarios"
        status: pass
    human_judgment: false
  - id: D2
    description: "Lista igual ao detalhe por perfil; filtros só restringem; paginação SQL com desempate estável; 11 parâmetros inválidos → 400."
    requirement: "TICK-02, TICK-03"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/ChamadoConsultaIntegrationTest.java#list scenarios"
        status: pass
    human_judgment: false
  - id: D3
    description: "D-11: categoria inativa legível por ADMIN ou por quem vê chamado vinculado, recalculado após reatribuição."
    requirement: "ADMN-04"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/ChamadoConsultaIntegrationTest.java#inactiveCategoryIsReadableByAdminOrWhoeverSeesALinkedTicket"
        status: pass
    human_judgment: false
  - id: D4
    description: "D-16: edição só ABERTO por dono/ADMIN; stale → 409; sem histórico; campos extras → 400."
    requirement: "TICK-05"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/ChamadoEdicaoIntegrationTest.java"
        status: pass
    human_judgment: false
---

# Plano 03-05 — Consultar, filtrar e editar chamados

## Resultado

| Rota | Comportamento |
|---|---|
| `GET /api/chamados` | Página dentro do escopo do perfil; filtros `status`, `prioridade`, `categoriaId`, `atendenteId`, `solicitanteId`; `sort` em `criadoEm`/`atualizadoEm`/`id` com `asc`/`desc` |
| `GET /api/chamados/{id}` | 200 no escopo, 403 fora, 404 inexistente |
| `PUT /api/chamados/{id}` | Dono SOLICITANTE ou ADMIN; só `ABERTO`; `versaoEsperada` obrigatória |
| `GET /api/categorias/{id}` (inativa) | ADMIN ou quem vê chamado vinculado |

## Commits

| Commit | Tipo | Descrição |
|---|---|---|
| `0412b93` | feat | Política única de leitura e detalhe |
| `453de22` | feat | Lista no banco, filtros, ordenação e categoria inativa vinculada |
| `c529663` | feat | Edição de título e descrição com versão esperada |

## Desvios e observações

- **Tarefa 2 sem execução vermelha prévia:** os testes foram escritos antes da implementação, mas não foram executados antes dela. A matriz de lista é comparada com o detalhe, já testado de forma independente na Tarefa 1.
- **Risco de INNER JOIN verificado:** `root.get("atendente").get("id")` poderia excluir a fila; o teste da lista do atendente inclui chamado sem atendente e passou.
- Nenhuma migração nova neste plano.

## Verificação

- Suíte completa no MySQL 8.4.11 (Testcontainers): **141/145**.
- As 4 falhas são `AuthHttpErrorIntegrationTest` (loopback do ambiente Windows), reproduzidas na `main`.

## Pendências

- TICK-12 parcial até os planos de transição.
- Handler genérico de `Exception` → 500 continua pendente (registrado no 03-04).
