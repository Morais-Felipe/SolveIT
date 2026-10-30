<!-- GSD:project-start source:PROJECT.md -->

## Project

**SolveIT**

SolveIT é um sistema web de abertura e gerenciamento de chamados de suporte técnico, construído do zero como um projeto educacional. Solicitantes registram e acompanham chamados, atendentes conduzem o atendimento e administradores gerenciam operação, usuários, categorias e indicadores.

**Core Value:** Permitir que um chamado percorra seu fluxo completo com autorização correta, histórico auditável e comunicação clara entre todos os envolvidos.

### Constraints

- **Back-end**: Java 17 e Spring Boot 3 — stack definida pelo projeto.
- **Banco de dados**: MySQL 8 com Flyway — esquema versionado e aprendizagem explícita de dados relacionais.
- **Autenticação**: JWT com senhas BCrypt — API stateless e armazenamento seguro de credenciais.
- **Notificações**: JavaMailSender via SMTP — canal obrigatório para eventos de chamado.
- **Front-end**: React, Tailwind CSS, React Router e Axios — stack definida para a aplicação web.
- **Arquitetura**: organização em config, controller, dto, entity, enums, exception, repository, security e service — consistência pedagógica e separação de responsabilidades.
- **Processo**: planejar e explicar antes de implementar; executar uma tarefa por vez; consultar ambiguidades — preservar o caráter Build to Learn.
- **Segurança**: autorização por perfil e por relação com o chamado deve ser validada no servidor — restrições de UI não são suficientes.
- **Auditoria**: comentários são imutáveis e mudanças de status sempre geram HistoricoStatus — rastreabilidade obrigatória.

<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->

## Technology Stack

## Recommendation in One Sentence

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Java | 17 LTS | Back-end language/runtime | Locked by the project and the minimum Java version for Spring Boot 3.5; stable and appropriate for learning modern Spring without requiring a runtime upgrade. |
| Spring Boot | 3.5.16 | REST application, dependency management, configuration, production runtime | Use the latest maintained Boot 3 line rather than Boot 4 because the project explicitly locks Boot 3. Boot 3.5.16 requires Java 17 and manages a compatible Spring Framework 6.2.x stack. |
| Spring MVC | Boot-managed (`spring-boot-starter-web`) | Synchronous REST endpoints | The workload is request/response CRUD and transactional domain logic; MVC is simpler and more appropriate than WebFlux. |
| Spring Data JPA / Hibernate | Boot-managed | Relational persistence and repositories | Fits the entity-rich relational domain and preserves explicit SQL learning through Flyway migrations and query review. |
| Spring Security | Boot-managed (`spring-boot-starter-security`) | Authentication and server-side authorization | Standard integration point for stateless JWT filters, BCrypt, method/request authorization, and security tests. |
| MySQL | 8.4 LTS (latest 8.4 patch) | Production relational database | Treat the locked “MySQL 8” requirement as 8.4 LTS. MySQL 8.0 entered Sustaining Support in April 2026; a greenfield system should not begin on 8.0. |
| Flyway | Boot-managed 11.7.2 | Versioned schema migrations | Makes schema evolution reproducible. With Boot 3.5 and MySQL, include both `flyway-core` and `flyway-mysql`. |
| React / React DOM | 19.2.8 | Browser UI | Current stable React 19.2 patch line; suitable for a client-rendered internal application. Pin the same version for `react` and `react-dom`. |
| Vite | 8.2.2 | Front-end dev server and production build | Current stable build tool for a standalone React SPA; React officially deprecated Create React App for new applications. Requires Node 20.19+ or 22.12+. |
| Tailwind CSS | 4.3.3 | Responsive utility-first styling | Locked UI choice; v4 has a first-party Vite plugin and a simpler CSS-first setup. |

### Back-end Dependencies

