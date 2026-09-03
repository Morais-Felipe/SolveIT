# Walking Skeleton — SolveIT

**Phase:** 1 — Base do Projeto  
**Generated:** 2026-09-02

## Capability Proven End-to-End

> Um desenvolvedor executa o Maven Wrapper; a aplicação inicia contra um MySQL 8.4 vazio; Flyway aplica V1..V5 sob lock; Hibernate valida o modelo sem criar DDL; e o teste persiste, limpa o contexto e relê um grafo mínimo do domínio.

O “usuário” desta história é o desenvolvedor do SolveIT. O fluxo observável da Fase 1 é exclusivamente de persistência: `Maven build → Spring context → MySQL limpo → Flyway migrate → Hibernate validate → repository write/read`.

## Architectural Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Framework | Java 17 + Spring Boot 3.5.16, Maven Wrapper 3.3.4/Maven 3.9.16 | Stack travada, build reproduzível no Windows e versões de ecossistema alinhadas pelo BOM. |
| Data layer | Spring Data JPA/Hibernate + MySQL Official Image `mysql:8.4.11` | Mantém o modelo relacional explícito e testa o mesmo dialeto da aplicação. |
| Schema authority | Flyway `V1__`..`V5__`; Hibernate `ddl-auto=validate` | Uma única autoridade DDL elimina schema drift oculto. |
| Physical schema | Tabelas singulares `snake_case`, InnoDB, `utf8mb4`, `utf8mb4_0900_ai_ci`, lengths explícitos | Contrato conservador, Unicode completo e unicidades case/accent-insensitive. |
| Enum representation | `EnumType.STRING` + `VARCHAR` + CHECK | Dados legíveis e independentes da posição ordinal. |
| Concurrency | `@Version` em Chamado + lock do Flyway no startup | Impede lost update silencioso e DDL concorrente duplicado. |
| Audit preservation | Comentario/HistoricoStatus append-only; relações LAZY; nenhuma cascade de remoção; FKs restritivas | Preserva rastreabilidade e evita exclusão acidental em cascata. |
| Auth | JWT/BCrypt explicitamente adiado à Fase 2 | Autenticação não pertence à fundação de persistência. O campo `senha` é apenas modelado internamente nesta fase. |
| Deployment target | Execução local reproduzível via Maven Wrapper + Docker Linux/Testcontainers | O gate da fase é local e automatizado; deployment remoto não foi requerido. |
| Directory layout | `solveit-api/`, package-base `com.solveit`, subpackages `enums`, `entity`, `repository` | Segue a arquitetura pedagógica e cria somente diretórios com conteúdo real. |

## Stack Touched in Phase 1

- [x] Project scaffold — Maven Wrapper, POM, build e test runner.
- [x] Database — MySQL 8.4 real, cinco migrations, uma escrita e uma releitura reais.
- [x] Local execution — `Set-Location 'C:\projetos\solveit-api'; & .\mvnw.cmd -q -Dtest=PersistenceIntegrationTest test; if ($LASTEXITCODE -ne 0) { throw 'PersistenceIntegrationTest falhou' }`.
- [x] Validation — Flyway migrate, Hibernate validate, constraints, idempotência e concorrência.
- [ ] Business routing — exceção canônica: rotas HTTP de autenticação/negócio começam nas Fases 2 e 3 e não podem ser antecipadas.
- [ ] UI — exceção canônica: React, React Router e qualquer interação visual pertencem à Fase 6 e não podem ser antecipados pelo Walking Skeleton genérico.
- [ ] Remote deployment — não requerido para a Fase 1; o ambiente local documentado é o alvo aprovado.

## Walking Skeleton Exception

O template genérico de Phase 1 sugere rota e UI. Essa sugestão entra em conflito com as fontes canônicas do SolveIT, que fixam a ordem dos cinco tickets de backend/banco e reservam frontend para a Fase 6. A decisão de maior autoridade é preservar a ordem `setup → enums → entidades → repositories → migrations/teste`. Portanto, o tracer do projeto nesta fase é a persistência observável pelo desenvolvedor; UI e routing de negócio são proibidos no escopo da Fase 1.

Os cinco PLANs formam uma única coluna vertebral sequencial, ativada de ponta a ponta no Ticket 05. Eles não autorizam execução automática da fase inteira. O contrato do orquestrador em `01-EXECUTION-PROTOCOL.md` despacha um PLAN por vez: a aprovação pré-código ocorre antes do despacho; o executor implementa, valida, cria e commita o SUMMARY e retorna; depois o orquestrador conduz aprovação de push/PR, aprovação humana separada de merge e prova em `origin/main` antes do próximo ticket. Esse fluxo pós-SUMMARY fica deliberadamente fora do XML das tasks.

