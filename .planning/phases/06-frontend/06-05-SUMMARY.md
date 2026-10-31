---
phase: 06-frontend
plan: "05"
subsystem: web + api
tags: [react, typescript, react-router, axios, vitest, spring-security, optimistic-locking]

requires:
  - phase: 05-dashboard
    plan: "01"
    provides: GET /api/dashboard/resumo no escopo do perfil
  - phase: 06-frontend
    plan: "04"
    provides: padrão de lista com estados, abas na URL, Paginacao
provides:
  - Dashboard (ADMIN e ATENDENTE) em cards e tabelas, com cards que abrem a lista filtrada
  - Administração de usuários (criar, editar dados, perfil e situação)
  - Administração de categorias (Ativas/Todas, criar, editar, ativar/desativar)
  - "GET /api/categorias?incluirInativas=true (só ADMIN; 403 para os demais)"
affects: [07]

ticket:
  number: 29
  branch: ticket/29-frontend-admin
  pr: ver STATE.md

actuals:
  tasks: 4
  commits: 6
  executor: Claude Code (sem GSD)

tech-stack:
  added: []
  patterns:
    - Um painel aberto por vez por tela (criar, editar ou operação), ancorado na linha do registro
    - "409 STALE_RESOURCE fecha o painel e recarrega a lista; nunca reenvia (D-6)"
    - Erros de unicidade (e-mail, nome de categoria) mostrados no próprio campo
    - Autorização condicional a um parâmetro decidida no service, não por anotação no controller

key-files:
  created:
    - solveit-web/src/lib/admin.ts
    - solveit-web/src/pages/DashboardPage.tsx
    - solveit-web/src/pages/UsuariosPage.tsx
    - solveit-web/src/pages/CategoriasPage.tsx
    - solveit-web/src/pages/dashboard.test.tsx
    - solveit-web/src/pages/usuarios.test.tsx
    - solveit-web/src/pages/categorias.test.tsx
  modified:
    - solveit-api/src/main/java/com/solveit/controller/CategoriaController.java
    - solveit-api/src/main/java/com/solveit/service/CategoriaService.java
    - solveit-api/src/test/java/com/solveit/controller/CategoriaIntegrationTest.java
    - solveit-web/src/lib/types.ts
    - solveit-web/src/App.tsx
    - solveit-web/src/auth/auth-flow.test.tsx
  renamed:
    - "solveit-web/src/pages/EmBreve.tsx → NaoEncontrado.tsx (placeholder EmBreve removido)"

key-decisions:
  - "D-1 aprovada: parâmetro opcional incluirInativas; sem ele, nada muda para o formulário de novo chamado."
  - "D-2 aprovada: quatro cards (Abertos, Em andamento, Resolvidos, Críticos abertos) e três tabelas; Carga por atendente só aparece com itens."
  - "D-3 aprovada com ajuste de forma: lista em cartões (não <table>) para caber em 375 px; mesma informação e paginação de 20."
  - "D-4 aprovada: aviso antes de o atendente deixar de atender e contagem de chamadosDevolvidos depois."
  - "D-5 aprovada: sem 'Perfil e situação' na própria linha do ADMIN logado."
  - "D-6 e D-7 aprovadas."

requirements-completed: [WEB-05]
requirements-partial: []

coverage:
  - id: D1
    description: "ADMIN lista ativas e inativas com incluirInativas; SOLICITANTE e ATENDENTE recebem 403; sem o parâmetro, só ativas."
    requirement: "WEB-05"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/controller/CategoriaIntegrationTest.java#listsInactiveCategoriesOnlyForAdminWhenRequested"
        status: pass
  - id: D2
    description: "Dashboard, usuários e categorias (criação, edição, operação, conflitos, D-5, abas)."
    requirement: "WEB-05"
    verification:
      - kind: component
        ref: "solveit-web/src/pages/dashboard.test.tsx, usuarios.test.tsx, categorias.test.tsx"
        status: pass
      - kind: manual
        ref: "Jornadas do ADMIN contra a API real com MySQL 8.4 em Docker; 375 px"
        status: pass
    human_judgment: false
