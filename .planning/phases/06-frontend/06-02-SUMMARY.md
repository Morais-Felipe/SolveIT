---
phase: 06-frontend
plan: "02"
subsystem: web
tags: [react, typescript, react-router, axios, vitest]

requires:
  - phase: 06-frontend
    plan: "01"
    provides: cliente HTTP, autenticação e layout por perfil
provides:
  - Lista de chamados com filtros na URL, paginação e estados de carregamento, vazio e erro
  - Formulário de abertura de chamado com erros por campo
  - Módulo src/lib/chamados.ts e tipos de chamado, categoria e página
affects: [06-03, 06-04, 06-05]

ticket:
  number: 26
  branch: ticket/26-frontend-solicitante
  pr: ver STATE.md

actuals:
  tasks: 3
  commits: 4
  executor: Claude Code (sem GSD)

tech-stack:
  added: []
  patterns:
    - Filtros e página guardados na URL (useSearchParams) e validados antes de ir para a API
    - Busca com AbortController; estado "carregando" derivado de uma chave dos filtros (sem setState síncrono no efeito)
    - <select> nativo com visual do Input (CampoSelecao) em vez do Select flutuante do shadcn
    - Tabela em telas largas, cartões no celular

key-files:
  created:
    - solveit-web/src/lib/chamados.ts
    - solveit-web/src/components/CampoSelecao.tsx
    - solveit-web/src/components/Selos.tsx
    - solveit-web/src/pages/ChamadosPage.tsx
    - solveit-web/src/pages/NovoChamadoPage.tsx
    - solveit-web/src/pages/chamados.test.tsx
  modified:
    - solveit-web/src/lib/types.ts
    - solveit-web/src/App.tsx
    - solveit-web/src/auth/auth-flow.test.tsx

key-decisions:
  - "D-1 aprovada: filtros e página na URL."
  - "D-2 aprovada: sem biblioteca de cache; useEffect + AbortController."
  - "D-3 aprovada: após abrir, volta para /chamados com aviso; itens ainda não são links (detalhe no 06-03)."
  - "D-4 aprovada: /chamados serve a todos os perfis; botão de abertura só para SOLICITANTE."
  - "Desvio menor do plano: <select> e <textarea> nativos estilizados em vez dos componentes shadcn Select/Textarea/Table/Badge — mais acessíveis no celular e sem dependência nova."
  - "Botão do cabeçalho chama-se 'Novo chamado' para não duplicar o item 'Abrir chamado' do menu."

requirements-completed: [WEB-02]
requirements-partial: []

coverage:
  - id: D1
    description: "Lista com status/prioridade, filtro enviado e página reiniciada, paginação, vazio sem filtro, vazio com filtro, erro com tentar novamente."
    requirement: "WEB-02"
    verification:
      - kind: component
        ref: "solveit-web/src/pages/chamados.test.tsx"
        status: pass
  - id: D2
    description: "Abertura envia exatamente titulo/descricao/prioridade/categoriaId, aviso de sucesso, erro da API no campo, obrigatórios bloqueiam o envio."
    requirement: "WEB-02"
    verification:
      - kind: component
        ref: "solveit-web/src/pages/chamados.test.tsx"
        status: pass
      - kind: manual
        ref: "Fluxo contra a API real e visual em 375 px"
        status: pending
    human_judgment: false
---

# Plano 06-02 — Lista e abertura de chamados

## Resultado

O solicitante vê "Meus chamados" com filtros por status, prioridade e categoria (preservados na URL), paginação de 10 itens e abre um chamado por um formulário que mostra os erros da API no campo correspondente.

## Bugs e ajustes encontrados

1. **Testes antigos com simulação genérica demais:** `respostaMe` devolvia o usuário para qualquer GET; com a lista real, `/categorias` recebia um usuário e a tela quebrava. A simulação agora responde por URL, e o título esperado passou a ser "Meus chamados".
2. **Nome duplicado:** o menu já tinha o link "Abrir chamado"; o botão da página virou "Novo chamado" (encontrado pelo teste por papel).
3. **Lint `set-state-in-effect`:** o "carregando" passou a ser derivado da chave dos filtros, em vez de `setState` no início do efeito.

## Verificação

- `npm test`: 22/22; `npm run typecheck` e `npm run build` limpos; `npm run lint` com o mesmo aviso pré-existente do componente gerado pelo shadcn.
- **Pendente:** verificação manual contra a API real (exige MySQL e API locais, indisponíveis nesta máquina durante a execução).
