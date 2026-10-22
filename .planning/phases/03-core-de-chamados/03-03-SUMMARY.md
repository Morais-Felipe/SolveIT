---
phase: 03-core-de-chamados
plan: "03"
subsystem: api
tags: [spring-mvc, spring-security, jpa, flyway, mysql, optimistic-locking, pessimistic-locking, bcrypt, testcontainers]

requires:
  - phase: 03-core-de-chamados
    plan: "02"
    provides: Padrão de comando administrativo versionado e protocolo de coordenação das escritas
provides:
  - Consulta administrativa paginada e detalhada de usuários sem credenciais
  - Criação administrativa de usuários de qualquer perfil, sempre ativos, com BCrypt
  - Edição somente de dados pessoais com versão esperada; perfil/ativo rejeitados com 400
  - Migração V8 com usuario.version e upgrade V6 para V8 preservando dados
  - Procedimento manual proposto para o primeiro ADMIN
  - 400 VALIDATION_ERROR para parâmetros de query/path inválidos em toda a API
affects: [03-04-chamados, 03-10-acoes-confirmadas, usuarios, categorias, core-write-protocol, auth]

ticket:
  number: 13
  branch: ticket/13-gestao-usuarios
  pr: pendente

actuals:
  tasks: 3
  commits: 7
  executor: Claude Code (sem GSD)

tech-stack:
  added: []
  patterns:
    - Locks de usuários sempre em ordem crescente de ID; 404 do alvo só após confirmar o ator ADMIN
    - Hash BCrypt calculado antes do guard global, fora de qualquer lock
    - Campos proibidos declarados com @Null para rejeitar em vez de ignorar
    - UniqueConstraints identifica a constraint UNIQUE específica de uma DataIntegrityViolationException

key-files:
  created:
    - solveit-api/src/main/java/com/solveit/controller/UsuarioController.java
    - solveit-api/src/main/java/com/solveit/service/UsuarioService.java
    - solveit-api/src/main/java/com/solveit/dto/usuario/UsuarioDtos.java
    - solveit-api/src/main/java/com/solveit/exception/UniqueConstraints.java
    - solveit-api/src/main/resources/db/migration/V8__version_usuario.sql
    - solveit-api/src/test/java/com/solveit/controller/UsuarioIntegrationTest.java
    - docs/PRIMEIRO-ADMIN.md
  modified:
    - solveit-api/src/main/java/com/solveit/entity/Usuario.java
    - solveit-api/src/main/java/com/solveit/dto/validation/ValidPassword.java
    - solveit-api/src/main/java/com/solveit/dto/validation/PasswordValidator.java
    - solveit-api/src/main/java/com/solveit/exception/GlobalExceptionHandler.java
    - solveit-api/src/main/java/com/solveit/controller/CategoriaController.java
    - solveit-api/src/main/java/com/solveit/service/AuthService.java
    - solveit-api/src/main/java/com/solveit/service/CategoriaService.java
    - solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java
    - solveit-api/src/test/java/com/solveit/controller/AuthSecurityIntegrationTest.java
    - solveit-api/src/test/java/com/solveit/controller/CategoriaIntegrationTest.java

key-decisions:
  - "BCrypt é calculado antes de CoreWriteCoordinator.adquirir(); o hash não lê estado do banco e não deve segurar o lock global (~250 ms)."
  - "@JsonIgnoreProperties(ignoreUnknown = false) foi aprovado no plano, mas o teste provou que não sobrepõe FAIL_ON_UNKNOWN_PROPERTIES=false do Spring Boot. Substituído por perfil/ativo declarados com @Null em EditarDados."
  - "@ValidPassword ganhou optional (padrão false); somente o PUT aceita senha ausente, preservando o hash."
  - "Parâmetros inválidos (ConstraintViolationException e MethodArgumentTypeMismatchException) respondem 400 VALIDATION_ERROR sem ecoar o valor recebido; antes resultavam em 500, inclusive em /api/categorias."
  - "POST ignora ativo enviado pelo cliente e grava sempre ativo=true."

patterns-established:
  - "Ordered user locks: ator e alvo travados por ID crescente; mesmo ID usa uma única leitura bloqueante."
  - "Reject, don't ignore: campos de autoridade fora do contrato da rota são declarados com @Null para produzir 400 explícito."

requirements-completed: [ADMN-01, ADMN-02]

