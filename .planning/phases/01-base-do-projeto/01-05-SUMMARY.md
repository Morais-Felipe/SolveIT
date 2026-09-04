---
phase: 01-base-do-projeto
plan: 05
subsystem: database
tags: [mysql-8.4, flyway, testcontainers, hibernate-validate, optimistic-locking]

requires:
  - phase: 01-03
    provides: Cinco entidades JPA, relações, timestamps UTC e @Version em Chamado
  - phase: 01-04
    provides: Cinco repositories Spring Data JPA sobre o modelo persistente
provides:
  - Schema MySQL completo criado exclusivamente por cinco migrations Flyway ordenadas
  - Contrato físico com PKs, FKs restritivas, uniques, CHECKs, índices e utf8mb4
  - Teste integrado em MySQL 8.4.11 para migrations, Hibernate, repositories, idempotência e concorrência
affects: [fase-2-autenticacao, fase-3-core-chamados, fase-4-notificacoes, fase-5-dashboard]

actuals:
  tokens: 7476
  tasks: 1
  commits: 4

tech-stack:
  added: []
  patterns: [flyway-only-ddl, immutable-versioned-migrations, mysql-testcontainers, hibernate-schema-validation, explicit-database-constraints]

key-files:
  created:
    - solveit-api/src/main/resources/db/migration/V1__create_usuario.sql
    - solveit-api/src/main/resources/db/migration/V2__create_categoria.sql
    - solveit-api/src/main/resources/db/migration/V3__create_chamado.sql
    - solveit-api/src/main/resources/db/migration/V4__create_comentario.sql
    - solveit-api/src/main/resources/db/migration/V5__create_historico_status.sql
    - solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java
  modified: []

key-decisions:
  - "Flyway permanece como única autoridade de DDL; Hibernate usa ddl-auto=validate e nunca cria ou atualiza o schema."
  - "As colunas Boolean do modelo usam BIT com default verdadeiro, alinhadas ao mapeamento Hibernate/MySQL."
  - "Contextos Spring criados pelo teste recebem datasource e perfil como argumentos de run para prevalecer sobre application-dev.yml."
  - "Assertions de CHECK validam a abstração DataAccessException e o nome da constraint, sem depender da classificação incompleta do tradutor JDBC."

patterns-established:
  - "Uma migration por tabela/agregado, ordenada pelas dependências de chave estrangeira."
  - "Idempotência de schema pertence a flyway_schema_history; migrations não usam IF NOT EXISTS."
  - "Testes de persistência usam MySQL real efêmero, flush/clear/reload e ordem explícita nas consultas de sequência."

requirements-completed: [DATA-01, DATA-02, DATA-03, DATA-04]

coverage:
  - id: D1
    description: "Um MySQL 8.4.11 vazio recebe exatamente V1 a V5 em ordem e mantém histórico idempotente."
    requirement: DATA-03
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java#concurrentSpringContextsMigrateAnEmptyDatabaseExactlyOnce"
        status: pass
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java#repeatedMigrationAndHibernateValidationDoNotChangeSchemaOrHistory"
        status: pass
    human_judgment: false
  - id: D2
    description: "Hibernate valida a paridade JPA/SQL e os repositories persistem e releem o grafo Unicode completo."
    requirement: DATA-02
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java#repositoriesPersistFlushClearAndReloadTheUnicodeDomainGraph"
        status: pass
    human_judgment: false
  - id: D3
    description: "O schema aplica PKs, FKs restritivas, uniques, CHECKs, índices, nulabilidade e collation aprovados."
    requirement: DATA-04
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java#schemaMetadataMatchesTheApprovedPhysicalContract"
        status: pass
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java#databaseRejectsDuplicatesRequiredNullsInvalidEnumsAndOrphanReferences"
        status: pass
    human_judgment: false
  - id: D4
    description: "Persistência mutável é idempotente e atualizações obsoletas de Chamado falham por optimistic locking."
    requirement: DATA-01
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java#savingTheSameMutableEntityUpdatesOneRowInsteadOfDuplicatingIt"
        status: pass
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java#staleConcurrentTicketUpdateFailsAndVersionIncrements"
        status: pass
    human_judgment: false

duration: 16min
completed: 2026-09-04
status: complete
---

# Phase 01 Plan 05: Flyway e validação integrada Summary

**Cinco migrations Flyway imutáveis com contrato MySQL completo, validadas por Hibernate e sete testes integrados de persistência, constraints, idempotência e concorrência**

## Performance

