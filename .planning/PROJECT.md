# SolveIT

## What This Is

SolveIT é um sistema web de abertura e gerenciamento de chamados de suporte técnico, construído do zero como um projeto educacional. Solicitantes registram e acompanham chamados, atendentes conduzem o atendimento e administradores gerenciam operação, usuários, categorias e indicadores.

## Core Value

Permitir que um chamado percorra seu fluxo completo com autorização correta, histórico auditável e comunicação clara entre todos os envolvidos.

## Requirements

### Validated

- ✓ A API Java 17/Spring Boot 3 compila e é empacotada pelo Maven Wrapper — Phase 1
- ✓ O modelo JPA representa Usuario, Categoria, Chamado, Comentario e HistoricoStatus com enums e relações canônicas — Phase 1
- ✓ Um MySQL 8.4 vazio recebe V1..V5 pelo Flyway com constraints e índices versionados — Phase 1
- ✓ Hibernate valida o schema gerenciado pelo Flyway sem gerar DDL — Phase 1

### Active

- [ ] Autenticar usuários com JWT e aplicar autorização por perfil.
- [ ] Administrar usuários e categorias com regras de acesso e ativação.
- [ ] Abrir, consultar, filtrar, atribuir, editar e atualizar chamados conforme o fluxo permitido.
- [ ] Registrar comentários imutáveis e todo histórico de mudanças de status.
- [ ] Enviar notificações por e-mail nos eventos definidos.
- [ ] Exibir indicadores operacionais para administradores e atendentes.
- [ ] Fornecer uma interface React responsiva e adequada a cada perfil.
- [ ] Cobrir regras críticas com testes e revisar a segurança dos endpoints.

### Out of Scope

- Reabertura de chamados fechados ou cancelados — o processo exige a criação de um novo chamado.
- Edição ou exclusão de comentários — a imutabilidade é necessária para auditoria.
- Cadastro público de atendentes ou administradores — somente administradores criam esses perfis.
- Aplicativo móvel nativo — a primeira versão será web responsiva.
- Funcionalidades fora das rotas, entidades e regras descritas no documento de origem — dependem de decisão explícita futura.

## Context

- Projeto greenfield e Build to Learn; aprendizagem de Spring Boot, APIs REST e fundamentos de MySQL é parte central do resultado.
- O desenvolvedor tem boa base em Java e orientação a objetos, experiência profissional com PHP/MySQL e está iniciando em Spring Boot e MySQL nesse contexto.
- Cada implementação deve ser explicada em linguagem acessível, incluindo o motivo das decisões e conceitos de banco de dados quando relevantes.
- O domínio possui três perfis: SOLICITANTE, ATENDENTE e ADMIN.
- As entidades centrais são Usuario, Categoria, Chamado, Comentario e HistoricoStatus.
- O fluxo permitido é ABERTO → EM_ANDAMENTO → RESOLVIDO → FECHADO, com cancelamento de ABERTO pelo solicitante e de EM_ANDAMENTO pelo administrador.
- Toda mudança de status deve ser atômica com a criação do respectivo histórico.
- O documento de origem define as rotas REST, notificações, estrutura do back-end e uma ordem inicial de desenvolvimento.
- A direção visual do front-end está registrada em `docs/design/SOLVEIT-UI-REFERENCE.md`: tema escuro, destaque laranja, navegação lateral e telas densas baseadas em cards, tabelas e gráficos.
- O fluxo de versionamento está registrado em `docs/GIT-WORKFLOW.md`: uma branch por ticket, commits atômicos, push após validação e integração na `main` somente com aprovação.

## Constraints

- **Back-end**: Java 17 e Spring Boot 3 — stack definida pelo projeto.
- **Banco de dados**: MySQL 8 com Flyway — esquema versionado e aprendizagem explícita de dados relacionais.
- **Autenticação**: JWT com senhas BCrypt — API stateless e armazenamento seguro de credenciais.
- **Notificações**: JavaMailSender via SMTP — canal obrigatório para eventos de chamado.
- **Front-end**: React, Tailwind CSS, React Router e Axios — stack definida para a aplicação web.
- **Arquitetura**: organização em config, controller, dto, entity, enums, exception, repository, security e service — consistência pedagógica e separação de responsabilidades.
- **Processo**: planejar e explicar antes de implementar; executar uma tarefa por vez; consultar ambiguidades — preservar o caráter Build to Learn.
- **Segurança**: autorização por perfil e por relação com o chamado deve ser validada no servidor — restrições de UI não são suficientes.
- **Auditoria**: comentários são imutáveis e mudanças de status sempre geram HistoricoStatus — rastreabilidade obrigatória.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| API REST em Spring Boot 3 sobre Java 17 | Stack escolhida para aprendizado e implementação do domínio | ✓ Validated — Phase 1 |
| MySQL 8 com migrações Flyway numeradas | Garantir esquema reproduzível e ensinar evolução de banco | ✓ Validated — Phase 1 |
| JWT e BCrypt para autenticação | Separar autenticação stateless de armazenamento seguro de senha | — Pending |
| Regras de transição concentradas na camada de serviço | Manter invariantes do domínio fora dos controllers e permitir testes unitários | — Pending |
| Front-end React responsivo com Tailwind | Entregar experiências específicas por perfil em uma aplicação web única | — Pending |
| Desenvolvimento incremental e explicativo | O objetivo principal inclui compreensão, não apenas entrega | — Pending |
| Usar `docs/design/SOLVEIT-UI-REFERENCE.md` como contrato visual da Fase 6 | Preservar a aparência aprovada sem transformar elementos ilustrativos em novos requisitos | — Pending |
| Adotar branch e push por ticket conforme `docs/GIT-WORKFLOW.md` | Permitir revisão, rollback e continuidade segura entre computadores sem levar trabalho incompleto à `main` | — Pending |
| Fixar Maven 3.9.16 via Wrapper com checksum SHA-256 | Reproduzir o build sem depender de Maven global e proteger a distribuição baixada | ✓ Validated — Phase 1 |
| Manter Flyway como única autoridade DDL e Hibernate em `validate` | Evitar schema implícito e detectar divergência entre SQL e JPA | ✓ Validated — Phase 1 |
| Persistir enums por nome e usar `@Version` em Chamado | Preservar semântica dos dados e rejeitar lost updates concorrentes | ✓ Validated — Phase 1 |
| Usar FKs `RESTRICT` e superfície append-only para registros auditáveis | Impedir exclusões em cascata e preparar enforcement nos services futuros | ✓ Validated — Phase 1 |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (internal GSD step after `$gsd-execute-phase`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `$gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-09-04 after Phase 1 verification*
