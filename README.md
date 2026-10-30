<div align="center">

# SolveIT

### Chamados de suporte com autorização correta, histórico auditável e comunicação clara

![Status: Fase 4 concluída](https://img.shields.io/badge/status-fase%204%20conclu%C3%ADda-22c55e)
[![Back-end CI](https://github.com/Morais-Felipe/SolveIT/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/Morais-Felipe/SolveIT/actions/workflows/backend-ci.yml)
![Abordagem: Build to Learn](https://img.shields.io/badge/abordagem-Build%20to%20Learn-f97316)
![Java 17](https://img.shields.io/badge/Java-17-007396)
![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3-6DB33F)
![React](https://img.shields.io/badge/React-planejado-61DAFB)
![MySQL 8](https://img.shields.io/badge/MySQL-8-4479A1)

</div>

> [!IMPORTANT]
> **Fases 3 e 4 concluídas.** Além da autenticação JWT, a API administra usuários (inclusive ativação e troca de perfil com devolução auditada dos chamados) e categorias e conduz o chamado por todo o fluxo: abertura, consulta e filtros, edição, atribuição, resolução, fechamento, cancelamento, comentários e histórico auditável. Os eventos do chamado notificam os envolvidos por e-mail depois do commit. A suíte tem 229 testes contra MySQL 8.4 real (Testcontainers) e roda no GitHub Actions a cada PR. Próximas fases: dashboard, front-end React e polimento.

## O que é o SolveIT

O SolveIT é um sistema web em construção para abertura e gerenciamento de chamados de suporte técnico. Solicitantes registram e acompanham problemas, atendentes conduzem o atendimento e administradores gerenciam a operação, os usuários, as categorias e, futuramente, os indicadores.

O projeto nasce como uma aplicação educacional construída do zero. Seu valor central é permitir que um chamado percorra todo o ciclo com autorização validada no servidor, comunicação entre os envolvidos e uma trilha de auditoria que explique cada mudança de status.

As entidades centrais são `Usuario`, `Categoria`, `Chamado`, `Comentario` e `HistoricoStatus`. Os contratos completos estão no [documento de origem](SolveIT_GSD_SystemPrompt.md) e nos [requisitos do v1](.planning/REQUIREMENTS.md).

## Por que o SolveIT

O projeto combina um problema de negócio reconhecível com fundamentos importantes de desenvolvimento web: modelagem relacional, APIs REST, autenticação stateless, autorização contextual, transações, concorrência, migrações, notificações e uma SPA responsiva.

O objetivo não é apenas chegar a uma aplicação funcional. A abordagem **Build to Learn** exige que cada incremento seja pequeno, explicado e verificável, incluindo o motivo das decisões de Spring Boot e os conceitos de banco de dados usados na solução.

## Estado atual

| Item | Situação |
| --- | --- |
| Estado do repositório | Fases 1 a 4 concluídas e integradas na `main`; CI no GitHub Actions |
| Fases | 4 de 7 concluídas |
| Requisitos do v1 | 38 de 55 concluídos (back-end de chamados e notificações) |
| Foco atual | Fase 5 — dashboard com indicadores (uma rota, escopo revisado em 2026-10-30) |
| Aplicação executável | API Spring Boot executável localmente com MySQL configurado |
| API disponível | Auth, usuários, categorias, chamados, comentários e histórico (ver [Contrato de API](#contrato-de-api)) |
| Telas | Ainda não existem; a SPA React é a Fase 6 |
| Processo | Planos em `.planning/` executados com Claude Code, um ticket/branch/PR por plano e modo pedagógico |

Tudo o que aparece como implementado neste README está coberto por testes de integração com MySQL real. Dashboard, front-end e polimento final continuam como **contratos planejados**.

## Como acompanhar o projeto

- Consulte o [estado atual](.planning/STATE.md) para saber o ponto de continuidade do trabalho.
- Acompanhe a ordem e os critérios de cada incremento no [roadmap](.planning/ROADMAP.md).
- Verifique o escopo e a rastreabilidade nos [requisitos](.planning/REQUIREMENTS.md).
- Veja o que cada plano entregou nos resumos `NN-PP-SUMMARY.md` dentro de [`.planning/phases/`](.planning/phases/).
- Leia o [fluxo Git por ticket](docs/GIT-WORKFLOW.md) antes de iniciar uma mudança.

O andamento só deve ser considerado entregue quando os artefatos de planejamento, os testes proporcionais e o histórico Git confirmarem a conclusão. O texto deste README deve evoluir junto com o estado real do repositório.

## Como verificar o back-end atual

Para compilar e executar os testes no Windows, tenha Java 17 e Docker Desktop com o engine Linux ativo. O Maven Wrapper fixa a versão do Maven do projeto, e o Testcontainers cria bancos MySQL 8.4 temporários para validar persistência, migrações, segurança, regras de negócio e concorrência — `DB_USERNAME` e `DB_PASSWORD` não são necessários nesse caminho.

```powershell
Set-Location solveit-api
.\mvnw.cmd clean verify
```

Em Linux ou macOS, use `./mvnw clean verify`. A suíte atual possui **229 testes** e leva cerca de 15 minutos, porque cada classe de integração sobe seu próprio MySQL. Ela cobre migrações, constraints, JWT, autorização por perfil e relação, filtros e paginação, transições de status, histórico, comentários, notificações por e-mail (com um servidor SMTP em memória, GreenMail) e disputas concorrentes reais entre transações.

A mesma suíte roda automaticamente no **GitHub Actions** ([`backend-ci.yml`](.github/workflows/backend-ci.yml)) a cada pull request e a cada push na `main`, em uma máquina Linux limpa.

> [!NOTE]
> Em algumas máquinas Windows, softwares do próprio host (antivírus, firewall ou VPN) anulam conexões para `localhost`. Nesse caso, os testes de `AuthHttpErrorIntegrationTest` e alguns testes de e-mail podem falhar localmente com `Unable to establish loopback connection` ou "conexão anulada pelo software no computador host". No CI todos os 229 testes passam; a falha é do ambiente, não do código.

Para iniciar a aplicação localmente, crie antes o database `solveit` em uma instância MySQL e forneça as credenciais pelo ambiente. O Flyway cria e versiona as tabelas (V1 a V9), mas deliberadamente não cria o database nem armazena credenciais no repositório.

```powershell
$env:DB_USERNAME = '<usuario-local>'
$env:DB_PASSWORD = '<senha-local>'
Set-Location solveit-api
.\mvnw.cmd spring-boot:run
```

`DB_URL` é opcional e, quando omitida, usa `jdbc:mysql://localhost:3306/solveit` com Unicode e UTC. Ainda não há `docker-compose.yml`; Docker é usado automaticamente pelos testes de integração.

O cadastro público cria apenas `SOLICITANTE`. Para ter o primeiro administrador em um banco novo, siga o procedimento manual e auditável de [primeiro ADMIN](docs/PRIMEIRO-ADMIN.md); não existe endpoint, *seed* nem senha padrão para isso.

### E-mails em ambiente local

Por padrão a aplicação envia e-mails para um SMTP em `localhost:1025`. Para ver as mensagens sem enviar nada de verdade, suba o [Mailpit](https://mailpit.axllent.org/), que oferece uma caixa de entrada web em `http://localhost:8025`:

```powershell
docker run -d --name solveit-mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit
```

| Variável | Padrão | Uso |
| --- | --- | --- |
| `SOLVEIT_MAIL_HOST` | `localhost` | Servidor SMTP |
| `SOLVEIT_MAIL_PORT` | `1025` | Porta SMTP |
| `SOLVEIT_MAIL_FROM` | `solveit@localhost` | Remetente |
| `SOLVEIT_MAIL_ENABLED` | `true` | `false` desliga os envios sem afetar a API |

Sem nenhum SMTP disponível a API continua funcionando normalmente: a falha de envio fica apenas no log. O `docker-compose` com aplicação, MySQL e Mailpit juntos está previsto para a Fase 7.

## Como funciona

Um `SOLICITANTE` abre um chamado em uma categoria ativa. O chamado nasce `ABERTO`, sem atendente, e no mesmo instante ganha seu primeiro `HistoricoStatus` (`null → ABERTO`). Um atendente assume o chamado da fila — ou um administrador o atribui — e ele passa a `EM_ANDAMENTO`. O responsável resolve, registrando `resolvidoEm`, e o solicitante encerra o ciclo ao fechar.

Durante o processo, pessoas autorizadas consultam o detalhe, comentam e acompanham o histórico. A API decide o acesso usando simultaneamente o perfil **atual** do usuário e sua relação **atual** com o chamado, relidos do banco a cada requisição; esconder um botão no front-end nunca é tratado como autorização.

Toda escrita relevante passa por uma trava global no banco (`core_write_guard`), que serializa decisões concorrentes. Cada transição válida atualiza o chamado e cria exatamente um `HistoricoStatus` na mesma transação, e formulários desatualizados são recusados pela versão esperada (`versaoEsperada`).

## Perfis e responsabilidades

| Perfil | Pode fazer hoje | Limites principais |
| --- | --- | --- |
| `SOLICITANTE` | Cadastrar-se, abrir e acompanhar os próprios chamados, editar título/descrição enquanto `ABERTO`, comentar, cancelar um chamado `ABERTO` e fechar um chamado `RESOLVIDO` | Não administra usuários ou categorias; não vê chamados alheios |
| `ATENDENTE` | Ver a fila (`ABERTO` sem responsável) e os chamados atribuídos a si, assumir chamados, comentar e resolver os que são seus | Só assume para si mesmo; não fecha nem cancela; perde o acesso quando o chamado passa a outro atendente |
| `ADMIN` | Administrar usuários e categorias, ver todos os chamados, atribuir atendentes, resolver e cancelar chamados `EM_ANDAMENTO`, editar chamados `ABERTO` | Não fecha chamados nem cancela chamados `ABERTO` (decisões do solicitante); continua sujeito ao fluxo de status e à auditoria |

O cadastro público cria exclusivamente usuários `SOLICITANTE`. Contas `ATENDENTE` e `ADMIN` são criadas por um administrador. Ativar/desativar usuários e trocar perfis acontece numa única operação administrativa, que devolve à fila os chamados em andamento de um atendente desativado.

## Regras críticas

Todas as regras abaixo estão implementadas e provadas por testes de integração, salvo indicação.

- Apenas solicitantes abrem chamados; todo chamado nasce `ABERTO`, sem atendente, com histórico inicial.
- A atribuição de um atendente muda o status automaticamente para `EM_ANDAMENTO`; a primeira atribuição confirmada vence.
- Apenas o atendente responsável ou um administrador pode mover `EM_ANDAMENTO` para `RESOLVIDO`.
- Apenas o solicitante proprietário pode mover `RESOLVIDO` para `FECHADO`.
- O solicitante proprietário pode cancelar um chamado `ABERTO`; somente um administrador pode cancelar um chamado `EM_ANDAMENTO`.
- Título e descrição só podem ser editados enquanto o chamado está `ABERTO`, pelo solicitante proprietário ou por um administrador.
- Chamados `FECHADO` ou `CANCELADO` não podem ser reabertos; um novo chamado deve ser criado.
- Comentários são imutáveis: não existe rota para editar ou excluir comentário, e comentar não reserva o chamado.
- Toda mudança de status cria exatamente um `HistoricoStatus`, atomicamente com a alteração do chamado; tentativas recusadas não geram histórico.
- Atualizações concorrentes não sobrescrevem silenciosamente uma transição já confirmada.
- `EM_ANDAMENTO → ABERTO` só acontece por devolução administrativa auditada, ao desativar ou retirar o perfil de um atendente.
- O sistema nunca fica sem um `ADMIN` ativo, inclusive sob operações simultâneas.

## Fluxo de status

```mermaid
flowchart LR
    A[ABERTO] -->|Atribuição: ATENDENTE para si ou ADMIN| E[EM_ANDAMENTO]
    E -->|Atendente responsável ou ADMIN| R[RESOLVIDO]
    R -->|Solicitante proprietário| F[FECHADO]
    A -->|Cancelamento pelo solicitante| C[CANCELADO]
    E -->|Cancelamento pelo ADMIN| C
    E -.->|Devolução administrativa: atendente desativado ou sem perfil| A
    F --- X[Reabertura proibida]
    C --- X
    X --> N[Criar um novo chamado]
```

Qualquer transição que não apareça no diagrama é rejeitada com `409 INVALID_STATE`; um ator sem permissão para a transição recebe `403`. A tabela de transições vive em `ChamadoTransitionPolicy` e é verificada por um teste que percorre todas as 150 combinações de origem, destino e ator.

### Notificações por e-mail

| Evento confirmado | Destinatários |
| --- | --- |
| Novo chamado `ABERTO` | Administradores ativos |
| Novo chamado `CRITICA` | Administradores ativos, com `[URGENTE]` no assunto |
| Atendente atribuído | Atendente e solicitante |
| Status atualizado (inclui devolução à fila) | Solicitante |
| Novo comentário | Solicitante e atendente atual, sem duplicatas |

Quem executa a ação não recebe e-mail dela, e usuários inativos nunca recebem. Os serviços publicam um evento dentro da transação; um listener com `@TransactionalEventListener(AFTER_COMMIT)` e `@Async` só envia depois que ela é confirmada, fora da thread da requisição. Uma transação desfeita não envia nada, e uma falha de SMTP é apenas registrada em log, sem desfazer nem atrasar a operação. Cada destinatário recebe uma mensagem própria, sem ver os demais endereços.

## Stack do projeto

| Camada | Tecnologias | Situação |
| --- | --- | --- |
| Back-end | Java 17, Spring Boot 3, Spring MVC | Implementado |
| Persistência | MySQL 8.4, Spring Data JPA, Hibernate, Specifications | Implementado |
| Migrações | Flyway (V1 a V9) | Implementado; único responsável pelo esquema |
| Segurança | Spring Security, JWT (JJWT), BCrypt | Implementado |
| Testes | JUnit, AssertJ, MockMvc, Testcontainers MySQL, GreenMail | Implementado (229 testes) |
| CI | GitHub Actions | Implementado — suíte completa a cada PR |
| Notificações | JavaMailSender via SMTP, eventos após commit, `@Async` | Implementado |
| Front-end | React, Tailwind CSS, React Router, Axios, Vite | Planejado — Fase 6 |
| Testes de interface | Vitest, React Testing Library, Playwright | Planejado — Fases 6 e 7 |

Segredos de banco, JWT e SMTP vêm do ambiente ou de um cofre de segredos; não são versionados em arquivos de configuração.

## Arquitetura do projeto

O back-end é uma aplicação modular única em Spring Boot. A separação por responsabilidade mantém regras de negócio nos services, contratos HTTP nos controllers e persistência nos repositories:

```text
solveit-api/
└── src/main/java/com/solveit/
    ├── config/
    ├── controller/
    ├── dto/
    ├── entity/
    ├── enums/
    ├── exception/
    ├── repository/
    ├── security/
    └── service/
```

- `config`: configuração de segurança, JWT e relógio UTC.
- `controller`: endpoints REST finos e validação da fronteira HTTP.
- `dto`: requests e responses em `record`s; requests rejeitam campos fora do contrato em vez de ignorá-los.
- `entity` e `enums`: modelo persistente e vocabulário do domínio; `Comentario` e `HistoricoStatus` não têm setters.
- `exception`: erros no formato Problem Details (RFC 9457), sem stack traces ou segredos.
- `repository`: consultas JPA, `Specification`s de visibilidade e filtros, paginação e ordenação no banco.
- `security`: filtro JWT, identidade autenticada e handlers de 401/403.
- `service`: transações, coordenação de escritas (`CoreWriteCoordinator`), política de leitura (`ChamadoAuthorizationService`), tabela de transições (`ChamadoTransitionPolicy`) e demais invariantes.

O front-end será uma SPA React separada, consumindo a API por uma instância Axios centralizada. As telas planejadas incluem login e cadastro, lista e abertura de chamados do solicitante, detalhe com comentários e histórico, fila do atendente, dashboard administrativo e administração de usuários e categorias. Essas telas ainda não existem.

## Contrato de API

As rotas marcadas como ✅ estão implementadas e testadas; as demais documentam contratos de fases futuras.

| Grupo | Método e rota | Acesso e finalidade | Estado |
| --- | --- | --- | --- |
| Auth | `POST /api/auth/register` | Público; cria apenas `SOLICITANTE` | ✅ |
| Auth | `POST /api/auth/login` | Público; autentica e retorna JWT limitado no tempo | ✅ |
| Auth | `GET /api/auth/me` | Autenticado; retorna os próprios dados | ✅ |
| Usuários | `GET /api/usuarios` · `GET /api/usuarios/{id}` | `ADMIN`; lista paginada e consulta por id | ✅ |
| Usuários | `POST /api/usuarios` | `ADMIN`; cria qualquer perfil, sempre ativo | ✅ |
| Usuários | `PUT /api/usuarios/{id}` | `ADMIN`; edita nome, e-mail e senha com versão esperada | ✅ |
| Usuários | `PATCH /api/usuarios/{id}/operacao` | `ADMIN`; muda perfil e atividade numa operação atômica, devolve à fila os chamados em andamento do atendente e protege o último ADMIN | ✅ |
| Categorias | `GET /api/categorias` · `GET /api/categorias/{id}` | Autenticado; ativas para todos; inativa por id para ADMIN ou quem vê chamado vinculado | ✅ |
| Categorias | `POST /api/categorias` · `PUT /api/categorias/{id}` | `ADMIN`; nome único, edição com versão esperada | ✅ |
| Categorias | `PATCH /api/categorias/{id}/ativo` | `ADMIN`; ativa ou desativa sem excluir | ✅ |
| Chamados | `GET /api/chamados` | Autenticado; página no escopo do perfil, filtros por status, prioridade, categoria, atendente e solicitante | ✅ |
| Chamados | `GET /api/chamados/{id}` | Autorizado por perfil e relação atual; detalhe completo | ✅ |
| Chamados | `POST /api/chamados` | `SOLICITANTE`; abre chamado com histórico inicial | ✅ |
| Chamados | `PUT /api/chamados/{id}` | Solicitante proprietário ou `ADMIN`; título e descrição somente em `ABERTO` | ✅ |
| Chamados | `PATCH /api/chamados/{id}/atendente` | `ATENDENTE` para si ou `ADMIN`; atribui e inicia atendimento | ✅ |
| Chamados | `PATCH /api/chamados/{id}/status` | Ator autorizado; resolver, fechar ou cancelar | ✅ |
| Comentários | `GET /api/chamados/{id}/comentarios` | Usuário com acesso; ordem cronológica | ✅ |
| Comentários | `POST /api/chamados/{id}/comentarios` | Usuário com acesso; comentário imutável | ✅ |
| Histórico | `GET /api/chamados/{id}/historico` | Usuário com acesso; trilha cronológica completa | ✅ |
| Dashboard | `GET /api/dashboard/resumo` | `ADMIN` e `ATENDENTE`; contadores operacionais | Fase 5 |
| Dashboard | `GET /api/dashboard/por-categoria` | `ADMIN` e `ATENDENTE`; chamados por categoria | Fase 5 |
| Dashboard | `GET /api/dashboard/por-atendente` | `ADMIN` e `ATENDENTE`; carga ativa por atendente | Fase 5 |
| Dashboard | `GET /api/dashboard/por-prioridade` | `ADMIN` e `ATENDENTE`; abertos por prioridade | Fase 5 |

Comandos que alteram recursos versionados exigem `versaoEsperada` e respondem `409 STALE_RESOURCE` quando o recurso mudou. Erros seguem Problem Details com um `code` estável (`VALIDATION_ERROR`, `ACCESS_DENIED`, `INVALID_STATE`, `COMMENTS_CLOSED`, `CORE_BUSY`, entre outros). Respostas nunca expõem senhas, entidades JPA, segredos ou stack traces, e listagens são calculadas no banco.

## Roadmap

As sete fases são incrementos sequenciais.

| Fase | Entrega | Estado |
| --- | --- | --- |
| 1 — Base do Projeto | Aplicação Spring inicial sobre esquema MySQL completo, versionado por Flyway e validado pelo JPA | Concluída — 5/5 planos |
| 2 — Autenticação | Cadastro de solicitante, login JWT e autorização por perfil e relação | Concluída — 8/8 planos |
| 3 — Core de Chamados | Administração, ciclo completo do chamado, comentários e histórico auditável | Concluída — 10/10 planos |
| 4 — Notificações por E-mail | Comunicação dos eventos confirmados aos envolvidos corretos | Concluída — 1/1 plano |
| 5 — Dashboard | Indicadores autorizados para administradores e atendentes | Pendente |
| 6 — Front-end (React + Tailwind) | Jornadas principais dos três perfis na SPA responsiva | Pendente |
| 7 — Polimento e Qualidade | Estados de interface, responsividade, testes e revisão de segurança | Pendente |

### Entregas da Fase 3

| Plano | Entrega | PR |
| --- | --- | --- |
| 03-01 | Catálogo de categorias e coordenação das escritas | [#13](https://github.com/Morais-Felipe/SolveIT/pull/13) |
| 03-02 | Edição e ativação/desativação de categorias | [#14](https://github.com/Morais-Felipe/SolveIT/pull/14) |
| 03-03 | Gestão de usuários pelo ADMIN | [#15](https://github.com/Morais-Felipe/SolveIT/pull/15) |
| 03-04 | Abertura de chamado com histórico atômico | [#16](https://github.com/Morais-Felipe/SolveIT/pull/16) |
| 03-05 | Consulta, filtros e edição no escopo correto | [#17](https://github.com/Morais-Felipe/SolveIT/pull/17) |
| 03-06 | Atribuição com um único vencedor | [#18](https://github.com/Morais-Felipe/SolveIT/pull/18) |
| 03-07 | Resolver, fechar e cancelar | [#19](https://github.com/Morais-Felipe/SolveIT/pull/19) |
| 03-08 | Comentários com acesso atual e concorrência segura | [#20](https://github.com/Morais-Felipe/SolveIT/pull/20) |
| 03-09 | Leitura de comentários e histórico; imutabilidade | [#21](https://github.com/Morais-Felipe/SolveIT/pull/21) |
| 03-10 | Ativação/perfil de usuários, devolução auditada e último ADMIN (sem prévia, após revisão de escopo) | Ticket 22 |

Metas, dependências, critérios de sucesso e rastreabilidade estão no [roadmap completo](.planning/ROADMAP.md). As Fases 4 a 7 ainda não foram detalhadas em planos.

### Pendências técnicas conhecidas

- Handler genérico de `Exception` que devolva `500 INTERNAL_ERROR` padronizado para falhas inesperadas.
- Rate limiting de login e cadastro antes de qualquer exposição à internet.

## Referência visual

![Referência visual planejada do SolveIT](docs/design/solveit-ui-reference.png)

A direção visual aprovada para a futura Fase 6 usa tema escuro, destaque laranja, navegação lateral, cards, tabelas densas, indicadores, gráficos e chips semânticos. A imagem é uma referência de composição desktop: seus textos e dados são ilustrativos e não criam novos requisitos, estados, telas ou relatórios.

Consulte o [contrato da referência visual](docs/design/SOLVEIT-UI-REFERENCE.md) para a tradução dos elementos ao domínio e seus limites.

## Fluxo Git por ticket

Cada plano de implementação usa um ticket com branch própria (`ticket/NN-descricao-curta`), depois de planejamento e aprovação antes da escrita de código. Durante o trabalho, mudanças verificáveis recebem commits atômicos no padrão Conventional Commits.

Após testes e resumo, há três gates independentes: autorização para fazer push da branch, autorização posterior para abrir o pull request e autorização posterior para fazer merge. Uma aprovação não implica as seguintes, salvo quando o responsável autoriza explicitamente as três ações na mesma mensagem. O procedimento completo está em [Fluxo Git por ticket](docs/GIT-WORKFLOW.md).

## Segurança e auditoria

- Senhas são armazenadas apenas como hash BCrypt e não aparecem em respostas ou logs.
- JWTs são temporários e carregam só a identidade; perfil e atividade são relidos do banco a cada requisição.
- Toda operação protegida verifica perfil e relação **atuais** com o recurso no servidor; autoria antiga de comentário não concede acesso.
- O cadastro público não cria `ATENDENTE` nem `ADMIN`; o primeiro ADMIN segue um [procedimento manual auditável](docs/PRIMEIRO-ADMIN.md).
- Categorias e usuários são desativados sem exclusão; o último ADMIN ativo é protegido.
- Mudança de status e criação do respectivo histórico ocorrem na mesma transação; uma falha no histórico desfaz a mudança.
- Escritas concorrentes são serializadas por uma trava no banco e por versão esperada, provadas com transações MySQL reais.
- Comentários são imutáveis e atribuídos à identidade autenticada.
- Requests rejeitam campos fora do contrato (como `autorId`, `status` ou datas) em vez de ignorá-los silenciosamente.
- Erros são normalizados sem vazar stack traces, entidades persistentes, credenciais ou detalhes internos.
- Testes negativos cobrem acesso sem token, perfil incorreto, recurso de outro usuário, transições proibidas e disputas concorrentes.

## Objetivos de aprendizagem

- Construir uma aplicação Spring Boot 3 em Java 17 com separação clara de responsabilidades.
- Modelar relações, chaves estrangeiras, restrições, índices e consultas paginadas em MySQL 8.
- Usar Flyway como fonte única e reproduzível do esquema, com JPA em modo de validação.
- Entender autenticação JWT, hash BCrypt e autorização além das restrições visuais.
- Projetar transações e locks que preservem invariantes e trilhas de auditoria sob concorrência.
- Integrar e-mail com comportamento de falha explícito e testar fronteiras externas.
- Criar uma SPA React responsiva orientada às jornadas de cada perfil.
- Evoluir em incrementos pequenos, explicados, testados e revisáveis.

## Limites de escopo

O v1 é limitado às entidades, rotas, regras, telas e indicadores registrados nos contratos atuais. Permanecem fora do escopo:

- anexos, base de conhecimento e entrada omnichannel;
- SLA avançado, escalonamento, CMDB e automação genérica;
- CSAT ou pesquisa de satisfação;
- exportação de listas ou relatórios;
- aplicativo móvel nativo;
- inteligência artificial;
- microserviços e broker de mensagens;
- fluxos de status configuráveis;
- edição ou exclusão de comentários;
- cadastro público de atendentes ou administradores;
- reabertura de chamados `FECHADO` ou `CANCELADO`.

Qualquer ampliação depende de uma decisão futura explícita e de atualização dos requisitos.

## Documentação

| Documento | Conteúdo |
| --- | --- |
| [Visão e decisões do projeto](.planning/PROJECT.md) | Valor central, contexto, restrições e decisões |
| [Requisitos e rastreabilidade](.planning/REQUIREMENTS.md) | Escopo v1, itens futuros e exclusões |
| [Roadmap](.planning/ROADMAP.md) | Sete fases, metas, dependências e critérios de sucesso |
| [Estado atual](.planning/STATE.md) | Posição, progresso, decisões e pontos de atenção |
| [Planos e resumos da Fase 3](.planning/phases/03-core-de-chamados/) | Planos, decisões aprovadas e evidências de cada entrega |
| [Contrato original do SolveIT](SolveIT_GSD_SystemPrompt.md) | Domínio, entidades, regras, rotas e ordem inicial |
| [Primeiro ADMIN](docs/PRIMEIRO-ADMIN.md) | Procedimento manual e auditável para o primeiro administrador |
| [Referência visual](docs/design/SOLVEIT-UI-REFERENCE.md) | Direção visual e limites da composição |
| [Imagem da referência visual](docs/design/solveit-ui-reference.png) | Composição versionada para consulta |
| [Fluxo Git por ticket](docs/GIT-WORKFLOW.md) | Branches, commits, validação, push e integração |

---

<div align="center">

**SolveIT — construir para aprender, sem perder segurança, rastreabilidade e clareza.**

</div>
