---
phase: 01-base-do-projeto
plan: 01
subsystem: api
tags: [java-17, spring-boot-3.5.16, maven-wrapper, mysql, flyway, testcontainers]

requires: []
provides:
  - Build reproduzível do módulo solveit-api com Java 17 e Maven Wrapper 3.9.16
  - Fundação Spring MVC/JPA/Flyway/MySQL com versões gerenciadas pelo Spring Boot 3.5.16
  - Configuração de desenvolvimento sem credenciais versionadas e com Flyway/Hibernate em responsabilidades separadas
affects: [01-02-enums, 01-03-entidades-jpa, 01-04-repositories-jpa, 01-05-flyway-validacao]

actuals:
  tokens: 6049
  tasks: 1
  commits: 2

tech-stack:
  added: [Java 17, Spring Boot 3.5.16, Maven Wrapper 3.3.4, Maven 3.9.16, Spring MVC, Spring Data JPA, Flyway 11.7.2, MySQL Connector/J 9.7.0, Testcontainers 1.21.4]
  patterns: [Spring Boot BOM-managed dependencies, environment-backed secrets, Flyway-only DDL, Hibernate schema validation]

key-files:
  created:
    - solveit-api/pom.xml
    - solveit-api/mvnw
    - solveit-api/mvnw.cmd
    - solveit-api/.mvn/wrapper/maven-wrapper.properties
    - solveit-api/src/main/java/com/solveit/SolveItApplication.java
    - solveit-api/src/main/resources/application.yml
    - solveit-api/src/main/resources/application-dev.yml
  modified:
    - .gitignore

key-decisions:
  - "Maven Wrapper 3.3.4 usa o modo only-script, fixa Maven 3.9.16 e valida a distribuição por SHA-256."
  - "DB_USERNAME e DB_PASSWORD não possuem defaults versionados; o ambiente fornece as credenciais."
  - "Flyway permanece habilitado como único dono do DDL e Hibernate usa exclusivamente ddl-auto=validate."

patterns-established:
  - "Build reproduzível: executar mvnw/mvnw.cmd em vez de depender de Maven global."
  - "Dependências do ecossistema: omitir versões individuais e usar o parent/BOM do Spring Boot."
  - "Configuração segura: versionar somente placeholders de variáveis de ambiente, nunca segredos reais."

requirements-completed: [DATA-01, DATA-04]

coverage:
  - id: D1
    description: "Módulo solveit-api empacota com Java 17, Spring Boot 3.5.16 e Maven 3.9.16 sem Maven global."
    requirement: DATA-01
    verification:
      - kind: integration
        ref: ".\\mvnw.cmd --version && .\\mvnw.cmd -q -DskipTests package"
        status: pass
    human_judgment: false
  - id: D2
    description: "Classpath contém somente a fundação aprovada e resolve versões compatíveis pelo BOM."
    requirement: DATA-01
    verification:
      - kind: other
        ref: ".\\mvnw.cmd dependency:tree -Dincludes=org.springframework.boot,org.flywaydb,com.mysql,org.testcontainers"
        status: pass
    human_judgment: false
  - id: D3
    description: "Perfil dev externaliza credenciais e mantém Flyway habilitado com Hibernate em validação."
    requirement: DATA-04
    verification:
      - kind: other
        ref: "Inspeção automatizada de application.yml e application-dev.yml"
        status: pass
    human_judgment: false

duration: 12min
completed: 2026-09-03
status: complete
---

# Phase 01 Plan 01: Bootstrap Spring Boot/Maven Summary

**API Spring Boot 3.5.16 reproduzível por Maven Wrapper 3.9.16, preparada para MySQL/Flyway sem credenciais versionadas**

## Performance

- **Duration:** 12 min
- **Started:** 2026-09-03T10:41:10Z
- **Completed:** 2026-09-03T10:52:38Z
- **Tasks:** 1
- **Files modified:** 8

## Accomplishments

- Criado `solveit-api/` com Java 17, Spring Boot 3.5.16 e um único entry point em `com.solveit`.
- Adicionado Maven Wrapper 3.3.4 no modo `only-script`, fixado em Maven 3.9.16 e protegido pelo SHA-256 oficial da distribuição.
- Configurado o perfil `dev` para MySQL com credenciais externas, Flyway habilitado, Hibernate em `validate`, Open Session in View desabilitado e timezone UTC.
- Limitado o `pom.xml` às nove dependências diretas aprovadas para a fundação e seus futuros testes MySQL/Testcontainers.

## Task Commits

1. **Task 1: Criar o scaffold Spring Boot/Maven e configurar MySQL com schema validado** - `57f1eaa` (`chore`)

## Files Created/Modified

- `.gitignore` - ignora somente a saída Maven local `solveit-api/target/`, preservando as regras existentes para `.env` e segredos.
- `solveit-api/pom.xml` - contrato de build Java 17/Spring Boot 3.5.16 e dependências BOM-managed.
- `solveit-api/mvnw` - script oficial do Wrapper para ambientes POSIX, marcado como executável no Git.
- `solveit-api/mvnw.cmd` - script oficial do Wrapper para Windows.
- `solveit-api/.mvn/wrapper/maven-wrapper.properties` - fixa Wrapper 3.3.4, Maven 3.9.16 e o checksum SHA-256 da distribuição.
- `solveit-api/src/main/java/com/solveit/SolveItApplication.java` - entry point canônico do Spring Boot.
- `solveit-api/src/main/resources/application.yml` - nome da aplicação e ativação externalizável do perfil `dev`.
- `solveit-api/src/main/resources/application-dev.yml` - conexão MySQL, política Flyway/JPA e timezone UTC.

