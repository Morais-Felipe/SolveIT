# Evidências da auditoria de 2026-09-10

Commit de aplicação auditado: `ee8c6b9`. Os arquivos desta pasta são diagnósticos de revisão, não integram a aplicação nem sua suíte permanente.

- `artifact-checks.json`: resultado estrutural dos oito planos/resumos e lista do escopo.
- `check-artifacts.cjs`: reproduz essa conferência com a instalação local GSD em `.codex/gsd-core`. Executar da raiz: `node .planning/phases/02-autentica-o/audit/check-artifacts.cjs`. Usa caminhos relativos para o verificador de resumos no Windows.
- `Phase2AuditProbeTest.java`: sete cenários de caracterização por HTTP real e MySQL 8.4.11. As asserções descrevem o comportamento observado no commit auditado, inclusive defeitos. Após correções, devem falhar onde o comportamento mudou; não devem substituir testes de regressão do comportamento correto.
- `observed-results.txt`: saída selecionada do diagnóstico, sem corpos de resposta, tokens ou logs completos.
- `regression-results.txt`: resumo do `mvnw.cmd clean verify` após remover o diagnóstico de `src/test`.

Para reproduzir o diagnóstico no commit auditado, com Docker e Java disponíveis, copiar o arquivo para `solveit-api/src/test/java/com/solveit/controller/Phase2AuditProbeTest.java`, executar `mvnw.cmd -Dtest=Phase2AuditProbeTest test` dentro de `solveit-api` e remover somente essa cópia ao terminar. Usa configuração/chave sintética do perfil de teste. BCrypt 4 reduz o custo exclusivamente nesse diagnóstico; não altera o padrão 12 de produção. Não executar contra banco real.

As chamadas Maven redirecionadas pelo PowerShell reportaram saída nativa da JVM como `NativeCommandError`/exit 1 na ferramenta. A classificação do build foi conferida no `BUILD SUCCESS` e nos XML Surefire: diagnóstico 7/0/0/0; suíte limpa 55/0/0/0. O JAR foi criado, sem classes Test/Probe em `BOOT-INF/classes`. O diretório de migrações inclui sua entrada de diretório mais as cinco migrações V1–V5; isso não representa uma sexta migração.
