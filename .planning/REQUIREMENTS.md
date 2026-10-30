# Requirements: SolveIT

**Defined:** 2026-09-02
**Core Value:** Permitir que um chamado percorra seu fluxo completo com autorização correta, histórico auditável e comunicação clara entre todos os envolvidos.

## v1 Requirements

### Fundação de Dados

- [x] **DATA-01**: O desenvolvedor pode iniciar a API Java 17/Spring Boot 3 com configuração de desenvolvimento para MySQL 8.
- [x] **DATA-02**: O sistema representa usuários, categorias, chamados, comentários e históricos com os campos, relações, nulabilidade e enums definidos.
- [x] **DATA-03**: O banco pode ser criado do zero por migrações Flyway versionadas, com chaves estrangeiras, unicidade e índices coerentes com as consultas.
- [x] **DATA-04**: A aplicação valida que o esquema gerenciado pelo Flyway corresponde ao mapeamento JPA sem gerar tabelas automaticamente.

### Autenticação e Acesso

- [x] **AUTH-01**: Uma pessoa pode se cadastrar publicamente com nome, e-mail e senha e recebe exclusivamente o perfil SOLICITANTE.
- [x] **AUTH-02**: Um usuário ativo pode entrar com e-mail e senha válidos e receber um JWT válido e limitado no tempo.
- [x] **AUTH-03**: Um usuário autenticado pode consultar seus próprios dados em `GET /api/auth/me`.
- [x] **AUTH-04**: Senhas são armazenadas somente como hash BCrypt e nunca aparecem em respostas ou logs.
- [x] **AUTH-05**: Requisições sem token, com token inválido/expirado ou de usuário inativo são rejeitadas nas rotas protegidas.
- [x] **AUTH-06**: Cada operação aplica no servidor tanto o perfil exigido quanto a relação do usuário com o recurso acessado.

### Usuários e Categorias

- [x] **ADMN-01**: Um administrador pode listar usuários de forma paginada e consultar um usuário por id.
- [x] **ADMN-02**: Um administrador pode criar e editar usuários com qualquer perfil sem permitir e-mails duplicados.
- [x] **ADMN-03**: Um administrador pode ativar ou desativar usuários sem excluí-los. Desativação ou retirada do perfil ATENDENTE devolve seus chamados EM_ANDAMENTO a ABERTO sem responsável, após confirmação explícita da quantidade, com histórico e operação atômica. Conflito exige nova revisão sem mudanças parciais. O último ADMIN ativo não pode ser desativado nem perder seu perfil.
  - *Revisão 2026-10-30:* sem prévia nem confirmação da quantidade. A operação é atômica, usa a versão esperada do usuário (conflito = 409, sem mudança parcial), devolve os chamados com histórico e informa na resposta quantos foram devolvidos.
- [x] **ADMN-04**: Qualquer usuário autenticado pode listar categorias ativas e consultar uma categoria permitida por id.
- [x] **ADMN-05**: Um administrador pode criar e editar categorias com nome único.
- [x] **ADMN-06**: Um administrador pode ativar ou desativar categorias sem excluí-las.

### Chamados e Fluxo

- [x] **TICK-01**: Um solicitante pode abrir chamado com título, descrição, prioridade e categoria ativa; o chamado nasce ABERTO, sem atendente e com datas automáticas.
- [x] **TICK-02**: Usuários autenticados podem listar somente os chamados que seu perfil e relação permitem, com paginação e ordenação determinística.
- [x] **TICK-03**: A listagem aceita filtros opcionais por status, prioridade, categoria, atendente e solicitante sem carregar todos os registros em memória.
- [x] **TICK-04**: Um usuário autorizado pode consultar o detalhe completo de um chamado por id.
- [x] **TICK-05**: O solicitante proprietário ou um administrador pode editar título e descrição somente enquanto o chamado estiver ABERTO.
- [x] **TICK-06**: Um administrador ou o próprio atendente pode atribuir um atendente ativo a um chamado ABERTO, alterando-o automaticamente para EM_ANDAMENTO.
- [x] **TICK-07**: Somente o atendente responsável ou um administrador pode alterar EM_ANDAMENTO para RESOLVIDO, preenchendo `resolvidoEm`.
- [x] **TICK-08**: Somente o solicitante proprietário pode alterar RESOLVIDO para FECHADO.
- [x] **TICK-09**: Somente o solicitante proprietário pode cancelar um chamado ABERTO, e somente um administrador pode cancelar um chamado EM_ANDAMENTO.
- [x] **TICK-10**: Transições não previstas, inclusive reabertura de FECHADO ou CANCELADO, são rejeitadas. EM_ANDAMENTO → ABERTO é permitido exclusivamente pela desativação ou retirada do perfil do atendente, com devolução à fila conforme ADMN-03; as regras de edição de ABERTO voltam a valer.
- [x] **TICK-11**: A abertura registra autor, data e estado inicial ABERTO no histórico. Toda transição confirmada cria exatamente um HistoricoStatus na mesma transação, com ator, estados, data e observação manual opcional; devoluções administrativas têm motivo automático obrigatório. Tentativas rejeitadas não geram histórico de status.
- [x] **TICK-12**: Atualizações concorrentes do mesmo chamado não podem sobrescrever silenciosamente uma transição já confirmada.

