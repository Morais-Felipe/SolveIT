---
phase: 01-base-do-projeto
plan: 03
subsystem: domain-model
tags: [jakarta-persistence, jpa, hibernate, lazy-loading, optimistic-locking, utc]

requires:
  - phase: 01-02
    provides: Enums canônicos e estáveis para perfil, status e prioridade
provides:
  - Cinco entidades JPA alinhadas ao contrato físico de usuario, categoria, chamado, comentario e historico_status
  - Relações filho-para-pai unidirecionais e LAZY com nulabilidade explícita
  - Timestamps de ciclo de vida em UTC, auditoria append-only e optimistic locking de Chamado
affects: [01-04-repositories-jpa, 01-05-flyway-validacao, fase-2-autenticacao, fase-3-core-chamados]

actuals:
  tokens: 3267
  tasks: 1
  commits: 2

tech-stack:
  added: []
  patterns: [explicit JPA physical metadata, child-to-parent lazy relations, UTC lifecycle callbacks, append-only audit records, optimistic locking]

key-files:
  created:
    - solveit-api/src/main/java/com/solveit/entity/Usuario.java
    - solveit-api/src/main/java/com/solveit/entity/Categoria.java
    - solveit-api/src/main/java/com/solveit/entity/Chamado.java
    - solveit-api/src/main/java/com/solveit/entity/Comentario.java
    - solveit-api/src/main/java/com/solveit/entity/HistoricoStatus.java
  modified: []

key-decisions:
  - "Nomes, nulabilidade, comprimentos, unicidade e tipos físicos relevantes ficam explícitos nas anotações para o Flyway copiá-los no Ticket 05."
  - "Relações permanecem unidirecionais e LAZY; apenas Chamado.atendente aceita null."
  - "Comentario e HistoricoStatus expõem construção completa e getters, sem setters de conteúdo ou relações."
  - "Chamado usa Long com @Version e timestamps obrigatórios são preenchidos por callbacks JPA em UTC."

patterns-established:
  - "Entidade JPA: construtor vazio protected para o ORM, construtor público para criação e nenhum Lombok."
  - "Contrato dual: anotações JPA espelham o schema, mas Flyway continuará sendo o único responsável por criá-lo."
  - "Entidades de auditoria são append-only na superfície Java e não recebem cascade de remoção."

requirements-completed: [DATA-02, DATA-04]

coverage:
  - id: D1
    description: "As cinco entidades representam os campos, nomes físicos e nulabilidade aprovados sem funcionar como contratos REST."
    requirement: DATA-02
    verification:
      - kind: other
        ref: ".\\mvnw.cmd -q -DskipTests compile + inspeção determinística do conjunto de arquivos e das anotações @Table/@Column"
        status: pass
    human_judgment: false
  - id: D2
    description: "Os cinco campos enum usam EnumType.STRING e as sete relações filho-para-pai são LAZY, unidirecionais e sem remoção em cascata."
    requirement: DATA-02
    verification:
      - kind: other
        ref: "Inspeção determinística: 5 @Enumerated(EnumType.STRING), 7 @ManyToOne LAZY e zero padrões de cascade/orphanRemoval"
        status: pass
    human_judgment: false
  - id: D3
    description: "Callbacks JPA preenchem timestamps obrigatórios em UTC e Chamado possui uma versão numérica para optimistic locking."
    requirement: DATA-04
    verification:
      - kind: other
        ref: "Inspeção determinística: 5 atribuições LocalDateTime.now(ZoneOffset.UTC) e 1 @Version; compilação Maven aprovada"
        status: pass
    human_judgment: false
  - id: D4
    description: "Comentario e HistoricoStatus têm forma append-only, sem setters de conteúdo/relações, coleções órfãs ou caminhos de remoção em cascata."
    requirement: DATA-02
    verification:
      - kind: other
        ref: "Busca estrutural por setters e padrões proibidos nas duas entidades de auditoria"
        status: pass
    human_judgment: false

duration: 6min
completed: 2026-09-03
status: complete
---

