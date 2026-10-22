---
gsd_state_version: 1.0
current_phase: 03
current_phase_name: Core de Chamados
status: executing
stopped_at: Plan 03-04 complete on ticket/14-abertura-chamados; awaiting push approval
last_updated: "2026-10-22T12:00:00.000Z"
last_activity: 2026-10-22
last_activity_desc: Plan 03-04 implemented and verified locally; flaky guard test fixed
state_head: 92d1879
progress:
  total_phases: 7
  completed_phases: 2
  total_plans: 23
  completed_plans: 17
  percent: 29
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-09-04)

**Core value:** Permitir que um chamado percorra seu fluxo completo com autorização correta, histórico auditável e comunicação clara entre todos os envolvidos.
**Current focus:** Phase 03 — Core de Chamados

## Current Position

Phase: 03 (Core de Chamados) — EXECUTING
Plan: 4 of 10 complete
Status: ticket/14-abertura-chamados committed locally; awaiting push approval
Last activity: 2026-10-22 — Plan 03-04 implemented with Claude Code

Progress by phase: [██████░░░░░░░░░░░░░░] 2/7 phases (29%)
Plan progress: 17/23 plans complete (74%); Phase 03 has 4/10 implementation tickets complete

## Performance Metrics

**Velocity:**

- Total plans completed: 15
- Average duration: 37.3 min
- Total execution time: 9.3 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| Phase 01 | 5 | 66 min | 13.2 min |
| Phase 02 | 8 | 176 min | 22.0 min |
| Phase 03 | 2 | 319 min | 159.5 min |

**Recent Trend:**

- Last 5 plans: 28 min, 28 min, 13 min, 276 min, 43 min
- Trend: o segundo plano reutilizou o protocolo compartilhado e fechou em 43 minutos com prova completa no MySQL

**Per-Plan Metrics:**

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 01 P01 | 12 min | 1 tasks | 8 files |
| Phase 01 P02 | 3 min | 1 tasks | 3 files |
| Phase 01 P03 | 6 min | 1 tasks | 5 files |
| Phase 01 P04 | 31 min | 1 tasks | 5 files |
| Phase 01 P05 | 16 min | 1 tasks | 6 files |
| Phase 02 P01 | 12 min | 1 tasks | 9 files |
| Phase 02 P02 | 24 min | 3 tasks | 10 files |
| Phase 02 P03 | 20 min | 2 tasks | 8 files |
| Phase 02 P04 | 31 min | 2 tasks | 8 files |
| Phase 02 P05 | 20 min | 1 tasks | 6 files |
| Phase 02 P06 | 28 min | 2 tasks | 6 files |
| Phase 02 P07 | 28 min | 2 tasks | 10 files |
| Phase 02 P08 | 13 min | 1 tasks | 2 files |
| Phase 03 P01 | 4h 36m | 3 tasks | 12 files |
| Phase 03 P02 | 43 min | 2 tasks | 8 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.

