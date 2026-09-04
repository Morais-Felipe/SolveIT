---
phase: 01-base-do-projeto
plan: 04
subsystem: persistence
tags: [spring-data-jpa, repository-pattern, derived-queries, optional]

requires:
  - phase: 01-03
    provides: Cinco entidades JPA tipadas e alinhadas ao futuro schema Flyway
provides:
  - Cinco boundaries Spring Data JPA tipados para as entidades do domínio
  - Consultas derivadas mínimas para identidade e unicidade de usuário e categoria
  - Superfície de repository preparada para o round-trip de persistência do Ticket 05
affects: [01-05-flyway-validacao, fase-2-autenticacao, fase-3-core-chamados, fase-5-dashboard]

actuals:
  tokens: 737
  tasks: 1
  commits: 2

tech-stack:
  added: []
  patterns: [typed Spring Data repository, derived query method, Optional lookup, explicit ordering at call site]

key-files:
  created:
    - solveit-api/src/main/java/com/solveit/repository/UsuarioRepository.java
    - solveit-api/src/main/java/com/solveit/repository/CategoriaRepository.java
    - solveit-api/src/main/java/com/solveit/repository/ChamadoRepository.java
    - solveit-api/src/main/java/com/solveit/repository/ComentarioRepository.java
    - solveit-api/src/main/java/com/solveit/repository/HistoricoStatusRepository.java
  modified: []

key-decisions:
  - "Somente identidade e unicidade justificam métodos derivados nesta fase: findByEmail, existsByEmail e existsByNome."
  - "Filtros, paginação, ordenação, Specifications, projeções e agregações serão declarados pelas fases consumidoras."
  - "ComentarioRepository e HistoricoStatusRepository não recebem métodos customizados de update/delete; services futuros preservarão D-10 em modo append-only."

patterns-established:
  - "Repository JPA: interface pequena que estende JpaRepository<Entidade, Long>, sem implementação manual."
  - "Consultas relacionais nunca prometem ordem implícita; consumidores devem declarar Sort ou OrderBy."

requirements-completed: [DATA-02]

coverage:
  - id: D1
    description: "As cinco entidades possuem repositories Spring Data tipados com Long como identificador."
    requirement: DATA-02
    verification:
      - kind: other
        ref: ".\\mvnw.cmd -q -DskipTests compile e inspeção estrutural de cinco extensões JpaRepository<Entidade, Long>"
        status: pass
    human_judgment: false
  - id: D2
    description: "A superfície customizada contém somente findByEmail, existsByEmail e existsByNome."
    requirement: DATA-02
    verification:
      - kind: other
        ref: "Inspeção determinística: três métodos derivados aprovados e zero hits proibidos"
        status: pass
    human_judgment: false
  - id: D3
    description: "Os repositories de comentário e histórico não antecipam métodos customizados de mutação, remoção ou consulta."
    requirement: DATA-02
    verification:
      - kind: other
        ref: "Revisão do diff e busca por deleteBy, update, @Query, Specification, Pageable e Sort"
        status: pass
    human_judgment: false

duration: 31 min
completed: 2026-09-03
status: complete
---

# Phase 01 Plan 04: Repositórios Spring Data JPA Summary

**Cinco repositories JPA tipados com consultas derivadas mínimas para identidade e unicidade, prontos para a validação real no MySQL**

## Performance

- **Duration:** 31 min
- **Started:** 2026-09-03T17:22:36Z
- **Completed:** 2026-09-03T17:54:03Z
- **Tasks:** 1
- **Files modified:** 5

## Accomplishments

- Criados repositories tipados para Usuario, Categoria, Chamado, Comentario e HistoricoStatus.
- Adicionados somente os lookups `findByEmail`, `existsByEmail` e `existsByNome`.
- Preservado o escopo append-only de auditoria sem métodos customizados de update/delete.
- Validada a integração estática com as entidades do Ticket 03 por compilação Maven.

## Task Commits

1. **Task 1: Criar os cinco repositories JPA com superfície justificada** - `6358841` (`feat`)

## Files Created/Modified

- `solveit-api/src/main/java/com/solveit/repository/UsuarioRepository.java` - CRUD tipado e consultas por email.
- `solveit-api/src/main/java/com/solveit/repository/CategoriaRepository.java` - CRUD tipado e checagem de nome.
- `solveit-api/src/main/java/com/solveit/repository/ChamadoRepository.java` - boundary CRUD do agregado chamado.
- `solveit-api/src/main/java/com/solveit/repository/ComentarioRepository.java` - boundary mínimo para comentários.
- `solveit-api/src/main/java/com/solveit/repository/HistoricoStatusRepository.java` - boundary mínimo para histórico.

