---
phase: 04-notificacoes-email
plan: "01"
subsystem: api
tags: [spring-mail, application-events, transactional-event-listener, async, greenmail, github-actions]

requires:
  - phase: 03-core-de-chamados
    plan: "10"
    provides: Todas as operações de chamado e a devolução à fila
provides:
  - Notificações por e-mail de abertura, atribuição, mudança de status e comentário
  - Envio após commit e fora da requisição, com falha de SMTP isolada
  - CI do back-end no GitHub Actions (antecipado da Fase 7)
affects: [05-dashboard, 06-front-end, 07-polimento]

ticket:
  number: 23
  branch: ticket/23-notificacoes-email
  pr: "#25"

actuals:
  tasks: 3
  commits: 5
  executor: Claude Code (sem GSD)

tech-stack:
  added: [spring-boot-starter-mail, greenmail-junit5 2.1.2, GitHub Actions]
  patterns:
    - Evento publicado na transação com destinatários, assunto e corpo já resolvidos
    - "@TransactionalEventListener(AFTER_COMMIT) + @Async em executor limitado"
    - Rejeição do executor registrada em log, nunca propagada à requisição
    - Um e-mail por destinatário

key-files:
  created:
    - solveit-api/src/main/java/com/solveit/config/AsyncConfig.java
    - solveit-api/src/main/java/com/solveit/service/notificacao/EmailNotificacao.java
    - solveit-api/src/main/java/com/solveit/service/notificacao/NotificacaoPublisher.java
    - solveit-api/src/main/java/com/solveit/service/notificacao/EmailNotificacaoListener.java
    - solveit-api/src/test/java/com/solveit/service/notificacao/NotificacaoEmailIntegrationTest.java
    - solveit-api/src/test/java/com/solveit/service/notificacao/NotificacaoSmtpIndisponivelIntegrationTest.java
    - .github/workflows/backend-ci.yml
  modified:
    - solveit-api/pom.xml
    - solveit-api/src/main/resources/application.yml
    - solveit-api/src/test/resources/application-test.yml
    - solveit-api/src/main/java/com/solveit/service/ChamadoService.java
    - solveit-api/src/main/java/com/solveit/service/ComentarioService.java
    - solveit-api/src/main/java/com/solveit/service/UsuarioService.java
    - solveit-api/src/main/java/com/solveit/repository/UsuarioRepository.java
    - README.md

key-decisions:
  - "Aprovadas D-A (autor não recebe a própria ação), D-B (comentário: solicitante + atendente atual) e D-C (CRITICA com [URGENTE] no assunto)."
  - "Pacote service.notificacao em vez de com.solveit.notificacao, para respeitar a organização fixa de pacotes do AGENTS.md; MailProperties dispensada (dois @Value)."
  - "spring.mail.default-encoding UTF-8: sem ele, corpos com acento falhavam no envio (descoberto pelos testes)."
  - "Teste de SMTP indisponível em classe própria apontando para porta sem servidor, em vez de parar e religar o GreenMail."
  - "CI antecipado da Fase 7 por decisão do responsável, para validar a suíte fora da máquina Windows atual."

requirements-completed: [MAIL-01, MAIL-02, MAIL-03, MAIL-04, MAIL-05, MAIL-06]
requirements-partial: []

coverage:
  - id: D1
    description: "Abertura para ADMINs ativos; CRITICA urgente; atribuição; status (inclui devolução); comentário sem duplicatas; autor e inativos excluídos; ações recusadas não enviam."
    requirement: "MAIL-01..05"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/service/notificacao/NotificacaoEmailIntegrationTest.java"
        status: pass (CI)
    human_judgment: false
  - id: D2
    description: "MAIL-06: transação desfeita (trigger) não envia; SMTP indisponível não desfaz nem falha a operação."
    requirement: "MAIL-06"
    verification:
      - kind: integration
        ref: "NotificacaoEmailIntegrationTest#rolledBackOpeningSendsNothing; NotificacaoSmtpIndisponivelIntegrationTest"
        status: pass (CI)
    human_judgment: false
---

# Plano 04-01 — Notificações por e-mail

## Resultado

| Evento | Destinatários |
|---|---|
| Chamado aberto | ADMINs ativos (`[URGENTE]` se CRITICA) |
| Atendente atribuído | Atendente e solicitante |
| Status alterado (inclui devolução à fila) | Solicitante |
| Comentário | Solicitante e atendente atual |

Sempre sem o autor da ação, sem inativos e sem duplicatas; uma mensagem por destinatário.

## Commits

| Commit | Tipo | Descrição |
|---|---|---|
| `9c311bb` | docs | Plano 04-01 |
| `1264bb8` | feat | Eventos, publisher, listener assíncrono, configuração e testes |
| CI | ci | Workflow `backend-ci.yml` |
| docs | docs | README, SUMMARY e documentos de planejamento |

## Bugs encontrados pelos testes

1. Corpos com acento falhavam no envio sem `default-encoding: UTF-8`.
2. Rejeição do executor assíncrono chegaria à requisição depois do commit (500 com dados gravados); passou a ser apenas registrada em log.

## Diagnóstico do ambiente local

Na máquina Windows de desenvolvimento, alguns envios SMTP para `localhost` falhavam com `WSAECONNABORTED` ("conexão anulada pelo software no computador host"), de forma intermitente, assim como os 4 testes de `AuthHttpErrorIntegrationTest` (loopback) falhavam desde a Fase 2. O CI no Linux executou **229/229** com sucesso, provando que ambas as falhas são do ambiente local e não do código. A pendência de loopback foi encerrada.

## Verificação

- GitHub Actions (ubuntu-latest, Java 17, MySQL 8.4 via Testcontainers): **229 testes, 0 falhas**, `BUILD SUCCESS`.
