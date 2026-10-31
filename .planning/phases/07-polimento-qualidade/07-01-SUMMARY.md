---
phase: 07-polimento-qualidade
plan: "01"
subsystem: api + infra
tags: [spring-mvc, problem-details, docker, docker-compose, nginx, mailpit]

requires:
  - phase: 06-frontend
    plan: "05"
    provides: aplicação web completa para os três perfis
provides:
  - Handler genérico de exceções (500 seguro com errorId; 404/405 no formato do SolveIT; 401/403 preservados)
  - Dockerfiles multi-stage da API e do front-end
  - docker-compose com API, web (nginx), MySQL 8.4 e Mailpit
  - Seção "Rodar tudo com Docker" no README
affects: [07-02]

ticket:
  number: 30
  branch: ticket/30-docker-erros
  pr: ver STATE.md

actuals:
  tasks: 3
  commits: 7
  executor: Claude Code (sem GSD)

tech-stack:
  added: [nginx 1.28.3, mailpit v1.33.0, eclipse-temurin 17.0.20_8]
  patterns:
    - "@ExceptionHandler(Exception.class) que relança AccessDeniedException/AuthenticationException para a cadeia do Spring Security"
    - Exceções ErrorResponse mantêm o status HTTP com texto fixo (nunca a mensagem original)
    - Segredos obrigatórios com ${VAR:?mensagem} no compose
    - Front-end e /api na mesma origem via nginx (sem CORS)

key-files:
  created:
    - solveit-api/src/test/java/com/solveit/exception/GlobalExceptionHandlerIntegrationTest.java
    - solveit-api/Dockerfile
    - solveit-api/.dockerignore
    - solveit-web/Dockerfile
    - solveit-web/nginx.conf
    - solveit-web/.dockerignore
    - docker-compose.yml
    - .env.example
  modified:
    - solveit-api/src/main/java/com/solveit/exception/GlobalExceptionHandler.java
    - solveit-api/src/main/java/com/solveit/exception/ApiErrorCode.java (METHOD_NOT_ALLOWED, REQUEST_INVALID)
    - README.md

key-decisions:
  - "D-1 a D-7 aprovadas sem alteração."
  - "Novos códigos METHOD_NOT_ALLOWED (405) e REQUEST_INVALID (outros 4xx do Spring), com texto fixo."
  - "O 500 registra o stack trace completo no log do servidor com o mesmo errorId da resposta; nada disso vai ao cliente."
  - "A imagem da API instala unzip para que o Maven Wrapper mantenha a verificação SHA-256 da distribuição (ver Bugs)."
  - ".gitignore já ignorava .env e permitia .env.example; não precisou de mudança."

requirements-completed: [QUAL-04]
requirements-partial: []

coverage:
  - id: D1
    description: "500 sem vazamento com errorId; 403 por perfil, 404 de recurso e de rota, 405 e 401 preservados."
    requirement: "QUAL-04"
    verification:
      - kind: integration
        ref: "solveit-api/src/test/java/com/solveit/exception/GlobalExceptionHandlerIntegrationTest.java"
        status: pass
  - id: D2
    description: "Compose sobe os quatro serviços a partir de um clone limpo; jornada completa pelo nginx com e-mail no Mailpit."
    requirement: "critério 6 da Fase 7"
    verification:
      - kind: manual
        ref: "Clone limpo da branch no Windows; docker compose up --build; jornada via http://localhost:8080"
        status: pass
    human_judgment: false
---

# Plano 07-01 — Entrega reproduzível e erros consistentes

## Resultado

Quem clona o repositório copia o `.env.example`, preenche dois segredos e roda `docker compose up --build -d`: sobem MySQL 8.4, Mailpit, API e o front-end servido por nginx em `http://localhost:8080`. Qualquer exceção não prevista da API vira um 500 no formato Problem Details do SolveIT, com mensagem fixa e um `errorId` que também aparece no log; 404 e 405, que antes saíam sem corpo, agora seguem o mesmo formato. 401 e 403 continuam exatamente como antes.

## Bugs e ajustes encontrados

1. **Build da API falhava num clone limpo** com `Failed to validate Maven distribution SHA-256`. Primeira hipótese (fim de linha CRLF no `maven-wrapper.properties`) estava **errada**, embora a normalização de CRLF continue útil e tenha ficado; o commit `be0bcf7` registra essa hipótese. A causa real, achada com `MVNW_VERBOSE=true` dentro da imagem: sem `unzip`, o `mvnw` baixa o Maven em `.tar.gz`, cujo hash difere do `.zip` fixado no projeto. Corrigido instalando `unzip` na etapa de build (`0adaac6`), mantendo a verificação de integridade em vez de removê-la.
2. **Suíte em segundo plano que não rodou:** a primeira execução falhou ao iniciar (`./mvnw: No such file`) e saiu com código 0 por causa do `grep` final; foi refeita a partir do diretório certo antes de qualquer conclusão.
3. **Contagem de testes no README** estava desatualizada (235); corrigida para 241.

## Verificação

- `mvnw test`: **241 testes, 0 falhas** (236 anteriores + 5 do handler), Testcontainers MySQL 8.4.
- Front-end sem mudança de código.
- **Compose a partir de um clone limpo da branch no Windows** (mvnw com CRLF no checkout):
  - Quatro serviços de pé; API subiu só depois do MySQL `healthy`; Flyway aplicou V1–V9.
  - Portas publicadas: apenas `127.0.0.1:8080` (web) e `127.0.0.1:8025` (Mailpit). MySQL e API só na rede interna. API roda como `uid=10001(solveit)`.
  - Sem `.env`, `docker compose config` recusa com "Defina SOLVEIT_JWT_SECRET no arquivo .env (Base64, 32+ bytes)".
  - Pelo nginx: cadastro de três usuários (201); primeiro ADMIN por `docs/PRIMEIRO-ADMIN.md` via `docker compose exec mysql` (`admins_ativos = 0`, `linhas_alteradas = 1`); ADMIN tornou Ari atendente; Sol abriu chamado; Ari assumiu. Mailpit recebeu o e-mail de chamado novo (ADMIN) e o de chamado assumido (Sol).
  - 405 real no formato novo (`METHOD_NOT_ALLOWED`); rota de API desconhecida sem token responde 401 (a segurança vem antes do roteamento).
  - `/chamados/1` recarregada devolve o `index.html` (200); `index.html` com `no-cache`, `/assets/*` com cache de 1 ano; cabeçalho `Server: nginx` sem versão.
  - Tela do chamado no navegador mostrou responsável e histórico; horários convertidos de UTC corretamente.
  - `docker compose down -v` removeu containers, rede e volume; clone e senha de teste apagados.