- **Duration:** 16 min
- **Started:** 2026-09-04T12:34:00Z
- **Completed:** 2026-09-04T12:50:26Z
- **Tasks:** 1
- **Files modified:** 6

## Accomplishments

- Criadas V1→V5 para `usuario`, `categoria`, `chamado`, `comentario` e `historico_status`, respeitando a ordem das FKs.
- Materializado o contrato físico com InnoDB, `utf8mb4_0900_ai_ci`, PKs, uniques, CHECKs, FKs restritivas e índices nomeados.
- Validada a paridade entre Flyway e as cinco entidades JPA usando `ddl-auto=validate`, sem DDL emitido pelo Hibernate.
- Comprovados grafo Unicode, nulabilidade, constraints adversariais, resave por ID, locking otimista e inicialização concorrente do Flyway em MySQL 8.4.11 real.

## Task Commits

1. **RED — contrato integrado de persistência** — `c0a38f4` (`test`)
2. **Correção do harness isolado** — `913198d` (`test`)
3. **GREEN — schema MySQL versionado V1→V5** — `c5fb6d0` (`feat`)

## Files Created/Modified

- `solveit-api/src/main/resources/db/migration/V1__create_usuario.sql` — usuário, email único e CHECK de perfil.
- `solveit-api/src/main/resources/db/migration/V2__create_categoria.sql` — categoria com nome único e descrição opcional.
- `solveit-api/src/main/resources/db/migration/V3__create_chamado.sql` — agregado, três FKs, filtros indexados, enums e `version`.
- `solveit-api/src/main/resources/db/migration/V4__create_comentario.sql` — comentário auditável com relações restritivas.
- `solveit-api/src/main/resources/db/migration/V5__create_historico_status.sql` — histórico de status auditável com CHECKs e índices.
- `solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java` — tracer integrado com sete testes sobre MySQL real.

## Conceitos explicados

### Migration e histórico do Flyway

Migration é uma alteração versionada do banco. O Flyway lê V1→V5, registra versão e checksum em `flyway_schema_history` e usa esse histórico para não executar novamente o mesmo SQL. Por isso não há `IF NOT EXISTS`: uma divergência deve falhar visivelmente, não ser escondida.

### PK, FK, UNIQUE, CHECK e índice

A chave primária identifica uma linha; a chave estrangeira exige que uma referência exista. UNIQUE impede duplicidades e CHECK restringe valores possíveis, como perfis e status. Índices aceleram consultas previstas, mas têm custo em escrita e armazenamento, por isso foram criados somente para relações e filtros aprovados.

### Flyway lock e concorrência

Dois contextos Spring iniciaram simultaneamente sobre o mesmo schema vazio. O Flyway coordenou o acesso ao histórico: cada versão apareceu uma única vez, o schema terminou em V5 e ambos os contextos só ficaram ativos depois da validação.

### Hibernate validate

`ddl-auto=validate` compara as entidades Java com o schema já criado. Ele falha quando tabela, coluna ou tipo diverge, mas não corrige o banco. Isso mantém Flyway como fonte única do DDL e transforma diferenças JPA/SQL em erro explícito de inicialização.

### Collation e Unicode

`utf8mb4` permite o conjunto Unicode completo, inclusive emoji. A collation `utf8mb4_0900_ai_ci` define comparação sem diferenciar maiúsculas/minúsculas e acentos para operações textuais, tornando unicidades previsíveis.

### Optimistic locking

O campo `@Version` acrescenta uma versão à linha de `chamado`. Duas transações podem ler a mesma versão, mas somente a primeira atualização confirma; a segunda percebe que sua cópia ficou obsoleta e falha em vez de sobrescrever dados silenciosamente.

## Verification Evidence

- Preflight: **PASS** — branch `ticket/05-flyway-validacao`, base `origin/main` em `b22b252`, Java 17.0.20.1 e Docker Linux 29.7.2.
- RED: **PASS** — teste compilou, MySQL iniciou, Flyway encontrou zero migrations e Hibernate falhou com `Schema-validation: missing table [categoria]`.
- GREEN final: **PASS** — 7 testes, 0 failures, 0 errors e 0 skipped.
- História: **PASS** — V1, V2, V3, V4 e V5 registradas uma única vez e em ordem; segunda migração executou zero versões.
- Concorrência: **PASS** — dois contextos completaram Flyway/Hibernate sobre o mesmo schema vazio e o conflito otimista rejeitou a cópia obsoleta de Chamado.
- Dependências: **PASS** — `mvn dependency:tree -Dincludes=com.h2database:h2` não encontrou H2.
- Escopo: **PASS** — diff técnico restrito às cinco migrations e ao teste, sem exclusões.