| Library / starter | Version | Purpose | When to Use |
|-------------------|---------|---------|-------------|
| `spring-boot-starter-web` | Boot-managed | REST controllers, JSON, embedded Tomcat | All API endpoints. |
| `spring-boot-starter-data-jpa` | Boot-managed | JPA entities, transactions, repositories | Persistence for users, categories, tickets, comments, and status history. |
| `spring-boot-starter-validation` | Boot-managed | Jakarta Bean Validation | Validate request DTOs at the controller boundary; domain authorization remains in services. |
| `spring-boot-starter-security` | Boot-managed | Security filter chain, BCrypt, authorization | All authentication and endpoint access rules. Use the provided `BCryptPasswordEncoder`; do not add a separate BCrypt library. |
| `io.jsonwebtoken:jjwt-api` | 0.13.0 | JWT public API | Compile scope. Keep token construction/parsing behind one small security component. |
| `io.jsonwebtoken:jjwt-impl` | 0.13.0 | JJWT implementation | Runtime scope only. |
| `io.jsonwebtoken:jjwt-jackson` | 0.13.0 | JWT JSON serialization | Runtime scope; reuses Jackson already present through Spring Web. |
| `com.mysql:mysql-connector-j` | Boot-managed 9.7.0 | JDBC driver | Runtime scope. Let the Spring Boot BOM choose the driver version. |
| `org.flywaydb:flyway-core` | Boot-managed 11.7.2 | Migration engine | Run migrations at application startup. |
| `org.flywaydb:flyway-mysql` | Boot-managed 11.7.2 | MySQL database support for Flyway | Required alongside Flyway core for modern Flyway/MySQL integration. |
| `spring-boot-starter-mail` | Boot-managed | `JavaMailSender` and SMTP configuration | Email notifications. Configure finite connection/read/write timeouts because Boot documents that some mail defaults are infinite. |
| `spring-boot-starter-actuator` | Boot-managed | Health and readiness endpoints | Add when deployment/operations begins; expose only `health` initially and secure other actuator endpoints. |

### Front-end Dependencies

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `react-router-dom` | 7.18.3 | Client-side routing | Use **Declarative mode** (`BrowserRouter`, routes, navigation). Framework mode adds server/runtime concepts that this separate Spring API SPA does not need. |
| `axios` | 1.20.0 | HTTP client | Locked by the project; create one configured instance for base URL, auth header, error normalization, and cancellation. |
| `tailwindcss` + `@tailwindcss/vite` | 4.3.3 | Tailwind engine and first-party Vite integration | Use the Vite plugin and `@import "tailwindcss"`; do not copy Tailwind v3 PostCSS/config boilerplate. |
| `@vitejs/plugin-react` | 6.x | React Fast Refresh and JSX transformation | Use the version scaffolded for Vite 8; do not enable React Compiler initially. |

### Testing Stack

| Scope | Tools | Recommendation |
|-------|-------|----------------|
| Pure domain/service rules | JUnit Jupiter + AssertJ + Mockito from `spring-boot-starter-test` | Fast unit tests for allowed transitions, role/ownership rules, timestamps, history creation, and notification decisions. Mock repositories and mail ports only at this layer. |
| MVC/security contract | MockMvc + `spring-security-test` | Test unauthenticated, wrong-role, wrong-owner, and permitted requests through the real security filter chain. Do not settle for service-only authorization tests. |
| Persistence and migrations | `spring-boot-testcontainers`, Testcontainers MySQL, `@ServiceConnection`, `@DataJpaTest` / `@SpringBootTest` | Run MySQL 8.4 in a container and apply the real Flyway migrations. Boot 3.5.16 manages Testcontainers 1.21.4. This catches MySQL-specific SQL, indexes, constraints, enum/string mappings, and migration drift that H2 misses. |
| Mail boundary | GreenMail (test scope) or a local SMTP container such as Mailpit for developer smoke tests | Assert recipient, subject, and body without contacting a real provider. Keep most notification tests at the service/event decision level. Add GreenMail only when automated SMTP integration tests are implemented. |
| React components | Vitest 4.1.x + React Testing Library 16.3.x + `@testing-library/user-event` + jsdom | Test visible behavior, validation, role-specific actions, loading/error states, and navigation. Avoid testing component internals. |
| End-to-end critical path | Playwright (deferred until the vertical flow exists) | Cover a small set of cross-stack journeys: register/login, open ticket, assign, resolve, close, and forbidden access. Do not introduce it during initial scaffolding. |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Maven Wrapper | Reproducible back-end builds | Commit `mvnw`, `mvnw.cmd`, and `.mvn/wrapper`; use the Spring Boot parent/BOM and omit versions for managed dependencies. |
| Node.js | Front-end toolchain | Use Node 22 LTS at a release satisfying Vite's `22.12+` floor; commit the lockfile and declare the supported Node range. |
| Docker Compose | Local MySQL 8.4 and SMTP sink | Keep local infrastructure reproducible. Do not make Docker a requirement for pure unit tests. |
| Spring Boot configuration profiles | Environment separation | Keep secrets in environment variables; version only safe defaults. Use `dev`, `test`, and production environment configuration without committing credentials. |

