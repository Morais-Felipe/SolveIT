---
phase: 03-core-de-chamados
plan: "04"
subsystem: api
tags: [spring-mvc, spring-security, jpa, flyway, mysql, check-constraint, transactions, concurrency, testcontainers]

requires:
  - phase: 03-core-de-chamados
    plan: "03"
    provides: Usuários administráveis, padrão de comando versionado e coordenação das escritas
provides:
  - POST /api/chamados para SOLICITANTE, com chamado ABERTO sem atendente
  - Histórico de abertura null→ABERTO gravado na mesma transação do chamado
  - Migração V9 permitindo status_anterior nulo somente para a abertura
  - Rejeição genérica de campos fora do contrato via @JsonAnySetter
  - Teste de concorrência determinístico baseado em performance_schema.data_lock_waits
affects: [03-05-consulta-chamados, 03-06-transicoes, historico, categorias, core-write-protocol]

ticket:
  number: 14
  branch: ticket/14-abertura-chamados
  pr: pendente

actuals:
  tasks: 3
  commits: 5
  executor: Claude Code (sem GSD)

tech-stack:
  added: []
  patterns:
    - Abertura segue guard → solicitante atual → categoria com PESSIMISTIC_WRITE → chamado + histórico + flush
    - HistoricoStatus.abertura copia criadoEm do chamado já persistido; nenhuma data vem do cliente
    - Request DTO com @JsonAnySetter + @Size(max = 0) rejeita qualquer campo desconhecido
    - Testes de ordem concorrente esperam a lock wait aparecer no InnoDB em vez de dormir

key-files:
  created:
    - solveit-api/src/main/resources/db/migration/V9__allow_initial_history.sql
    - solveit-api/src/main/java/com/solveit/controller/ChamadoController.java
    - solveit-api/src/main/java/com/solveit/service/ChamadoService.java
    - solveit-api/src/main/java/com/solveit/dto/chamado/ChamadoDtos.java
    - solveit-api/src/test/java/com/solveit/controller/ChamadoAberturaIntegrationTest.java
    - solveit-api/src/test/java/com/solveit/service/ChamadoAberturaConcorrenciaTest.java
  modified:
    - solveit-api/src/main/java/com/solveit/entity/HistoricoStatus.java
    - solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java
    - solveit-api/src/test/java/com/solveit/service/CoreWriteCoordinatorIntegrationTest.java

key-decisions:
  - "V9 é aditiva: MODIFY status_anterior NULL + CHECK (status_anterior IS NOT NULL OR status_novo = 'ABERTO'). V5 não foi editada e nenhum histórico retroativo é criado."
  - "Categoria inexistente → 404; categoria inativa → 409 INVALID_STATE, lida após o guard e travada com PESSIMISTIC_WRITE."
  - "Opção B aprovada: @JsonAnySetter coleta campos fora do contrato e @Size(max = 0) os rejeita com 400; comprovado com Jackson 2.21.4, sem necessidade do fallback @Null."
  - "O teste flaky do guard passa a aceitar PessimisticLockingFailureException ou QueryTimeoutException; os dois limites são 5 s e ambos viram 503 CORE_BUSY."

patterns-established:
  - "Reject unknown fields: componente @JsonAnySetter Map validado com @Size(max = 0)."
  - "Deterministic ordering test: segurar a primeira transação até performance_schema.data_lock_waits mostrar a segunda na fila."

requirements-completed: [TICK-01]
requirements-partial:
  - id: TICK-11
    covered: "Abertura registra autor, data e estado inicial ABERTO na mesma transação."
    remaining: "Histórico das transições e devoluções administrativas (planos seguintes)."
  - id: TICK-12
    covered: "Abertura e desativação de categoria são serializadas pelo guard com resultado determinado."
    remaining: "Atualizações concorrentes do mesmo chamado (planos de transição)."

coverage:
  - id: D1
    description: "D-13/D-16: SOLICITANTE abre chamado ABERTO; histórico null→ABERTO com autor e data reais; campos do servidor rejeitados."
    requirement: "TICK-01"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/ChamadoAberturaIntegrationTest.java"
        status: pass
    human_judgment: false
  - id: D2
    description: "D-19: falha real do histórico (trigger MySQL) desfaz o chamado já enviado ao banco."
    requirement: "TICK-11"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/ChamadoAberturaIntegrationTest.java#historyFailureRollsBackTheTicket"
        status: pass
    human_judgment: false
  - id: D3
    description: "D-11: desativação primeiro → 409 sem histórico; abertura primeiro → chamado preservado."
    requirement: "TICK-12"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/service/ChamadoAberturaConcorrenciaTest.java"
        status: pass
    human_judgment: false
  - id: D4
    description: "V9 em banco vazio e upgrade, sem fabricar passado; CHECK aceita só null→ABERTO."
    requirement: "TICK-11"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java"
        status: pass
    human_judgment: false
---

# Plano 03-04 — Abrir chamado com histórico atômico

## Resultado

`POST /api/chamados` permite ao SOLICITANTE abrir um chamado. Chamado e histórico de origem (`null → ABERTO`) são gravados juntos ou nenhum é gravado.

| Situação | Resposta |
|---|---|
| SOLICITANTE, categoria ativa | 201 + `Location` + detalhe com `solicitante`, `atendente:null`, `categoria` |
| ADMIN ou ATENDENTE | 403 |
| Campo inválido | 400 |
| Campo do servidor ou desconhecido | 400 `VALIDATION_ERROR` |
| Categoria inexistente | 404 |
| Categoria inativa | 409 `INVALID_STATE` |

## Commits

| Commit | Tipo | Descrição |
|---|---|---|
| `2ddb3d1` | feat | V9, rota, service, DTOs e histórico de abertura |
| `e0ecf22` | test | Rollback por trigger real e as duas ordens de concorrência |
| `29ed2e0` | test | V9 em banco vazio/upgrade e CHECKs no MySQL |
| `917c348` | test | Teste flaky do guard aceita os dois timeouts equivalentes |

## Desvios do plano

1. **Fixture de upgrade em V6, não V8:** o teste de upgrade existente foi estendido com chamado e transição legítimos em V6; ele atravessa V7, V8 e V9, e a V9 é a única que toca o histórico.
2. **Rollback provado no service, não via HTTP:** o `GlobalExceptionHandler` não tem handler genérico de `Exception`, então uma falha de banco não classificada não vira 500 controlado. O plano exige provar a atomicidade, o que foi feito chamando o service.
3. **Correção de teste instável fora do escopo, aprovada:** `CoreWriteCoordinatorIntegrationTest` falhava intermitentemente por dois timeouts de 5 s concorrentes.

## Verificação

- Suíte completa no MySQL 8.4.11 (Testcontainers): **121/126** antes da correção do teste instável; após a correção, `CoreWriteCoordinatorIntegrationTest` passou 5/5 em três execuções seguidas.
- As 4 falhas restantes são `AuthHttpErrorIntegrationTest` (loopback do ambiente Windows), reproduzidas na `main`.

## Pendências

- Handler genérico de `Exception` → 500 `INTERNAL_ERROR` sem vazar detalhes.
- TICK-11 e TICK-12 continuam parciais até os planos de transição.
