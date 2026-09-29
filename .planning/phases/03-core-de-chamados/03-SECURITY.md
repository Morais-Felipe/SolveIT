---
phase: "03"
slug: "core-de-chamados"
status: verified
threats_open: 0
asvs_level: 1
block_on: high
created: "2026-09-29"
scope:
  - "03-01"
  - "03-02"
---

# Fase 03 — Segurança dos incrementos executados

> Contrato de segurança retroativo dos Planos 03-01 e 03-02. Os planos 03-03 a 03-10 ainda não foram executados e, portanto, não fazem parte deste sign-off.

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Cliente → API | Identidade, IDs, versões e campos enviados pelo cliente não concedem autoridade. | JWT, identificadores, versão esperada e dados de categoria |
| Service → MySQL | Autorização atual, estado mutável e persistência devem ser observados na mesma transação. | Perfil do ator, categoria, guard global, constraints e versão JPA |

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation / Evidence | Status |
|-----------|----------|-----------|----------|-------------|-----------------------|--------|
| T-03-01-1 | Elevation of Privilege | `CategoriaService` | high | mitigate | Escritas adquirem `CoreWriteCoordinator`, recarregam o usuário atual sob lock e exigem `ADMIN`; controller também aplica `hasRole('ADMIN')`. | closed |
| T-03-01-2 | Tampering | Unicidade de nome | high | mitigate | A constraint `uk_categoria_nome` é a autoridade; `saveAndFlush` força sua avaliação e somente essa constraint é traduzida para conflito controlado. | closed |
| T-03-01-3 | Information Disclosure | `DomainException` / handler | high | mitigate | Erros esperados usam títulos e detalhes fixos via `ProblemDetailFactory`; mensagens JDBC/SQL não são devolvidas ao cliente. | closed |
| T-03-01-4 | Denial of Service | Guard global | medium | mitigate | O lock usa `innodb_lock_wait_timeout=5`, `setQueryTimeout(5)`, transação com timeout de 10 segundos, resposta 503 e nenhum retry automático. | closed |
| T-03-02-1 | Elevation of Privilege | PUT/PATCH de categoria | high | mitigate | Edição e ativação adquirem o guard antes de `carregarUsuarioAtual`, revalidam `ADMIN` no service e têm defesa adicional no controller. | closed |
| T-03-02-2 | Tampering | Formulário desatualizado | high | mitigate | DTO exige `versaoEsperada` não negativa; service compara depois dos locks e a entidade usa `@Version` sobre coluna `BIGINT NOT NULL DEFAULT 0`. | closed |
| T-03-02-3 | Repudiation | Vínculos históricos | medium | mitigate | O fluxo não expõe `DELETE`; desativação altera somente `ativo`, e o teste de integração confirma preservação de `chamado.categoria_id` e do nome. | closed |

*A severidade bloqueante configurada é `high`; nenhuma ameaça permanece aberta, inclusive as classificadas como `medium`.*

## Accepted Risks Log

No accepted risks.

## Security Audit Trail

| Audit Date | Scope | Threats Total | Closed | Open | Run By |
|------------|-------|---------------|--------|------|--------|
| 2026-09-29 | Planos 03-01 e 03-02 | 7 | 7 | 0 | Codex / `$gsd-secure-phase 03` |

## Verification Evidence

- Planos 03-01 e 03-02 contêm registros STRIDE elaborados antes da implementação.
- ASVS nível 1 permite verificação direta das mitigações; não foi necessária auditoria profunda por subagente.
- Os testes de integração do Plano 03-02 passaram: 18 testes direcionados, 10 testes de regressão do catálogo e 90 testes no back-end completo, todos sem falhas ou erros.
- Evidências principais: `CategoriaController`, `CategoriaService`, `CoreWriteCoordinator`, `Categoria`, `CategoriaDtos`, `GlobalExceptionHandler`, migrations V6/V7 e `CategoriaEdicaoIntegrationTest`.

## Sign-Off

- [x] Todas as ameaças têm disposição `mitigate`.
- [x] Não há riscos aceitos a documentar.
- [x] `threats_open: 0` confirmado para o escopo executado.
- [x] `status: verified` definido no frontmatter.

**Approval:** verified 2026-09-29