- [Roadmap]: Preservar a ordem estrita das sete fases definida no documento de origem.
- [Roadmap]: Tratar cada fase como incremento MVP e manter o servidor como autoridade de autorização.
- [Phase 01]: Maven Wrapper 3.3.4 usa only-script, Maven 3.9.16 fixo e checksum SHA-256 da distribuição.
- [Phase 01]: Credenciais MySQL do perfil dev são fornecidas somente por DB_USERNAME e DB_PASSWORD.
- [Phase 01]: Flyway é a única autoridade DDL; Hibernate permanece em ddl-auto=validate.
- [Phase 01]: Enums de domínio permanecem mínimos e sem regras de autorização, transição ou apresentação.
- [Phase 01]: Enums serão persistidos por nome com EnumType.STRING; reordenação não altera dados, renomeação exige migração.
- [Phase 01]: Repositories permanecem mínimos; somente identidade e unicidade justificam métodos derivados no Ticket 04.
- [Phase 01]: Ordenação será sempre explícita nas consultas consumidoras; Comentario/HistoricoStatus permanecem append-only nos services futuros.
- [Phase 01]: Flyway é a única autoridade DDL; V1..V5 não usam IF NOT EXISTS e Hibernate apenas valida o schema.
- [Phase 01]: Contextos de integração recebem perfil e datasource por argumentos de run para prevalecer sobre application-dev.yml.
- [Phase 01]: FKs usam RESTRICT e nenhuma relação auditável possui delete cascade.
- [Phase 02]: BCrypt strength 12 é o padrão externalizável após medir 10, 11 e 12 no hardware alvo.
- [Phase 02]: Somente POST /api/auth/register e POST /api/auth/login são públicos; qualquer outra requisição permanece protegida por padrão.
- [Phase 02]: E-mail é normalizado no service e senha é entregue intacta ao PasswordEncoder.
- [Phase 02]: Senhas são validadas por code points Unicode e bytes UTF-8 sem trim ou normalização. — Preserva o texto exato da credencial e respeita o limite de 72 bytes do BCrypt.
- [Phase 02]: existsByEmail é pré-checagem; uk_usuario_email no MySQL é a autoridade final sob concorrência. — Duas requisições podem passar simultaneamente pela consulta, então saveAndFlush classifica somente a constraint específica.
- [Phase 02]: Problem Details de cadastro usa textos controlados e nunca deriva detail de exceções JDBC. — Evita vazamento de senha, SQL, constraint, stack trace ou classes internas.
- [Phase 02]: Segredo JWT Base64 externo deve decodificar para pelo menos 32 bytes, sem default versionado. — A aplicação falha no startup para impedir assinatura com configuração ausente ou fraca.
- [Phase 02]: JWT usa HS256 fixo, issuer/audience exigidos e payload limitado a sub, iat, exp, iss e aud. — Impede escolha de algoritmo pelo token e mantém perfil, estado e dados pessoais fora da credencial legível.
- [Phase 02]: Clock.systemUTC é injetado no JwtService. — Produção usa tempo absoluto e testes controlam emissão e expiração de forma determinística.
- [Phase 02]: DaoAuthenticationProvider compara BCrypt e exige conta ativa; AuthService não interpreta o hash. — Mantém a comprovação de credenciais sob responsabilidade do Spring Security.
- [Phase 02]: UsuarioPrincipal é separado da entidade JPA e apaga o hash após autenticar. — Evita manter credenciais no contexto e prepara ID/perfil atuais para autorização.
- [Phase 02]: Login normaliza somente o e-mail e deriva expiresIn do TTL JWT configurado. — Preserva a senha exata e impede divergência entre resposta e expiração real.
- [Phase 02]: Falhas de usuário inexistente, senha errada e conta inativa convergem para 401 AUTH_CREDENTIALS_INVALID. — Impede enumeração de contas por status, mensagem ou schema.
- [Phase 02]: Rate limiting fica fora da Fase 2, mas é obrigatório decidir o controle antes de exposição à internet. — BCrypt e resposta uniforme não impedem credential stuffing ou brute force.
- [Phase 02]: Bearer usa somente o sub assinado e recarrega perfil/atividade do banco em toda requisição. — Impede que estado de autorização obsoleto no token sobreviva a mudança de perfil ou desativação.
- [Phase 02]: O principal Bearer não copia o hash e usa um SecurityContext vazio por requisição. — Reduz material sensível em memória e preserva isolamento stateless entre threads.
- [Phase 02]: GET /api/auth/me deriva o usuário somente de UsuarioPrincipal.getId(). — Impede que identificadores fornecidos pelo cliente substituam a identidade autenticada.
- [Phase 02]: JwtService classifica expiração antes das demais falhas JWT. — Preserva AUTH_TOKEN_EXPIRED sem expor mensagens ou classes do parser.
- [Phase 02]: Token ausente segue anônimo e Authorization presente fora do formato aceito é inválido. — Mantém AUTH_TOKEN_MISSING distinto de credenciais Bearer rejeitadas.
- [Phase 02]: Entry point e denied handler escrevem Problem Details pelo factory comum. — Unifica 401 e 403 no contrato RFC 9457 sem reutilizar mensagens de exceção.
- [Phase 02]: Perfil governa quem pode tentar a operação; relação persistida governa sobre qual registro pode agir. — Separa a regra ampla em @PreAuthorize da regra dependente de dados dentro do service transacional.
- [Phase 02]: A autorização usa somente a identidade de UsuarioPrincipal; userId enviado pelo cliente é ignorado. — Impede spoofing e evita que entrada controlada pelo cliente substitua a identidade autenticada.
- [Phase 02]: A prova da fundação de autorização usa componentes exclusivos de teste; políticas concretas de Chamado ficam para a Fase 3. — Comprova Method Security e guarda de relação sem criar endpoints ou regras de domínio prematuras em produção.
- [Phase 02 audit]: Tokens Bearer exigem `iat` e `exp` coerentes; ausência ou intervalo inválido é `AUTH_TOKEN_INVALID`.
- [Phase 02 audit]: E-mail de cadastro é normalizado no DTO antes de validar formato e limite; a senha permanece intacta.
- [Phase 02 audit]: Erros de leitura JSON e mídia não suportada possuem Problem Details próprios; somente despachos internos `ERROR` atravessam a segurança, e o filtro JWT executa apenas na cadeia Spring Security.
- [Phase 03]: Escritas curtas da Fase 3 compartilham a linha singleton core_write_guard e mantem o lock ate commit ou rollback. — Serializa decisoes criticas entre instancias com uma ordem de locks unica e verificavel.
- [Phase 03]: Timeout de row lock e temporario na sessao JDBC e restaurado em finally; setQueryTimeout permanece como segunda barreira. — O teste MySQL mostrou que setQueryTimeout sozinho nao interrompe a espera de lock de forma confiavel.
- [Phase 03]: Toda escrita recarrega perfil e atividade do ator depois de adquirir o guard. — Impede que autoridade revogada durante a espera produza uma escrita.
- [Phase 03]: Categoria usa @Version e o cliente envia versaoEsperada; comparação ocorre depois do guard e do lock pessimista. — Impede sobrescrita silenciosa por formulário desatualizado.
- [Phase 03]: Ativação sem mudança valida a versão mas não executa UPDATE; desativação nunca remove a categoria ou seus vínculos. — Preserva idempotência, auditoria relacional e versão significativa.
- [Phase 03 / 03-03]: BCrypt é calculado antes do guard global; o hash não lê estado do banco e não deve segurar o lock.
- [Phase 03 / 03-03]: Campos de autoridade fora do contrato (perfil/ativo no PUT) são declarados com @Null para gerar 400; @JsonIgnoreProperties(ignoreUnknown = false) não sobrepõe a configuração global do Spring Boot.
- [Phase 03 / 03-03]: Usuários são travados em ordem crescente de ID; o 404 do alvo só aparece após confirmar o ator ADMIN.
- [Phase 03 / 03-03]: Parâmetros de query/path inválidos respondem 400 VALIDATION_ERROR sem ecoar o valor.
- [Phase 03 / 03-04]: V9 permite status_anterior nulo somente quando status_novo = ABERTO; V5 intacta e nenhum histórico retroativo.
- [Phase 03 / 03-04]: Requests de criação rejeitam campos fora do contrato via @JsonAnySetter + @Size(max = 0).
- [Phase 03 / 03-04]: Abertura com categoria inativa responde 409 INVALID_STATE; inexistente responde 404.
- [Process]: A partir de 2026-10-22 o projeto segue os planos em .planning com Claude Code, sem GSD; fluxo Git por ticket e modo pedagógico permanecem.

