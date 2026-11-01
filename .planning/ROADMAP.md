# Roadmap: SolveIT

## Overview

O SolveIT será entregue em sete incrementos MVP na ordem definida pelo documento de origem: base reproduzível, identidade segura, ciclo completo de chamados, e-mail, indicadores, jornadas web e validação integrada de qualidade e segurança.

## Phases

- [x] **Phase 1: Base do Projeto** - A API inicia sobre um esquema MySQL reproduzível e validado. (completed 2026-09-04)
- [x] **Phase 2: Autenticação** - Usuários obtêm identidade segura e acesso autorizado. (completed 2026-09-10)
- [x] **Phase 3: Core de Chamados** - Os três perfis operam o ciclo auditável completo do chamado. (completed 2026-10-30)
- [x] **Phase 4: Notificações por E-mail** - Eventos confirmados comunicam os envolvidos corretos. (completed 2026-10-30)
- [x] **Phase 5: Dashboard** - Admins e atendentes consultam indicadores autorizados. (completed 2026-10-30)
- [x] **Phase 6: Front-end (React + Tailwind)** - Cada perfil conclui suas jornadas principais na web. (completed 2026-10-31)
- [ ] **Phase 7: Polimento e Qualidade** - O v1 comprova usabilidade, segurança e fluxos críticos.

## Revisão de escopo — 2026-10-30

Decisão do responsável, com o projeto público e usado como portfólio: concluir o v1 mais rápido sem perder o que diferencia o projeto. O back-end já demonstrou autorização por relação, auditoria atômica e concorrência provada com MySQL real; daqui em diante o rigor passa a ser **proporcional**.

| Área | Antes | Depois |
|---|---|---|
| 03-10 | Prévia do impacto com confirmação explícita antes de desativar/trocar perfil | Sem prévia: a operação é atômica, devolve os chamados `EM_ANDAMENTO` do atendente à fila com histórico e informa a quantidade na resposta; versão esperada do usuário e proteção do último ADMIN continuam |
| Fase 4 | Decisão entre outbox durável e envio após commit | Envio após o commit, fora da requisição, com falha registrada em log; testado contra SMTP falso. Outbox fica para v2 (OPER-01) |
| Fase 5 | Quatro rotas de indicadores | Uma rota `GET /api/dashboard/resumo` com todos os agregados, calculados no banco e no escopo do perfil |
| Fase 6 | Jornadas completas dos três perfis | Mantida: telas objetivas por perfil; gráficos somente se sobrar tempo (cards e tabelas bastam) |
| Fase 7 | Matriz completa de todos os endpoints e E2E de todas as jornadas | Handler genérico de 500, CI no GitHub Actions, `docker-compose` (app + MySQL + SMTP falso) e uma jornada E2E por perfil; a matriz de autorização é a dos testes já existentes |
| Processo | Prova de concorrência e de rollback em toda funcionalidade; três autorizações por ticket | Testes proporcionais (caminho feliz, permissões, erros); concorrência só para disputa nova; tickets maiores; push + PR + merge com uma única autorização final por ticket |

Nenhum requisito foi apagado; os que mudaram de forma estão anotados em `REQUIREMENTS.md`.

## Phase Details

### Phase 1: Base do Projeto

**Goal**: O desenvolvedor pode iniciar a API sobre um modelo relacional completo, versionado e coerente.
**Mode:** mvp
**Depends on**: Nothing (first phase)
**Requirements**: DATA-01, DATA-02, DATA-03, DATA-04
**Success Criteria** (what must be TRUE):

  1. O desenvolvedor inicia a API Spring Boot conectada ao MySQL com a configuração de desenvolvimento.
  2. Um banco vazio recebe todas as entidades, relações, restrições e índices ao executar as migrações Flyway.
  3. A aplicação valida a correspondência entre esquema Flyway e modelo JPA sem criar tabelas automaticamente.
  4. Dados persistidos respeitam enums, relações, nulabilidade e unicidade do domínio.

**Plans**: 5/5 planos sequenciais executados localmente

- [x] 01-01-PLAN.md
- [x] 01-02-PLAN.md
- [x] 01-03-PLAN.md
- [x] 01-04-PLAN.md
- [x] 01-05-PLAN.md

