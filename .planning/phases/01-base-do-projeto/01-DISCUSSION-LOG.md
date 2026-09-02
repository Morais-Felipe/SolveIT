# Phase 1: Base do Projeto - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.

**Date:** 2026-09-02
**Phase:** 1-Base do Projeto
**Areas discussed:** Bootstrap e dependências, Esquema e migrações, Modelo JPA, Ambiente e validação

---

## Bootstrap e dependências

| Option | Description | Selected |
|--------|-------------|----------|
| Maven com BOM do Spring Boot | Stack simples e versões gerenciadas | ✓ |
| Versões manuais para cada biblioteca | Mais controle, maior risco de incompatibilidade | |

**User's choice:** Seleção automática da opção recomendada por `--auto`.
**Notes:** Dependências serão introduzidas quando necessárias por fase.

## Esquema e migrações

| Option | Description | Selected |
|--------|-------------|----------|
| Flyway como autoridade e Hibernate validate | Esquema reproduzível e explícito | ✓ |
| Hibernate cria o esquema | Menor trabalho inicial, maior drift | |

**User's choice:** Seleção automática da opção recomendada por `--auto`.
**Notes:** Enums textuais, constraints e locking otimista entram desde a fundação.

## Modelo JPA

| Option | Description | Selected |
|--------|-------------|----------|
| Entidades internas e DTOs nas APIs | Evita vazar o modelo de persistência | ✓ |
| Entidades como respostas REST | Menos classes, contratos frágeis | |

**User's choice:** Seleção automática da opção recomendada por `--auto`.
**Notes:** Comentários e históricos permanecem append-only.

## Ambiente e validação

| Option | Description | Selected |
|--------|-------------|----------|
| MySQL Testcontainers | Prova migrações e semântica real do banco | ✓ |
| H2 em memória | Mais rápido, mas diverge do MySQL | |

**User's choice:** Seleção automática da opção recomendada por `--auto`.
**Notes:** Segredos serão externalizados e a documentação será pedagógica.

## the agent's Discretion

- Detalhes reversíveis de plugins Maven e convenções internas compatíveis com o escopo.

## Deferred Ideas

- Capacidades das fases 2 a 7 permanecem fora desta discussão.