# Phase 01 Plan 03: Entidades e mapeamentos JPA Summary

**Cinco entidades JPA compiláveis com contrato físico explícito, relações LAZY, timestamps UTC, auditoria append-only e optimistic locking de chamados**

## Performance

- **Duration:** 6 min
- **Started:** 2026-09-03T17:06:30Z
- **Completed:** 2026-09-03T17:12:46Z
- **Tasks:** 1
- **Files modified:** 5

## Accomplishments

- Criadas `Usuario`, `Categoria`, `Chamado`, `Comentario` e `HistoricoStatus` no package `com.solveit.entity`.
- Declarados nomes físicos, tipos, comprimentos, unicidade e nulabilidade para o futuro schema Flyway.
- Mapeados cinco campos enum por nome e sete relações filho-para-pai com carregamento LAZY.
- Adicionados callbacks JPA em UTC e `@Version` numérico em `Chamado`.
- Preservada a auditoria com `Comentario` e `HistoricoStatus` sem setters de conteúdo/relações ou cascades de remoção.

## Task Commits

1. **Task 1: Mapear Usuario, Categoria, Chamado, Comentario e HistoricoStatus** - `f8293ad` (`feat`)

## Files Created/Modified

- `solveit-api/src/main/java/com/solveit/entity/Usuario.java` - identidade persistente, perfil textual, ativação e data de criação em UTC.
- `solveit-api/src/main/java/com/solveit/entity/Categoria.java` - categoria ativável com nome único e descrição opcional em `TEXT`.
- `solveit-api/src/main/java/com/solveit/entity/Chamado.java` - agregado com estado, prioridade, três relações, timestamps e optimistic locking.
- `solveit-api/src/main/java/com/solveit/entity/Comentario.java` - registro append-only associado a chamado e autor.
- `solveit-api/src/main/java/com/solveit/entity/HistoricoStatus.java` - registro append-only da mudança de estado, ator, instante e observação opcional.

## Conceitos explicados

### Entidade, chave primária e chave estrangeira

`@Entity` torna uma classe Java gerenciada pelo JPA e `@Table` liga essa classe a uma tabela. `@Id` marca a chave primária, que identifica cada linha; `GenerationType.IDENTITY` delega ao `AUTO_INCREMENT` do MySQL a geração do próximo identificador.

As anotações `@ManyToOne` e `@JoinColumn` descrevem as futuras chaves estrangeiras. Neste ticket elas formam o contrato do ORM; as constraints reais no banco serão criadas pelas migrations do Ticket 05.

### LAZY e relações unidirecionais

`FetchType.LAZY` solicita que Hibernate carregue a entidade relacionada somente quando ela for acessada. Isso evita buscar usuários, categorias e chamados desnecessariamente. A relação é unidirecional: o filho conhece o pai, sem coleções automáticas no pai que aumentariam o grafo e poderiam provocar ciclos.

### Nulabilidade e contrato dual JPA/Flyway

`nullable = false` documenta que o campo é obrigatório; `nullable = true` explicita as exceções legítimas. A anotação sozinha não cria o banco porque `ddl-auto=validate` permanece ativo. O Ticket 05 repetirá o mesmo contrato em SQL e o Hibernate verificará se os dois lados correspondem.

### Persistência textual de enums

`@Enumerated(EnumType.STRING)` armazena nomes como `EM_ANDAMENTO` e `CRITICA`. Isso mantém os dados legíveis e evita que uma simples reordenação das constantes Java mude seu significado no banco. Renomear uma constante persistida continuará exigindo migração de dados.

### Callbacks e UTC

`@PrePersist` executa antes do primeiro `INSERT`, enquanto `@PreUpdate` executa antes de um `UPDATE`. Os callbacks usam `LocalDateTime.now(ZoneOffset.UTC)` para manter uma referência temporal consistente entre máquinas. `resolvidoEm` permanece opcional e será controlado pela regra de transição na Fase 3.

### Optimistic locking