## Physical Contract

| Table | Required columns | Nullable columns |
|-------|------------------|------------------|
| `usuario` | `id`, `nome VARCHAR(120)`, `email VARCHAR(255)`, `senha VARCHAR(255)`, `perfil VARCHAR(20)`, `ativo`, `criado_em DATETIME(6)` | — |
| `categoria` | `id`, `nome VARCHAR(120)`, `ativo` | `descricao TEXT` |
| `chamado` | `id`, `titulo VARCHAR(200)`, `descricao TEXT`, `status VARCHAR(20)`, `prioridade VARCHAR(10)`, `solicitante_id`, `categoria_id`, `criado_em`, `atualizado_em`, `version` | `atendente_id`, `resolvido_em` |
| `comentario` | `id`, `chamado_id`, `autor_id`, `texto TEXT`, `criado_em` | — |
| `historico_status` | `id`, `chamado_id`, `status_anterior VARCHAR(20)`, `status_novo VARCHAR(20)`, `alterado_por_id`, `alterado_em` | `observacao VARCHAR(500)` |

Todos os IDs são `BIGINT AUTO_INCREMENT`; todos os timestamps são `DATETIME(6)` e representam UTC na aplicação/JDBC. Todas as tabelas usam InnoDB/utf8mb4/utf8mb4_0900_ai_ci. Nenhuma migration usa `IF NOT EXISTS`: idempotência é responsabilidade de `flyway_schema_history` e checksums.

Cada tabela declara `PRIMARY KEY (id)` normalmente. No MySQL, a chave primária aparece em metadata com o nome reservado `PRIMARY`; nomes estáveis próprios são usados somente para UNIQUE, CHECK, FOREIGN KEY e índices secundários.

## Edge-Probe Coverage

O probe spec-less encontrou 14 itens e todos foram preservados como truths executáveis em `01-05-PLAN.md`:

| Requirement | Items | Resolution |
|-------------|-------|------------|
| DATA-01 | concurrency | Startup concorrente contra schema limpo prova o lock Flyway. |
| DATA-02 | adjacency; empty/null; encoding/representation; ordering; idempotency; concurrency | Grafo/FKs, opcionais/obrigatórios, Unicode/enums string, ordem explícita, resave por ID e optimistic locking. |
| DATA-03 | adjacency; empty/clean DB; ordering; idempotency; concurrency | V1→V5 por dependência, container vazio, history ordenado, migrate zero-change e lock concorrente. |
| DATA-04 | idempotency; concurrency | Validação repetida e concorrente sem DDL. |

**Coverage:** 14 expected, 14 covered, 0 silently dismissed, 0 assumption flags.

## Prohibition Recall → Precision

O recall adversarial de DATA-01..DATA-04 levantou apenas higiene rotineira (segredos, integridade de schema, supply chain e auditoria) ou controles canônicos de segurança. Eles foram encaminhados aos `<threat_model>` dos cinco planos e ao futuro `$gsd-secure-phase`, não cunhados como proibições bespoke.

**Coverage:** 0 applicable, 0 unresolved, 0 test-tier, 0 judgment-tier. Cada PLAN registra `must_haves.prohibitions: []` honestamente.

## Multi-Source Coverage Audit

