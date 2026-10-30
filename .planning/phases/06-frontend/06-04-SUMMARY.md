---
phase: 06-frontend
plan: "04"
subsystem: web
tags: [react, typescript, react-router, axios, vitest, spa, concurrency]

requires:
  - phase: 06-frontend
    plan: "03"
    provides: detalhe do chamado, acoes.ts, tratamento de 409
provides:
  - Tela /fila do atendente com abas Fila e Meus chamados guardadas na URL
  - Assumir na fila e no detalhe; atribuir pelo ADMIN no detalhe
  - Regras ASSUMIR e ATRIBUIR em acoes.ts
  - Componente Paginacao compartilhado
affects: [06-05]

ticket:
  number: 28
  branch: ticket/28-frontend-fila
  pr: ver STATE.md

actuals:
  tasks: 3
  commits: 7
  executor: Claude Code (sem GSD)

tech-stack:
  added: []
  patterns:
    - Abas acessíveis (tablist/tab/aria-selected) que só trocam URL e lista, sem recarregar a página
    - Lista de atendentes carregada sob demanda ao abrir o painel e percorrendo todas as páginas
    - Mensagem de conflito única para 409 na fila (versão é checada antes do estado no servidor)

key-files:
  created:
    - solveit-web/src/pages/FilaPage.tsx
    - solveit-web/src/pages/fila.test.tsx
    - solveit-web/src/components/Paginacao.tsx
  modified:
    - solveit-web/src/lib/acoes.ts
    - solveit-web/src/lib/acoes.test.ts
    - solveit-web/src/lib/chamados.ts
    - solveit-web/src/pages/ChamadoDetalhePage.tsx
    - solveit-web/src/pages/detalhe.test.tsx
    - solveit-web/src/pages/ChamadosPage.tsx
    - solveit-web/src/App.tsx

key-decisions:
  - "D-1 aprovada após esclarecimento: abas na URL; trocar de aba só atualiza a lista (SPA), comprovado no navegador sem recarga."
  - "D-2 aprovada: fila por ordem de chegada (criadoEm,asc); ordenar por prioridade exige mudança na API (v2)."
  - "D-3 aprovada: Assumir na fila e no detalhe; após assumir abre o detalhe com aviso."
  - "D-4 ajustada: o texto planejado ('Outro atendente assumiu este chamado primeiro.') virou 'Este chamado mudou ou já foi assumido por outro atendente. A fila foi atualizada.', porque o servidor responde STALE_RESOURCE tanto na corrida quanto em edição do chamado."
  - "D-5 aprovada: Atribuir (ADMIN) no detalhe com atendentes ativos de todas as páginas de /usuarios."
  - "D-6 aprovada: Assumir com um clique."
  - "No detalhe, todo 409 passa a recarregar o chamado (antes só STALE_RESOURCE)."

requirements-completed: [WEB-04]
requirements-partial: []

coverage:
  - id: D1
    description: "ASSUMIR/ATRIBUIR na tabela de ações (16 cenários) e casos negativos."
    requirement: "WEB-04"
    verification:
      - kind: unit
        ref: "solveit-web/src/lib/acoes.test.ts"
        status: pass
  - id: D2
    description: "Fila (parâmetros, assumir, corrida com STALE_RESOURCE, aba Meus, aba pela URL) e detalhe (assumir, atribuir só a ativos, atendente obrigatório)."
    requirement: "WEB-04"
    verification:
      - kind: component
        ref: "solveit-web/src/pages/fila.test.tsx, solveit-web/src/pages/detalhe.test.tsx"
        status: pass
      - kind: manual
        ref: "Dois atendentes, corrida real, assumir/comentar/resolver e atribuição pelo ADMIN contra a API em Docker; 375 px"
        status: pass
    human_judgment: false
---

# Plano 06-04 — Fila do atendente

## Resultado

O atendente entra direto em `/fila`: vê os chamados abertos sem responsável do mais antigo para o mais novo, assume um com um clique e cai no detalhe, onde comenta e resolve. A aba "Meus chamados" lista os atribuídos a ele (Em andamento por padrão, com filtro). O ADMIN atribui um chamado da fila a um atendente ativo, com observação que vai para o histórico.

## Bugs e ajustes encontrados

1. **Mensagem da corrida errada** (teste manual com dois atendentes): o servidor checa a versão antes do estado, então quem perde a corrida recebe `STALE_RESOURCE`, nunca `INVALID_STATE` — os testes simulavam o código errado. Teste corrigido para o código real e mensagem única que cobre corrida e edição.
2. **Asserção frágil no teste da aba pela URL:** verificava a ausência da chave `status`, mas o valor `undefined` é descartado pelo Axios; o teste agora confere o valor.
3. **Paginação duplicada:** extraída para `components/Paginacao.tsx`.

## Verificação

- `npm test`: 59/59 (7 arquivos); `npm run typecheck` e `npm run build` limpos; `npm run lint` só com o aviso pré-existente do shadcn.
- **Ponta a ponta** com MySQL 8.4 e API em containers Docker descartáveis (ADMIN de teste por `docs/PRIMEIRO-ADMIN.md`; atendentes Ari e Bia criados pelo ADMIN; três chamados):
  - Login do atendente leva a `/fila`; requisição `status=ABERTO&sort=criadoEm,asc`; ordem #1, #2, #3.
  - **Corrida real** (duas vezes): Bia assumiu pela API enquanto a fila do Ari mostrava o chamado; o clique do Ari recebeu 409, a fila recarregou sem o chamado e mostrou o aviso.
  - Ari assumiu o #3 (200), caiu no detalhe com aviso, comentou e resolveu.
  - Abas: marcador em `window` sobreviveu à troca de aba e de filtro (sem recarga); URL `?aba=meus&status=TODOS`; requisições com `atendenteId`.
  - ADMIN: painel Atribuir listou só Ari e Bia (carregado ao abrir); atribuiu o #4 à Bia com observação (200); histórico registrou.
  - 375 px sem rolagem horizontal. Containers, rede e credenciais removidos ao final.
