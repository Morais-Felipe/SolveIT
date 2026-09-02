# Feature Research

**Domain:** Sistema web interno de help desk / gerenciamento de chamados de TI
**Project:** SolveIT v1
**Researched:** 2026-09-02
**Confidence:** MEDIUM — escopo e regras têm alta confiança por serem decisões explícitas do projeto; classificação de mercado tem confiança média por se apoiar em documentação oficial de três produtos, sem pesquisa direta com usuários do SolveIT.

## Feature Landscape

### Table Stakes (Users Expect These)

Recursos cuja ausência quebra o fluxo central ou faz o produto parecer um cadastro de tarefas, não um help desk.

| Feature | Why Expected | Complexity | Notes for SolveIT v1 |
|---------|--------------|------------|----------------------|
| Cadastro e login seguro | Solicitantes precisam entrar no portal e identificar a autoria de cada ação | MEDIUM | Cadastro público cria somente `SOLICITANTE`; JWT, BCrypt, `/login`, `/register` e `/me` |
| Perfis e autorização contextual | Solicitante, atendente e administrador veem e executam ações diferentes | HIGH | Validar no servidor tanto o perfil quanto a relação com o chamado; esconder botões na UI é apenas conveniência |
| Abertura estruturada de chamado | O pedido precisa registrar assunto, contexto, categoria e urgência | MEDIUM | Título, descrição, categoria e prioridade; nasce `ABERTO`, sem atendente e com solicitante autenticado |
| Lista/fila de chamados | Solicitantes acompanham pedidos e atendentes fazem triagem do trabalho | MEDIUM | Visões por perfil; filtros por status, prioridade, categoria, atendente e solicitante; paginação é essencial nas listas administrativas e recomendada para chamados |
| Detalhe completo do chamado | Todos os envolvidos precisam de uma fonte única para entender situação e próximos passos | MEDIUM | Exibir metadados, responsáveis, comentários e histórico; ações disponíveis derivadas do estado e do usuário |
| Atribuição de responsável | Um chamado sem dono operacional tende a ficar esquecido | HIGH | Admin ou próprio atendente atribui; atribuição muda `ABERTO` para `EM_ANDAMENTO` de forma consistente |
| Fluxo de status controlado | Usuários esperam saber se o pedido está aberto, sendo tratado ou concluído | HIGH | Fluxo fixo: `ABERTO → EM_ANDAMENTO → RESOLVIDO → FECHADO`, com cancelamentos explicitamente permitidos |
| Comentários entre envolvidos | O atendimento exige troca de contexto sem perder a conversa do chamado | MEDIUM | Inclusão e leitura cronológica; comentários imutáveis; acesso somente a quem pode ver o chamado |
| Histórico auditável | Mudanças relevantes precisam indicar o que ocorreu, quando e por quem | HIGH | Toda transição cria `HistoricoStatus` na mesma transação; observação opcional; histórico completo no detalhe |
| Notificações de eventos | Usuários não devem depender de atualizar a tela para descobrir mudanças importantes | MEDIUM | E-mail em criação, criticidade, atribuição, mudança de status e comentário; falha de SMTP não pode corromper a transição do chamado |
| Administração de usuários | A operação precisa criar equipe, corrigir dados e revogar acesso | MEDIUM | Somente Admin cria perfis privilegiados, edita e ativa/desativa usuários; não excluir registros referenciados |
| Administração de categorias | Categorias tornam abertura, triagem, filtros e indicadores úteis | LOW | Leitura autenticada de categorias ativas; escrita e ativação somente por Admin; preservar referências históricas |
| Indicadores operacionais básicos | Atendentes e administradores precisam enxergar volume, criticidade e distribuição | MEDIUM | Resumo e agrupamentos por categoria, atendente e prioridade; evitar transformar v1 em plataforma analítica |
| Feedback e responsividade | Ações remotas precisam comunicar carregamento, sucesso e erro; portal deve funcionar em telas menores | MEDIUM | React responsivo, mensagens amigáveis e estados de loading/empty/error nas jornadas principais |

### Differentiators (Aligned With Core Value)

Para este projeto educacional, a vantagem não é competir em quantidade com suítes maduras. É tornar o fluxo pequeno especialmente correto, compreensível e demonstrável.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Transições guiadas por estado e papel | Impede atalhos inválidos e deixa claro quem deve agir a seguir | HIGH | Uma política central de domínio deve alimentar validação, testes e ações exibidas na UI |
| Auditoria por construção | Gera confiança porque status e histórico nunca divergem | HIGH | Transação atômica e comentários imutáveis são invariantes, não opções administrativas |
| Experiência focada por perfil | Reduz ruído: solicitante acompanha, atendente trabalha a fila, Admin governa a operação | MEDIUM | Reutilizar o mesmo domínio/API, com rotas e ações de interface adequadas a cada papel |
| Alertas explícitos para criticidade | Dá visibilidade imediata a pedidos críticos sem exigir um motor genérico de regras | MEDIUM | Regra simples e testável: novo chamado `CRITICA` notifica Admin com caráter urgente |
| Implementação explicável e incremental | O produto valida simultaneamente o fluxo de suporte e o aprendizado de REST, Spring e dados relacionais | MEDIUM | Cada fatia deve expor decisões, invariantes, SQL/FKs e testes, evitando abstrações prematuras |

