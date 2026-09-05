---
status: resolved
trigger: "gsd-context-monitor não exibe mensagens e o Codex informa hook failed / invalid stop hook JSON output"
created: 2026-09-04
updated: 2026-09-04
---

# Debug: GSD context monitor hook

## Symptoms

- Expected: o hook deve encerrar silenciosamente em eventos que não aceitam injeção e emitir JSON válido quando houver aviso de contexto.
- Actual: o Codex mostra `hook failed` e `hook returned invalid stop hook JSON output` ao final das respostas.
- Reproduction: executar `.codex/hooks/gsd-context-monitor.cmd` com um payload de evento `Stop`.

## Current Focus

- hypothesis: confirmed — além de omitir `hooks/lib`, a integração registra o monitor em `Stop`, evento no qual o Codex 0.153.3 rejeitou a resposta do hook mesmo após os helpers serem restaurados.
- test: completed — nova sessão carregou a configuração sem `Stop`, manteve `PostToolUse` e não apresentou falha na inicialização.
- expecting: met — hooks carregados, ambiente persistido disponível e monitor preservado no evento compatível.
- next_action: none.

## Evidence

- timestamp: 2026-09-04T22:10:00-03:00
  observation: reprodução controlada encerrou com código 1 e `MODULE_NOT_FOUND` para `./lib/hook-exit.js`.
- timestamp: 2026-09-04T22:10:00-03:00
  observation: GSD confirmou identidade `@opengsd/gsd-core` versão 1.12.0 e runtime `codex`.
- timestamp: 2026-09-04T22:18:00-03:00
  observation: reinstalação limpa oficial 1.12.0 declarou sucesso, mas não criou `.codex/hooks/lib`; a falha permaneceu reproduzível.
- timestamp: 2026-09-04T22:20:00-03:00
  observation: pacote npm oficial contém dez arquivos em `hooks/lib`; cópia restaurada apresentou zero diferenças SHA-256 em relação à origem.
- timestamp: 2026-09-04T22:21:00-03:00
  observation: `Stop` retornou código 0 com stdout vazio e `PostToolUse` retornou código 0 com JSON válido e aviso de contexto.
- timestamp: 2026-09-04T22:22:37-03:00
  observation: suíte Maven executou 7 testes com Java 17 e MySQL 8.4 via Testcontainers, sem falhas ou erros.
- timestamp: 2026-09-04T22:25:20-03:00
  observation: uso real ainda exibiu `hook failed` após a restauração, embora o teste sintético de `Stop` retornasse código 0 e stdout vazio.
- timestamp: 2026-09-04T22:32:00-03:00
  observation: registro `Stop` removido de `.codex/hooks.json`; JSON validado e registros `SessionStart`/`PostToolUse` preservados.
- timestamp: 2026-09-04T22:40:00-03:00
  observation: após reiniciar o Codex, Java 17 e Docker estavam no ambiente, os dez helpers existiam, `Stop` permaneceu ausente, `PostToolUse` ativo e `$gsd-health` saudável.

## Eliminated

- hypothesis: o GSD inteiro não está instalado ou não reconhece o projeto.
  reason: consultas `runtime-identity`, `init.resume` e `init.debug` funcionaram.

## Resolution

- root_cause: o instalador local do GSD 1.12.0 para Codex omitiu `hooks/lib` e registrou o monitor de injeção também em `Stop`; a primeira falha causou `MODULE_NOT_FOUND` e a segunda continuou incompatível com o validador `Stop` do Codex 0.153.3.
- fix: restaurados os dez arquivos oficiais de `hooks/lib`, removido somente o registro `Stop` e mantido `PostToolUse`; `JAVA_HOME` do usuário também foi alinhado ao JDK 17.
- verification: testes sintéticos e de projeto aprovados; nova sessão confirmou carregamento da configuração corrigida e ambiente persistido.
- files_changed: `.planning/debug/resolved/gsd-context-monitor-hook.md`; configuração local ignorada `.codex/hooks/lib/*` e `.codex/hooks.json`; variável de usuário `JAVA_HOME`.
