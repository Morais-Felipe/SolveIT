# Fase 1: Base do Projeto — Pesquisa

**Pesquisado em:** 2026-09-02
**Domínio:** fundação Spring Boot/JPA sobre MySQL com Flyway e Testcontainers
**Confiança:** ALTA para escopo e modelo do projeto; MÉDIA para documentação de framework obtida em fontes oficiais via WebSearch

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

DATA_A7K2M9QX_START

## Implementation Decisions

### Bootstrap e dependências
- **D-01:** Usar Maven e Java 17 com a linha estável atual do Spring Boot 3, deixando o BOM do Boot controlar versões do ecossistema.
- **D-02:** Incluir somente dependências necessárias à fundação e aos testes desta fase; segurança, JWT e e-mail entram quando suas fases começarem.
- **D-03:** Organizar o back-end em `solveit-api/` com package base `com.solveit` e os diretórios prescritos no documento de origem.

### Esquema e migrações
- **D-04:** Flyway é a única autoridade de criação/evolução do esquema; Hibernate usa `ddl-auto=validate` — **Reversibility:** costly — mudar depois exigiria reconciliar histórico de migrações e ambientes existentes.
- **D-05:** Criar migrações numeradas e imutáveis para as cinco tabelas, com chaves estrangeiras, unicidade, nulabilidade e índices alinhados às consultas previstas.
- **D-06:** Persistir enums como texto, nunca ordinal — **Reversibility:** costly — alterar representação após dados existentes exige migração de dados.
- **D-07:** Adotar controle de versão otimista no agregado Chamado desde a criação do modelo — **Reversibility:** costly — introduzi-lo depois mudaria esquema e contratos de atualização concorrente.

### Modelo JPA
- **D-08:** Entidades refletem o domínio, mas não serão contratos REST; DTOs serão introduzidos com os endpoints.
- **D-09:** Datas de criação e atualização são controladas consistentemente pela aplicação/JPA, com o banco preservando restrições essenciais.
- **D-10:** Comentario e HistoricoStatus são modelados como registros append-only; nenhuma operação de atualização ou exclusão será planejada para eles.

### Ambiente e validação
- **D-11:** `application.yml` ativa o perfil de desenvolvimento, enquanto credenciais e segredos ficam externalizáveis por variáveis de ambiente; nenhum segredo real será versionado.
- **D-12:** Validar a fundação com um teste de integração sobre MySQL via Testcontainers, provando migrações limpas e compatibilidade JPA; não usar H2 como substituto de MySQL.
- **D-13:** Fornecer configuração local simples e explicações pedagógicas sobre PK, FK, índices, nulabilidade e migrações.

DATA_A7K2M9QX_END

[VERIFIED: .planning/phases/01-base-do-projeto/01-CONTEXT.md:13-35]

### the agent's Discretion

DATA_P4R8V1ZT_START

- Nomes exatos de artefatos Maven auxiliares, plugins de qualidade e detalhes internos de builders/construtores, desde que mantenham Java 17, simplicidade pedagógica e compatibilidade com JPA.

DATA_P4R8V1ZT_END

[VERIFIED: .planning/phases/01-base-do-projeto/01-CONTEXT.md:37-38]

### Deferred Ideas (OUT OF SCOPE)

DATA_N6H3C8WY_START

- DTOs HTTP, tratamento global de erros e endpoints serão aprofundados nas fases 2 e 3.
- Autenticação, e-mail, dashboard e frontend permanecem nas fases designadas pelo roadmap.

DATA_N6H3C8WY_END

[VERIFIED: .planning/phases/01-base-do-projeto/01-CONTEXT.md:85-89]
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DATA-01 | O desenvolvedor pode iniciar a API Java 17/Spring Boot 3 com configuração de desenvolvimento para MySQL 8. | Stack Maven mínima, perfis e preflight de ambiente. |
| DATA-02 | O sistema representa usuários, categorias, chamados, comentários e históricos com os campos, relações, nulabilidade e enums definidos. | Blueprint entidade ↔ tabela, enums `STRING`, relacionamentos e timestamps. |
| DATA-03 | O banco pode ser criado do zero por migrações Flyway versionadas, com chaves estrangeiras, unicidade e índices coerentes com as consultas. | Cinco migrações imutáveis, constraints e matriz de índices. |
| DATA-04 | A aplicação valida que o esquema gerenciado pelo Flyway corresponde ao mapeamento JPA sem gerar tabelas automaticamente. | `ddl-auto=validate` e teste MySQL/Testcontainers de startup + persistência/leitura. |