---

# Plano 06-05 — Administrador: dashboard, usuários e categorias

## Resultado

As três telas que mostravam "Em breve" agora funcionam. O ADMIN acompanha a operação no dashboard e cada card abre a lista de chamados com o mesmo filtro, para conferir de onde saiu o número. Em Usuários, cria contas com qualquer perfil, edita dados pessoais e muda perfil/situação; ao tirar um atendente de serviço, a tela avisa antes e informa depois quantos chamados voltaram para a fila. Em Categorias, alterna entre Ativas e Todas, cria, edita e ativa/desativa. O atendente também vê o dashboard, no escopo que a API calcula para ele.

## Back-end

`GET /api/categorias` só listava ativas, inclusive para o ADMIN: uma categoria desativada sumia da tela e não podia ser reativada. Novo parâmetro `incluirInativas` (padrão `false`); com `true`, o service exige ADMIN e lança `AccessDeniedException` (403) para os demais. A decisão fica no service porque a rota continua aberta a qualquer autenticado: só a combinação parâmetro + perfil é proibida.

## Bugs e ajustes encontrados

1. **Mock desatualizado em `auth-flow.test.tsx`:** respondia qualquer URL com uma página vazia; com o dashboard real, o ADMIN quebrava em `resumo.totais`. O mock passou a responder `/dashboard/resumo` com um resumo vazio.
2. **Tipo do mock em `categorias.test.tsx`:** o parâmetro `config` era mais estreito que `AxiosRequestConfig`; corrigido com leitura explícita de `params`.
3. **Helper de linha nos testes:** o nome do ADMIN logado também aparece no cabeçalho; o helper agora procura dentro dos itens da lista, com `waitFor`.

## Observações para a Fase 7

- **Aviso de último ADMIN não é alcançável pela tela.** A API só aceita a operação de um ADMIN ativo; se o alvo é outro ADMIN ativo, existem ao menos dois, e a regra não dispara. Ela só disparava na autoalteração, que a D-5 escondeu. A proteção segue no servidor (testes do back-end) e a mensagem segue coberta no Vitest; o cenário planejado no ponta a ponta foi substituído por essa análise.
- **Menu do ADMIN no celular:** com quatro itens, a faixa de navegação (`overflow-x-auto`, do 06-01) exige rolar para ver "Categorias". Funciona; candidato a polimento em WEB-07.

## Verificação

- Back-end: `mvnw test` com 236 testes, 0 falhas (Testcontainers MySQL 8.4).
- Front-end: `npm test` 75/75 (10 arquivos); `npm run build` (inclui `tsc -b`) limpo; `npm run lint` só com o aviso pré-existente do shadcn.
- **Ponta a ponta** com MySQL 8.4 em container descartável e API local (e-mail desligado); ADMIN de teste por `docs/PRIMEIRO-ADMIN.md` (`admins_ativos = 0`, `linhas_alteradas = 1`):
  - Dashboard igual a `/dashboard/resumo`; card Abertos (1) abriu `/chamados?status=ABERTO` com exatamente o #2.
  - Ari (atendente, com o #1 em andamento) desativado pela tela: aviso exibido, "1 chamado voltou para a fila."; API confirmou #1 ABERTO sem atendente e histórico `EM_ANDAMENTO → ABERTO` por Ada Admin com "Retorno à fila por desativação do atendente".
  - Criação pela tela: e-mail repetido mostrou "E-mail já cadastrado" no campo; depois Bia Atendente criada.
  - **Conflito real:** painel "Editar dados" aberto, Sol alterada pela API por fora; o salvar recebeu `STALE_RESOURCE`, a tela avisou e recarregou mostrando a alteração de fora, sem sobrescrevê-la.
  - Categorias: Impressoras desativada sumiu da aba Ativas; solicitante passou a ver só Rede e recebeu 403 com `incluirInativas=true`; reativada pela aba Todas.
  - 375 px: `scrollWidth = 375` em dashboard, usuários (inclusive com painel aberto) e categorias. Container e credenciais removidos ao final.