**Wave 1**

- [x] `01-01-PLAN.md` — Bootstrap Spring Boot/Maven e configuração MySQL

**Wave 2** *(bloqueada pela conclusão e integração da Wave 1)*

- [x] `01-02-PLAN.md` — Enums canônicos do domínio

**Wave 3** *(bloqueada pela conclusão e integração da Wave 2)*

- [x] `01-03-PLAN.md` — Entidades e mapeamentos JPA

**Wave 4** *(bloqueada pela conclusão e integração da Wave 3)*

- [x] `01-04-PLAN.md` — Repositórios Spring Data JPA

**Wave 5** *(bloqueada pela conclusão e integração da Wave 4)*

- [x] `01-05-PLAN.md` — Migrações Flyway e validação integrada em MySQL

### Phase 2: Autenticação

**Goal**: Usuários possuem identidade confiável e só acessam operações permitidas por perfil e relação com o recurso.
**Mode:** mvp
**Depends on**: Phase 1
**Requirements**: AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06
**Success Criteria** (what must be TRUE):

  1. Uma pessoa se cadastra e recebe somente o perfil SOLICITANTE, sem exposição da senha.
  2. Um usuário ativo entra, recebe um JWT temporário e consulta seus dados em `/api/auth/me`.
  3. Tokens ausentes, inválidos ou expirados e contas inativas são rejeitados nas rotas protegidas.
  4. Operações negam acesso quando perfil ou relação com o recurso não é permitido.
  5. Senhas ficam exclusivamente em hash BCrypt e não aparecem em respostas ou logs.

**Plans**: 8/8 planos sequenciais executados e fase verificada após auditoria/correções

- [x] 02-01-PLAN.md
- [x] 02-02-PLAN.md
- [x] 02-03-PLAN.md
- [x] 02-04-PLAN.md
- [x] 02-05-PLAN.md
- [x] 02-06-PLAN.md
- [x] 02-07-PLAN.md
- [x] 02-08-PLAN.md

### Phase 3: Core de Chamados

**Goal**: Solicitantes, atendentes e administradores conduzem o chamado pelo fluxo permitido com colaboração e auditoria íntegras.
**Mode:** mvp
**Depends on**: Phase 2
**Requirements**: ADMN-01, ADMN-02, ADMN-03, ADMN-04, ADMN-05, ADMN-06, TICK-01, TICK-02, TICK-03, TICK-04, TICK-05, TICK-06, TICK-07, TICK-08, TICK-09, TICK-10, TICK-11, TICK-12, COLL-01, COLL-02, COLL-03, COLL-04
**Success Criteria** (what must be TRUE):

  1. Um administrador gerencia usuários e categorias sem duplicidade ou exclusão física, e usuários autenticados consultam categorias ativas.
  2. Um solicitante abre, lista, filtra, consulta e, enquanto permitido, edita ou cancela seus chamados; cada perfil vê somente seu escopo.
  3. Perfis autorizados atribuem e percorrem `ABERTO → EM_ANDAMENTO → RESOLVIDO → FECHADO` ou cancelamentos permitidos, com erros claros para transições inválidas.
  4. Cada mudança confirmada cria exatamente um histórico na mesma transação e concorrência não sobrescreve o estado silenciosamente.
  5. Usuários autorizados adicionam e leem comentários imutáveis e consultam comentários e histórico cronologicamente.

**Plans**: 2/10 planos executados; os tickets restantes seguem a ordem sequencial aprovada

- [x] 03-01-PLAN.md
- [x] 03-02-PLAN.md
- [x] 03-03-PLAN.md
- [x] 03-04-PLAN.md
- [x] 03-05-PLAN.md
- [x] 03-06-PLAN.md
- [x] 03-07-PLAN.md
- [x] 03-08-PLAN.md
- [x] 03-09-PLAN.md
- [x] 03-10-PLAN.md

**Wave 1**

- [x] `03-01-PLAN.md` — Consultar e cadastrar categorias pela API protegida

**Wave 2** *(bloqueada pela conclusão e integração da Wave 1)*