As descrições acima são verbatim de `.planning/REQUIREMENTS.md`. [VERIFIED: .planning/REQUIREMENTS.md:8-13]
</phase_requirements>

## Summary

A Fase 1 deve ser planejada como um walking skeleton exclusivamente de persistência: `build -> aplicação inicia -> Flyway migra um MySQL vazio -> Hibernate valida -> teste persiste e lê um grafo mínimo`. Não há UI, autenticação, endpoints de negócio, e-mail ou dashboard. O repositório é greenfield e ainda não contém código de aplicação. [VERIFIED: .planning/phases/01-base-do-projeto/01-CONTEXT.md:7-10,61-73]

O caminho prescritivo é Spring Boot **3.5.16** sobre Java **17**, usando o parent/BOM do Boot sem sobrescrever versões gerenciadas; `spring-boot-starter-web`, Data JPA, Connector/J, `flyway-core` + `flyway-mysql`, testes do Boot e Testcontainers MySQL. A documentação oficial confirma Java 17 como mínimo do Boot 3.5.16, Maven 3.6.3+ e as versões gerenciadas Connector/J 9.7.0, Flyway 11.7.2 e Testcontainers 1.21.4. [CITED: https://docs.spring.io/spring-boot/3.5/system-requirements.html] [CITED: https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html]

**Recomendação principal:** transforme os cinco tickets de origem em cinco planos sequenciais e bloqueados por aprovação, fazendo o Ticket 5 provar todo o contrato de dados em MySQL real antes de encerrar a fase.

## Project Constraints (from AGENTS.md)

- Java 17 + Spring Boot 3; MySQL 8 + Flyway; arquitetura nos packages `config`, `controller`, `dto`, `entity`, `enums`, `exception`, `repository`, `security`, `service`; uma tarefa por vez; autorização futura no servidor; comentários imutáveis e histórico obrigatório. Esses valores aparecem verbatim em `AGENTS.md`. [VERIFIED: AGENTS.md:11-21]
- Para esta fase, criar somente os packages/classes que tenham conteúdo: raiz `com.solveit`, `enums`, `entity` e `repository`; não criar classes placeholder em `controller`, `security`, `service`, `dto` ou `exception`. Isso preserva a estrutura prescrita sem antecipar fases. [VERIFIED: SolveIT_GSD_SystemPrompt.md:127-146]
- Cada ticket usa branch `ticket/NN-descricao-curta`, plano aprovado antes de código, commits atômicos, testes e resumo antes de push; merge em `main` só com aprovação explícita. [VERIFIED: docs/GIT-WORKFLOW.md:5-35]
- O executor deve explicar PK, FK, índices, nulabilidade, migrações e o motivo de cada decisão em linguagem acessível. [VERIFIED: SolveIT_GSD_SystemPrompt.md:281-290]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| Bootstrap Maven/Spring Boot | API / Backend | — | O executável e sua configuração pertencem ao módulo `solveit-api`. |
| Configuração de conexão | API / Backend | Database / Storage | O perfil fornece connection settings; credenciais continuam externas. |
| Enums e entidades JPA | API / Backend | Database / Storage | JPA representa o modelo, enquanto SQL preserva o contrato físico. |
| Repositories | API / Backend | Database / Storage | Interfaces Spring Data acessam o modelo relacional sem lógica de negócio. |
| Migrações/constraints/índices | Database / Storage | API / Backend | Flyway é a única autoridade DDL; Hibernate apenas valida. |
| Validação integrada | API / Backend | Database / Storage | O contexto Spring precisa iniciar contra MySQL real e provar persistência. |

## Canonical Ticket Order

DATA_Q9D2L5FS_START

1. Setup Spring Boot + `pom.xml` com dependências + `application.yml` com conexão MySQL
2. Criação dos Enums (`StatusChamado`, `Prioridade`, `PerfilUsuario`)
3. Criação das Entidades JPA (`Usuario`, `Categoria`, `Chamado`, `Comentario`, `HistoricoStatus`)
4. Criação dos Repositórios JPA (interfaces com queries customizadas onde necessário)
5. Scripts SQL de criação das tabelas (V1__ a V5__ para Flyway)

DATA_Q9D2L5FS_END

[VERIFIED: SolveIT_GSD_SystemPrompt.md:213-220]

O planner deve produzir exatamente essa ordem e não fundir tickets. Cada PLAN termina quando o executor implementa, valida, cria e commita seu SUMMARY. Depois do retorno, o gate externo do orquestrador apresenta o resumo e conduz aprovação para push/PR, aprovação humana separada para merge, prova dos commits e do SUMMARY em `origin/main` e somente então libera o próximo PLAN. O Ticket 5 também contém a validação de integração, pois é quando o esquema completo finalmente existe.

## Standard Stack

### Core

| Library / tool | Version | Scope | Why Standard |
|----------------|---------|-------|--------------|
| Java | 17 | build/runtime | Escolha travada e mínimo do Boot 3.5.16. [CITED: https://docs.spring.io/spring-boot/3.5/system-requirements.html] |
| Spring Boot parent | 3.5.16 | dependency platform/runtime | Linha Boot 3 estável compatível com Java 17; não usar Boot 4. [CITED: https://docs.spring.io/spring-boot/3.5/system-requirements.html] |
| Maven Wrapper | Wrapper gerado; Maven 3.6.3+ | build | O wrapper evita depender de Maven global e funciona no Windows via `mvnw.cmd`. [CITED: https://docs.spring.io/spring-boot/3.5/system-requirements.html] |
| MySQL Server | 8.4.11 LTS (`mysql:8.4.11`) | database | Satisfaz “MySQL 8” em uma linha LTS e fixa a mesma imagem oficial multi-arquitetura adotada pelos planos para Testcontainers. [CITED: https://dev.mysql.com/doc/refman/8.4/en/] |
| Spring Data JPA / Hibernate | BOM-managed | ORM/repositories | Mapeamento e validação do esquema. [CITED: https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html] |
| Flyway | 11.7.2, BOM-managed | migrations | `flyway-core` e `flyway-mysql` devem estar juntos para MySQL. [CITED: https://docs.spring.io/spring-boot/how-to/data-initialization.html] |
| MySQL Connector/J | 9.7.0, BOM-managed | JDBC runtime | Coordenada oficial `com.mysql:mysql-connector-j`. [CITED: https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html] |
| Testcontainers | 1.21.4, BOM-managed | integration tests | MySQL real administrado pelo JUnit; sem H2. [CITED: https://docs.spring.io/spring-boot/reference/testing/testcontainers.html] |

### Maven dependency shape

```xml
<!-- versões omitidas: controladas pelo parent/BOM do Spring Boot 3.5.16 -->
org.springframework.boot:spring-boot-starter-web
org.springframework.boot:spring-boot-starter-data-jpa
org.flywaydb:flyway-core
org.flywaydb:flyway-mysql
com.mysql:mysql-connector-j              <!-- runtime -->

org.springframework.boot:spring-boot-starter-test  <!-- test -->
org.springframework.boot:spring-boot-testcontainers <!-- test -->
org.testcontainers:junit-jupiter          <!-- test -->
org.testcontainers:mysql                  <!-- test -->
```

[CITED: https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html]

Não adicionar Security, JWT, Mail, Actuator, Lombok, MapStruct ou H2 nesta fase. Segurança/JWT/e-mail são explicitamente adiados; Lombok/MapStruct não têm necessidade demonstrada; H2 violaria D-12. [VERIFIED: .planning/phases/01-base-do-projeto/01-CONTEXT.md:16-19,32-35]

### Version verification

| Coordinate | Managed version | Checked | Publication date |
|------------|-----------------|---------|------------------|
| `org.springframework.boot:spring-boot-dependencies` | 3.5.16 | 2026-09-02, official Boot docs | Não informada na página de coordenadas |
| `org.flywaydb:flyway-core`, `flyway-mysql` | 11.7.2 | 2026-09-02, official Boot BOM | Não informada na página de coordenadas |
| `com.mysql:mysql-connector-j` | 9.7.0 | 2026-09-02, official Boot BOM | Não informada na página de coordenadas |
| `org.testcontainers:junit-jupiter`, `mysql` | 1.21.4 | 2026-09-02, official Boot BOM | Não informada na página de coordenadas |

As datas de publicação não devem ser inventadas: a consulta oficial confirmou versões, mas a página não expôs datas. O Ticket 1 deve registrar o resultado de `./mvnw dependency:tree` após o primeiro resolve.

## Package Legitimacy Audit

O gate `gsd-tools package-legitimacy` foi executado, mas suporta somente `npm|pypi|crates`; ele recusou `maven`. Portanto, não é possível atribuir veredito `OK/SUS/SLOP` do seam a coordenadas Maven. As coordenadas abaixo foram confirmadas no BOM/documentação oficial do Spring Boot, o que permite recomendá-las como `[CITED]`, não como `[VERIFIED: npm registry]`.

| Package | Registry | Evidence | Gate verdict | Disposition |
|---------|----------|----------|--------------|-------------|
| `spring-boot-starter-web` | Maven Central | Spring Boot managed coordinates | N/A — Maven unsupported | Approved from official BOM |
| `spring-boot-starter-data-jpa` | Maven Central | Spring Boot managed coordinates | N/A — Maven unsupported | Approved from official BOM |
| `mysql-connector-j` | Maven Central | Spring Boot managed coordinates | N/A — Maven unsupported | Approved from official BOM |
| `flyway-core`, `flyway-mysql` | Maven Central | Spring Boot docs/BOM | N/A — Maven unsupported | Approved from official BOM |
| `spring-boot-testcontainers`, `junit-jupiter`, `mysql` | Maven Central | Spring Boot Testcontainers docs/BOM | N/A — Maven unsupported | Approved from official BOM |

**Packages removed due to SLOP:** none; no SLOP verdict can be produced for Maven by this gate.
**Packages flagged SUS:** none; no SUS verdict can be produced for Maven by this gate.

## Architecture Patterns

### System Architecture Diagram

```text
Developer / test command
        |
        v
Maven Wrapper -> Spring Boot application context
                         |
                         v
                 DataSource / Connector-J
                         |
                         v
             MySQL 8.4 empty database
                         |
                         v
              Flyway V1..V5 migrations
                         |
                         v
             Hibernate ddl-auto=validate
                         |
                         +---- validation failure -> build fails
                         |
                         v
             Spring Data repositories
                         |
                         v
                 persist + read proof
```

### Recommended Project Structure

```text
solveit-api/
├── pom.xml
├── mvnw
├── mvnw.cmd
├── .mvn/wrapper/
└── src/
    ├── main/
    │   ├── java/com/solveit/
    │   │   ├── SolveItApplication.java
    │   │   ├── enums/
    │   │   ├── entity/
    │   │   └── repository/
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       └── db/migration/
    │           ├── V1__create_usuario.sql
    │           ├── V2__create_categoria.sql
    │           ├── V3__create_chamado.sql
    │           ├── V4__create_comentario.sql
    │           └── V5__create_historico_status.sql
    └── test/java/com/solveit/
        └── PersistenceIntegrationTest.java
```

O diretório de migrações e a faixa `V1__`..`V5__` são prescritos pela fonte. [VERIFIED: SolveIT_GSD_SystemPrompt.md:141-145,213-220]

### Pattern 1: Dual contract, single schema owner

**What:** Flyway cria/altera; anotações JPA espelham nomes, nulabilidade, comprimentos, relacionamentos e enums; Hibernate apenas valida com `ddl-auto=validate`.

**When:** em todos os perfis reais e no teste de integração. Nunca usar `update`, `create` ou `create-drop` para “ajudar” a migração. A documentação do Boot informa que `flyway-mysql` acompanha Flyway para MySQL e que o Boot chama `Flyway.migrate()`. [CITED: https://docs.spring.io/spring-boot/how-to/data-initialization.html]

### Pattern 2: Explicit textual enums

DATA_B3J7U2KR_START

perfil        Enum: SOLICITANTE | ATENDENTE | ADMIN
status        Enum: ABERTO | EM_ANDAMENTO | RESOLVIDO | FECHADO | CANCELADO
prioridade    Enum: BAIXA | MEDIA | ALTA | CRITICA

DATA_B3J7U2KR_END

[VERIFIED: SolveIT_GSD_SystemPrompt.md:37-46,56-69]

Mapear todos com `@Enumerated(EnumType.STRING)` e colunas `VARCHAR`, nunca ordinal. `EnumType.STRING` persiste o valor textual do enum. [CITED: https://jakarta.ee/specifications/persistence/3.1/apidocs/jakarta.persistence/jakarta/persistence/enumtype]

Adicionar `CHECK` para esses conjuntos no MySQL torna DATA-02 observável também no banco, sem usar o tipo proprietário `ENUM`; o SQL continua legível e mudanças futuras entram por migração. [CITED: https://dev.mysql.com/doc/refman/8.4/en/create-table.html]

### Pattern 3: Child-to-parent relations only in Phase 1

Usar relações unidirecionais `@ManyToOne(fetch = LAZY)` nos filhos, `optional=false` para FKs obrigatórias e `optional=true` somente em `Chamado.atendente`. Não criar coleções bidirecionais nem cascades de remoção nesta fase. A especificação define `optional=false` como relação não nula e documenta LAZY para to-one como hint; o default de `ManyToOne` é EAGER, portanto a escolha deve ser explícita. [CITED: https://jakarta.ee/specifications/persistence/3.0/apidocs/jakarta.persistence/jakarta/persistence/manytoone]

### Pattern 4: Application-owned timestamps and optimistic version

Usar callbacks JPA `@PrePersist` e `@PreUpdate` para preencher `LocalDateTime` nos campos definidos pela fonte, sem defaults temporais concorrentes no banco. O banco mantém `NOT NULL` nos timestamps obrigatórios. Jakarta Persistence define esses callbacks como eventos de ciclo de vida síncronos. [CITED: https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1]

Em `Chamado`, mapear um campo numérico `version` com `@Version` e coluna `version BIGINT NOT NULL DEFAULT 0`. O campo não aparece na especificação original, mas é decisão travada D-07. A especificação JPA usa a versão para detectar atualização obsoleta e evitar lost updates. [VERIFIED: .planning/phases/01-base-do-projeto/01-CONTEXT.md:21-25] [CITED: https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1]

## Entity and Schema Blueprint

Os campos de domínio são estes, verbatim:

DATA_E8M4T6VP_START

Usuario: id, nome, email, senha, perfil, ativo, criadoEm
Categoria: id, nome, descricao, ativo
Chamado: id, titulo, descricao, status, prioridade, solicitante_id, atendente_id, categoria_id, criadoEm, atualizadoEm, resolvidoEm
Comentario: id, chamado_id, autor_id, texto, criadoEm
HistoricoStatus: id, chamado_id, statusAnterior, statusNovo, alteradoPor_id, alteradoEm, observacao

DATA_E8M4T6VP_END

[VERIFIED: SolveIT_GSD_SystemPrompt.md:35-89]

| Entity/table | Required constraints | Planned indexes |
|--------------|----------------------|-----------------|
| `usuario` | PK auto increment; `nome`, `email`, `senha`, `perfil`, `ativo`, `criado_em` NOT NULL; UNIQUE `email`; CHECK perfil | unique `email` |
| `categoria` | PK; `nome`, `ativo` NOT NULL; UNIQUE `nome`; `descricao` nullable | unique `nome` |
| `chamado` | PK; title/text/status/priority/requester/category/create/update/version NOT NULL; attendant/resolution nullable; three FKs; CHECK status/priority | `(solicitante_id, criado_em)`, `(atendente_id, status)`, `(status, prioridade)`, `categoria_id` |
| `comentario` | PK; ticket/author/text/create NOT NULL; two FKs; no update/delete API later | `(chamado_id, criado_em)` |
| `historico_status` | PK; ticket/old/new/actor/time NOT NULL; observation nullable; three FKs; CHECK old/new status | `(chamado_id, alterado_em)` |

Os filtros futuros são verbatim `status`, `prioridade`, `categoria`, `atendente`, `solicitante`. [VERIFIED: SolveIT_GSD_SystemPrompt.md:177-185] Os índices acima cobrem os principais caminhos previstos sem indexar toda coluna; MySQL exige índices para FKs e alerta que índices desnecessários aumentam custo de escrita. [CITED: https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html] [CITED: https://dev.mysql.com/doc/refman/8.4/en/optimization-indexes.html]

Usar `InnoDB`, `utf8mb4` e a collation explícita `utf8mb4_0900_ai_ci`. Os planos adotaram conscientemente sua semântica case/accent-insensitive para unicidade de email/nome; a normalização funcional de login continua na Fase 2. [CITED: https://dev.mysql.com/doc/refman/8.4/en/charset-unicode-sets.html]

## Repository Boundary

Criar exatamente cinco interfaces `JpaRepository<Entidade, Long>`. Nesta fase, adicionar apenas métodos que provem constraints ou sejam inevitáveis para as próximas fases: `UsuarioRepository.findByEmail/existsByEmail` e `CategoriaRepository.existsByNome`; adiar Specifications, paginação e consultas de Chamado até o Ticket/Fase que as usa. [VERIFIED: SolveIT_GSD_SystemPrompt.md:213-220]

Não usar repository genérico próprio, classe base CRUD, métodos `delete` customizados para audit records ou consultas nativas prematuras.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Dependency alignment | versões individuais de Hibernate/Flyway/Testcontainers | Spring Boot parent/BOM | Evita combinações incompatíveis. |
| Schema migration | executor SQL próprio | Flyway core + MySQL module | Histórico, ordenação, checksum e startup integration já existem. |
| ORM/repositories | JDBC mapper/base repository próprio | Spring Data JPA | A fase precisa aprender e validar JPA, não duplicá-lo. |
| Optimistic locking | comparação manual de timestamp | JPA `@Version` | O provider inclui a versão no update e detecta conflito. |
| MySQL test lifecycle | scripts que sobem/derrubam banco em portas fixas | Testcontainers + `@ServiceConnection` | Isolamento, porta dinâmica e connection details automáticos. |
| Timestamp framework | biblioteca externa | callbacks JPA | O caso é pequeno e já coberto pela API padrão. |

## Common Pitfalls

### 1. Migration/JPA drift hidden by Hibernate DDL

**What goes wrong:** a máquina local funciona, mas um banco limpo falha. **Avoid:** `ddl-auto=validate`, migrações imutáveis e teste desde schema vazio. **Warning sign:** trocar `validate` por `none/update` para o contexto subir. [VERIFIED: .planning/research/PITFALLS.md:148-167]

### 2. Ticket order causes a temporarily unstartable app

Entidades chegam no Ticket 3, mas as tabelas só no Ticket 5. Com `ddl-auto=validate` já ativo, o app não deve ser usado como gate de startup entre os Tickets 3 e 4; nesses tickets, o gate é compilação/testes unitários. O startup MySQL completo vira gate no Ticket 5. Planejar isso evita “resolver” o intervalo com `ddl-auto=update`. [ASSUMED]

### 3. Enum drift

Sem `EnumType.STRING`, reordenar constantes altera dados; sem checks ou comprimentos coerentes, SQL/JPA divergem. Validar os três enums em round-trip e inspecionar os valores textuais persistidos. [CITED: https://jakarta.ee/specifications/persistence/3.1/apidocs/jakarta.persistence/jakarta/persistence/enumtype]

### 4. Cascade/delete destroys auditability

Não usar `CascadeType.REMOVE`, `orphanRemoval=true` ou `ON DELETE CASCADE` de usuário/chamado para comentários/histórico. Preferir `RESTRICT/NO ACTION`; desativação futura preserva referências. [VERIFIED: AGENTS.md:19-21]

### 5. Testcontainers marked optional

O teste principal não deve ser silently skipped quando Docker faltar. Falta de runtime precisa aparecer como preflight/blocked test, pois DATA-03/04 não podem ser validados com H2. [VERIFIED: .planning/phases/01-base-do-projeto/01-CONTEXT.md:32-35]

## Code Examples

### Mapping pattern

```java
@Enumerated(EnumType.STRING)
@Column(nullable = false)
private StatusChamado status;

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "solicitante_id", nullable = false)
private Usuario solicitante;

@Version
@Column(nullable = false)
private Long version;
```

The exact domain values used by `StatusChamado` are quoted in Pattern 2. The JPA annotations follow Jakarta Persistence official enum, relationship and optimistic locking contracts. [CITED: https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1]

### Testcontainers startup pattern

```java
@Testcontainers
@SpringBootTest
class PersistenceIntegrationTest {
    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.11");

    @Test
    void contextMigratesValidatesAndPersistsDomainGraph() {
        // persist entities through repositories, flush, clear, reload, assert
    }
}
```

`@ServiceConnection` requires `spring-boot-testcontainers` and supplies connection details that override normal datasource properties. [CITED: https://docs.spring.io/spring-boot/reference/testing/testcontainers.html]

## Integration Validation Strategy

Mesmo com `workflow.nyquist_validation` definido verbatim como `"nyquist_validation": false`, D-12 e DATA-04 exigem teste integrado; apenas a seção Nyquist formal foi omitida. [VERIFIED: .planning/config.json:7-14]

| Proof | Test action | Pass condition |
|-------|-------------|----------------|
| Clean migrations | Start `@SpringBootTest` with a new `MySQLContainer` | all V1..V5 applied; context starts |
| Flyway/JPA parity | same startup with `ddl-auto=validate` | Hibernate reports no schema validation error |
| Repository graph | persist users/category/ticket/comment/history; flush/clear/read | fields, FKs and lazy relations reload correctly |
| Enum text | inspect persisted columns via JDBC | exact textual constants, never ordinals |
| Uniqueness | insert duplicate email and category name | DB rejects on flush |
| Nullability/FKs | attempt missing mandatory relationship | DB/JPA rejects on flush |
| Optimistic version | update/read `Chamado` | version is present and increments on update |
| Append-only shape | review repository/service surface | no custom update/delete flow for comments/history |

**Windows commands after prerequisites exist:**

```powershell
.\mvnw.cmd -q test
.\mvnw.cmd -q spring-boot:run
docker version
```

Ticket 1 may use a local MySQL instance/profile to demonstrate startup, but the phase gate is the Testcontainers proof against a fresh MySQL 8.4 container. No fixed host port is needed in the integration test.

## Environment Availability

| Dependency | Required by | Available now | Version | Fallback |
|------------|-------------|---------------|---------|----------|
| Java | all tickets | ✗ | — | Install JDK 17 before Ticket 1; blocking |
| Maven global | build | ✗ | — | Maven Wrapper; not blocking once JDK exists |
| Docker CLI/daemon | Ticket 5 integration | ✗ | — | Docker Desktop/compatible Docker API runtime; blocking for phase gate |
| MySQL local | manual dev startup | not probed | — | Testcontainers for automated gate |

Testcontainers requires a Docker-API compatible runtime and is actively tested with Docker Desktop on Windows; WSL2 backend works with Linux containers, while Windows containers are unsupported. [CITED: https://java.testcontainers.org/supported_docker_environment/] [CITED: https://java.testcontainers.org/supported_docker_environment/windows/]

**Planner checkpoint:** Ticket 1 starts with `java -version`; Ticket 5 starts with `docker version` and an explicit check that Docker is in Linux-container mode. Docker Desktop/WSL2 installation is an environment prerequisite, not application code. [CITED: https://docs.docker.com/desktop/setup/install/windows-install/]

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | no | Deferred to Phase 2 |
| V3 Session Management | no | Deferred to Phase 2 |
| V4 Access Control | no runtime endpoint yet | Preserve FK ownership model; enforce in later services |
| V5 Validation/Sanitization | yes, persistence boundary | JPA nullability plus DB NOT NULL/UNIQUE/FK/CHECK |
| V6 Cryptography | no | No cryptographic feature in Phase 1 |
| V14 Configuration | yes | secrets via environment; safe defaults only; no `.env`/credential commit |

### Known Threat Patterns

| Pattern | STRIDE | Mitigation in Phase 1 |
|---------|--------|-----------------------|
| Real DB credentials committed in YAML | Information Disclosure | env placeholders; ignored local secrets; Git diff check |
| Orphan/inconsistent references | Tampering | FK + NOT NULL + restrictive delete semantics |
| Invalid enum/state text inserted directly | Tampering | textual columns plus CHECK constraints |
| Dependency/version confusion | Tampering / Supply chain | official Boot parent/BOM, no manual overrides |
| Audit rows deleted by cascade | Repudiation | no remove cascade; restrict referenced deletions |

## State of the Art

| Old/unsafe approach | Current recommendation | Impact |
|---------------------|------------------------|--------|
| `ddl-auto=update` as schema manager | Flyway owns DDL; Hibernate validates | reproducible clean schema |
| `flyway-core` alone for MySQL | add `flyway-mysql` | correct modern database support |
| H2 compatibility test | MySQL Testcontainers + `@ServiceConnection` | verifies actual dialect/constraints |
| enum ordinal | `EnumType.STRING` + VARCHAR/CHECK | stable readable data |
| timestamp as concurrency token | numeric JPA `@Version` | explicit lost-update detection |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Entre os Tickets 3 e 4, compilação é o gate; startup com schema completo só no Ticket 5. | Pitfalls | Planner pode precisar de uma migração temporária, o que violaria a ordem da fonte. |
| A2 | **RESOLVED:** tabelas usam singular `snake_case`; lengths aprovados são nome 120, email/senha 255, título 200, enums 20/10 e observação 500; descrições/textos usam `TEXT`. | Schema | Ticket 03 e V1..V5 devem copiar o mesmo contrato literalmente para impedir drift. |
| A3 | **RESOLVED:** `utf8mb4_0900_ai_ci` é a collation adotada para todas as tabelas e para unicidade case/accent-insensitive de email/nome. | Schema | Normalização funcional de login permanece responsabilidade explícita da Fase 2. |
| A4 | **RESOLVED:** usar a Docker Official Image multi-arquitetura `mysql:8.4.11`, sem tag flutuante nem digest preso a uma arquitetura. | Stack/Test | Alterar a imagem exige atualização coerente de planos, skeleton e teste. |

## Open Questions (RESOLVED)

1. **Comprimentos e nomes físicos — RESOLVED:** tabelas singulares `snake_case`; `nome VARCHAR(120)`, `email/senha VARCHAR(255)`, `titulo VARCHAR(200)`, perfil/status `VARCHAR(20)`, prioridade `VARCHAR(10)`, observação `VARCHAR(500)` e descrições/textos `TEXT`. O Ticket 03 define o mapping e o Ticket 05 o copia literalmente em V1..V5.
2. **Collation/normalização de email — RESOLVED:** todas as tabelas usam `utf8mb4` + `utf8mb4_0900_ai_ci`; unicidade de email/nome é case/accent-insensitive. Normalização funcional do login continua na Fase 2.
3. **Imagem MySQL — RESOLVED:** usar a imagem oficial multi-arquitetura `mysql:8.4.11` no Testcontainers. A tag é específica de patch; nenhum digest específico de amd64/arm64 é imposto.

As três escolhas foram incorporadas de forma coerente em `01-03-PLAN.md`, `01-05-PLAN.md` e `01-SKELETON.md`; nenhuma amplia o escopo nem muda a ordem dos cinco tickets.

## Sources

### Primary (HIGH confidence)

- `.planning/phases/01-base-do-projeto/01-CONTEXT.md` — decisões travadas e limites.
- `.planning/REQUIREMENTS.md` — DATA-01..04.
- `SolveIT_GSD_SystemPrompt.md` — entidades, enums, paths e ordem de tickets.
- `AGENTS.md` e `docs/GIT-WORKFLOW.md` — processo obrigatório.

### Official documentation (MEDIUM confidence via verified WebSearch)

- [Spring Boot 3.5.16 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [Spring Boot 3.5 managed dependency coordinates](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html)
- [Spring Boot database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [Spring Boot Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
- [Jakarta Persistence 3.1 specification](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1)
- [Jakarta EnumType](https://jakarta.ee/specifications/persistence/3.1/apidocs/jakarta.persistence/jakarta/persistence/enumtype)
- [MySQL 8.4 foreign keys](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html)
- [MySQL 8.4 indexes](https://dev.mysql.com/doc/refman/8.4/en/optimization-indexes.html)
- [Testcontainers runtime requirements](https://java.testcontainers.org/supported_docker_environment/)
- [Testcontainers Windows support](https://java.testcontainers.org/supported_docker_environment/windows/)
- [Docker Desktop on Windows](https://docs.docker.com/desktop/setup/install/windows-install/)

## Metadata

**Confidence breakdown:**
- Standard stack: **MEDIUM-HIGH** — exact managed coordinates confirmed in official Boot 3.5.16 docs; Maven legitimacy seam unavailable.
- Architecture: **HIGH** — phase boundary, entities and ticket order come from repository sources of truth.
- Schema details: **HIGH** — fields/relations, nomes físicos `snake_case`, lengths explícitos e collation `utf8mb4_0900_ai_ci` foram fechados pelos planos; a imagem de integração adotada é a oficial `mysql:8.4.11`, sem decisão de schema ou imagem pendente.
- Pitfalls and validation: **HIGH** — directly derived from DATA-03/04 and D-04/D-12, with official framework support.

**Research date:** 2026-09-02
**Valid until:** 2026-10-02 for Boot 3.5 patch/dependency coordinates; project decisions remain valid until changed in CONTEXT/PROJECT.