| SOURCE | ID | Feature/Requirement | Plan | Status | Notes |
|--------|----|---------------------|------|--------|-------|
| GOAL | — | Desenvolvedor inicia API sobre modelo relacional completo, versionado e coerente | 01→05 | COVERED | Ticket 05 prova o fluxo completo. |
| REQ | DATA-01 | Java 17/Spring Boot 3 + MySQL 8 dev | 01, 05 | COVERED | Build/configuração e startup real. |
| REQ | DATA-02 | Cinco entidades, relações, nullability e enums | 02, 03, 04, 05 | COVERED | Vocabulário, mapping, repositories e round-trip. |
| REQ | DATA-03 | Banco limpo por Flyway com FKs/unique/indexes | 05 | COVERED | V1..V5 e metadata assertions. |
| REQ | DATA-04 | Paridade Flyway/JPA sem schema generation | 01, 03, 05 | COVERED | `validate` desde o bootstrap e gate integrado. |
| RESEARCH | STACK | Boot 3.5.16, Wrapper, dependências BOM-managed | 01 | COVERED | Sem overrides do ecossistema. |
| RESEARCH | SCHEMA | InnoDB, utf8mb4/collation, nomes/lengths explícitos | 03, 05 | COVERED | Contrato fechado neste documento e nos planos. |
| RESEARCH | RELATIONS | Many-to-one LAZY e delete restritivo | 03, 05 | COVERED | Mapping + FKs. |
| RESEARCH | REPOSITORIES | Cinco JpaRepository; apenas lookups justificados | 04 | COVERED | Sem queries prematuras. |
| RESEARCH | VALIDATION | MySQL Testcontainers, clean migrate, Hibernate validate, CRUD/constraints/locking | 05 | COVERED | Tracer integrado sem H2. |
| RESEARCH | PREFLIGHT | JDK 17 e Docker Linux bloqueantes | 01, 05 | COVERED | Preconditions explícitas. |
| RESEARCH | SECURITY | Segredos externos, supply chain, integridade e auditoria | 01→05 | COVERED | Threat models ASVS L1. |
| CONTEXT | D-01 | Maven, Java 17, Spring Boot 3/BOM | 01 | COVERED | Versões exatas definidas. |
| CONTEXT | D-02 | Dependências somente da fundação | 01 | COVERED | Escopo fechado. |
| CONTEXT | D-03 | `solveit-api/` + `com.solveit` | 01→04 | COVERED | Estrutura real, sem packages vazios. |
| CONTEXT | D-04 | Flyway único DDL; Hibernate validate | 01, 03, 05 | COVERED | Gate automático final. |
| CONTEXT | D-05 | Cinco migrations imutáveis com constraints/índices | 05 | COVERED | V1..V5. |
| CONTEXT | D-06 | Enums textuais | 02, 03, 05 | COVERED | Java/JPA/SQL/JDBC. |
| CONTEXT | D-07 | Optimistic locking em Chamado | 03, 05 | COVERED | Mapping e conflito real. |
| CONTEXT | D-08 | Entidades não são contratos REST | 03 | COVERED | Nenhum DTO/endpoint. |
| CONTEXT | D-09 | Datas controladas por aplicação/JPA | 03, 05 | COVERED | Callbacks UTC + NOT NULL. |
| CONTEXT | D-10 | Comentário/histórico append-only | 03, 04, 05 | COVERED | Sem setters/cascade/custom mutation; FKs restritivas. |
| CONTEXT | D-11 | Perfil dev e segredos externos | 01 | COVERED | Variáveis de ambiente. |
| CONTEXT | D-12 | MySQL Testcontainers, sem H2 | 01, 05 | COVERED | Dependências e teste. |
| CONTEXT | D-13 | Setup simples e explicação pedagógica | 01→05 | COVERED | Cada summary deve explicar os conceitos do ticket. |

**Audit result:** 1 GOAL + 4 REQ + 7 RESEARCH groups + 13 CONTEXT decisions covered; 0 missing. Deferred Ideas e requisitos de outras fases foram excluídos corretamente.

## Out of Scope (Deferred to Later Slices)

- Fase 2: Spring Security, BCrypt operacional, JWT e rotas `/api/auth/*`.
- Fase 3: services, controllers, DTOs, authorization, transições, comentários e histórico como fluxos HTTP.
- Fase 4: JavaMailSender/SMTP e semântica de entrega.
- Fase 5: queries/agregados de dashboard.
- Fase 6: React, Tailwind, React Router, Axios e toda UI.
- Fase 7: jornadas E2E web e revisão integrada do sistema completo.
- Fora desta fase: exception handlers, endpoints de negócio, packages vazios, placeholders, `schema.sql`, `data.sql`, H2, DDL do Hibernate, broker/microservices.

## Subsequent Slice Plan

Cada fase posterior adiciona sua capacidade sem alterar silenciosamente o contrato desta fundação:

- Phase 2: usuário obtém identidade JWT/BCrypt e acesso autorizado.
- Phase 3: perfis conduzem o fluxo auditável completo do chamado.
- Phase 4: eventos confirmados notificam destinatários por e-mail.
- Phase 5: operadores consultam indicadores autorizados.
- Phase 6: cada perfil conclui jornadas na aplicação React.
- Phase 7: qualidade, segurança e fluxos críticos são validados ponta a ponta.