### Anti-Features (Explicitly Avoid in v1)

| Feature | Why Requested | Why Problematic in v1 | Alternative |
|---------|---------------|-----------------------|-------------|
| Reabrir chamados fechados/cancelados | Parece economizar o preenchimento de um novo pedido | Apaga a fronteira do atendimento concluído e complica histórico, métricas e regras | Criar novo chamado; futuramente pode haver referência ao chamado anterior mediante decisão explícita |
| Editar ou excluir comentários | Corrigir erros ou “limpar” a conversa | Enfraquece auditoria e exige histórico de versões/moderação | Adicionar comentário corretivo imutável |
| Cadastro público de Atendente/Admin | Facilita montar equipe rapidamente | Permite elevação de privilégio e viola a governança definida | Somente Admin cria perfis privilegiados |
| Workflow configurável | Parece tornar o sistema aplicável a qualquer operação | Multiplica estados, transições, permissões, migrações e testes antes de validar um único fluxo | Codificar e testar o fluxo fixo do SolveIT |
| SLA, calendários e escalonamento automático | São comuns em suítes profissionais | Exigem relógios pausáveis, horários úteis, metas por prioridade e jobs confiáveis; desviam do núcleo pedagógico | Usar prioridade, datas e dashboard; pesquisar SLA somente após dados reais de operação |
| Motor genérico de automações | Reduz trabalho repetitivo | Condições, ações, ordenação, idempotência e diagnóstico formam outro produto | Implementar apenas os eventos de e-mail explicitamente definidos |
| Omnichannel (e-mail/chat/WhatsApp como entrada) | Centraliza todos os contatos | Traz threading, identidade, duplicidade, webhooks e expectativas de tempo real | Canal único: portal web; e-mail apenas como saída |
| Base de conhecimento e autoatendimento | Pode reduzir volume de chamados | Requer autoria, busca, governança e conteúdo útil que ainda não existe | Medir categorias recorrentes; só propor depois de evidência |
| Gestão de ativos/CMDB, incidentes, problemas e mudanças | Aproxima o produto de uma suíte ITSM | Expande muito entidades e workflows e obscurece o aprendizado do ticket básico | Manter categoria, prioridade e chamado como único agregado operacional em v1 |
| IA para triagem ou respostas | Demonstra modernidade | Introduz custo, privacidade, avaliação e comportamento não determinístico sem necessidade validada | Filtros e atribuição humana explícita |
| Aplicativo móvel nativo | Conveniência em celulares | Duplica superfície de produto e distribuição | Web responsiva |
| Anexos, busca textual avançada e tags | São úteis em operações maduras | Armazenamento/antivírus/permissões ou indexação/taxonomia adicionam subsistemas fora das rotas definidas | Validar primeiro o fluxo textual estruturado; qualquer inclusão exige decisão futura explícita |

## Feature Dependencies

```text
Modelo relacional + migrações
    ├──> autenticação e usuários
    │       └──> autorização por perfil e relação
    │               ├──> administração de usuários/categorias
    │               └──> todas as operações de chamado
    └──> chamado + enums + repositórios
            └──> abertura e consulta
                    └──> fila filtrável e detalhe
                            ├──> atribuição
                            │       └──> transição para EM_ANDAMENTO
                            ├──> comentários imutáveis
                            └──> transições de status
                                    └──> histórico atômico

Eventos de domínio estáveis ──> notificações por e-mail
Dados operacionais confiáveis ──> dashboard
API e regras validadas ──> interface React por perfil
Fluxos completos ──> testes críticos e revisão de segurança
```

### Dependency Notes

