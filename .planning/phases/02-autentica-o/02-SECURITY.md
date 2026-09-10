---
phase: "02"
slug: "autentica-o"
status: verified
threats_open: 0
asvs_level: 1
block_on: high
created: "2026-09-10"
audited_commit: "8966736"
---

# Phase 02 — Security

> Contrato de segurança da autenticação: registro STRIDE, evidência das mitigações e risco aceito antes da exposição à internet.

## Trust Boundaries

| Boundary | Description | Data Crossing |
|---|---|---|
| Cliente anônimo → API Auth | Cadastro, e-mail e senha entram sem identidade prévia. | Dados pessoais e credencial em texto claro durante a requisição HTTPS futura. |
| API Auth → Spring Security | Credenciais de login são comprovadas e convertidas em identidade autenticada. | E-mail normalizado, senha e principal sem hash. |
| Cliente → filtro Bearer | Token não confiável entra antes do MVC. | JWT assinado e motivo público de rejeição. |
| Filtro/principal → MySQL | O `sub` assinado resolve perfil e atividade atuais. | ID de usuário e estado persistido. |
| SecurityContext → método/service | Perfil autoriza a tentativa e a relação persistida autoriza o recurso. | Identidade atual e identificador do recurso. |
| Exceção interna → HTTP/log | Falhas internas são convertidas para contrato público. | Problem Details controlado, sem segredo ou stack trace. |

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|---|---|---|---|---|---|---|
| T-02-01 | Elevation of Privilege | Cadastro | high | mitigate | Perfil ausente do DTO e `SOLICITANTE` imposto em `AuthService.java:62`; tentativa de ADMIN testada em `AuthRegistrationIntegrationTest.java:102`. | closed |
| T-02-02 | Information Disclosure | Saída/persistência | high | mitigate | BCrypt antes do save em `AuthService.java:56`; response DTO explícito e inspeções negativas em `AuthRegistrationIntegrationTest`. | closed |
| T-02-03 | Spoofing | Sessão | high | mitigate | `STATELESS`, `NullSecurityContextRepository` e `NullRequestCache` em `SecurityConfig.java:75`; ausência de sessão/cookie testada. | closed |
| T-02-04 | Denial of Service | BCrypt | medium | mitigate | Strength externalizável com padrão 12 e calibração 10/11/12 em `BcryptCostCalibrationTest.java:15`. | closed |
| T-02-05 | Tampering | Senha Unicode | high | mitigate | Code points e limite de 72 bytes UTF-8 em `PasswordValidator.java:18`, com fronteiras testadas. | closed |
| T-02-06 | Tampering | E-mail concorrente | high | mitigate | `saveAndFlush` e classificação exclusiva de `uk_usuario_email` em `AuthService.java:65`/`:145`, com corrida integrada. | closed |
| T-02-07 | Information Disclosure | Handler | high | mitigate | `ProblemDetailFactory` e handlers usam textos controlados; respostas/logs têm inspeções negativas. | closed |
| T-02-08 | Spoofing/Tampering | JwtService | high | mitigate | `verifyWith`, allowlist HS256, iss/aud e claims temporais obrigatórias em `JwtService.java:32`/`:58`; chave mínima validada. | closed |
| T-02-09 | Information Disclosure | Config/token | high | mitigate | Segredo vem de `SOLVEIT_JWT_SECRET`; payload mínimo é verificado em `JwtServiceTest.java:48`. | closed |
| T-02-10 | Spoofing | Login | high | mitigate | `DaoAuthenticationProvider`, BCrypt e `UsuarioPrincipal.isEnabled()` comprovam credencial e conta ativa. | closed |
| T-02-11 | Information Disclosure | Principal/resposta | high | mitigate | Principal sem credencial após autenticação, `TokenResponse` mínimo e testes de ausência em resposta/log. | closed |
| T-02-12 | Information Disclosure | Falhas de login | high | mitigate | Conta inexistente, senha errada e conta inativa convergem para 401 `AUTH_CREDENTIALS_INVALID`, com schema/log testados. | closed |
| T-02-13 | Spoofing/DoS | Login público | medium | accept | Risco de brute force aceito somente durante desenvolvimento local; rate limiting é obrigatório antes de exposição à internet. | closed — accepted risk R-02-01 |
| T-02-14 | Spoofing/Elevation of Privilege | Filtro | high | mitigate | Token é validado e usuário/perfil/atividade são recarregados por ID em `JwtAuthenticationFilter.java:68`. | closed |
| T-02-15 | Elevation of Privilege | `/me` | high | mitigate | `AuthController.java:41` recebe `UsuarioPrincipal`; service consulta apenas seu ID, ignorando identidade do cliente. | closed |
| T-02-16 | Repudiation | SecurityContext | medium | mitigate | Contexto vazio por requisição em `JwtAuthenticationFilter.java:84`; isolamento paralelo e ausência de sessão testados. | closed |
| T-02-17 | Spoofing/Tampering | Parser/filtro | high | mitigate | Allowlist HS256 e matriz de chave, algoritmo, issuer, audience, expiração e formato em `JwtServiceTest`/`AuthSecurityIntegrationTest`. | closed |
| T-02-18 | Information Disclosure | Handlers/logs | high | mitigate | Entry point, denied handler e handler MVC produzem Problem Details controlado; testes negativos cobrem token, SQL e classes internas. | closed |
| T-02-19 | Elevation of Privilege | 401/403 | high | mitigate | `RestAuthenticationEntryPoint` e `RestAccessDeniedHandler` separados, registrados e testados com códigos distintos. | closed |
| T-02-20 | Elevation of Privilege | Método/service | high | mitigate | `@EnableMethodSecurity`, `@PreAuthorize` e guarda de relação persistida são exercitados pela matriz negativa/positiva do Plano 08. | closed |
| T-02-21 | Spoofing | userId do cliente | high | mitigate | Autorização usa somente `UsuarioPrincipal`; teste com `userId` forjado confirma que o parâmetro não substitui a identidade. | closed |

Status: `closed` indica que a mitigação declarada foi localizada na implementação ou que o risco aceito está formalmente registrado. A auditoria ASVS L1 verifica presença do controle na fronteira citada; não equivale a pentest de produção.

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---|---|---|---|---|
| R-02-01 | T-02-13 | A Fase 2 é desenvolvimento local e o projeto já decidiu adiar rate limiting. Antes de qualquer exposição à internet, o controle deve ser definido e verificado; BCrypt e resposta uniforme não bastam contra brute force/credential stuffing. | Decisão aprovada no Plano 02-05 e registrada no STATE | 2026-09-10 |

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|---|---:|---:|---:|---|
| 2026-09-10 | 21 | 21 | 0 | Codex, auditoria inline GSD secure-phase |

## Unregistered Flags

Nenhuma superfície nova sem mapeamento foi identificada nos resumos. O risco pré-produção de rate limiting do Plano 05 corresponde a T-02-13. Os erros HTTP e o registro do filtro encontrados na auditoria global foram corrigidos e documentados em `02-REVIEW-FIX.md`.

## Sign-Off

- [x] Todas as ameaças têm disposição (`mitigate` ou `accept`).
- [x] Risco aceito documentado no Accepted Risks Log.
- [x] `threats_open: 0` confirmado para o limiar `high`.
- [x] `status: verified` definido no frontmatter.

**Approval:** verified 2026-09-10
