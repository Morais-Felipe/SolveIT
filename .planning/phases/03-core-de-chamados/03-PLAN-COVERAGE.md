# Phase 03 Plan Coverage — Core de Chamados

**Phase goal:** Solicitantes, atendentes e administradores conduzem o chamado pelo fluxo permitido com colaboração e auditoria íntegras.
**Plan shape:** 10 planos, 10 waves estritamente sequenciais por `docs/GIT-WORKFLOW.md`.
**Status:** planejamento completo; implementação continua sem autorização até a aprovação individual de cada plano.

## Multi-Source Coverage Audit

| SOURCE | ID | Feature / constraint | Plan | Status | Notes |
|---|---|---|---|---|---|
| GOAL | — | Fluxo completo autorizado, colaborativo e auditável | 03-01..03-10 | COVERED | Administração, criação, leitura, edição, atribuição, transições, comentários, histórico e retorno administrativo formam o percurso completo. |
| RESEARCH | R-01 | Stack existente, sem nova dependência | 03-01..03-10 | COVERED | Spring MVC/JPA/Security, Flyway, MySQL, JUnit/MockMvc/Testcontainers; nenhum install. |
| RESEARCH | R-02 | Coordenação persistente antes das escritas e ordem uniforme de locks | 03-01; consumido por 03-02..03-08 e 03-10 | COVERED | Proposta técnica destacada para aprovação do plano 03-01; nenhuma espera externa sob lock. |
| RESEARCH | R-03 | Versão esperada e conflitos controlados | 03-02..03-07, 03-10 | COVERED | DTOs não definem @Version; comparação ocorre depois do guard. |
| RESEARCH | R-04 | Prévia administrativa completa, inclusive conjunto vazio | 03-10 | COVERED | Checkpoint escolhe stateless echo versus persistência; contrato recomendado compara ação, quantidade, IDs e versões. |
| RESEARCH | R-05 | Último ADMIN e corridas perfil/atribuição | 03-10 | COVERED | Contagem sob guard e provas com duas transações MySQL. |
| RESEARCH | R-06 | Histórico inicial sem estado fictício anterior | 03-04, 03-09 | COVERED | Migração aditiva e leitura nullable do evento de abertura. |
| RESEARCH | R-07 | Visibilidade SQL, filtros e mesma política no detalhe/coleções | 03-05, 03-08, 03-09 | COVERED | Predicado relacional único; filtros nunca ampliam escopo. |
| RESEARCH | R-08 | Provisionamento controlado do primeiro ADMIN | 03-03 | COVERED | Documento operacional; sem endpoint, senha padrão ou execução automática. |
| RESEARCH | R-09 | Concorrência real, sem sleeps/mocks como evidência | 03-01, 03-04, 03-06..03-08, 03-10 | COVERED | MySQL/Testcontainers, conexões independentes, latches e timeouts finitos. |
| RESEARCH | R-10 | Problem Details e ausência de dados internos | 03-01 e todos os contratos seguintes | COVERED | Códigos controlados; entidade/hash/SQL nunca são resposta. |
| CONTEXT | D-01 | Escopo por perfil/relação e fila disponível | 03-05, 03-08, 03-09 | COVERED | Lista, detalhe, comentários e histórico reutilizam a política. |
| CONTEXT | D-02 | Atendente lê/comenta antes de assumir | 03-05, 03-08, 03-09 | COVERED | A fila ABERTO sem responsável é legível e comentável. |
| CONTEXT | D-03 | Comentário não reserva nem conserva acesso | 03-06, 03-08 | COVERED | Sequência Maria comenta → João assume e ordenamento inverso. |
| CONTEXT | D-04 | Primeira atribuição confirmada vence | 03-06 | COVERED | Um responsável, uma história, segundo comando409. |
| CONTEXT | D-05 | Desativação exige confirmação da quantidade/conjunto | 03-10 | COVERED | Lista completa id/versão, não só contador. |
| CONTEXT | D-06 | Retorno por desativação gera motivo automático | 03-10 | COVERED | Uma história por chamado com ADMIN ator. |
| CONTEXT | D-07 | Retirada de ATENDENTE usa o mesmo protocolo | 03-10 | COVERED | Motivo automático específico de mudança de perfil. |
| CONTEXT | D-08 | EM_ANDAMENTO→ABERTO apenas na operação administrativa | 03-07, 03-10 | COVERED | Rota manual não reabre; retorno restaura regras de ABERTO. |
| CONTEXT | D-09 | Proteger último ADMIN ativo | 03-03, 03-10 | COVERED | PUT comum não muda perfil/status; operação específica bloqueia resultado sem ADMIN. |
| CONTEXT | D-10 | Vínculos encerrados preservados; autoridade atual | 03-05, 03-09, 03-10 | COVERED | Nenhuma permissão herdada do papel antigo. |
| CONTEXT | D-11 | Categoria inativa bloqueia abertura e preserva vínculos | 03-01, 03-02, 03-04, 03-05 | COVERED | Catálogo, abertura concorrente e consulta histórica. |
| CONTEXT | D-12 | Comentários só nos três estados; encerrados read-only | 03-08, 03-09 | COVERED | POST recusa; GET permanece autorizado. |
| CONTEXT | D-13 | Abertura com anterior inexistente, não fictício | 03-04, 03-09 | COVERED | `statusAnterior=null` exclusivo do primeiro evento. |
| CONTEXT | D-14 | Observação manual opcional e motivo administrativo obrigatório | 03-06, 03-07, 03-09, 03-10 | COVERED | DTOs e timeline preservam cada semântica. |
| CONTEXT | D-15 | Ordem crescente + ID; comentários imutáveis | 03-09 | COVERED | Queries SQL e testes de rotas negativas. |
| CONTEXT | D-16 | Rejeitar alterações desatualizadas | 03-02, 03-03, 03-05..03-07, 03-10 | COVERED | Formulários e comandos versionados não sobrescrevem estado. |
| CONTEXT | D-17 | Conflito administrativo reverte tentativa inteira | 03-10 | COVERED | Usuário, chamados e histórias mudam juntos ou não mudam. |
| CONTEXT | D-18 | Comentário valida encerramento atual | 03-08 | COVERED | Dois ordenamentos concorrentes e mensagem literal. |
| CONTEXT | D-19 | Exatamente uma história por mudança confirmada; zero por rejeição | 03-04, 03-06..03-10 | COVERED | Abertura, atribuição, transições e retornos têm contagens explícitas. |

