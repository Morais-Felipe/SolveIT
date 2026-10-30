---
phase: 03-core-de-chamados
plan: "10"
subsystem: api
tags: [spring-mvc, spring-security, jpa, pessimistic-locking, optimistic-locking, concurrency, history, testcontainers]

requires:
  - phase: 03-core-de-chamados
    plan: "09"
    provides: Leitura de histórico e todas as transições manuais
provides:
  - PATCH /api/usuarios/{id}/operacao para perfil e atividade numa operação atômica
  - Devolução auditada dos chamados EM_ANDAMENTO à fila ao desativar ou retirar o perfil ATENDENTE
  - Proteção do último ADMIN ativo, inclusive sob concorrência
affects: [04-notificacoes, 06-front-end]

ticket:
  number: 22
  branch: ticket/22-atividade-perfil-usuarios
  pr: ver STATE.md

actuals:
  tasks: 2
  commits: 3
  executor: Claude Code (sem GSD)

scope-review:
  date: 2026-10-30
  removed: "Rotas de prévia e confirmação com lista ecoada; corridas entre prévia e confirmação; trigger de rollback."
  kept: "Atomicidade, versão esperada do alvo, motivos automáticos, um histórico por chamado devolvido, último ADMIN."

tech-stack:
  added: []
  patterns:
    - Operação administrativa única com guard, locks por id e versão esperada
    - Devolução direta EM_ANDAMENTO → ABERTO pelo service, fora da tabela de transições manuais
    - Contagem de ADMINs ativos feita depois do guard, no estado confirmado

key-files:
  created:
    - solveit-api/src/test/java/com/solveit/controller/UsuarioOperacaoIntegrationTest.java
    - solveit-api/src/test/java/com/solveit/service/UsuarioOperacaoConcorrenciaTest.java
  modified:
    - solveit-api/src/main/java/com/solveit/controller/UsuarioController.java
    - solveit-api/src/main/java/com/solveit/service/UsuarioService.java
    - solveit-api/src/main/java/com/solveit/dto/usuario/UsuarioDtos.java
    - solveit-api/src/main/java/com/solveit/repository/ChamadoRepository.java
    - solveit-api/src/main/java/com/solveit/repository/UsuarioRepository.java

key-decisions:
  - "Aprovada: uma rota PATCH /api/usuarios/{id}/operacao com {perfil, ativo, versaoEsperada}; resposta {usuario, chamadosDevolvidos}."
  - "Operação sem mudança real responde 409 INVALID_STATE."
  - "Motivos fixos: 'Retorno à fila por desativação do atendente' (prevalece quando também troca o perfil) e 'Retorno à fila por mudança de perfil do atendente'."
  - "Na desativação mútua entre dois ADMINs, o segundo é barrado como ator inativo (401) antes da regra de último ADMIN; na autodesativação simultânea, por LAST_ACTIVE_ADMIN."
  - "A rota manual de status continua recusando EM_ANDAMENTO → ABERTO."

requirements-completed: [ADMN-03, TICK-10, TICK-11, TICK-12]
requirements-partial: []

coverage:
  - id: D1
    description: "Desativação e troca de perfil devolvem os EM_ANDAMENTO do atendente com um histórico cada; encerrados e chamados de outros intactos; fila e token refletem a mudança."
    requirement: "ADMN-03, TICK-10, TICK-11"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/UsuarioOperacaoIntegrationTest.java"
        status: pass
    human_judgment: false
  - id: D2
    description: "Último ADMIN protegido sequencialmente e sob concorrência real (mútua e autodesativação)."
    requirement: "ADMN-03, TICK-12"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/service/UsuarioOperacaoConcorrenciaTest.java"
        status: pass
    human_judgment: false
---

# Plano 03-10 — Atividade e perfil de usuários com devolução auditada

## Resultado

`PATCH /api/usuarios/{id}/operacao` (ADMIN) com `{perfil, ativo, versaoEsperada}` → `{usuario, chamadosDevolvidos}`.

| Situação | Resposta |
|---|---|
| Mudança válida | 200; chamados EM_ANDAMENTO do atendente voltam a ABERTO com histórico |
| Não ADMIN | 403 |
| Versão antiga | 409 `STALE_RESOURCE` |
| Nada muda | 409 `INVALID_STATE` |
| Deixaria o sistema sem ADMIN ativo | 409 `LAST_ACTIVE_ADMIN` |
| Corpo inválido ou campo extra | 400 |
| Usuário inexistente | 404 |

## Commits

| Commit | Tipo | Descrição |
|---|---|---|
| `aa70f49` | feat | Rota, service, consultas e DTOs da operação |
| `81ac4ae` | test | Concorrência do último ADMIN (mútua e autodesativação) |

## Observações

- Primeiro plano executado sob a revisão de escopo de 2026-10-30: sem prévia, testes proporcionais, uma única disputa concorrente nova testada.
- Com este plano a Fase 3 está concluída.

## Verificação

- Suíte completa no MySQL 8.4.11 (Testcontainers): **212/216**.
- As 4 falhas são `AuthHttpErrorIntegrationTest` (loopback do ambiente Windows), reproduzidas na `main`.
