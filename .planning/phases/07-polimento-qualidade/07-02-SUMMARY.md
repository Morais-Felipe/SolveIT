---
phase: 07-polimento-qualidade
plan: "02"
subsystem: web + e2e + qualidade
tags: [playwright, e2e, docker-compose, responsividade, autorizacao, evidencias]

requires:
  - phase: 07-polimento-qualidade
    plan: "01"
    provides: docker-compose com API, web/nginx, MySQL e Mailpit
provides:
  - Jornadas E2E dos três perfis (Playwright) em desktop e celular, contra um compose isolado
  - Workflow E2E no GitHub Actions a cada PR
  - Menu do celular sem itens escondidos; estados vazios e de erro completados
  - Teste de autorização do ATENDENTE nas escritas de categoria
  - docs/EVIDENCIAS-QUALIDADE.md (mapa QUAL-01..03, QUAL-05, WEB-07 e matriz das 23 rotas)
affects: []

ticket:
  number: 31
  branch: ticket/31-e2e-qualidade
  pr: ver STATE.md

actuals:
  tasks: 5
  commits: 9
  executor: Claude Code (sem GSD)

tech-stack:
  added: ["@playwright/test 1.64.1 (Chromium)"]
  patterns:
    - Compose de teste isolado por nome de projeto (-p solveit-e2e) e portas próprias
    - Primeiro ADMIN não interativo com a condição "nenhum ADMIN ativo" dentro do UPDATE
    - Seletores acessíveis (getByRole/getByLabel) e esperas pelo que o usuário vê; zero retries
    - toBeInViewport({ ratio 1 }) para exigir item inteiro na tela
    - Verificação de rolagem horizontal em cada tela visitada
    - Mutation testing manual para provar que um teste de autorização detecta a regressão

key-files:
  created:
    - solveit-web/playwright.config.ts
    - solveit-web/tsconfig.e2e.json
    - solveit-web/e2e/ambiente.ts
    - solveit-web/e2e/global-setup.ts
    - solveit-web/e2e/global-teardown.ts
    - solveit-web/e2e/jornadas.e2e.ts
    - .github/workflows/e2e.yml
    - docs/EVIDENCIAS-QUALIDADE.md
    - docs/estudos/07-02-e2e-e-qualidade.md
  modified:
    - solveit-web/src/layout/AppLayout.tsx (menu quebra linha no celular)
    - solveit-web/src/pages/NovoChamadoPage.tsx (sem categoria ativa)
    - solveit-web/src/pages/ChamadoDetalhePage.tsx (Tentar novamente em falha passageira)
    - solveit-web/src/pages/chamados.test.tsx, detalhe.test.tsx
    - solveit-api/src/test/java/com/solveit/controller/CategoriaEdicaoIntegrationTest.java
    - solveit-web/vite.config.ts, tsconfig.json, package.json, .gitignore, .dockerignore
    - docker-compose.yml (SOLVEIT_MAILPIT_PORT)
    - README.md

key-decisions:
  - "D-1 a D-7 aprovadas sem alteração."
  - "Celular de referência: 360 px (Galaxy S8) em vez do Pixel 7 (412 px) do plano; ver Desvios."
  - "Segredos do E2E gerados na hora e passados pelo ambiente do processo, sem escrever .env (o plano falava em .env gerado)."
  - "QUAL-01 documentado com honestidade: regras puras têm teste unitário; o restante é provado por integração com MySQL real, por escolha da revisão de escopo."

requirements-completed: [QUAL-01, QUAL-02, QUAL-03, QUAL-05, WEB-06, WEB-07]
requirements-partial: []

coverage:
  - id: D1
    description: "Jornadas ADMIN, solicitante e atendente de ponta a ponta, com e-mail no Mailpit e 403 direto na API."
    requirement: "QUAL-05, WEB-07"
    verification:
      - kind: e2e
        ref: "solveit-web/e2e/jornadas.e2e.ts (desktop 1280 px e celular 360 px)"
        status: pass
  - id: D2
    description: "Menu inteiro e telas sem rolagem horizontal no celular; estados vazio/erro completos."
    requirement: "WEB-06, WEB-07"
    verification:
      - kind: e2e
        ref: "jornadas.e2e.ts (toBeInViewport ratio 1; scrollWidth <= innerWidth)"
        status: pass
      - kind: component
        ref: "chamados.test.tsx (sem categoria ativa); detalhe.test.tsx (nova tentativa)"
        status: pass
  - id: D3
    description: "Matriz de autorização das 23 rotas com referência a teste; lacuna do ATENDENTE coberta."
    requirement: "QUAL-01, QUAL-02, QUAL-03"
    verification:
      - kind: integration
        ref: "CategoriaEdicaoIntegrationTest.forbidsAtendenteFromEveryCategoryWrite"
        status: pass
      - kind: manual
        ref: "docs/EVIDENCIAS-QUALIDADE.md — 107 referências conferidas por script contra o código de teste"
        status: pass
    human_judgment: false