- **Autorização requer autenticação e relacionamentos persistidos:** o servidor só decide “pode agir?” conhecendo identidade, perfil, solicitante e atendente do chamado.
- **Atribuição requer transição de status:** no SolveIT, atribuir não é uma edição isolada; deve colocar o chamado em `EM_ANDAMENTO` sem estado intermediário inconsistente.
- **Atualização de status requer histórico atômico:** salvar ambos na mesma transação evita status novo sem evidência ou evidência de uma mudança que falhou.
- **Comentários requerem a política de acesso do chamado:** “qualquer perfil” significa qualquer perfil autorizado naquele chamado, não qualquer usuário autenticado.
- **Notificações requerem eventos já corretos:** e-mail deve reagir a uma operação de domínio confirmada; não deve ser a origem da regra nem deixar a transação parcialmente aplicada.
- **Dashboard requer dados íntegros:** contadores só têm valor depois que status, prioridade, categoria e atendente são gravados de forma consistente.
- **Front-end requer contrato estável da API:** desenvolver as telas após as fatias de backend reduz retrabalho e mantém o foco Build to Learn, mas convém validar cada endpoint cedo com testes/cliente HTTP.
- **SLAs e automações futuras requereriam workflow, timestamps e métricas maduros:** essa dependência confirma que não devem orientar o desenho do v1.

## MVP Definition

### Launch With (SolveIT v1)

- [ ] Cadastro/login e identidade atual com JWT/BCrypt.
- [ ] Perfis `SOLICITANTE`, `ATENDENTE` e `ADMIN` com autorização server-side por papel e relação.
- [ ] Administração de usuários e categorias por Admin, com ativação/desativação.
- [ ] Abertura de chamado com categoria e prioridade.
- [ ] Listagem por perfil, filtros previstos e detalhe completo.
- [ ] Edição de título/descrição somente quando `ABERTO` e pelo dono ou Admin.
- [ ] Atribuição de atendente com transição automática para `EM_ANDAMENTO`.
- [ ] Todas as transições e cancelamentos do fluxo definido, sem reabertura.
- [ ] Comentários imutáveis e cronológicos para envolvidos autorizados.
- [ ] Histórico obrigatório e atômico para cada mudança de status.
- [ ] E-mails nos cinco eventos definidos, incluindo tratamento especial de criticidade.
- [ ] Dashboard enxuto com resumo e agrupamentos especificados.
- [ ] Interface React responsiva por perfil, com loading, vazio, sucesso e erro.
- [ ] Testes das regras críticas e revisão de autorização de todos os endpoints.

### Add After Validation (v1.x, Only With Explicit Scope Decision)

- [ ] Paginação consistente em filas de chamados — adicionar se volume real tornar listas lentas ou difíceis de navegar.
- [ ] Recuperação de senha — adicionar quando houver SMTP de produção e fluxo de token seguro; é uma lacuna operacional comum, mas não está nas rotas de v1.
- [ ] Preferências/registro de entrega de notificações — adicionar se falhas ou excesso de e-mails aparecerem na validação.
- [ ] Exportação simples de indicadores — adicionar apenas se administradores demonstrarem uma necessidade recorrente de análise externa.
- [ ] Referência entre chamado novo e anterior — adicionar se a regra “não reabrir” gerar perda frequente de contexto.

### Future Consideration (v2+)

- [ ] SLAs, calendários úteis e escalonamento — após existir histórico real de tempos e compromisso operacional.
- [ ] Anexos com armazenamento e verificação de segurança — quando chamados textuais se mostrarem insuficientes.
- [ ] Base de conhecimento — após identificar dúvidas recorrentes e responsáveis pelo conteúdo.
- [ ] Entrada por e-mail/chat e roteamento omnichannel — somente com capacidade para identidade e threading confiáveis.
- [ ] Automações configuráveis — depois que regras repetitivas estáveis forem observadas.
- [ ] Ativos/CMDB e práticas de incidente/problema/mudança — em um marco próprio, não como extensão casual de `Chamado`.
- [ ] Recursos de IA — somente com caso de uso, dados, privacidade, custo e avaliação definidos.

## Feature Prioritization Matrix

| Feature Group | User Value | Implementation Cost | Priority |
|---------------|------------|---------------------|----------|
| Modelo de dados e migrações | HIGH | MEDIUM | P1 |
| Autenticação e autorização contextual | HIGH | HIGH | P1 |
| Administração de usuários/categorias | HIGH | MEDIUM | P1 |
| Abertura, fila, filtros e detalhe | HIGH | HIGH | P1 |
| Atribuição e workflow de status | HIGH | HIGH | P1 |
| Comentários e histórico | HIGH | HIGH | P1 |
| Notificações de eventos | MEDIUM | MEDIUM | P1 |
| Dashboard operacional básico | MEDIUM | MEDIUM | P1 |
| UI responsiva, feedback e erros | HIGH | HIGH | P1 |
| Paginação ampla e recuperação de senha | MEDIUM | MEDIUM | P2 |
| SLA, anexos e base de conhecimento | MEDIUM | HIGH | P3 |
| Omnichannel, automações, CMDB e IA | LOW before validation | HIGH | P3 |

**Priority key:**

- **P1:** necessário para entregar o SolveIT v1 explicitamente definido.
- **P2:** lacuna operacional plausível, mas só entra após validação e mudança explícita de escopo.
- **P3:** expansão futura; não deve influenciar as primeiras fases além de evitar bloqueios óbvios.

