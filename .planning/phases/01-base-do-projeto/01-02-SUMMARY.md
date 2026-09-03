---
phase: 01-base-do-projeto
plan: 02
subsystem: domain-model
tags: [java-17, enums, jpa, textual-persistence]

requires:
  - phase: 01-01
    provides: Build reproduzível do módulo solveit-api com Java 17 e Maven Wrapper
provides:
  - Perfis canônicos SOLICITANTE, ATENDENTE e ADMIN
  - Estados canônicos ABERTO, EM_ANDAMENTO, RESOLVIDO, FECHADO e CANCELADO
  - Prioridades canônicas BAIXA, MEDIA, ALTA e CRITICA
affects: [01-03-entidades-jpa, 01-05-flyway-validacao, fase-2-autenticacao, fase-3-core-chamados]

actuals:
  tokens: 83
  tasks: 1
  commits: 2

tech-stack:
  added: []
  patterns: [vocabulários fechados com enum Java, nomes canônicos preparados para persistência textual]

key-files:
  created:
    - solveit-api/src/main/java/com/solveit/enums/PerfilUsuario.java
    - solveit-api/src/main/java/com/solveit/enums/StatusChamado.java
    - solveit-api/src/main/java/com/solveit/enums/Prioridade.java
  modified: []

key-decisions:
  - "Os enums contêm somente as constantes canônicas; regras de autorização, transição e apresentação permanecem nas fases próprias."
  - "A ordem das constantes é documental e a persistência futura usará os nomes textuais com EnumType.STRING, nunca posições ordinais."

patterns-established:
  - "Enums de domínio ficam em com.solveit.enums e não carregam labels de UI ou lógica de negócio."
  - "Identificadores persistidos são contratos de dados: reordenar é seguro com STRING, mas renomear exige migração."

requirements-completed: [DATA-02]

coverage:
  - id: D1
    description: "Os três enums expõem somente os valores canônicos, na grafia e ordem documental aprovadas."
    requirement: DATA-02
    verification:
      - kind: other
        ref: ".\\mvnw.cmd -q -DskipTests compile + inspeção determinística das constantes"
        status: pass
    human_judgment: false

duration: 3min
completed: 2026-09-03
status: complete
---

# Phase 01 Plan 02: Enums canônicos do domínio Summary

**Três vocabulários Java fechados e compiláveis, com nomes estáveis para o futuro mapeamento textual entre JPA e MySQL**

## Performance

- **Duration:** 3 min
- **Started:** 2026-09-03T16:19:47Z
- **Completed:** 2026-09-03T16:22:24Z
- **Tasks:** 1
- **Files modified:** 3

## Accomplishments

- Criado `PerfilUsuario` com `SOLICITANTE`, `ATENDENTE` e `ADMIN`.
- Criado `StatusChamado` com `ABERTO`, `EM_ANDAMENTO`, `RESOLVIDO`, `FECHADO` e `CANCELADO`.
- Criado `Prioridade` com `BAIXA`, `MEDIA`, `ALTA` e `CRITICA`.
- Mantidos os três tipos sem métodos, campos, labels de interface ou regras pertencentes a tickets posteriores.

## Task Commits

1. **Task 1: Criar e compilar os enums canônicos do domínio** - `77072a3` (`feat`)

## Files Created/Modified

- `solveit-api/src/main/java/com/solveit/enums/PerfilUsuario.java` - vocabulário fechado dos três perfis do sistema.
- `solveit-api/src/main/java/com/solveit/enums/StatusChamado.java` - vocabulário fechado dos cinco estados possíveis de um chamado.
- `solveit-api/src/main/java/com/solveit/enums/Prioridade.java` - vocabulário fechado dos quatro níveis de prioridade.

## Conceitos explicados

### O que um enum representa

Um `enum` define um conjunto fechado de valores válidos. Em vez de aceitar qualquer texto para perfil, status ou prioridade, o compilador restringe o código às constantes declaradas. Isso evita grafias inconsistentes e torna o domínio mais explícito.

### Persistência textual versus ordinal

No Ticket 03, os campos das entidades serão anotados com `@Enumerated(EnumType.STRING)`. Assim, o banco armazenará nomes legíveis como `EM_ANDAMENTO` ou `CRITICA`. A anotação pertence às entidades, não aos próprios enums; este ticket prepara os identificadores exatos que formarão esse contrato.

Com persistência ordinal, o banco guardaria posições numéricas como `0`, `1` e `2`. Inserir ou reordenar uma constante poderia fazer um número antigo passar a significar outra coisa. Com `STRING`, reordenar as constantes não altera os dados porque cada registro continua associado ao mesmo nome.

Por outro lado, renomear uma constante persistida não é apenas uma refatoração Java: os registros existentes ainda contêm o nome anterior. Essa mudança exige uma migração de dados e atualização coordenada das constraints SQL e dos consumidores.

## Verification Evidence

- Preflight Git: **PASS** — branch `ticket/02-enums`, `origin/main` como ancestral e Ticket 01 comprovado no remoto.
- Java: **PASS** — Temurin 17.0.20.1 carregado a partir do `JAVA_HOME` da máquina.
- `.\\mvnw.cmd -q -DskipTests compile`: **PASS**.
- Inspeção determinística: **PASS** — constantes, grafia e ordem correspondem exatamente ao plano.
- Revisão de comportamento: **PASS** — nenhum campo, método, construtor especial ou dependência adicional.
- Revisão de escopo: **PASS** — o commit de implementação contém exatamente os três arquivos declarados.

## Decisions Made

- Os enums permanecem tipos mínimos: autorização por perfil, transições de status e labels visuais não foram antecipados.
- A ordem declarada é mantida para leitura documental, mas nenhuma persistência futura poderá depender dela.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- A primeira inspeção de escopo tratou o diretório não rastreado agregado pelo `git status` como se fosse um arquivo fora do plano. A verificação foi corrigida para enumerar arquivos não rastreados individualmente; a implementação não precisou de alteração.

## Known Stubs

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Os tipos estão prontos para serem usados pelas entidades com `@Enumerated(EnumType.STRING)` no Ticket 03.
- O avanço permanece bloqueado pelos gates do protocolo: evidência local, autorização de push/PR, autorização separada de merge e prova em `origin/main`.
- Nenhuma entidade, repository, migration ou funcionalidade do Ticket 03 foi criada.

## Self-Check: PASSED

- Os três arquivos declarados existem, compilam e pertencem ao commit `77072a3`.
- O commit `77072a3` está alcançável a partir de `HEAD` na branch `ticket/02-enums`.
- As constantes e o escopo foram verificados novamente após o commit.

---
*Phase: 01-base-do-projeto*
*Completed: 2026-09-03*
