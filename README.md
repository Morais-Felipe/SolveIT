<div align="center">

# SolveIT

### Chamados de suporte com autorização correta, histórico auditável e comunicação clara

![Status: Planejamento](https://img.shields.io/badge/status-planejamento-6b7280)
![Abordagem: Build to Learn](https://img.shields.io/badge/abordagem-Build%20to%20Learn-f97316)
![Java 17](https://img.shields.io/badge/Java-17-007396)
![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3-6DB33F)
![React](https://img.shields.io/badge/React-planejado-61DAFB)
![MySQL 8](https://img.shields.io/badge/MySQL-8-4479A1)

</div>

> [!IMPORTANT]
> **Projeto em planejamento.** Nenhuma das sete fases foi implementada: o progresso atual é 0%, com a Fase 1 — Base do Projeto — pronta para ser planejada. Ainda não existem aplicação executável, comandos de instalação ou execução, endpoints disponíveis, build publicado ou deploy. As seções abaixo descrevem contratos e decisões planejados para o produto.

## O que é o SolveIT

O SolveIT será um sistema web para abertura e gerenciamento de chamados de suporte técnico. Solicitantes poderão registrar e acompanhar problemas, atendentes conduzirão o atendimento e administradores gerenciarão a operação, os usuários, as categorias e os indicadores.

O projeto nasce como uma aplicação educacional construída do zero. Seu valor central é permitir que um chamado percorra todo o ciclo com autorização validada no servidor, comunicação entre os envolvidos e uma trilha de auditoria que explique cada mudança de status.

As entidades centrais planejadas são `Usuario`, `Categoria`, `Chamado`, `Comentario` e `HistoricoStatus`. Os contratos completos estão no [documento de origem](SolveIT_GSD_SystemPrompt.md) e nos [requisitos do v1](.planning/REQUIREMENTS.md).

## Por que o SolveIT

O projeto combina um problema de negócio reconhecível com fundamentos importantes de desenvolvimento web: modelagem relacional, APIs REST, autenticação stateless, autorização contextual, transações, migrações, notificações e uma SPA responsiva.

O objetivo não é apenas chegar a uma aplicação funcional. A abordagem **Build to Learn** exige que cada incremento seja pequeno, explicado e verificável, incluindo o motivo das decisões de Spring Boot e os conceitos de banco de dados usados na solução.

## Estado atual

| Item | Situação |
| --- | --- |
| Estado do repositório | Planejamento |
| Fases implementadas | 0 de 7 |
| Progresso | 0% |
| Foco atual | Fase 1 — Base do Projeto |
| Aplicação executável | Não existe ainda |
| API e telas disponíveis | Não existem ainda |

Stack, arquitetura, endpoints, telas e fases descritos neste README são **contratos planejados**, não evidência de implementação concluída.

## Como acompanhar o projeto

- Consulte o [estado atual](.planning/STATE.md) para saber o ponto de continuidade do trabalho.
- Acompanhe a ordem e os critérios de cada incremento no [roadmap](.planning/ROADMAP.md).
- Verifique o escopo e a rastreabilidade nos [requisitos](.planning/REQUIREMENTS.md).
- Leia o [fluxo Git por ticket](docs/GIT-WORKFLOW.md) antes de iniciar uma mudança.

O andamento só deve ser considerado entregue quando os artefatos de planejamento, os testes proporcionais e o histórico Git confirmarem a conclusão. O texto deste README deve evoluir junto com o estado real do repositório.

## Como funciona

O fluxo planejado começa quando um `SOLICITANTE` abre um chamado em uma categoria ativa. O chamado nasce `ABERTO`, sem atendente, e recebe título, descrição e prioridade. A atribuição de um atendente inicia o trabalho, a resolução registra o momento de conclusão técnica e o solicitante encerra o ciclo ao confirmar o atendimento.

Durante o processo, pessoas autorizadas poderão consultar o detalhe, adicionar comentários e acompanhar o histórico. A API deverá decidir o acesso usando simultaneamente o perfil do usuário e sua relação com o chamado; esconder um botão no front-end nunca será tratado como autorização suficiente.

Cada transição válida deverá atualizar o chamado e criar exatamente um `HistoricoStatus` na mesma transação. Os eventos previstos também alimentarão notificações por e-mail depois da confirmação da transação.

## Perfis e responsabilidades

| Perfil | Responsabilidades planejadas | Limites principais |
| --- | --- | --- |
| `SOLICITANTE` | Cadastrar-se, abrir e acompanhar os próprios chamados, comentar, cancelar um chamado `ABERTO` e fechar um chamado `RESOLVIDO` | Não administra usuários ou categorias; não acessa chamados alheios sem relação permitida |
| `ATENDENTE` | Trabalhar na fila permitida, assumir chamados, comentar e resolver chamados sob sua responsabilidade | Não cria perfis privilegiados; só resolve o chamado atribuído a si, salvo regras de `ADMIN` |
| `ADMIN` | Administrar usuários e categorias, supervisionar chamados, atribuir atendentes, operar transições autorizadas e consultar indicadores | Continua sujeito ao fluxo de status e às invariantes de auditoria |

O cadastro público criará exclusivamente usuários `SOLICITANTE`. Contas `ATENDENTE` e `ADMIN` serão criadas por um administrador para impedir elevação indevida de privilégio.

## Regras críticas

- Apenas solicitantes abrem chamados; todo chamado nasce `ABERTO` e sem atendente.
- A atribuição de um atendente muda o status automaticamente para `EM_ANDAMENTO`.
- Apenas o atendente responsável ou um administrador pode mover `EM_ANDAMENTO` para `RESOLVIDO`.
- Apenas o solicitante proprietário pode mover `RESOLVIDO` para `FECHADO`.
- O solicitante proprietário pode cancelar um chamado `ABERTO`; somente um administrador pode cancelar um chamado `EM_ANDAMENTO`.
- Título e descrição só podem ser editados enquanto o chamado está `ABERTO`, pelo solicitante proprietário ou por um administrador.
- Chamados `FECHADO` ou `CANCELADO` não podem ser reabertos; um novo chamado deve ser criado.
- Comentários são imutáveis: não haverá rota para editar ou excluir comentário.
- Toda mudança de status deve criar exatamente um registro de `HistoricoStatus`, atomicamente com a alteração do chamado.
- Atualizações concorrentes não poderão sobrescrever silenciosamente uma transição já confirmada.

## Fluxo de status

```mermaid
flowchart LR
    A[ABERTO] -->|Atribuição de atendente| E[EM_ANDAMENTO]
    E -->|Atendente responsável ou ADMIN| R[RESOLVIDO]
    R -->|Solicitante proprietário| F[FECHADO]
    A -->|Cancelamento pelo solicitante| C[CANCELADO]
    E -->|Cancelamento pelo ADMIN| C
    F --- X[Reabertura proibida]
    C --- X
    X --> N[Criar um novo chamado]
```

Qualquer transição que não apareça no diagrama deverá ser rejeitada com uma resposta de erro clara.

### Notificações previstas

| Evento confirmado | Destinatários planejados |
| --- | --- |
| Novo chamado `ABERTO` | Administradores |
| Novo chamado `CRITICA` | Administradores, com destaque urgente adicional |
| Atendente atribuído | Atendente e solicitante |
| Status atualizado | Solicitante |
| Novo comentário | Todos os envolvidos, sem destinatários duplicados |

Os envios deverão ocorrer somente após a confirmação da transação. Uma falha de SMTP não poderá desfazer uma alteração válida do chamado; a estratégia definitiva de entrega será decidida no planejamento da Fase 4.

## Stack planejada

Nada nesta tabela está implementado no repositório ainda.

| Camada | Tecnologias planejadas | Papel no projeto |
| --- | --- | --- |
| Back-end | Java 17, Spring Boot 3, Spring MVC | API REST síncrona em um monólito modular |
| Persistência | MySQL 8, Spring Data JPA, Hibernate | Modelo relacional e acesso a dados |
| Migrações | Flyway | Único responsável por criar e evoluir o esquema |
| Segurança | Spring Security, JWT, BCrypt | Autenticação stateless e autorização no servidor |
| Notificações | JavaMailSender via SMTP | Comunicação dos eventos do chamado |
| Front-end | React, Tailwind CSS, React Router, Axios | SPA responsiva com jornadas por perfil |
| Testes | JUnit, Mockito, MockMvc, Testcontainers MySQL, Vitest e React Testing Library | Verificação de domínio, segurança, persistência e interface |

Segredos de banco, JWT e SMTP deverão vir do ambiente ou de um cofre de segredos; não serão versionados em arquivos de configuração.

## Arquitetura planejada

O back-end será um único deploy modular em Spring Boot. A separação por responsabilidade manterá regras de negócio nos serviços, contratos HTTP nos controllers e persistência nos repositories:

```text
backend/
└── src/main/java/.../solveit/
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

- `config`: configuração geral e integrações.
- `controller`: endpoints REST e validação da fronteira HTTP.
- `dto`: requests e responses sem exposição direta das entidades JPA.
- `entity` e `enums`: modelo persistente e vocabulário do domínio.
- `exception`: erros consistentes, sem stack traces ou segredos nas respostas.
- `repository`: consultas JPA, paginação, filtros e agregações executadas no banco.
- `security`: filtro JWT, identidade autenticada e regras de acesso.
- `service`: transações, autorização contextual, transições e demais invariantes.

O front-end será uma SPA React separada, consumindo a API por uma instância Axios centralizada. As telas planejadas incluem login e cadastro, lista e abertura de chamados do solicitante, detalhe com comentários e histórico, fila do atendente, dashboard administrativo e CRUD de usuários e categorias. Essas telas ainda não existem; sua aparência seguirá a referência da Fase 6 sem ampliar o escopo funcional.

## Contrato de API planejado

> **Endpoints ainda não disponíveis.** A tabela documenta o contrato pretendido e não pode ser usada como guia de execução da aplicação atual.

| Grupo | Método e rota planejados | Acesso e finalidade |
| --- | --- | --- |
| Auth | `POST /api/auth/register` | Público; cria apenas `SOLICITANTE` |
| Auth | `POST /api/auth/login` | Público; autentica e retorna JWT limitado no tempo |
| Auth | `GET /api/auth/me` | Autenticado; retorna os próprios dados |
| Usuários | `GET /api/usuarios` · `GET /api/usuarios/{id}` | `ADMIN`; lista paginada e consulta por id |
| Usuários | `POST /api/usuarios` · `PUT /api/usuarios/{id}` | `ADMIN`; cria qualquer perfil e edita usuário |
| Usuários | `PATCH /api/usuarios/{id}/status` | `ADMIN`; ativa ou desativa sem excluir |
| Categorias | `GET /api/categorias` · `GET /api/categorias/{id}` | Autenticado; consulta categorias permitidas |
| Categorias | `POST /api/categorias` · `PUT /api/categorias/{id}` | `ADMIN`; cria e edita com nome único |
| Categorias | `PATCH /api/categorias/{id}/status` | `ADMIN`; ativa ou desativa sem excluir |
| Chamados | `GET /api/chamados` | Autenticado; lista paginada no escopo permitido, com filtros por status, prioridade, categoria, atendente e solicitante |
| Chamados | `GET /api/chamados/{id}` | Autorizado por perfil e relação; detalhe completo |
| Chamados | `POST /api/chamados` | `SOLICITANTE`; abre chamado |
| Chamados | `PUT /api/chamados/{id}` | Solicitante proprietário ou `ADMIN`; edita título e descrição somente em `ABERTO` |
| Chamados | `PATCH /api/chamados/{id}/atribuir` | `ADMIN` ou o próprio `ATENDENTE`; atribui e inicia atendimento |
| Chamados | `PATCH /api/chamados/{id}/status` | Ator autorizado; aplica apenas transições válidas |
| Comentários | `GET /api/chamados/{id}/comentarios` | Usuário com acesso; lista em ordem cronológica |
| Comentários | `POST /api/chamados/{id}/comentarios` | Usuário com acesso; adiciona comentário imutável |
| Histórico | `GET /api/chamados/{id}/historico` | Usuário com acesso; lista a trilha cronológica |
| Dashboard | `GET /api/dashboard/resumo` | `ADMIN` e `ATENDENTE`; contadores operacionais |
| Dashboard | `GET /api/dashboard/por-categoria` | `ADMIN` e `ATENDENTE`; chamados por categoria |
| Dashboard | `GET /api/dashboard/por-atendente` | `ADMIN` e `ATENDENTE`; carga ativa por atendente |
| Dashboard | `GET /api/dashboard/por-prioridade` | `ADMIN` e `ATENDENTE`; abertos por prioridade |

Respostas nunca deverão expor senhas, entidades JPA, segredos ou stack traces. Listagens e indicadores deverão respeitar o escopo de acesso e ser calculados no banco, sem carregar todos os registros em memória.

## Roadmap

As sete fases são incrementos planejados e permanecem pendentes. A ordem é deliberada: primeiro a base reproduzível, depois identidade, fluxo de negócio, comunicação, leitura operacional, experiência web e validação integrada.

| Fase | Entrega planejada | Estado |
| --- | --- | --- |
| 1 — Base do Projeto | API inicial sobre esquema MySQL completo, versionado por Flyway e validado pelo JPA | Pendente |
| 2 — Autenticação | Cadastro de solicitante, login JWT e autorização por perfil e relação | Pendente |
| 3 — Core de Chamados | Administração, ciclo completo do chamado, comentários e histórico auditável | Pendente |
| 4 — Notificações por E-mail | Comunicação dos eventos confirmados aos envolvidos corretos | Pendente |
| 5 — Dashboard | Indicadores autorizados para administradores e atendentes | Pendente |
| 6 — Front-end (React + Tailwind) | Jornadas principais dos três perfis na SPA responsiva | Pendente |
| 7 — Polimento e Qualidade | Estados de interface, responsividade, testes e revisão de segurança | Pendente |

Metas, dependências, critérios de sucesso e rastreabilidade estão no [roadmap completo](.planning/ROADMAP.md). Nenhuma linha da tabela representa uma entrega já implementada.

## Referência visual

![Referência visual planejada do SolveIT](docs/design/solveit-ui-reference.png)

A direção visual aprovada para a futura Fase 6 usa tema escuro, destaque laranja, navegação lateral, cards, tabelas densas, indicadores, gráficos e chips semânticos. A imagem é uma referência de composição desktop: seus textos e dados são ilustrativos e não criam novos requisitos, estados, telas ou relatórios.

Consulte o [contrato da referência visual](docs/design/SOLVEIT-UI-REFERENCE.md) para a tradução dos elementos ao domínio e seus limites.

## Fluxo Git por ticket

Cada ticket deve ser desenvolvido em branch própria, depois de planejamento e aprovação antes da escrita de código. Durante o trabalho, mudanças verificáveis recebem commits atômicos no padrão Conventional Commits.

Após testes e resumo, a branch é enviada ao repositório remoto para revisão. A integração na `main` só ocorre com confirmação explícita do usuário; trabalho parcial pode receber um checkpoint e ser publicado na branch sem ser integrado. O procedimento completo está em [Fluxo Git por ticket](docs/GIT-WORKFLOW.md).

## Segurança e auditoria

- Senhas serão armazenadas apenas como hash BCrypt e nunca aparecerão em respostas ou logs.
- JWTs deverão ser temporários; tokens ausentes, inválidos, expirados ou ligados a usuários inativos serão rejeitados.
- Toda operação protegida verificará perfil e relação com o recurso no servidor.
- O cadastro público não poderá criar `ATENDENTE` nem `ADMIN`.
- Categorias e usuários serão desativados sem exclusão destrutiva.
- Mudança de status e criação do respectivo histórico ocorrerão na mesma transação.
- Comentários permanecerão imutáveis e atribuídos à identidade autenticada.
- Erros serão normalizados sem vazar stack traces, entidades persistentes, credenciais ou detalhes internos.
- Testes negativos deverão cobrir acesso sem token, perfil incorreto, recurso de outro usuário e transições proibidas.

## Objetivos de aprendizagem

- Construir uma aplicação Spring Boot 3 em Java 17 com separação clara de responsabilidades.
- Modelar relações, chaves estrangeiras, restrições, índices e consultas paginadas em MySQL 8.
- Usar Flyway como fonte única e reproduzível do esquema, com JPA em modo de validação.
- Entender autenticação JWT, hash BCrypt e autorização além das restrições visuais.
- Projetar transações que preservem invariantes e trilhas de auditoria.
- Integrar e-mail com comportamento de falha explícito e testar fronteiras externas.
- Criar uma SPA React responsiva orientada às jornadas de cada perfil.
- Evoluir em incrementos pequenos, explicados, testados e revisáveis.

## Limites de escopo

O v1 será limitado às entidades, rotas, regras, telas e indicadores registrados nos contratos atuais. Permanecem fora do escopo:

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
| [Contrato original do SolveIT](SolveIT_GSD_SystemPrompt.md) | Domínio, entidades, regras, rotas e ordem inicial |
| [Referência visual](docs/design/SOLVEIT-UI-REFERENCE.md) | Direção visual e limites da composição |
| [Imagem da referência visual](docs/design/solveit-ui-reference.png) | Composição versionada para consulta |
| [Fluxo Git por ticket](docs/GIT-WORKFLOW.md) | Branches, commits, validação, push e integração |

---

<div align="center">

**SolveIT — construir para aprender, sem perder segurança, rastreabilidade e clareza.**

</div>