coverage:
  - id: D1
    description: "ADMIN lista e detalha usuários paginados por ID, inclusive inativos, sem credenciais; demais perfis recebem 403 e mudança de perfil vale na requisição seguinte."
    requirement: "ADMN-01"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/UsuarioIntegrationTest.java#listing, detail, pagination and profile reload scenarios"
        status: pass
    human_judgment: false
  - id: D2
    description: "ADMIN cria usuários de qualquer perfil, ativos, com BCrypt e e-mail normalizado único; cadastro público continua SOLICITANTE."
    requirement: "ADMN-02"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/UsuarioIntegrationTest.java#creation, duplicate and public registration scenarios"
        status: pass
    human_judgment: false
  - id: D3
    description: "D-09/D-10/D-16: PUT altera somente nome/email/senha, rejeita perfil/ativo com 400 e versão antiga com 409."
    requirement: "ADMN-02"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/UsuarioIntegrationTest.java#edition, forbidden fields and stale-version scenarios"
        status: pass
    human_judgment: false
  - id: D4
    description: "V8 faz backfill version=0 preservando usuários; corrida entre cadastro público e administrativo cria uma conta e um 409."
    requirement: "ADMN-02"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java#versionSixDatabaseUpgradesToCurrentWithoutLosingCategoryOrUserData"
        status: pass
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/UsuarioIntegrationTest.java#concurrentPublicAndAdminRegistrationOfSameEmailCreatesExactlyOneUser"
        status: pass
    human_judgment: false
  - id: D5
    description: "Procedimento do primeiro ADMIN é manual, seletivo, sem endpoint, runner ou senha padrão."
    requirement: "ADMN-02"
    verification:
      - kind: document
        ref: "docs/PRIMEIRO-ADMIN.md"
        status: proposed
    human_judgment: true
---

# Plano 03-03 — Gestão de usuários pelo ADMIN

## Resultado

O ADMIN passa a consultar, criar e corrigir dados pessoais de usuários por `/api/usuarios`. Perfil e atividade continuam sem rota de mutação; essa troca fica para o plano 03-10, com confirmação explícita.

| Rota | Comportamento |
|---|---|
| `GET /api/usuarios?page&size` | ADMIN; ordem por ID; `page >= 0`, `size` 1..100 |
| `GET /api/usuarios/{id}` | ADMIN; 404 para ID inexistente |
| `POST /api/usuarios` | ADMIN; 201 + `Location`; qualquer perfil; conta sempre ativa |
| `PUT /api/usuarios/{id}` | ADMIN; somente `nome`, `email`, `senha?`, `versaoEsperada`; `perfil`/`ativo` → 400 |

## Commits

| Commit | Tipo | Descrição |
|---|---|---|
| `bc74d67` | fix | 400 para parâmetros de query/path inválidos (antes 500) |
| `ecc2d75` | feat | Listagem e detalhe administrativos |
| `c43599b` | feat | V8, criação e edição versionada |
| `5a159ea` | test | Testes de auth reutilizam a entidade mesclada após `@Version` |
| `15bdb39` | test | Upgrade V6→V8 e corrida de e-mail |
| `35f3cfd` | docs | Procedimento do primeiro ADMIN |
| `e1853c5` | refactor | `UniqueConstraints` compartilhado |

## Desvios do plano

1. **Correção fora do escopo original, aprovada no ticket:** o teste de paginação revelou que parâmetros inválidos geravam 500 em toda a API. Correção em commit `fix` separado, com testes também em categorias.
2. **Mecanismo de rejeição de campos:** `@JsonIgnoreProperties(ignoreUnknown = false)` não funciona com a configuração padrão do Spring Boot (comprovado por teste que recebeu 200). Substituído por `@Null`.
3. **Regressão encontrada pela suíte completa:** `@Version` fez dois testes de `AuthSecurityIntegrationTest` falharem por reutilizarem uma entidade *detached* desatualizada. Produção estava correta; os testes foram ajustados.
4. **Refactor adicional:** a detecção da constraint UNIQUE foi extraída para `UniqueConstraints`, removendo três cópias.

## Verificação

- Suíte completa no MySQL 8.4.11 via Testcontainers: **111/115**.
- As 4 falhas são de `AuthHttpErrorIntegrationTest` (`Unable to establish loopback connection` ao criar `java.net.http.HttpClient`). Elas se reproduzem na `main` sem as mudanças deste ticket e são do ambiente Windows atual, não regressão.

## Pendências

- Investigar o erro de loopback desta máquina para que a suíte volte a ficar inteira verde localmente.
- A primeira execução real de `docs/PRIMEIRO-ADMIN.md` exige aprovação operacional explícita.
