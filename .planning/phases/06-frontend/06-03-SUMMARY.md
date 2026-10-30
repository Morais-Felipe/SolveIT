---
phase: 06-frontend
plan: "03"
subsystem: web
tags: [react, typescript, react-router, axios, vitest, optimistic-locking]

requires:
  - phase: 06-frontend
    plan: "02"
    provides: lista de chamados, módulo chamados.ts, selos de status e prioridade
provides:
  - Página /chamados/:id com dados, descrição, comentários e histórico de status
  - Ações Resolver, Fechar, Cancelar e Editar conforme estado e perfil, com confirmação e observação
  - Regras de ações em src/lib/acoes.ts espelhando ChamadoTransitionPolicy
  - Tratamento de conflito de versão (409 STALE_RESOURCE) com recarga
  - Leitura das datas da API como UTC (lerDataDaApi)
affects: [06-04, 06-05]

ticket:
  number: 27
  branch: ticket/27-frontend-detalhe
  pr: ver STATE.md

actuals:
  tasks: 3
  commits: 8
  executor: Claude Code (sem GSD)

tech-stack:
  added: []
  patterns:
    - Regras de UI derivadas de uma função pura testada como tabela (it.each)
    - Concorrência otimista na UI: toda escrita envia versaoEsperada; 409 recarrega e avisa
    - Recarga sem "piscar": dados anteriores ficam na tela enquanto a nova busca não chega
    - Layout de duas colunas que reordena no celular com display:contents + order
    - CampoAreaTexto reutilizável (rótulo, erro e contador)

key-files:
  created:
    - solveit-web/src/lib/acoes.ts
    - solveit-web/src/lib/acoes.test.ts
    - solveit-web/src/lib/datas.test.ts
    - solveit-web/src/components/CampoAreaTexto.tsx
    - solveit-web/src/pages/ChamadoDetalhePage.tsx
    - solveit-web/src/pages/detalhe.test.tsx
  modified:
    - solveit-web/src/lib/types.ts
    - solveit-web/src/lib/chamados.ts
    - solveit-web/src/pages/ChamadosPage.tsx
    - solveit-web/src/pages/NovoChamadoPage.tsx
    - solveit-web/src/App.tsx

key-decisions:
  - "D-1 aprovada: regras de ações em acoes.ts, testadas como tabela; o servidor continua sendo a autoridade."
  - "D-2 aprovada: Assumir e atribuir ficam no 06-04 (inclusive a atribuição feita pelo ADMIN)."
  - "D-3 aprovada: 409 STALE_RESOURCE recarrega e avisa; nada é reenviado automaticamente."
  - "D-4 aprovada: Resolver, Fechar e Cancelar pedem confirmação com observação opcional (até 500)."
  - "D-5 aprovada: duas colunas no desktop; no celular as ações ficam logo abaixo do título."
  - "D-6 aprovada: 403 e 404 mostram a mesma mensagem."
  - "Datas da API (LocalDateTime em UTC, sem fuso) passam a ser lidas como UTC e exibidas no fuso de quem vê."

requirements-completed: [WEB-03]
requirements-partial: []

coverage:
  - id: D1
    description: "Ações por perfil, status e relação (13 cenários + atendente que é dono)."
    requirement: "WEB-03"
    verification:
      - kind: unit
        ref: "solveit-web/src/lib/acoes.test.ts"
        status: pass
  - id: D2
    description: "Detalhe, acesso negado neutro, link da lista, comentar, conversa encerrada, fechar com versão e observação, conflito 409, Resolver só para o responsável, edição."
    requirement: "WEB-03"
    verification:
      - kind: component
        ref: "solveit-web/src/pages/detalhe.test.tsx"
        status: pass
      - kind: unit
        ref: "solveit-web/src/lib/datas.test.ts (rodado também com TZ=UTC e TZ=America/Sao_Paulo)"
        status: pass
      - kind: manual
        ref: "Ciclo completo contra a API real em Docker, desktop e 375 px"
        status: pass
    human_judgment: false
---

# Plano 06-03 — Detalhe do chamado

## Resultado

Qualquer perfil com acesso abre `/chamados/:id` (os títulos da lista viraram links) e vê dados, descrição, comentários e histórico. Os botões seguem a tabela de transições do servidor: o solicitante edita e cancela enquanto o chamado está aberto e fecha quando está resolvido; o atendente responsável resolve; o ADMIN edita, resolve e cancela em andamento. Comentários ficam bloqueados em Fechado e Cancelado.

## Bugs e ajustes encontrados

1. **Horários 3 horas adiantados** (teste manual): a API envia `LocalDateTime` em UTC sem fuso e o navegador lia como hora local. `lerDataDaApi` acrescenta `Z` quando não há fuso; corrige também a data da lista do 06-02 perto da meia-noite.
2. **Ações no fim da página no celular** (teste manual, contrariava D-5): colunas viram `display: contents` abaixo de `lg` e `order` põe as ações logo após o título.
3. **Textarea repetida:** extraída para `CampoAreaTexto`, reaproveitada no formulário do 06-02.

## Verificação

- `npm test`: 49/49 (6 arquivos); `npm run typecheck` e `npm run build` limpos; `npm run lint` só com o aviso pré-existente do shadcn.
- **Ponta a ponta** com MySQL 8.4 e API em containers Docker descartáveis (ADMIN de teste promovido por `docs/PRIMEIRO-ADMIN.md`; atendente criado pelo ADMIN via API):
  - Solicitante: lista → detalhe pelo link; comentou (201); editou o título (PUT 200); **conflito real**: o chamado foi alterado pela API em paralelo, o cancelamento pela tela recebeu 409, a tela recarregou e avisou; confirmou de novo e cancelou com observação (200); conversa encerrada.
  - Atendente responsável: só "Resolver" visível; chamado de outra pessoa fora da fila → 403 nos três recursos e mensagem neutra; comentou e resolveu com observação.
  - Solicitante, em 375 px: "Fechar chamado" logo abaixo do título; fechou (200). Histórico completo Aberto → Em andamento → Resolvido → Fechado com autores, horários locais e observações. Sem rolagem horizontal; duas colunas mantidas em 1280 px.
  - Containers, rede e credenciais removidos ao final.
