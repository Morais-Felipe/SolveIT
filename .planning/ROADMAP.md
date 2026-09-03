# Roadmap: SolveIT

## Overview

O SolveIT será entregue em sete incrementos MVP na ordem definida pelo documento de origem: base reproduzível, identidade segura, ciclo completo de chamados, e-mail, indicadores, jornadas web e validação integrada de qualidade e segurança.

## Phases

- [ ] **Phase 1: Base do Projeto** - A API inicia sobre um esquema MySQL reproduzível e validado.
- [ ] **Phase 2: Autenticação** - Usuários obtêm identidade segura e acesso autorizado.
- [ ] **Phase 3: Core de Chamados** - Os três perfis operam o ciclo auditável completo do chamado.
- [ ] **Phase 4: Notificações por E-mail** - Eventos confirmados comunicam os envolvidos corretos.
- [ ] **Phase 5: Dashboard** - Admins e atendentes consultam indicadores autorizados.
- [ ] **Phase 6: Front-end (React + Tailwind)** - Cada perfil conclui suas jornadas principais na web.
- [ ] **Phase 7: Polimento e Qualidade** - O v1 comprova usabilidade, segurança e fluxos críticos.

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

**Plans**: 5 planos sequenciais

**Wave 1**

- [x] `01-01-PLAN.md` — Bootstrap Spring Boot/Maven e configuração MySQL

**Wave 2** *(bloqueada pela conclusão e integração da Wave 1)*

- [x] `01-02-PLAN.md` — Enums canônicos do domínio

**Wave 3** *(bloqueada pela conclusão e integração da Wave 2)*

- [x] `01-03-PLAN.md` — Entidades e mapeamentos JPA

**Wave 4** *(bloqueada pela conclusão e integração da Wave 3)*

- [ ] `01-04-PLAN.md` — Repositórios Spring Data JPA

**Wave 5** *(bloqueada pela conclusão e integração da Wave 4)*

- [ ] `01-05-PLAN.md` — Migrações Flyway e validação integrada em MySQL

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

**Plans**: TBD

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

**Plans**: TBD

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

**Plans**: TBD
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

**Plans**: TBD
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

**Plans**: TBD
**UI hint**: yes

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Base do Projeto | 3/5 | In Progress | - |
| 2. Autenticação | 0/TBD | Not started | - |
| 3. Core de Chamados | 0/TBD | Not started | - |
| 4. Notificações por E-mail | 0/TBD | Not started | - |
| 5. Dashboard | 0/TBD | Not started | - |
| 6. Front-end (React + Tailwind) | 0/TBD | Not started | - |
| 7. Polimento e Qualidade | 0/TBD | Not started | - |