## Installation Shape

## Database and Migration Policy

- Start on the latest MySQL **8.4 LTS patch**, with `utf8mb4` and an explicit collation chosen once for the schema.
- Make Flyway the **only** schema owner. Use `spring.jpa.hibernate.ddl-auto=validate` outside narrowly scoped throwaway tests; never use `update`, `create`, or `create-drop` for the real application schema.
- Keep production migrations in `src/main/resources/db/migration` with names such as `V1__create_usuario.sql`. Once applied to a shared environment, never edit a migration; add a new one.
- Use foreign keys, unique constraints, not-null constraints, useful indexes, and explicit column lengths in SQL. JPA annotations should mirror—not replace—the database contract.
- Run migrations against MySQL Testcontainers in CI. Do not use H2 as evidence that MySQL DDL or repository queries work.
- Store timestamps consistently (prefer UTC at the application boundary) and set JDBC/JVM timezone explicitly in deployment configuration.

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| Spring Boot 3.5.16 | Java 17–25; Spring Framework 6.2.19+ | Java 17 is valid and locked. Stay on Boot 3.5.x patches; do not cross to Boot 4 without a planned migration. |
| Spring Boot 3.5.16 BOM | Connector/J 9.7.0; Flyway 11.7.2; Testcontainers 1.21.4 | Prefer BOM-managed versions. Manual overrides create compatibility and security-maintenance work. |
| Flyway 11.7.2 | MySQL via `flyway-mysql` 11.7.2 | Include the database-specific module and keep it on exactly the same version as `flyway-core`. |
| MySQL 8.4 LTS | Connector/J 9.x | Meets the locked MySQL 8 constraint on a supported LTS server line. |
| JJWT 0.13.0 | Java 17 + Jackson | `jjwt-api` compile; `jjwt-impl` and `jjwt-jackson` runtime. No Bouncy Castle is required for the normal HMAC/RSA choices on Java 17. |
| React 19.2.8 | React DOM 19.2.8 | Keep versions identical. Use a patched 19.2 release, not early 19.0/19.1 versions affected by published React Server Components issues—even though this SPA does not use RSC. |
| Vite 8.2.2 | Node 20.19+ or 22.12+ | Standardize on Node 22 LTS for development and CI. |
| Tailwind CSS 4.3.3 | `@tailwindcss/vite` 4.3.3 + Vite 8 | Keep Tailwind packages aligned. Tailwind v4 targets modern browsers; verify organizational browser requirements before relying on its newest CSS features. |
| React Router DOM 7.18.3 | React 19 | Use declarative SPA mode; no React Router server adapter is needed. |

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| MySQL 8.4 LTS | MySQL 8.0 | Only when constrained by an existing managed environment; it is not a sound greenfield default after its April 2026 EOL transition. |
| Spring MVC | WebFlux | Only for a proven end-to-end reactive workload; blocking JPA and SMTP eliminate the expected benefit here. |
| JJWT 0.13.0 | Spring Security OAuth2 Resource Server JWT support | Prefer the latter when an external identity provider issues tokens or standards-based OAuth2/OIDC becomes a requirement. For the locked self-issued JWT learning scope, JJWT is direct and understandable. |
| Vite SPA | React Router Framework mode / Next.js | Use a full-stack React framework when SSR, server actions, or React-owned server routing is a product requirement. None is required for an authenticated internal SPA backed by Spring REST. |
| Mockito unit tests + MySQL Testcontainers integration tests | H2-only repository tests | H2 can be useful for disposable examples, but not as the database compatibility oracle for a MySQL application. |
| Plain DTO mapping | MapStruct | Add MapStruct only after repetitive mapping is measured across many DTOs; manual mapping is clearer for this small educational domain. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Spring Boot 4 | Violates the locked Boot 3 constraint and raises the Java baseline beyond the project choice. | Spring Boot 3.5.x latest patch. |
| MySQL 8.0 for a new deployment | Entered Sustaining Support/EOL transition in April 2026. | MySQL 8.4 LTS. |
| `spring.jpa.hibernate.ddl-auto=update` | Produces unreviewed, non-reproducible schema changes and teaches the wrong migration model. | Flyway-only schema changes plus `ddl-auto=validate`. |
| `schema.sql` / `data.sql` alongside Flyway | Spring Boot recommends one initialization mechanism; mixing them creates ordering and environment drift. | Flyway migrations, including test-only migrations when truly needed. |
| H2 as the primary integration database | Its SQL, type, constraint, and collation behavior differs from MySQL. | MySQL 8.4 Testcontainers. |
| Lombok | Hides generated behavior while the developer is learning Java/Spring; entity-generated equality/toString can also be hazardous around JPA relationships. | Explicit constructors/accessors or Java records for immutable response DTOs where appropriate. |
| MapStruct initially | Adds annotation processing and indirection before mapping repetition exists. | Explicit mapper methods; reassess later. |
| WebFlux / R2DBC | Incompatible in spirit with the locked JPA and blocking SMTP path; adds a second programming model. | Spring MVC + JPA transactions. |
| Redux/Zustand initially | Authentication and server data needs do not yet justify a global state framework. | React context for auth and local component state; add a server-state library only after caching complexity is demonstrated. |
| React Compiler initially | It is an optional optimization layer and not needed to build the product or learn React fundamentals. | Normal React 19 with lint rules and profiling before optimization. |
| Create React App | Deprecated for new apps by the React team. | Vite 8. |
| Tailwind v3 setup tutorials (`tailwind.config.js`, `@tailwind` directives) | They lead to incorrect or needlessly complex v4 configuration. | Tailwind v4 first-party Vite plugin and `@import "tailwindcss"`. |
| Storing SMTP/database/JWT secrets in `application*.yml` | Leaks credentials and couples builds to an environment. | Environment variables or a deployment secret store. |
| A broad “utility” dependency bundle | Expands vulnerability and learning surface without product value. | Add one dependency only when a concrete requirement justifies it. |

