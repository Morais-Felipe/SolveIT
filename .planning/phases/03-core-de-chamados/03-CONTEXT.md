# Phase 3: Core de Chamados - Context

**Gathered:** 2026-09-11
**Status:** Ready for planning
**Branch documental:** ticket/10-discussao-fase-3

<domain>
## Phase Boundary
Administração de usuários/categorias, abertura e atendimento de chamados, autorização por relação, comentários imutáveis e histórico transacional. Mantém o escopo ADMN-01–06, TICK-01–12 e COLL-01–04. As exceções abaixo foram explicitamente aprovadas; não são autorização para reabertura genérica ou reatribuição livre.
</domain>

<decisions>
## Implementation Decisions

### Visibilidade e fila
- **D-01:** SOLICITANTE acessa seus próprios chamados; ADMIN mantém visão geral. ATENDENTE acessa ABERTOS sem responsável e chamados atribuídos a ele, inclusive encerrados.
- **D-02:** Antes de assumir, atendente pode ler detalhe completo, comentários e histórico, e adicionar comentários nos chamados disponíveis.
- **D-03:** Comentário não reserva o chamado, não altera status/responsável e não concede preferência nem acesso permanente. Após outro atendente assumir, o comentarista perde acesso; seus comentários permanecem.
- **D-04:** Vence a primeira atribuição confirmada pelo servidor. A tentativa concorrente não sobrescreve o responsável.

### Administração
- **D-05:** Desativar um atendente com chamados EM_ANDAMENTO exige confirmação explícita mostrando a quantidade a devolver. A conta é desativada e todos esses chamados voltam a ABERTO, sem responsável, numa única transação.
- **D-06:** Cada retorno gera histórico com o administrador como ator e motivo automático obrigatório: retorno à fila por desativação do atendente. Preservar comentários e históricos anteriores.
- **D-07:** Qualquer mudança que retire o perfil ATENDENTE usa o mesmo comportamento para chamados EM_ANDAMENTO, inclusive confirmação e motivo automático específico de mudança de perfil.
- **D-08:** EM_ANDAMENTO → ABERTO é exceção exclusiva dessas operações administrativas. Ao retornar, título/descrição voltam a ser editáveis conforme TICK-05. FECHADO e CANCELADO continuam sem reabertura.
- **D-09:** Bloquear desativação ou retirada do perfil do último ADMIN ativo. Com outro ADMIN ativo, essas ações são permitidas.
- **D-10:** Chamados RESOLVIDOS/FECHADOS/CANCELADOS preservam o vínculo histórico ao antigo atendente e não impedem desativação. Acesso obedece ao perfil atual, sem conservar permissões antigas; ao virar SOLICITANTE, vê apenas os chamados que abriu.
- **D-11:** Categoria inativa não aceita novos chamados. Chamados existentes preservam vínculo/nome visível e seguem o atendimento normalmente.

### Comentários e histórico
- **D-12:** Comentários novos apenas em ABERTO, EM_ANDAMENTO e RESOLVIDO, por identidade autorizada. FECHADO/CANCELADO permitem apenas leitura de mensagens e histórico, respeitando o escopo de acesso.
- **D-13:** Registrar a abertura no histórico com autor, data e estado inicial ABERTO. Pesquisar a representação do estado anterior inexistente no esquema atual; não inventar transição anterior fictícia.
- **D-14:** Observação opcional nas transições manuais; motivo obrigatório gerado pelo sistema nas devoluções administrativas.
- **D-15:** Comentários e histórico em ordem cronológica crescente, com ID como desempate determinístico. Comentários permanecem imutáveis.

### Concorrência e atomicidade
- **D-16:** Rejeitar alterações desatualizadas e orientar atualização antes de nova tentativa. Exemplo: edição iniciada em ABERTO não pode sobrescrever uma atribuição confirmada depois.
- **D-17:** Conflito em qualquer chamado durante desativação/mudança de perfil desfaz toda essa tentativa administrativa, mantendo a conta como estava e sem devoluções parciais. Mudanças confirmadas por outras transações permanecem. Exigir nova revisão/confirmação.
- **D-18:** Validar estado atual ao adicionar comentário. Se o encerramento já ocorreu, recusar: “Este chamado foi encerrado e não aceita novos comentários.”
- **D-19:** Tentativas rejeitadas não geram HistoricoStatus. Abertura e mudanças confirmadas geram exatamente seus registros na mesma transação.

### Diretrizes para pesquisa e planejamento
- Preservar JWT Bearer sem cookies/refresh, identidade atual do banco, DTOs explícitos, Problem Details, Flyway e modo pedagógico.
- Usar uma branch/ticket/PR por plano de implementação aprovado, integrando o anterior antes da execução seguinte. Esta branch é documental, não corresponde ao plano 03-01.
- Pesquisar confirmação de operações em lote na API e proteção contra mudanças entre prévia e confirmação; implementação exata ainda não escolhida.
- Verificar proteção concorrente do último ADMIN, mudanças de perfil versus atribuição e fechamento versus comentário. @Version em Chamado é base existente, não prova suficiente de todos esses casos.
- Avaliar migração aditiva para registro inicial e semântica de resolvidoEm no retorno à fila. Não editar migrações já aplicadas.
- Nomes internos, códigos adicionais de erro, paginação e desenho transacional ficam para pesquisa/planejamento, preservando as decisões acima.
</decisions>

<canonical_refs>
## Canonical References
- `.planning/REQUIREMENTS.md` — requisitos com os ajustes aprovados nesta discussão.
- `.planning/ROADMAP.md` — escopo da Fase 3.
- `.planning/PROJECT.md` — restrições e valor central.
- `SolveIT_GSD_SystemPrompt.md` — documento original de rotas e domínio; exceções aprovadas nesta discussão prevalecem sobre seu fluxo original.
- `.planning/phases/02-autentica-o/02-CONTEXT.md` — contratos de autenticação e erros herdados.
- `docs/GIT-WORKFLOW.md` e `AGENTS.md` — aprovações, branches e ensino.
</canonical_refs>

<code_context>
## Existing Code Insights
- `solveit-api/src/main/java/com/solveit/entity/Chamado.java` — associações de solicitante, atendente e categoria; @Version existente.
- `solveit-api/src/main/java/com/solveit/security/UsuarioPrincipal.java` — identidade autenticada para autoria e autorização.
- `solveit-api/src/main/java/com/solveit/config/SecurityConfig.java` — cadeia stateless e autorização por método.
- `solveit-api/src/main/java/com/solveit/exception/GlobalExceptionHandler.java` — contrato comum de erros.
- `solveit-api/src/main/resources/db/migration` — esquema versionado; avaliar novas migrações para as decisões.
- Reutilizar repositórios JPA existentes e testes com MySQL/Testcontainers; validar regras de relação no service e filtros de listagem no banco.
</code_context>

<specifics>
## Specific Ideas
Maria comentar antes não impede João de assumir. Respeitar informalmente quem iniciou a conversa é convenção social, não reserva implementada.
Mensagem proposta para alteração desatualizada: “Este chamado foi alterado por outra pessoa. Atualize os dados antes de tentar novamente.”
</specifics>

<deferred>
## Deferred Ideas
Interface de confirmação será concretizada na Fase 6; contrato seguro da operação pertence à Fase 3. E-mail permanece na Fase 4. Nenhuma autorização para implementar reabertura genérica ou transferência livre.
</deferred>
