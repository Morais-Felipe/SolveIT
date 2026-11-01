# Estudo — Plano 07-02: testes de ponta a ponta e evidências de qualidade

Notas guardadas na aprovação do plano (2026-10-31) para estudar depois. O plano completo está em `.planning/phases/07-polimento-qualidade/07-02-PLAN.md`.

## 1. Por que um teste E2E, se já existem 241 testes?

Até a Fase 7, o SolveIT tinha dois tipos de prova:

| Tipo | O que roda de verdade | O que é simulado |
|---|---|---|
| Integração do back-end (MockMvc + Testcontainers) | Spring, segurança, JPA, MySQL 8.4 | O navegador e o front-end |
| Componente do front-end (Vitest + Testing Library) | React e as telas | O navegador (jsdom) e a API (mock) |

Nenhum dos dois prova que as peças funcionam **juntas**. O teste **E2E** (*end-to-end*, "de ponta a ponta") abre um navegador real, clica como uma pessoa e atravessa tudo: nginx → API → MySQL → Mailpit. O **Playwright** é a ferramenta que controla esse navegador.

Trade-off: E2E é o teste mais fiel e também o mais lento e frágil. Por isso existem poucos (uma jornada por perfil), e as regras finas continuam nos testes de integração.

## 2. As decisões do plano

| | Decisão | Por quê |
|---|---|---|
| D-1 | Playwright em `solveit-web/e2e/`, só com o Chromium | Reaproveita Node, lint e tipos do front-end; `npm test` continua só com os testes de componente. |
| D-2 | Ambiente isolado e descartável: compose `solveit-e2e` nas portas 8180/8125, apagado com `down -v` | O primeiro ADMIN exige banco sem ADMIN; o E2E precisa de banco vazio sem apagar os dados de desenvolvimento. |
| D-3 | Primeiro ADMIN pelo mesmo SQL protegido de `docs/PRIMEIRO-ADMIN.md` | Não cria atalho de segurança na aplicação e testa o próprio procedimento. |
| D-4 | E2E também no GitHub Actions a cada PR | Prova visível de QUAL-05; custo de ~5–8 min por PR. |
| D-5 | Três jornadas (ADMIN, solicitante, atendente) em desktop e celular, mais e-mail no Mailpit e 403 direto na API | O 403 pela API prova que a autorização não depende de a tela esconder botões (WEB-07). |
| D-6 | Menu do celular quebra linha em vez de rolar na horizontal | Resolve o item "Categorias" escondido com uma classe CSS; hambúrguer seria exagero para 4 links. |
| D-7 | Mapa de evidências: requisito → teste que o prova, e matriz endpoint × perfil | Cumpre a revisão de escopo usando os testes existentes; lacunas de segurança ganham teste. |

## 3. Ponto de atenção: "unitário" no QUAL-01

O QUAL-01 fala em testes **unitários**, mas boa parte das regras é provada por testes de **integração** com MySQL real. Foi escolha deliberada da revisão de escopo de 2026-10-30: um teste de integração prova a regra *e* a autorização, a transação e o SQL de uma vez. O mapa de evidências diz isso abertamente, em vez de criar testes unitários duplicados só para cumprir a palavra.

## 4. Conceitos para aprofundar

- **Projetos do Playwright:** a mesma suíte executada em configurações diferentes (aqui, desktop 1280 px e celular Pixel 7). Cada projeto é como uma "rodada" com outro navegador ou tela.
- **Testes instáveis (*flaky*):** passam numa execução e falham na seguinte sem mudança de código, quase sempre por timing (o teste age antes de a tela estar pronta). Defesa: esperar pelo que o usuário vê (`getByRole`, `toBeVisible`), nunca `sleep` fixo.
- **`docker compose -p <nome>`:** o *project name* prefixa containers, redes e volumes. Dois projetos com o mesmo `docker-compose.yml` não se enxergam, o que permite um ambiente de teste ao lado do de desenvolvimento, desde que as portas publicadas sejam diferentes.
- **Seletores acessíveis:** procurar elementos pelo papel e nome que um leitor de tela anunciaria (`getByRole('button', { name: 'Assumir' })`). O teste fica mais estável que seletores CSS e ainda acusa problemas de acessibilidade.
- **Trace do Playwright:** gravação de uma execução que falhou (DOM, rede, console, capturas a cada passo), aberta com `npx playwright show-trace`. É a principal ferramenta de diagnóstico de E2E.