---

# Plano 07-02 — Jornadas E2E, telas no celular e evidências de qualidade

## Resultado

`npm run e2e` sobe um compose isolado (`solveit-e2e`), cria o primeiro ADMIN pelo SQL protegido de `docs/PRIMEIRO-ADMIN.md` e percorre as três jornadas num navegador real, em desktop e num celular de 360 px: o ADMIN promove um atendente e cria uma categoria; o solicitante se cadastra, abre chamado e comenta; o atendente assume e resolve, o solicitante recebe o e-mail no Mailpit e fecha. As jornadas também provam o 403 direto na API, o menu inteiro na tela e a ausência de rolagem horizontal. Ao final, nenhum container, volume ou rede do E2E sobra. O mesmo roda no GitHub Actions a cada PR.

A revisão da matriz de autorização achou uma lacuna (escritas de categoria nunca testadas com ATENDENTE), agora coberta. O mapa `docs/EVIDENCIAS-QUALIDADE.md` liga cada requisito de qualidade e cada rota ao teste que a prova. Com isso, o v1 tem 55 de 55 requisitos concluídos.

## Bugs e ajustes encontrados

1. **Menu do ADMIN no celular** escondia parte de "Categorias" numa faixa de rolagem sem indicação (77,8% visível em 360 px). Agora quebra linha (`c449bc3`).
2. **Teste que passava diante do defeito:** a primeira versão usava `toBeInViewport()` sem `ratio`, que aceita qualquer pedaço visível, e o Pixel 7 (412 px) é largo o suficiente para os quatro itens caberem. Uma sonda numa página estática confirmou que a asserção detecta corte por overflow; a correção foi exigir o item inteiro (`ratio: 1`) e usar 360 px. Só então o teste ficou vermelho antes da correção do menu e verde depois.
3. **Abrir chamado sem categoria ativa** mostrava uma lista vazia e falhava com "Escolha a categoria."; agora explica e desabilita o envio (`69aa8f2`).
4. **Detalhe do chamado** não oferecia nova tentativa em falha passageira (rede, 500); agora oferece, e continua sem oferecer em 403/404 (`69aa8f2`).
5. **Lacuna de autorização (QUAL-03):** escritas de categoria só eram testadas contra SOLICITANTE (`9f02fe6`). A mutação que abria **só** o controller ao ATENDENTE não derrubou o teste, porque o `CategoriaService` também checa o perfil (defesa em profundidade); abrindo controller **e** service, o teste falhou com `expected 403 but was 201`.
6. **Build da imagem web quebrou** ao excluir `e2e/` do contexto Docker: o `tsc -b` do build checa o `tsconfig.e2e.json`. `e2e/` voltou ao contexto (só texto; a imagem final recebe apenas `dist/`).
7. **Erros meus de ambiente, sem efeito no código:** um `cat > arquivo` sem entrada travou uma sonda até ser interrompida; e a primeira suíte completa em segundo plano não rodou (`./mvnw: No such file`, código 127) e foi refeita com o caminho absoluto.

## Desvios do plano

- **Celular de 360 px** (Galaxy S8) em vez do Pixel 7: com 412 px o defeito do menu não aparecia, então o teste não provaria nada. 360 px é a largura de muitos Androids comuns.
- **Sem `.env` gerado:** os segredos descartáveis ficam no ambiente do processo do Playwright, que o repassa ao teardown; nada é escrito em disco.
- **Correção do menu sem teste de componente:** o jsdom não calcula layout, então a prova é o E2E (`toBeInViewport({ ratio: 1 })`).

## Verificação

- `mvnw test`: **242 testes, 0 falhas** (241 + `forbidsAtendenteFromEveryCategoryWrite`), Testcontainers MySQL 8.4.
- Front-end: `npm test` **77 testes** (75 + 2), `typecheck` e `lint` limpos (o único aviso, em `components/ui/button.tsx`, é anterior e vem do shadcn).
- `npm run e2e`: **6/6 verdes em três execuções seguidas** (41,6 s, 39,5 s, 46,8 s com imagens em cache) e de novo após as verificações de rolagem; depois de cada execução, nenhum container, volume ou rede `solveit-e2e`.
- Revisão visual das capturas em 360 px (usuários, abrir chamado, fila, detalhe): sem lacunas além das corrigidas.
- `docs/EVIDENCIAS-QUALIDADE.md`: 107 referências `Classe.método` conferidas por script; todas existem.
- **Não verificado localmente:** o workflow `e2e.yml` só roda no GitHub; a primeira execução será no PR deste ticket.

## Pendências registradas

- `npm audit`: 6 vulnerabilidades (2 baixas, 4 moderadas) já existentes na cadeia da CLI do shadcn (`ts-morph` → `minimatch`), fora do build; a correção sugerida rebaixa o shadcn para 1.0.0. Anotada no README.