## Competitor Feature Analysis

| Capability | Jira Service Management | Zendesk / Freshdesk | SolveIT v1 Approach |
|------------|-------------------------|---------------------|---------------------|
| Intake and agent work | Portal/request types feed configurable queues | Tickets feed views/inbox and routing | One responsive portal, structured ticket, role-specific filtered queue |
| Workflow | Configurable work types, workflows and customer-facing statuses | Statuses plus triggers/automations | One fixed, explicit status graph with server-side guards |
| Ownership and prioritization | Assignment, priority and queue filters | Assignment/groups, priority and routing | Human assignment; category/priority/status/assignee filters |
| Communication | Portal comments, email/help-center channels and notifications | Multichannel ticket conversation and notifications | Immutable comments in portal; SMTP notifications as output only |
| Service targets | SLA clocks, goals and SLA-aware queues | SLA policies, calendars, views and escalation automation | Deferred; dashboard and raw timestamps only |
| Administration and access | Separate customer, agent and admin capabilities | End user, agent and admin roles | Three fixed profiles plus record-level relationship authorization |
| Reporting | Dashboards, trends, custom reports | Ticket activity, SLA and performance analytics | Four specified operational endpoints; no report builder |
| ITSM breadth | Knowledge, assets, incidents, problems, changes, alerts, on-call | Automation, omnichannel, knowledge and advanced routing | Explicitly excluded from v1 to protect learning and workflow correctness |

## Roadmap Implications

1. **Establish data invariants first:** enums, entities, FKs, timestamps and Flyway migrations underpin every user-visible feature.
2. **Secure identity before domain operations:** authentication alone is insufficient; define and test the authorization matrix before exposing ticket actions.
3. **Deliver one vertical ticket path before breadth:** open → view → assign → resolve → close, with history, proves the core value earlier than completing every CRUD screen in isolation.
4. **Add collaboration and cancellation paths next:** comments and exceptional allowed transitions reuse the same access and audit policies.
5. **Connect email only after domain events are stable:** isolate SMTP side effects and test event selection independently.
6. **Build indicators from trusted data:** dashboards come after the workflow, not before it.
7. **Build the profile-focused UI over a stable API:** complete each role’s primary journey, then polish responsiveness and feedback.
8. **End with adversarial rule tests/security review:** explicitly cover forbidden transitions, cross-user access, inactive users and comment/history immutability.

## Sources

- [Atlassian — Main Jira Service Management features](https://support.atlassian.com/jira-service-management-cloud/docs/get-to-know-the-main-jira-service-management-features/) — official documentation; current page observed 2026-09-02; feature breadth and advanced layers.
- [Atlassian — About queues](https://support.atlassian.com/jira-service-management-cloud/docs/check-out-your-queues/) — official documentation; current page observed 2026-09-02; triage, assignment and queue behavior.
- [Atlassian — Requests and work items](https://support.atlassian.com/jira-service-management-cloud/docs/what-are-issues-and-requests/) — official documentation; current page observed 2026-09-02; requester/agent views and request tracking.
- [Atlassian — Default Jira Service Management configuration](https://support.atlassian.com/jira-cloud-administration/docs/default-configurations-for-jira-service-management/) — official documentation; current page observed 2026-09-02; workflows, statuses and permission separation.
- [Atlassian — Customize Jira Service Management permissions](https://support.atlassian.com/jira-cloud-administration/docs/customize-jira-service-management-permissions/) — official documentation; current page observed 2026-09-02; customer, agent and administrator boundaries.
- [Zendesk — Launch guide](https://support.zendesk.com/hc/en-us/articles/4408836154266-Launch-guide-for-Zendesk) — official documentation, published/updated 2026; prerequisites and dependencies for workflow, automation and SLA.
- [Zendesk — Routing and automation options](https://support.zendesk.com/hc/en-us/articles/4408831658650-Routing-and-automation-options-for-incoming-tickets) — official documentation, published/updated 2026; fields, routing, views, macros, SLA and reporting.
- [Freshdesk — Understanding SLA policies](https://support.freshdesk.com/support/solutions/articles/37626) — official documentation, modified 2026-08-20; SLA complexity and dependence on priority, groups, channels and schedules.

## Confidence Notes

- **HIGH — SolveIT scope:** features and exclusions come directly from `PROJECT.md` and `SolveIT_GSD_SystemPrompt.md`.
- **MEDIUM — market classification:** multiple current official product documents agree on foundational versus advanced capabilities, but vendor documentation is not neutral market research.
- **LOW — post-v1 demand:** no interviews, usage analytics or operational data exist yet; v1.x/v2 items are hypotheses and must not become implied commitments.

---
*Feature research for: SolveIT v1 web IT help desk*
*Researched: 2026-09-02*