## Requirement Traceability and Manual Edge Review

O probe spec-less classificou os 22 requisitos como `unclassified — review manually` por não reconhecer vocabulário em português. Cada linha abaixo registra a revisão manual e o edge behavior que deve aparecer nas acceptance criteria/testes. Não há descarte silencioso.

| Requirement | Plan(s) | Edge behavior made explicit | Probe disposition |
|---|---|---|---|
| ADMN-01 | 03-03 | página vazia, limite de size, ordem id, ID ausente, resposta sem senha | COVERED explicitly |
| ADMN-02 | 03-03 | e-mail normalizado/duplicado concorrente, senha opcional no PUT, campos perfil/ativo rejeitados | COVERED explicitly |
| ADMN-03 | 03-10 | conjunto vazio→não vazio, mesma quantidade/outros IDs, último ADMIN, rollback de qualquer item | COVERED explicitly |
| ADMN-04 | 03-01, 03-05 | ativa para qualquer autenticado; inativa só ADMIN ou relação visível; ausente404 | COVERED with flagged assumption A-02 |
| ADMN-05 | 03-01, 03-02 | nome vazio/limites, unicidade entre ativas/inativas, edição stale | COVERED explicitly |
| ADMN-06 | 03-02 | no-op atual, versão antiga, vínculo de chamado preservado, sem DELETE | COVERED explicitly |
| TICK-01 | 03-04 | campos servidor rejeitados, categoria inexistente/inativa, abertura+história atômicos | COVERED explicitly |
| TICK-02 | 03-05 | matriz perfil/relação/estado, página vazia, empate determinístico | COVERED explicitly |
| TICK-03 | 03-05 | filtros combinados por AND sem ampliar escopo, inválidos400, total SQL correto | COVERED explicitly |
| TICK-04 | 03-05 | inexistente404 versus existente sem relação403; categoria inativa ainda visível | COVERED explicitly |
| TICK-05 | 03-05 | somente campos permitidos/ABERTO; stale após atribuição; sem história de status | COVERED explicitly |
| TICK-06 | 03-06, 03-08 | próprio atendente versus ADMIN, destino inativo, dupla atribuição, comentário sem reserva | COVERED explicitly |
| TICK-07 | 03-07 | responsável/ADMIN, outro atendente403, data UTC, falha de história reverte | COVERED explicitly |
| TICK-08 | 03-07 | somente solicitante proprietário; ADMIN não recebe atalho; resolvidoEm preservado | COVERED explicitly |
| TICK-09 | 03-07 | cancelamento por origem e ator exatos; estados finais imutáveis | COVERED explicitly |
| TICK-10 | 03-07, 03-10 | mesmo estado/reabertura rejeitados; retorno administrativo exclusivo e editável como ABERTO | COVERED explicitly |
| TICK-11 | 03-04, 03-06, 03-07, 03-10 | abertura anterior null, observação semântica, uma história por confirmação, nenhuma por rejeição | COVERED explicitly |
| TICK-12 | 03-04..03-08, 03-10 | ambos os ordenamentos das corridas críticas e releitura pós-commit | COVERED explicitly |
| COLL-01 | 03-08 | identidade do principal, três estados permitidos, encerramento concorrente, sem reserva | COVERED explicitly |
| COLL-02 | 03-09 | vazio, encerrado legível, ordem timestamp/id, perda de acesso por relação atual | COVERED explicitly |
| COLL-03 | 03-09 | PUT/PATCH/DELETE autenticados não mutam; linha relida permanece igual | COVERED explicitly |
| COLL-04 | 03-09 | abertura e todas as transições confirmadas, empates, observação/ator, rejeições ausentes | COVERED explicitly |