### Colaboração e Auditoria

- [x] **COLL-01**: Um usuário com acesso pode comentar com sua identidade autenticada somente em ABERTO, EM_ANDAMENTO ou RESOLVIDO. FECHADO/CANCELADO permitem apenas leitura das mensagens anteriores e histórico por usuários autorizados; validar o estado atual inclusive em envios concorrentes ao encerramento.
- [x] **COLL-02**: Um usuário com acesso ao chamado pode listar comentários em ordem cronológica.
- [x] **COLL-03**: Comentários não podem ser editados nem excluídos por nenhuma rota da aplicação.
- [x] **COLL-04**: Um usuário com acesso ao chamado pode consultar todo o histórico de status em ordem cronológica.

### Notificações

- [ ] **MAIL-01**: Administradores recebem e-mail quando um novo chamado ABERTO é criado.
- [ ] **MAIL-02**: Administradores recebem uma notificação urgente adicional ou claramente destacada quando o novo chamado é CRITICO.
- [ ] **MAIL-03**: Atendente e solicitante recebem e-mail após a atribuição de atendente.
- [ ] **MAIL-04**: O solicitante recebe e-mail após cada atualização de status.
- [ ] **MAIL-05**: Todos os envolvidos no chamado recebem e-mail após novo comentário, sem duplicar destinatários.
- [ ] **MAIL-06**: E-mails são disparados somente após confirmação da transação do evento e falhas SMTP não desfazem mudanças válidas do chamado.
  - *Revisão 2026-10-30:* envio após o commit e fora da requisição, com falha registrada em log; sem outbox (reenvio durável segue em OPER-01, v2).

### Dashboard

- [ ] **DASH-01**: Administradores e atendentes podem consultar totais de chamados ABERTO, EM_ANDAMENTO, RESOLVIDO e CRITICO aberto.
- [ ] **DASH-02**: Administradores e atendentes podem consultar a quantidade de chamados agrupada por categoria.
- [ ] **DASH-03**: Administradores e atendentes podem consultar a carga ativa agrupada por atendente.
- [ ] **DASH-04**: Administradores e atendentes podem consultar a distribuição de chamados abertos por prioridade.
- [ ] **DASH-05**: Os agregados respeitam o escopo de acesso definido para o perfil e são calculados no banco.

### Aplicação Web

- [ ] **WEB-01**: A aplicação React oferece cadastro, login, restauração da sessão válida e saída, usando um cliente Axios configurado centralmente.
- [ ] **WEB-02**: O solicitante pode visualizar seus chamados, filtrar a lista e abrir um novo chamado.
- [ ] **WEB-03**: Usuários autorizados podem visualizar detalhe, comentários e histórico do chamado e executar somente ações permitidas pelo estado e perfil.
- [ ] **WEB-04**: O atendente dispõe de uma fila de chamados adequada ao seu trabalho e pode assumir, comentar e resolver chamados permitidos.
- [ ] **WEB-05**: O administrador dispõe de dashboard e telas para administrar usuários e categorias.
- [ ] **WEB-06**: Todas as telas principais possuem estados de carregamento, vazio, sucesso e erro com mensagens compreensíveis.
- [ ] **WEB-07**: As jornadas principais são utilizáveis em telas móveis e desktop, sem depender da interface para garantir autorização.

### Qualidade e Segurança

- [ ] **QUAL-01**: Serviços críticos possuem testes unitários para autorização, transições, edição, comentários e destinatários de notificação.
- [ ] **QUAL-02**: Testes de integração verificam migrações em MySQL, autenticação JWT, escopo por usuário, atomicidade de histórico e filtros paginados.
- [ ] **QUAL-03**: Todos os endpoints são revisados contra uma matriz de perfis, propriedade e atribuição, incluindo cenários negativos.
  - *Revisão 2026-10-30:* atendido pelos testes de integração existentes (matrizes de detalhe, lista, transições e comentários) e pelos das fases 4–6; sem nova matriz dedicada.
