---
phase: 02-autentica-o
fixed_at: 2026-09-10
status: all_fixed
findings_in_scope: 5
fixed: 5
skipped: 0
iteration: 1
verified_commit: 99aeaf2
tests: 64
---

# Correções da auditoria da Fase 2

Todos os apontamentos de [02-REVIEW.md](02-REVIEW.md) foram tratados inline pelo fluxo `$gsd-code-review --fix`. Cada correção técnica recebeu um commit atômico; este relatório e a atualização documental são registrados ao final.

| Achado | Correção | Commit/evidência |
|---|---|---|
| R1 — claims temporais JWT | `JwtService` exige `iat` e `exp` e rejeita expiração que não seja posterior à emissão. Falhas continuam classificadas como token inválido; expiração real mantém código próprio. | `1381166`; 14 testes JWT passam. |
| R2 — validação cadastral | `RegisterRequest` normaliza e-mail antes de aplicar formato/tamanho; nome respeita 120 e e-mail 255 caracteres; senha não é alterada. | `c5f357f`; 12 testes de cadastro passam. |
| R3 — contrato de erros HTTP | JSON ilegível retorna 400 `REQUEST_BODY_INVALID`; mídia não suportada retorna 415 `MEDIA_TYPE_NOT_SUPPORTED`; despacho interno `ERROR` não é mascarado pela autenticação. | `99aeaf2`; testes via Tomcat real. |
| R4 — documentos desatualizados | README, Tarefa Atual, ROADMAP, STATE e verificação refletem 8/8 planos, 64 testes e conclusão local da fase. | Mesmo commit documental deste relatório. |
| R5 — registro do filtro | `FilterRegistrationBean` desabilitado impede registro servlet automático; o filtro continua inserido explicitamente na cadeia Spring Security. | `99aeaf2`; estado do bean e fluxo HTTP testados. |

## Validação final

`mvnw.cmd clean verify` terminou com `BUILD SUCCESS`: **64 testes, 0 falhas, 0 erros, 0 ignorados**, cinco migrações aplicadas em MySQL 8.4.11 e JAR reempacotado pelo Spring Boot. O acesso direto a `/error` permanece 401 e não foi criada sessão/cookie de autenticação.

O teste diagnóstico preservado em `audit/Phase2AuditProbeTest.java` representa o commit auditado anterior e não compõe a suíte permanente. Os novos testes de regressão afirmam o comportamento correto e permanecem em `src/test`.

Não houve push, PR ou merge. A publicação da branch depende de aprovação explícita do usuário.
