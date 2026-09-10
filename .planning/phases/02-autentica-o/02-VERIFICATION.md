---
phase: 02-autentica-o
verified: 2026-09-10
status: passed
audited_commit: ee8c6b9
verified_commit: 99aeaf2
tests: 64
gaps: []
---

# Verificação global — Fase 2

**Resultado: passed.** A auditoria inicialmente encontrou cinco apontamentos. R1–R3 bloqueavam o aceite da fase, R4 era divergência documental e R5 era melhoria de robustez. Todos foram corrigidos e revalidados; detalhes estão em [02-REVIEW.md](02-REVIEW.md) e [02-REVIEW-FIX.md](02-REVIEW-FIX.md).

| Requisito | Resultado no escopo da fase | Evidência/limite |
|---|---|---|
| AUTH-01 | Atendido | Cadastro força SOLICITANTE; normaliza e valida e-mail; respeita limites relacionais e preserva a senha antes do BCrypt. |
| AUTH-02 | Atendido | Conta ativa autentica com BCrypt e recebe JWT HS256 com duração configurada. |
| AUTH-03 | Atendido | `/me` usa o ID do principal e retorna dados atuais sem aceitar identidade fornecida pelo cliente. |
| AUTH-04 | Atendido nos caminhos implementados | Hash BCrypt não aparece nas respostas nem nos logs exercitados. |
| AUTH-05 | Atendido | Tokens ausentes, malformados, adulterados, expirados, sem claims temporais obrigatórias ou ligados a conta inativa são rejeitados. |
| AUTH-06 | Fundação da fase atendida | Perfil e relação persistida foram comprovados no servidor com probes exclusivos de teste; cada operação futura deverá aplicar e testar sua política concreta. |

As decisões D-01–D-19 permanecem coerentes. O contrato continua Bearer stateless, sem cookie de autenticação, refresh token ou sessão HTTP. Erros de campo, JSON malformado, mídia não suportada, autenticação e autorização usam Problem Details controlados. A URL `/error` continua protegida em acesso direto; somente o despacho interno `ERROR` é permitido. O filtro JWT fica exclusivamente na cadeia Spring Security.

Verificação estrutural dos oito planos/resumos passou, incluindo requisitos, arquivos e referências de testes. `mvnw.cmd clean verify` recompilou o projeto, executou **64 testes com zero falhas, erros ou ignorados** e gerou o JAR Spring Boot. Nove testes permanentes foram acrescentados após a auditoria: três de claims JWT, dois de validação cadastral e quatro de HTTP/registro do filtro.

Os testes de integração aplicaram as cinco migrações em MySQL 8.4.11 via Testcontainers. Os avisos conhecidos de compatibilidade declarada do Flyway e encerramento de pools Hikari não impediram migração, validação ou execução. Não houve medição percentual de cobertura, auditoria de infraestrutura de produção, frontend ou SMTP, que continuam fora do escopo desta fase. Rate limiting permanece obrigatório antes de exposição à internet.

A Fase 2 está tecnicamente concluída e verificada localmente. Push, PR e merge continuam como autorizações independentes.
