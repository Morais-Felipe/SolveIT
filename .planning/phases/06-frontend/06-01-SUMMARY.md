---
phase: 06-frontend
plan: "01"
subsystem: web
tags: [react, typescript, vite, tailwind-v4, shadcn-ui, react-router, axios, vitest, github-actions]

requires:
  - phase: 05-dashboard
    plan: "01"
    provides: API completa do v1
provides:
  - Projeto solveit-web (React 19 + TypeScript + Vite + Tailwind v4 + shadcn/ui)
  - Cliente HTTP único com token e normalização de Problem Details
  - Autenticação (login, cadastro, restauração de sessão, saída) e layout por perfil
  - CI do front-end
affects: [06-02, 06-03, 06-04, 06-05]

ticket:
  number: 25
  branch: ticket/25-frontend-base
  pr: ver STATE.md

actuals:
  tasks: 3
  commits: 5
  executor: Claude Code (sem GSD)

tech-stack:
  added:
    - "react 19.3, react-dom 19.3, vite 8.3, typescript 7.0 (versão do template oficial), oxlint (lint do template)"
    - "tailwindcss 4.3 + @tailwindcss/vite, shadcn/ui CLI 4 (base-nova, Base UI, pacote cn oficial)"
    - "react-router-dom 7.18, axios 1.21"
    - "vitest 5, jsdom, @testing-library/react, user-event, jest-dom"
  patterns:
    - AuthContext separado em contexto, provider e hook (fast refresh)
    - Guardas de rota apenas para navegação; a API continua sendo a autoridade
    - Erros da API exibidos por campo e em alerta geral

key-files:
  created:
    - solveit-web/src/lib/api.ts
    - solveit-web/src/lib/types.ts
    - solveit-web/src/auth/AuthProvider.tsx
    - solveit-web/src/routes/guards.tsx
    - solveit-web/src/layout/AppLayout.tsx
    - solveit-web/src/pages/LoginPage.tsx
    - solveit-web/src/pages/CadastroPage.tsx
    - solveit-web/src/auth/auth-flow.test.tsx
    - .github/workflows/frontend-ci.yml
  modified:
    - README.md

key-decisions:
  - "TypeScript escolhido pelo responsável antes do scaffolding; shadcn/ui em vez de MUI por ser construído sobre o Tailwind fixado na stack."
  - "D-1 aprovada: token no localStorage, validado com /auth/me ao abrir; 401 encerra a sessão. Cookie httpOnly fica para v2."
  - "D-2 aprovada: proxy do Vite para /api; back-end sem CORS."
  - "D-3 aprovada: pasta solveit-web/ na raiz."
  - "Versões do template oficial (TypeScript 7, oxlint) mantidas em vez de forçar as do plano."
  - "Pacote npm cn verificado: oficial (github.com/shadcn-ui/cn), usado pelo shadcn no lugar de clsx + tailwind-merge."

requirements-completed: [WEB-01]
requirements-partial: []

coverage:
  - id: D1
    description: "Login, credencial inválida, cadastro com senha curta e e-mail duplicado, rota protegida, restauração, token recusado, saída e tela de outro perfil."
    requirement: "WEB-01"
    verification:
      - kind: component
        ref: "solveit-web/src/auth/auth-flow.test.tsx"
        status: pass
      - kind: unit
        ref: "solveit-web/src/lib/api.test.ts"
        status: pass
      - kind: manual
        ref: "Navegador: tela de login em desktop e celular; API desligada mostra 'O servidor não respondeu'."
        status: pass
    human_judgment: false
---

# Plano 06-01 — Base do front-end

## Resultado

Aplicação `solveit-web` com tema escuro e laranja, login, cadastro, sessão restaurada ao recarregar, saída e menu lateral por perfil (telas das próximas etapas como provisórias).

## Bugs e ajustes encontrados

1. **Testes vazando entre si:** sem os globais do Vitest, o Testing Library não desmonta a tela; `cleanup` registrado no setup.
2. **Acessibilidade:** o título do card era uma `div`; as páginas agora têm `h1` (encontrado pelos testes por papel).
3. **Mensagem vaga com API fora do ar:** o proxy devolve 500 sem corpo; 5xx sem Problem Details agora mostra "O servidor não respondeu. Tente novamente em instantes." (encontrado na verificação manual).

## Verificação

- `npm test`: 13/13; `npm run typecheck`, `npm run lint` (1 aviso em componente gerado pelo shadcn) e `npm run build` limpos.
- Verificação manual no navegador em 1280×800 e 375×812.
- Fluxo completo contra a API real ainda não verificado manualmente (exige MySQL e API locais); coberto por testes de componente com a API simulada.