## Decisions Made

- Booleans foram materializados como `BIT NOT NULL DEFAULT b'1'`, compatíveis com o mapeamento Hibernate/MySQL existente.
- Todas as FKs declaram `ON DELETE RESTRICT ON UPDATE RESTRICT`; nenhuma auditoria é removida em cascata.
- Contextos concorrentes recebem configurações como argumentos de `run(...)`, cuja precedência impede o perfil `dev` de substituir a URL do Testcontainers.
- A prova de CHECK exige a constraint `ck_usuario_perfil`, sem acoplar o teste à subclasse escolhida pelo tradutor de exceções JDBC.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Contextos isolados herdavam o datasource de desenvolvimento**
- **Found during:** Task 1, primeira execução GREEN.
- **Issue:** `SpringApplicationBuilder.properties(...)` tinha precedência inferior a `application-dev.yml`; os contextos concorrentes ativavam `dev` e tentavam `localhost:3306`.
- **Fix:** Perfil e datasource passaram a ser argumentos de `run(...)`, com precedência de linha de comando.
- **Files modified:** `solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java`.
- **Verification:** ambos os contextos registraram perfil `test`, URL dinâmica do container e iniciaram com sucesso.
- **Committed in:** `913198d`.

**2. [Rule 1 - Bug] Assertion dependia de classificação JDBC não fornecida para CHECK**
- **Found during:** Task 1, segunda execução GREEN.
- **Issue:** MySQL rejeitou o perfil inválido com erro 3819, mas Spring o traduziu como `UncategorizedSQLException`, não `DataIntegrityViolationException`.
- **Fix:** a assertion passou a exigir `DataAccessException` e o nome `ck_usuario_perfil` na mensagem.
- **Files modified:** `solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java`.
- **Verification:** a constraint continuou sendo comprovada e os sete testes passaram.
- **Committed in:** `913198d`.

**Total deviations:** 2 auto-fixed (2 Rule 1 bugs no harness de teste). **Impact:** correções necessárias para testar o comportamento planejado; sem mudança arquitetural ou expansão de escopo.

## Issues Encountered

- O primeiro script de preflight tratou a saída histórica de `java -version` em stderr como erro PowerShell; foi corrigido para `java --version` antes de repetir.
- Flyway 11.7.2 avisou que sua matriz declarada testa MySQL até 8.1. O teste real completo em MySQL 8.4.11 passou; o aviso permanece registrado para reavaliação em futuras atualizações do Spring Boot BOM.
- Git continua inferindo a identidade corporativa `FELIPE MIRANDA MORAIS <felipe.morais@gabaritoeducacao.com>`; confirmar a identidade desejada antes do push e antes de configurar o desktop pessoal.
- O hook local `gsd-context-monitor` está quebrado por ausência de `.codex/hooks/lib/hook-exit.js`; `gsd-check-update` funciona.
- `init.execute-phase` reporta `agent_runtime: claude` apesar do runtime global e da sessão serem Codex; origem ainda não recuperada.

## Pendências registradas

- **GSD:** reparar o arquivo ausente do context monitor e investigar a identificação divergente de runtime.
- **Documentação:** atualizar o status defasado do README e a seção **Tarefa Atual** de `SolveIT_GSD_SystemPrompt.md` em ação documental separada.
- **Git:** confirmar `user.name` e `user.email` desejados antes da publicação e alinhar a mesma identidade no desktop pessoal.

## User Setup Required

None — Docker Desktop já está instalado e o daemon Linux foi validado. Não foram usadas credenciais reais; o Testcontainers criou banco e credenciais efêmeros.

## Next Phase Readiness

- O ticket 05 está completo e validado localmente; a fundação relacional está pronta para a Fase 2.
- Publicação permanece bloqueada até autorização separada para push e PR; merge continuará exigindo outra aprovação explícita.
- A Fase 2 não será iniciada automaticamente. As pendências documentais/GSD acima devem ser reconciliadas sem alterar retroativamente as migrations.

## Self-Check: PASSED

- Os seis arquivos técnicos existem e os commits `c0a38f4`, `913198d` e `c5fb6d0` estão alcançáveis a partir de HEAD.
- O teste pós-commit registrou 7/7 aprovações, sem skip, sobre `mysql:8.4.11`.
- H2 está ausente e o diff técnico não contém arquivos fora do plano nem exclusões.

---
*Phase: 01-base-do-projeto*
*Completed: 2026-09-04*