### Pending Todos

- PR [#14](https://github.com/Morais-Felipe/SolveIT/pull/14) (plano 03-02) integrado na `main` em 2026-10-22 (a3fc265).
- PR [#15](https://github.com/Morais-Felipe/SolveIT/pull/15) (plano 03-03) integrado na `main` em 2026-10-22 (8e376c7).
- Ticket 14 (`ticket/14-abertura-chamados`, plano 03-04): commits locais; push, PR e merge exigem aprovações separadas.
- Adicionar handler genérico de `Exception` → 500 `INTERNAL_ERROR` sem vazar detalhes (achado no 03-04).
- TICK-11 e TICK-12 seguem parciais até os planos de transição.
- Investigar `Unable to establish loopback connection` no AuthHttpErrorIntegrationTest nesta máquina (reproduz na `main`).
- Remover do AGENTS.md e do docs/GIT-WORKFLOW.md as instruções que exigem GSD (decisão de 2026-10-22: GSD descontinuado).
- Decisão aprovada em 2026-09-10: cada plano de implementação da Fase 3 em diante terá ticket/branch/PR próprio, integração sequencial e modo pedagógico. Fonte: docs/GIT-WORKFLOW.md.

### Blockers/Concerns

- [Pre-production]: Definir e verificar rate limiting para login/cadastro antes de expor o SolveIT à internet.
- [Phase 3 / Plan 05]: Decidir no checkpoint humano a visibilidade de categoria inativa por ID antes da implementação da consulta de chamados.
- [Phase 4]: Decidir entre entrega best-effort após commit e outbox durável.
- [Phase 5]: Definir escopo dos agregados para atendentes e significado de carga ativa.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260910-jhi | Documentar uma branch por plano aprovado e retomada após merge da Fase 2 | 2026-09-10 | 242f1e3 | [260910-jhi](./quick/260910-jhi-documentar-branch-por-plano-aprovado-e-r/) |
| 260902-et2 | Criar README completo do SolveIT inspirado na organização do README do GSD Core | 2026-09-02 | 6cf0155 | [260902-et2-criar-readme-completo-do-solveit-inspira](./quick/260902-et2-criar-readme-completo-do-solveit-inspira/) |
| 2 | Adicionar modo pedagógico obrigatório ao AGENTS.md | 2026-09-03 | 6c3266c | — |
| 260904-k6p | Atualizar a documentação após a verificação da Fase 1, alinhar as aprovações Git e corrigir a identidade local dos commits | 2026-09-04 | be21535 | [260904-k6p-atualizar-a-documentacao-apos-a-verifica](./quick/260904-k6p-atualizar-a-documentacao-apos-a-verifica/) |
| 260908-g6x | Corrigir configuração local após renomear a raiz para C:/SolveIT e atualizar a documentação da Fase 2 | 2026-09-08 | 8bd8dce | [260908-g6x-corrigir-configura-o-local-ap-s-renomear](./quick/260908-g6x-corrigir-configura-o-local-ap-s-renomear/) |

## Deferred Items

| Category | Item | Status | Deferred At | Milestone |
|----------|------|--------|-------------|-----------|
| GSD | Reparar `.codex/hooks/lib/hook-exit.js` para restaurar `gsd-context-monitor` | Resolved localmente em C:/SolveIT — helpers oficiais 1.12.0 restaurados; como `.codex` não é versionado, verificar a instalação ao trocar de máquina ou raiz | Phase 02 quick 260908-g6x | v1 |
| GSD | Investigar `init.execute-phase` reportando runtime `claude` durante sessão Codex | Reaberto — Wave 5 voltou a reportar `agent_runtime: claude`; execução sequencial não foi afetada, mas revisar antes de delegação | Phase 02 Plan 05 | v1 |

## Session Continuity

Last session: 2026-09-30T10:44:52.656Z
Stopped at: Plan 03-04 complete on ticket/14-abertura-chamados; awaiting push approval
Resume file: none