- [x] `03-02-PLAN.md` — Editar e ativar/desativar categorias sem perder vínculos

**Wave 3** *(bloqueada pela conclusão e integração da Wave 2)*

- [x] `03-03-PLAN.md` — Administrar cadastro de usuários sem atalhos de perfil/status

**Wave 4** *(bloqueada pela conclusão e integração da Wave 3)*

- [x] `03-04-PLAN.md` — Abrir chamado e registrar sua origem atomicamente

**Wave 5** *(bloqueada pela conclusão e integração da Wave 4)*

- [x] `03-05-PLAN.md` — Consultar, filtrar e editar chamados no escopo correto

**Wave 6** *(bloqueada pela conclusão e integração da Wave 5)*

- [x] `03-06-PLAN.md` — Assumir e atribuir chamados com um único vencedor

**Wave 7** *(bloqueada pela conclusão e integração da Wave 6)*

- [x] `03-07-PLAN.md` — Resolver, fechar e cancelar pelo fluxo autorizado

**Wave 8** *(bloqueada pela conclusão e integração da Wave 7)*

- [x] `03-08-PLAN.md` — Comentar com acesso atual e concorrência segura

**Wave 9** *(bloqueada pela conclusão e integração da Wave 8)*

- [x] `03-09-PLAN.md` — Consultar comentários e histórico cronologicamente e provar imutabilidade

**Wave 10** *(bloqueada pela conclusão e integração da Wave 9)*

- [x] `03-10-PLAN.md` — Alterar atividade/perfil com devolução auditada e proteção do último ADMIN (prévia removida na revisão de 2026-10-30)

### Phase 4: Notificações por E-mail

**Goal**: Eventos confirmados de chamado notificam os destinatários corretos sem comprometer a operação principal.
**Mode:** mvp
**Depends on**: Phase 3
**Requirements**: MAIL-01, MAIL-02, MAIL-03, MAIL-04, MAIL-05, MAIL-06
**Success Criteria** (what must be TRUE):

  1. Administradores recebem e-mail ao abrir chamado e destaque urgente adicional quando ele é CRITICO.
  2. Atendente e solicitante recebem a atribuição, e o solicitante recebe cada atualização de status.
  3. Novo comentário notifica todos os envolvidos uma única vez por destinatário.
  4. Transações revertidas não enviam e-mail, e falha SMTP não desfaz mudança válida do chamado.

**Abordagem (revisão 2026-10-30)**: eventos publicados na transação e enviados depois do commit, fora da requisição; sem outbox. Estimativa: 1–2 planos.

Plans:

- [x] `04-01-PLAN.md` — Eventos de domínio, envio após commit e assíncrono, destinatários e mensagens (MAIL-01..06)

**Plans**: TBD

### Phase 5: Dashboard

**Goal**: Administradores e atendentes enxergam uma visão operacional confiável e autorizada dos chamados.
**Mode:** mvp
**Depends on**: Phase 4
**Requirements**: DASH-01, DASH-02, DASH-03, DASH-04, DASH-05
**Success Criteria** (what must be TRUE):

  1. Administradores e atendentes consultam totais ABERTO, EM_ANDAMENTO, RESOLVIDO e CRITICO aberto.
  2. Consultam quantidades por categoria, carga ativa por atendente e abertos por prioridade.
  3. Indicadores respeitam o escopo de acesso e reconciliam com listagens equivalentes.
  4. Agregados são calculados no banco sem carregar todos os chamados na aplicação.

**Abordagem (revisão 2026-10-30)**: uma única rota `GET /api/dashboard/resumo`. Estimativa: 1 plano.

**Plans**: 1 plan

Plans:

- [x] `05-01-PLAN.md` — Resumo do dashboard no escopo do perfil, calculado no banco e reconciliado com a lista (DASH-01..05)
**UI hint**: yes

### Phase 6: Front-end (React + Tailwind)