## Conceitos explicados

### Repository e implementação gerada

Um repository é a fronteira Java usada para acessar persistência. Ao estender `JpaRepository<Usuario, Long>`, a interface informa ao Spring Data qual entidade gerencia e qual é o tipo da chave primária. O Spring gera a implementação em tempo de execução, então não é necessário escrever uma classe concreta ou SQL para o CRUD básico.

### Generics

Os tipos entre `< >` são generics: eles fazem o compilador garantir que `UsuarioRepository.save` receba um `Usuario` e que `findById` use um `Long`. Isso detecta incompatibilidades ainda na compilação.

### Query derivation e Optional

Spring Data interpreta nomes como `findByEmail` e deriva uma consulta usando o atributo `email` da entidade. `Optional<Usuario>` representa explicitamente os dois resultados válidos — encontrado ou ausente — sem usar `null` como contrato implícito. Métodos `existsBy...` devolvem somente um booleano quando a aplicação precisa verificar unicidade.

### Ordenação explícita

Um banco relacional não garante a ordem de linhas sem `ORDER BY`. Por isso estes repositories não prometem ordem implícita; as fases que implementarem listagens deverão fornecer `Sort`, `Pageable` ou nomes com `OrderBy` conforme o contrato de cada caso de uso.

### Limite do append-only

`ComentarioRepository` e `HistoricoStatusRepository` herdam operações gerais de `JpaRepository`, incluindo `save` e `delete`. A ausência de métodos customizados reduz a superfície antecipada, mas não é uma proteção absoluta. A Fase 3 deverá expor somente fluxos de inclusão nos services, aplicar autorização e não oferecer endpoints de alteração ou remoção desses registros.

## Verification Evidence

- Gate Git: **PASS** — branch `ticket/04-repositories-jpa`, base `079598a`, resumo predecessor `676e755` e commit citado `f8293ad` comprovados em `origin/main`.
- `.\\mvnw.cmd -q -DskipTests compile`: **PASS** com JDK Temurin 17.
- Inspeção estrutural: **PASS** — 5 arquivos, 5 extensões `JpaRepository<Entidade, Long>` e 3 métodos derivados.
- Busca de escopo: **PASS** — zero ocorrências de `Specification`, `Pageable`, `Sort`, `@Query`, `nativeQuery`, `deleteBy` ou `update`.
- Revisão do commit: **PASS** — `6358841` contém somente os cinco arquivos declarados.

## Decisions Made

- Mantida a superfície mínima do plano para não acoplar persistência a regras ainda não especificadas.
- Nenhuma anotação `@Repository` foi adicionada, pois o Spring Data registra automaticamente interfaces descobertas abaixo do package principal.
- `DATA-02` permanece pendente em REQUIREMENTS.md porque também é declarada pelo Ticket 05, que fará a prova integrada no MySQL.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- O monitor auxiliar de contexto do GSD não iniciou por ausência de `hooks/lib/hook-exit.js`; a falha é externa ao SolveIT e as verificações de Git foram executadas diretamente.
- A primeira montagem do preflight falhou antes de chegar ao PowerShell por erro de sintaxe na chamada da ferramenta; nenhum comando foi executado e a tentativa corrigida passou.
- O Git informou normalização futura de LF para CRLF na cópia de trabalho do Windows; não houve alteração semântica.
- A identidade corporativa configurada automaticamente no Git foi mantida, consistente com os tickets anteriores.

## User Setup Required

None - este ticket compila sem banco ou serviço externo.

## Next Phase Readiness

- Os cinco repositories estão prontos para o teste de round-trip do Ticket 05.
- O schema ainda não existe; migrations Flyway, MySQL 8.4 e o teste integrado pertencem ao próximo ticket.
- Nenhum service, controller, endpoint, filtro, paginação, dashboard ou query nativa foi antecipado.
- Publicação, PR e merge continuam aguardando os gates separados do protocolo.

## Self-Check: PASSED

- Os cinco arquivos existem e pertencem ao commit `6358841`.
- A compilação e as verificações estruturais passaram.
- O diff de implementação respeita integralmente os arquivos e a superfície do plano.

---
*Phase: 01-base-do-projeto*
*Completed: 2026-09-03*
