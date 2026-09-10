---
phase: 02-autentica-o
verified: 2026-09-10
status: gaps_found
audited_commit: ee8c6b9
gaps:
  - "R1: verificar presença e coerência das claims temporais JWT"
  - "R2: validar formato e limites do cadastro antes da persistência"
  - "R3: preservar status e Problem Details nos erros de protocolo HTTP"
---

# Verificação global — Fase 2

**Resultado: gaps_found.** Oito planos implementados não equivalem a fase aprovada. Evidências, reprodução e correções propostas estão em [02-REVIEW.md](02-REVIEW.md).

| Requisito | Resultado no escopo da fase | Evidência/limite |
|---|---|---|
| AUTH-01 | Fluxo implementado; validação incompleta | Cadastro força SOLICITANTE; e-mail inválido persiste e excesso de tamanho gera 500 (R2). |
| AUTH-02 | Atendido na emissão atual | Login de ativo, BCrypt e token emitido com duração configurada; R1 trata a aceitação de tokens fora desse contrato. |
| AUTH-03 | Atendido | `/me` usa ID do principal e dados atuais, não identidade fornecida pelo cliente. |
| AUTH-04 | Atendido nos caminhos inspecionados/testados | BCrypt e ausência de senha/hash nas respostas e logs exercitados; não é garantia universal sobre ambientes externos. |
| AUTH-05 | Lacuna | Casos usuais são rejeitados; token corretamente assinado sem `exp` foi aceito (R1). |
| AUTH-06 | Fundação aprovada atendida | Perfil e relação persistida verificados no servidor em probes exclusivos de teste; operações futuras exigem suas próprias regras e testes. |

Decisões D-01–D-05: fluxos e senha coerentes, com lacuna de validação de entrada. D-06/D-10: emissão coerente, verificação temporal incompleta. D-07–D-09 e D-11–D-15: coerentes no escopo implementado. D-16–D-18: contrato preservado nos erros tratados, mas incompleto nos erros HTTP de R3. D-19: uniformidade de respostas de login testada; não se afirma resistência a todos os canais laterais de tempo.

Verificação estrutural dos oito planos/resumos passou, incluindo referências e metadados de cobertura. Build limpo: 55 testes passaram; empacotamento concluído. Diagnóstico adicional: sete cenários executados, seis reproduções de comportamento incorreto e um controle positivo. Não houve medição percentual de cobertura de código.

O checklist de requisitos anterior registra execução dos planos; este relatório registra as lacunas descobertas posteriormente e impede interpretar aquele checklist como aceite global. R4 exige sincronização dos documentos de entrada; R5 é melhoria de robustez. A fase permanece em verificação até correção e revalidação. Nenhuma aprovação de push, PR ou merge foi inferida.