## Stack Patterns by Variant

- Use JavaScript/JSX unless the project explicitly chooses TypeScript before front-end scaffolding.
- Keep the back end a single deployable modular monolith and the front end a separate Vite application.
- Send email synchronously only behind an `EmailService` boundary at first, with finite timeouts and explicit failure behavior; do not add Kafka/RabbitMQ.
- Use JJWT for self-issued short-lived access tokens and Spring Security for all authorization decisions.
- Add an outbox/job mechanism for reliable asynchronous email rather than sending SMTP inside the database transaction.
- Add Actuator health/readiness and structured logging before adding a full observability stack.
- Add refresh-token rotation only with a documented revocation/storage design; do not casually turn access JWTs into long-lived credentials.
- Re-run the npm version/security check because npm patches move faster than the back-end roadmap.
- Keep React Router in declarative mode and Axios in one API module.
- Add charting only when dashboard requirements specify exact charts; counters and tables may be sufficient initially.

## Sources

- [Spring Boot 3.5.16 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html) — Java and build-tool compatibility.
- [Spring Boot 3.5 managed dependency coordinates](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html) — Connector/J, Flyway, Spring, and Testcontainers versions.
- [Spring Boot database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html) — one schema mechanism, Flyway MySQL module, migration locations, test migrations.
- [Spring Boot SQL databases](https://docs.spring.io/spring-boot/3.5/reference/data/sql.html) — datasource and JPA integration.
- [Spring Boot email](https://docs.spring.io/spring-boot/3.5/reference/io/email.html) — `JavaMailSender` auto-configuration and SMTP timeouts.
- [Spring Boot Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html) — service connections and MySQL container support.
- [Spring Security MockMvc testing](https://docs.spring.io/spring-security/reference/servlet/test/mockmvc/setup.html) — real security filter-chain integration in MVC tests.
- [JJWT official README](https://github.com/jwtk/jjwt/blob/main/README.adoc) — 0.13.0 dependency scopes and JDK setup.
- [MySQL 8.0 EOL announcements](https://www.mysql.com/support/eol-notice.html) — April 2026 transition to Sustaining Support and 8.4 LTS upgrade recommendation.
- [MySQL 8.4 LTS release model](https://dev.mysql.com/doc/refman/8.4/en/mysql-releases.html) — LTS stability/support intent.
- [MySQL Connector/J compatibility](https://dev.mysql.com/doc/connector-j/en/connector-j-versions.html) — server/JRE compatibility.
- [React versions](https://react.dev/versions) — current React 19.2 line and patch history.
- [React: sunsetting Create React App](https://react.dev/blog/2025/02/14/sunsetting-create-react-app) — official direction away from CRA.
- [Vite 8 announcement](https://vite.dev/blog/announcing-vite8) — stable Vite 8 and Node requirements.
- [Tailwind CSS v4](https://tailwindcss.com/blog/tailwindcss-v4) — first-party Vite plugin and CSS-first setup.
- [Tailwind compatibility](https://tailwindcss.com/docs/compatibility) — modern-browser baseline and preprocessor guidance.
- [React Router home](https://reactrouter.com/home) — declarative/data/framework mode choices.
- [Axios documentation](https://axios-http.com/docs/intro) — browser HTTP client and interceptors.
- [React Testing Library](https://testing-library.com/docs/react-testing-library/intro/) — behavior-oriented React testing.
- npm registry metadata checked 2026-09-02 for exact current package patches; patch pins are intentionally revalidated at implementation time.

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->

## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

## Fluxo de trabalho

O GSD foi descontinuado em 2026-10-22. O trabalho segue os planos já documentados em `.planning/` (ROADMAP, REQUIREMENTS, STATE e `phases/`), executados diretamente pelo agente, sem comandos `$gsd-*`. As seções acima marcadas com `GSD:` são geradas a partir desses documentos e continuam válidas como contexto.

- Antes de cada plano: ler o `NN-PP-PLAN.md`, apresentá-lo explicado e aguardar aprovação.
- Ao concluir: escrever `NN-PP-SUMMARY.md` e atualizar STATE, ROADMAP e REQUIREMENTS.
- Testes proporcionais ao risco (revisão de escopo de 2026-10-30, registrada no ROADMAP).

## Git e GitHub por ticket

- A política obrigatória está em `docs/GIT-WORKFLOW.md` e deve ser lida antes de iniciar qualquer ticket.
- Use uma branch `ticket/NN-descricao-curta`; não implemente tickets diretamente na `main`.
- A partir da Fase 3, cada plano de implementação aprovado usa um ticket/branch/PR próprio. Integre o anterior antes de implementar o seguinte; não concentre uma fase inteira na mesma branch. A numeração de tickets é sequencial e independente da numeração de fases/planos. Registre a correspondência plano → branch → PR e retome a mesma branch ao trocar de máquina.
- Planeje e obtenha aprovação antes de escrever código.
- Faça commits atômicos durante o ticket.
- Publicação (push, PR e merge) somente após testes, resumo e **uma** aprovação explícita do usuário para aquele ticket (decisão de 2026-10-30).
- Para continuar em outra máquina no meio de um ticket, registre um checkpoint e faça push da branch sem integrá-la.

## Modo pedagógico obrigatório

O responsável é um desenvolvedor Java júnior e usa a construção do SolveIT também como processo de aprendizagem. Qualquer agente que trabalhe neste repositório devem ensinar no contexto da tarefa, sem presumir domínio prévio de Spring, segurança, infraestrutura, containers, identidade, armazenamento ou operações Git.

- Antes de cada ação, explicar em linguagem clara o que será feito, por que isso é necessário e qual conceito técnico está envolvido.
- Antes de executar um comando relevante, explicar seu efeito e o resultado esperado; depois, interpretar a saída, incluindo falhas, avisos e consequências.
- Ao apresentar código ou configuração, relacionar a explicação com Java, Spring e os arquivos reais do SolveIT sempre que isso ajudar a compreensão.
- Traduzir jargões na primeira ocorrência e distinguir explicitamente o que é essencial entender agora do que é aprofundamento opcional.
- Explicar decisões, alternativas, riscos, trade-offs e a forma prática de diagnosticar problemas, em vez de apresentar somente a solução final.
- Ensinar de maneira incremental, tecnicamente correta e respeitosa, sem infantilização, teoria desconectada ou volume que esconda a decisão principal.
- Quando uma decisão depender da compreensão do responsável, pausar para perguntas e não interpretar silêncio ou aprovação operacional como confirmação de entendimento conceitual.
- O responsável pode escrever `resumo` para receber uma versão mais curta ou `aprofundar` para ampliar a explicação de qualquer conceito.
- O modo pedagógico não substitui nenhuma aprovação exigida neste arquivo nem amplia o escopo autorizado de uma ação.

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `$gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