## Conceitos explicados

### Por que usar Maven Wrapper

O Wrapper versiona os scripts de entrada do build e baixa a versão exata do Maven definida no projeto. Assim, outra máquina precisa apenas do JDK 17: `mvnw.cmd` instala Maven 3.9.16 no cache do usuário e repete o mesmo build, sem depender de uma instalação global possivelmente diferente.

### Por que deixar o Spring Boot gerenciar versões

O parent/BOM do Spring Boot mantém versões testadas em conjunto. Por isso o `pom.xml` informa versões apenas para a plataforma (Boot 3.5.16) e para Java 17; Flyway, Connector/J e Testcontainers herdam versões compatíveis, reduzindo conflitos entre bibliotecas.

### Por que as credenciais vêm do ambiente

Usuário e senha variam por máquina e ambiente e são informações sensíveis. `DB_USERNAME` e `DB_PASSWORD` são resolvidas somente em tempo de execução, enquanto o Git guarda apenas seus nomes. A URL tem um default local não secreto e também pode ser substituída por `DB_URL`.

### Por que Flyway cria e Hibernate apenas valida

Flyway aplicará scripts SQL numerados e auditáveis, tornando o banco reproduzível. `ddl-auto=validate` impede o Hibernate de criar ou alterar tabelas silenciosamente: ele compara o modelo JPA ao esquema existente e falha quando houver divergência. As migrações e a prova contra um MySQL vazio entram no Ticket 05.

## Verification Evidence

- `java -version` e `javac -version`: **PASS**, ambos em 17.0.20.1.
- `.\\mvnw.cmd --version`: **PASS**, Apache Maven 3.9.16 sobre Java 17.0.20.1.
- `.\\mvnw.cmd -q -DskipTests package`: **PASS**.
- `dependency:tree`: **PASS** com Spring Boot 3.5.16, Flyway 11.7.2, Connector/J 9.7.0 e Testcontainers 1.21.4.
- Inspeção do `pom.xml`: **PASS**, exatamente nove dependências diretas aprovadas; Security, JWT, Mail, Actuator, Validation, Lombok, MapStruct, H2 e frontend ausentes.
- Inspeção de configuração: **PASS**, `DB_USERNAME`/`DB_PASSWORD` sem valores, `flyway.enabled=true`, `ddl-auto=validate`, `open-in-view=false` e UTC.
- Revisão de escopo: **PASS**, exatamente os oito arquivos declarados no plano.
- Revisão de segredos e stubs: **PASS**, somente placeholders esperados e nenhum stub da aplicação.

### Coordenadas efetivamente resolvidas

```text
org.springframework.boot:spring-boot-starter-web:3.5.16
org.springframework.boot:spring-boot-starter-data-jpa:3.5.16
org.flywaydb:flyway-core:11.7.2
org.flywaydb:flyway-mysql:11.7.2
com.mysql:mysql-connector-j:9.7.0 (runtime)
org.springframework.boot:spring-boot-testcontainers:3.5.16 (test)
org.testcontainers:junit-jupiter:1.21.4 (test)
org.testcontainers:mysql:1.21.4 (test)
```

## Decisions Made

- Foi mantido exatamente o conjunto de dependências aprovado; recursos de autenticação, e-mail, observabilidade e frontend continuam nas fases próprias.
- O Wrapper usa somente scripts oficiais e valida a distribuição Maven baixada com `distributionSha256Sum`.
- O perfil `dev` exige credenciais por ambiente, evitando até mesmo uma senha local fictícia no repositório.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- O primeiro build precisou baixar o Maven e as dependências para o cache local, comportamento esperado do Wrapper.
- O atualizador automático do ROADMAP gerou texto duplicado na linha de planos; o tracking foi corrigido para um diff mínimo e coerente antes do commit de metadados.

## Known Stubs

None.

## User Setup Required

Para iniciar o perfil `dev`, o ambiente deverá fornecer `DB_USERNAME` e `DB_PASSWORD` e disponibilizar um MySQL 8.4 no endereço de `DB_URL`. Isso não é necessário para compilar ou empacotar o Ticket 01. Docker será pré-requisito apenas para a validação automatizada do Ticket 05.

## Next Phase Readiness

- O contrato de build está pronto para o Ticket 02 (enums).
- O avanço continua bloqueado pelos gates do protocolo: revisão local, autorização de push/PR, autorização separada de merge e prova em `origin/main`.
- Nenhum código do Ticket 02 ou posterior foi criado.

## Self-Check: PASSED

- Todos os oito arquivos declarados existem e pertencem ao commit `57f1eaa`.
- O commit `57f1eaa` está alcançável a partir de `HEAD` na branch `ticket/01-setup-spring-boot`.
- Build, árvore de dependências, configuração segura, escopo, segredos e stubs foram verificados novamente após o commit.

---
*Phase: 01-base-do-projeto*
*Completed: 2026-09-03*