- [ ] **QUAL-04**: Exceções são convertidas em respostas consistentes, sem expor stack traces, segredos ou entidades JPA.
- [ ] **QUAL-05**: Jornadas essenciais de solicitante, atendente e administrador são verificadas de ponta a ponta antes da entrega do v1.
  - *Revisão 2026-10-30:* uma jornada E2E automatizada por perfil.

## v2 Requirements

### Conta e Operação

- **ACCT-01**: Usuário pode redefinir senha por um fluxo seguro de recuperação.
- **ACCT-02**: Usuário pode configurar preferências de notificação.
- **OPER-01**: Administrador pode consultar e reenviar notificações que falharam de forma durável.
- **OPER-02**: Usuário autorizado pode exportar listas e indicadores.
- **OPER-03**: Chamados podem referenciar outros chamados relacionados.

## Out of Scope

| Feature | Reason |
|---------|--------|
| Reabrir chamados FECHADOS ou CANCELADOS | A regra do domínio exige um novo chamado |
| Editar ou excluir comentários | Viola a trilha de auditoria |
| Cadastro público de ATENDENTE ou ADMIN | Permitiria elevação de privilégio |
| Fluxos de status configuráveis | Complexidade desnecessária para o v1 e incompatível com o fluxo fixo definido |
| Anexos, base de conhecimento e intake omnichannel | Não são necessários para validar o ciclo principal |
| SLA, escalonamento, CMDB e automação genérica | Expandem o produto para uma suíte ITSM antes de validar demanda |
| Inteligência artificial | Não contribui para o objetivo de aprendizagem e fluxo auditável do v1 |
| Aplicativo móvel nativo | A interface web responsiva cobre o escopo inicial |
| Microserviços ou broker de mensagens | Um monólito modular atende à escala e reduz complexidade pedagógica |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| DATA-01 | Phase 1 | Complete |
| DATA-02 | Phase 1 | Complete |
| DATA-03 | Phase 1 | Complete |
| DATA-04 | Phase 1 | Complete |
| AUTH-01 | Phase 2 | Complete |
| AUTH-02 | Phase 2 | Complete |
| AUTH-03 | Phase 2 | Complete |
| AUTH-04 | Phase 2 | Complete |
| AUTH-05 | Phase 2 | Complete |
| AUTH-06 | Phase 2 | Complete |
| ADMN-01 | Phase 3 | Complete |
| ADMN-02 | Phase 3 | Complete |
| ADMN-03 | Phase 3 | Complete |
| ADMN-04 | Phase 3 | Complete |
| ADMN-05 | Phase 3 | Complete |
| ADMN-06 | Phase 3 | Complete |
| TICK-01 | Phase 3 | Complete |
| TICK-02 | Phase 3 | Complete |
| TICK-03 | Phase 3 | Complete |
| TICK-04 | Phase 3 | Complete |
| TICK-05 | Phase 3 | Complete |
| TICK-06 | Phase 3 | Complete |
| TICK-07 | Phase 3 | Complete |
| TICK-08 | Phase 3 | Complete |
| TICK-09 | Phase 3 | Complete |
| TICK-10 | Phase 3 | Complete |
| TICK-11 | Phase 3 | Complete |
| TICK-12 | Phase 3 | Complete |
| COLL-01 | Phase 3 | Complete |
| COLL-02 | Phase 3 | Complete |
| COLL-03 | Phase 3 | Complete |
| COLL-04 | Phase 3 | Complete |
| MAIL-01 | Phase 4 | Pending |
| MAIL-02 | Phase 4 | Pending |
| MAIL-03 | Phase 4 | Pending |
| MAIL-04 | Phase 4 | Pending |
| MAIL-05 | Phase 4 | Pending |
| MAIL-06 | Phase 4 | Pending |
| DASH-01 | Phase 5 | Pending |
| DASH-02 | Phase 5 | Pending |
| DASH-03 | Phase 5 | Pending |
| DASH-04 | Phase 5 | Pending |
| DASH-05 | Phase 5 | Pending |
| WEB-01 | Phase 6 | Pending |
| WEB-02 | Phase 6 | Pending |
| WEB-03 | Phase 6 | Pending |
| WEB-04 | Phase 6 | Pending |
| WEB-05 | Phase 6 | Pending |
| WEB-06 | Phase 7 | Pending |
| WEB-07 | Phase 7 | Pending |
| QUAL-01 | Phase 7 | Pending |
| QUAL-02 | Phase 7 | Pending |
| QUAL-03 | Phase 7 | Pending |
| QUAL-04 | Phase 7 | Pending |
| QUAL-05 | Phase 7 | Pending |

**Coverage:**

- v1 requirements: 55 total
- Mapped to phases: 55
- Unmapped: 0

---
*Requirements defined: 2026-09-02*
*Last updated: 2026-09-02 after roadmap creation*