**Goal**: Cada perfil conclui suas jornadas principais em uma aplicação web única conectada à API segura.
**Mode:** mvp
**Depends on**: Phase 5
**Requirements**: WEB-01, WEB-02, WEB-03, WEB-04, WEB-05
**Success Criteria** (what must be TRUE):

  1. Uma pessoa se cadastra, entra, restaura sessão válida e sai pela aplicação React.
  2. O solicitante filtra seus chamados, abre um novo e acompanha detalhe, comentários, histórico e ações permitidas.
  3. O atendente usa sua fila para assumir, comentar e resolver chamados permitidos.
  4. O administrador visualiza o dashboard e administra usuários e categorias.
  5. A interface oferece ações compatíveis com estado e perfil, mantendo a API como autoridade final.

**Abordagem (revisão 2026-10-30)**: telas objetivas; dashboard em cards e tabelas, gráficos opcionais. Estimativa: 4–5 planos.

**Stack (2026-10-30)**: TypeScript + shadcn/ui sobre Tailwind v4.

**Plans**: 5 plans

Plans:

- [x] `06-01-PLAN.md` — Base: projeto, tema, cliente HTTP, autenticação, layout por perfil e CI (WEB-01)
- [x] `06-02-PLAN.md` — Solicitante: lista com filtros e abertura (WEB-02)
- [x] `06-03-PLAN.md` — Detalhe: comentários, histórico e ações permitidas (WEB-03)
- [x] `06-04-PLAN.md` — Atendente: fila, assumir, comentar e resolver (WEB-04)
- [x] `06-05-PLAN.md` — Administrador: dashboard, usuários e categorias (WEB-05)
**UI hint**: yes

### Phase 7: Polimento e Qualidade

**Goal**: O v1 é utilizável em diferentes telas e comprova que regras críticas, integrações e fronteiras de segurança funcionam juntas.
**Mode:** mvp
**Depends on**: Phase 6
**Requirements**: WEB-06, WEB-07, QUAL-01, QUAL-02, QUAL-03, QUAL-04, QUAL-05
**Success Criteria** (what must be TRUE):

  1. Telas principais funcionam em mobile e desktop com estados claros de carregamento, vazio, sucesso e erro.
  2. Erros são consistentes e compreensíveis, sem stack traces, segredos ou entidades internas.
  3. A matriz de perfis, propriedade e atribuição comprova permissões e recusas de todos os endpoints.
  4. Testes unitários e de integração comprovam regras críticas, MySQL/Flyway, JWT, escopo, filtros, atomicidade, concorrência e e-mail.
  5. Jornadas essenciais dos três perfis completam de ponta a ponta antes da entrega.
  6. *(Revisão 2026-10-30)* Quem clona o repositório sobe app, MySQL e SMTP falso com um comando (`docker-compose`), e o GitHub Actions executa a suíte a cada PR.

**Abordagem (revisão 2026-10-30)**: os critérios 3 e 4 são atendidos pelos testes já existentes mais os testes das fases 4–6; o critério 5 por uma jornada E2E por perfil. Estimativa: 2 planos.

**Estado na abertura (2026-10-31)**: o CI do critério 6 já existe desde o PR #25 (back-end e front-end). Faltam o handler genérico de erro, o `docker-compose` e as jornadas E2E.

**Plans**: 2 plans

Plans:

- [x] `07-01-PLAN.md` — Entrega reproduzível e erros consistentes: handler genérico de 500, Dockerfiles e `docker-compose` (API + web + MySQL + Mailpit) (QUAL-04; critério 6)
- [ ] `07-02-PLAN.md` — Jornadas E2E por perfil (Playwright contra o compose), revisão de estados/telas móveis e mapa de evidências de QUAL-01..03 (QUAL-01, QUAL-02, QUAL-03, QUAL-05, WEB-06, WEB-07)
**UI hint**: yes

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Base do Projeto | 5/5 | Complete    | 2026-09-04 |
| 2. Autenticação | 8/8 | Complete | 2026-09-10 |
| 3. Core de Chamados | 10/10 | Complete | 2026-10-30 |
| 4. Notificações por E-mail | 1/1 | Complete | 2026-10-30 |
| 5. Dashboard | 1/1 | Complete | 2026-10-30 |
| 6. Front-end (React + Tailwind) | 5/5 | Complete | 2026-10-31 |
| 7. Polimento e Qualidade | 1/2 | In Progress |  |