**Edge probe accounting:** `applicable=22`, `resolved=22`, `unresolved=0`, `byVerification={explicit:22, backstop:0}` após revisão manual. A classificação automática original permanece registrada no histórico da execução; esta tabela é a resolução humana-legível consumida pelos planos.

## Flagged Technical Assumptions

| ID | Assumption | Coverage / resolution gate |
|---|---|---|
| A-01 | Uma linha global `core_write_guard` serializa as escritas curtas da Fase 3. | 03-01 explica contenção e exige aprovação individual antes da implementação; 03-01 prova mesma transação e timeout. |
| A-02 | Categoria inativa por ID é visível a ADMIN ou a usuário com chamado visível vinculado. | 03-05 mantém a proposta como [ASSUMED] e insere `checkpoint:decision` `blocking-human` antes da tarefa 2; a implementação só segue após decisão conceitual explícita, e qualquer alternativa exige revisar o plano. |
| A-03 | Prévia administrativa stateless é ecoada integralmente na confirmação. | 03-10 começa com `checkpoint:decision` `blocking-human`; opção persistida exige revisão do plano. |
| A-04 | Listas de comentários e histórico são completas, sem paginação silenciosa. | 03-09 assume o contrato canônico/COLL-04 e registra risco de disponibilidade como aceito no threat model. |

## Prohibition Recall Accounting

Itens rotineiros de engenharia foram descartados como proibitions e permanecem nos testes/STRIDE. Itens canônicos de autenticação, IDOR, injeção, segredo e vazamento de SQL são responsabilidade de Spring Security, testes da fase e `$gsd-secure-phase`; não foram duplicados como proibições bespoke.

| ID | Bespoke must-NOT | Source | Status | Verification |
|---|---|---|---|---|
| P-03-01 | Autoria de comentário não pode virar reserva, preferência técnica ou acesso permanente. | D-03 | RESOLVED; unverified until execution | descriptor-less; cenários 03-06/03-08 |
| P-03-02 | Comentários e registros auditáveis não podem ser editados ou apagados por rota ou exclusão física. | COLL-03, D-15, constraints do projeto | RESOLVED; unverified until execution | descriptor-less; 03-02/03-09/03-10 |
| P-03-03 | O fluxo não pode se tornar reabertura genérica nem transferência livre de responsável. | D-08, Deferred Ideas | RESOLVED; unverified until execution | descriptor-less; 03-06/03-07/03-10 |

Os três itens permanecem `descriptor-less` porque os testes JUnit ainda serão criados durante execução; até que as suites nomeadas existam e passem, o estado é explicitamente não verificado, nunca verde por ausência de prova.

## Deferred / Excluded Without Gap

- UI da confirmação administrativa: Fase 6; Fase 3 entrega o contrato seguro.
- Notificações SMTP: Fase 4.
- Reabertura genérica, transferência livre, anexos, microserviços e demais itens de `REQUIREMENTS.md ## Out of Scope`: excluídos por decisão, não lacunas.
- API externa: detector determinístico retornou `detected:false`; a fase expõe a própria API REST e não integra SDK/serviço externo. Nenhum `COVERAGE.md` externo é necessário.
- Schema-push TypeScript: nenhum padrão Payload/Prisma/Drizzle/Supabase/TypeORM foi detectado. Migrações V6..V9 são Flyway/MySQL e são verificadas pelos testes de persistência dos planos 03-01..03-04.

## Requirement Coverage Summary

| Requirement family | IDs | Covered by |
|---|---|---|
| Administração | ADMN-01..ADMN-06 | 03-01, 03-02, 03-03, 03-05, 03-10 |
| Chamados | TICK-01..TICK-12 | 03-04, 03-05, 03-06, 03-07, 03-08, 03-10 |
| Colaboração | COLL-01..COLL-04 | 03-08, 03-09 |

**Coverage result:** 22/22 requirement IDs, 19/19 locked decisions, phase goal and all in-scope research constraints are covered. No source-audit item is missing.