`@Version` faz o Hibernate incluir a versão esperada no `UPDATE`. Se outra transação tiver alterado o chamado antes, a versão não corresponderá e o JPA poderá rejeitar a atualização obsoleta, evitando o chamado *lost update* — quando uma gravação sobrescreve silenciosamente outra.

### Append-only e seus limites

`Comentario` e `HistoricoStatus` oferecem construtor completo e apenas getters. Essa superfície orienta a aplicação a acrescentar registros em vez de editá-los. A proteção será reforçada nas camadas seguintes: ausência de setters não substitui autorização de serviço nem constraints do banco.

### Campo de senha

`Usuario.senha` é persistência interna e não participa de `toString`. O hash BCrypt será produzido na Fase 2; este ticket apenas reserva a coluna. Quando a API chegar, DTOs impedirão que a entidade e a senha sejam serializadas diretamente em respostas HTTP.

## Verification Evidence

- Preflight Git: **PASS** — branch `ticket/03-entidades-jpa`, `origin/main` como ancestral e Ticket 02 comprovado no remoto.
- Java: **PASS** — Eclipse Temurin 17.0.20.1 e `javac` 17.0.20.1.
- `.\\mvnw.cmd -q -DskipTests compile`: **PASS** antes e depois do commit.
- Inspeção determinística: **PASS** — 5 entidades, 5 campos enum `STRING`, 7 relações `LAZY`, 5 atribuições UTC e 1 campo `@Version`.
- Auditoria de forma: **PASS** — zero `CascadeType.REMOVE`, `orphanRemoval`, `equals`, `hashCode`, `toString` ou setters nas entidades append-only.
- Revisão de escopo: **PASS** — o commit `f8293ad` contém somente os cinco arquivos declarados.

## Decisions Made

- Defaults opcionais do JPA foram explicitados para que o contrato seja compreensível e copiável nas migrations.
- `Usuario.email` e `Categoria.nome` usam `unique = true`, espelhando as regras de unicidade que o MySQL aplicará no Ticket 05.
- Timestamps declaram `DATETIME(6)` para alinhar a precisão física esperada sem permitir que Hibernate crie tabelas.
- O solicitante não recebeu setter porque a autoria do chamado não deve mudar; atendente, categoria, conteúdo editável e estado mantêm a superfície necessária às fases futuras.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- A primeira compilação não iniciou porque `JAVA_HOME` não estava definido na sessão. O JDK Temurin 17 existente foi localizado e usado somente no processo Maven, sem alterar permanentemente a máquina.
- O Git avisou sobre normalização de `LF` para `CRLF` na cópia de trabalho do Windows; não houve alteração semântica nem falha de formatação.
- O Git mantém nos commits locais a mesma identidade corporativa usada nos Tickets 01 e 02; nenhuma mudança de autoria foi introduzida neste ticket.
- O atualizador automático do GSD gerou uma frase inválida e calculou `0%` apesar de três planos concluídos; o tracking foi corrigido para `3/5` e `60%` antes do commit documental.

## User Setup Required

None - a compilação não exige banco de dados nem serviço externo. MySQL/Docker será necessário para a validação integrada do Ticket 05.

## Next Phase Readiness

- O contrato JPA está pronto para os cinco repositories do Ticket 04.
- O schema físico ainda não existe por ordem deliberada; iniciar a aplicação não é gate até o Ticket 05.
- O avanço permanece bloqueado pela evidência local, autorização de push/PR, autorização separada de merge e prova em `origin/main`.
- Nenhum repository, migration, service, endpoint ou código de fase posterior foi criado.

## Self-Check: PASSED

- As cinco entidades existem, compilam e pertencem ao commit `f8293ad`.
- O commit está alcançável a partir de `HEAD` na branch `ticket/03-entidades-jpa`.
- Os contratos de enum, relação, nulabilidade, timestamp, versão, append-only e escopo foram verificados novamente após o commit.

---
*Phase: 01-base-do-projeto*
*Completed: 2026-09-03*
