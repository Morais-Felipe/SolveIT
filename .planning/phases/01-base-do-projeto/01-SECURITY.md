---
phase: "01"
slug: "base-do-projeto"
status: verified
threats_open: 0
asvs_level: 1
block_on: high
register_authored_at_plan_time: true
created: "2026-09-04"
updated: "2026-09-04"
---

# Phase 1 — Security

> Verificação ASVS L1 dos registros STRIDE presentes nos cinco planos da Fase 1.

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Maven Central/Apache → workstation | Dependências e distribuição do Maven entram pela supply chain. | Binários e metadados de build |
| Docker registry → Testcontainers | A imagem oficial do MySQL entra no ambiente local de teste. | Imagem de banco executável |
| Ambiente → Spring configuration | Variáveis externas configuram o datasource. | URL e credenciais do banco |
| Aplicação/Flyway/Hibernate → MySQL | DDL, validação e DML atravessam JDBC. | Schema e dados de domínio |
| Chamadores internos → repositories | Futuras camadas de serviço usarão os boundaries de persistência. | Entidades, IDs e consultas |
| Contextos/transações concorrentes → MySQL | Instâncias disputam migration lock e versões de Chamado. | Locks, schema history e updates |

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-01-01-SC | Tampering | Maven Wrapper/dependencies | high | mitigate | Maven 3.9.16 e SHA-256 fixos, HTTPS, parent Spring Boot 3.5.16 e BOM; wrapper e `dependency:tree` verificados. | closed |
| T-01-01-ID | Information Disclosure | `application-dev.yml` | high | mitigate | Credenciais somente por variáveis; `.env*` ignorado; varredura não encontrou segredo real. | closed |
| T-01-01-TM | Tampering | Inicialização JPA | medium | mitigate | Flyway habilitado e `ddl-auto=validate`; nenhum modo DDL destrutivo. | closed |
| T-01-01-DS | Denial of Service | MySQL local indisponível | low | accept | Build/teste de compilação é separado; disponibilidade real foi comprovada no Ticket 05. | closed |
| T-01-02-TM | Tampering | Constantes enum | medium | mitigate | Conjuntos canônicos fixos, `EnumType.STRING`, SQL `CHECK` e round-trip textual. | closed |
| T-01-02-EP | Elevation of Privilege | `PerfilUsuario` | low | accept | A fase apenas declara perfis; autorização server-side permanece na Fase 2. | closed |
| T-01-02-RP | Repudiation | `StatusChamado` | low | accept | A fase preserva o vocabulário; regras e geração obrigatória de histórico ficam no core de chamados. | closed |
| T-01-03-TM | Tampering | Relações/campos obrigatórios | high | mitigate | Nullability, comprimentos, FKs e constraints explícitos, validados contra MySQL real. | closed |
| T-01-03-RP | Repudiation | Comentario/HistoricoStatus | high | mitigate | Sem setters/cascade remove; FKs `RESTRICT`; superfície verificada por teste e inspeção. | closed |
| T-01-03-CC | Tampering | Atualização concorrente de Chamado | high | mitigate | `@Version` e teste com duas transações; update obsoleto foi rejeitado. | closed |
| T-01-03-ID | Information Disclosure | `Usuario.senha` | medium | mitigate | Entidade interna sem `toString`/serialização REST; BCrypt e DTOs permanecem na Fase 2. | closed |
| T-01-03-DS | Denial of Service | Grafo JPA | low | accept | Relações LAZY/unidirecionais; tuning pertence às consultas consumidoras. | closed |
| T-01-04-TM | Tampering | Repositories de auditoria | medium | mitigate | Nenhum update/delete customizado, entidades sem setters e DDL sem cascade de remoção. | closed |
| T-01-04-EP | Elevation of Privilege | Chamadores de repository | low | accept | Repositories são internos; services/controllers futuros aplicarão autorização server-side. | closed |
| T-01-04-ID | Information Disclosure | `findByEmail` | low | accept | Método não é exposto; DTOs/respostas seguras permanecem na Fase 2. | closed |
| T-01-04-DS | Denial of Service | Consultas futuras | low | accept | Filtros, paginação e ordenação serão explícitos nas fases consumidoras. | closed |
| T-01-05-SC | Tampering | Imagem MySQL | high | mitigate | Docker Official Image fixada em `mysql:8.4.11`; versão efetiva confirmada no teste. | closed |
| T-01-05-TM | Tampering | Schema/constraints | high | mitigate | Flyway exclusivo, checksums, constraints nomeadas e testes adversariais no MySQL. | closed |
| T-01-05-RP | Repudiation | Comentario/HistoricoStatus | high | mitigate | FKs restritivas, sem cascade/setters customizados e inspeção append-only. | closed |
| T-01-05-CC | Tampering | Concorrência Chamado/Flyway | high | mitigate | Optimistic locking e dois contextos concorrentes contra schema vazio passaram. | closed |
| T-01-05-ID | Information Disclosure | Credenciais do container | medium | mitigate | Credenciais efêmeras confinadas ao Testcontainers e não gravadas em arquivo. | closed |
| T-01-05-DS | Denial of Service | Índices/startup | medium | mitigate | Índices aprovados, dois contextos e timeouts explícitos; teste concluiu. | closed |

*Status: `open` · `closed` · `open — below high threshold`.*

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-01 | T-01-01-DS | MySQL local pode estar desligado fora de testes; não bloqueia compilação e foi validado via Testcontainers. | Planos aprovados da Fase 1 | 2026-09-04 |
| AR-02 | T-01-02-EP | Declarar perfis sem conceder autoridade não cria boundary externo; autorização virá na Fase 2. | Planos aprovados da Fase 1 | 2026-09-04 |
| AR-03 | T-01-02-RP | O vocabulário precede deliberadamente o workflow e a geração de histórico. | Planos aprovados da Fase 1 | 2026-09-04 |
| AR-04 | T-01-03-DS | Otimização prematura do grafo foi evitada; relações LAZY limitam o risco atual. | Planos aprovados da Fase 1 | 2026-09-04 |
| AR-05 | T-01-04-EP | Não há endpoint ou service chamando repositories; o risco reabre quando esse boundary existir. | Planos aprovados da Fase 1 | 2026-09-04 |
| AR-06 | T-01-04-ID | `findByEmail` permanece interno até a implementação de autenticação/DTOs. | Planos aprovados da Fase 1 | 2026-09-04 |
| AR-07 | T-01-04-DS | Consultas e paginação devem ser desenhadas com os casos de uso reais das fases 3 e 5. | Planos aprovados da Fase 1 | 2026-09-04 |

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-09-04 | 22 | 22 | 0 | Codex primary agent, inline ASVS L1 verification |

## Follow-up Boundaries

- Fase 2 deve implementar BCrypt, JWT, DTOs sem `senha` e autorização server-side.
- Fase 3 deve impedir update/delete de comentários e históricos na camada de serviço/API e sempre gerar `HistoricoStatus` em mudanças de status.
- Fases 3 e 5 devem usar paginação e ordenação explícitas nas consultas expostas.
- A imagem `mysql:8.4.11` está fixa por tag, não por digest; reavaliar proveniência/digest no desenho de CI e deployment.

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-09-04
