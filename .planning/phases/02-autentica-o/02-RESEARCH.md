# Phase 2: Autenticação - Research

**Researched:** 2026-09-05
**Domain:** Spring Security 6.5, autenticação stateless com JWT, BCrypt e Problem Details
**Confidence:** MEDIUM

<user_constraints>
## User Constraints (from CONTEXT.md)

> Conteúdo abaixo copiado de `.planning/phases/02-autentica-o/02-CONTEXT.md:16-49,105-110`.

### Locked Decisions

### Cadastro e entrada
- **D-01:** Cadastro e login são operações separadas. `POST /api/auth/register` cria a conta e confirma seus dados públicos; somente `POST /api/auth/login` emite JWT.
- **D-02:** O cadastro público aceita nome, e-mail e senha, cria uma conta ativa e força o perfil `SOLICITANTE`; o cliente não pode escolher ou elevar o perfil.
- **D-03:** Cadastro bem-sucedido retorna `201 Created` com `id`, `nome`, `email`, `perfil`, `ativo` e `criadoEm`. Senha e hash nunca aparecem em respostas ou logs.
- **D-04:** O e-mail é normalizado com remoção de espaços externos e conversão para minúsculas antes de persistir ou pesquisar. Cadastro duplicado informa `E-mail já cadastrado`; login não revela se o e-mail existe.
- **D-05:** Senhas aceitam caracteres livres, inclusive espaços e símbolos, com mínimo de 15 caracteres e máximo de 72 bytes por causa do limite do BCrypt. Não há regras obrigatórias de maiúscula, número ou símbolo, e a senha não é aparada nem normalizada antes do hash. — **Reversibility:** costly — reduzir ou reinterpretar senhas depois mudaria o contrato de validação e poderia tornar credenciais existentes impossíveis de reproduzir.

### Duração e transporte do JWT
- **D-06:** O access token expira uma hora após a emissão; a duração é externalizada por configuração de ambiente.
- **D-07:** A primeira versão não possui refresh token. Após a expiração, o usuário autentica-se novamente.
- **D-08:** O login retorna `accessToken`, `tokenType: "Bearer"` e `expiresIn: 3600`; os dados atuais do usuário são consultados separadamente em `/api/auth/me`.
- **D-09:** O cliente envia o token no cabeçalho `Authorization: Bearer <token>`, e o back-end não cria sessão HTTP. — **Reversibility:** costly — trocar para autenticação baseada em cookie alteraria o contrato do cliente e exigiria uma estratégia conjunta de cookie, CORS e CSRF.
- **D-10:** O JWT contém apenas as claims necessárias: ID do usuário em `sub`, instante de emissão em `iat`, expiração em `exp` e emissor em `iss`. Nome, e-mail e outros dados pessoais não são incluídos.

### Estado da conta e autorização
- **D-11:** Em toda requisição protegida, o servidor usa `sub` para carregar o usuário e obter `ativo` e `perfil` atuais do banco; claims de perfil não são autoridade de autorização.
- **D-12:** Desativação bloqueia imediatamente tokens existentes, e mudança de perfil vale na requisição seguinte.
- **D-13:** Se uma conta for reativada, um JWT anterior volta a funcionar somente se ainda não tiver expirado. Revogação persistente de sessões não faz parte desta versão.
- **D-14:** Cadastro e login são as únicas rotas públicas desta fase. `/api/auth/me` aceita qualquer perfil ativo; as demais rotas ficam protegidas por padrão.
- **D-15:** Toda operação de negócio valida no servidor tanto o perfil quanto a relação do usuário com o recurso. Restrições futuras da interface apenas refletem essas regras.

### Respostas e erros da API
- **D-16:** Erros seguem RFC 9457 Problem Details e podem acrescentar `code` e `errors`. `errors` representa falhas de campos; respostas nunca expõem stack trace, SQL, segredo, JWT completo ou nome interno de classe. — **Reversibility:** costly — o formato se torna contrato consumido pelo front-end e por testes de integração.
- **D-17:** Usar `400 Bad Request` para validação, `409 Conflict` para e-mail duplicado, `401 Unauthorized` para autenticação ausente ou inválida e `403 Forbidden` para identidade válida sem permissão.
- **D-18:** Códigos estáveis e mensagens humanas: `AUTH_CREDENTIALS_INVALID` — “E-mail ou senha inválidos.”; `AUTH_TOKEN_MISSING` — “Autenticação necessária.”; `AUTH_TOKEN_INVALID` — “Sua autenticação não é válida. Entre novamente.”; `AUTH_TOKEN_EXPIRED` — “Sua sessão expirou. Entre novamente para continuar.”; `AUTH_ACCOUNT_INACTIVE` — “Sua conta está inativa. Entre em contato com um administrador.”; `ACCESS_DENIED` — “Você não tem permissão para realizar esta operação.”
- **D-19:** Conta inexistente, senha incorreta e conta inativa no endpoint de login não revelam qual condição ocorreu. A mensagem específica de conta inativa só pode ser usada depois que uma credencial assinada identificar a conta.

### the agent's Discretion
- Nomes exatos de DTOs, exceções e componentes internos, mantendo a organização pedagógica prescrita em `config`, `controller`, `dto`, `exception`, `security` e `service`.
- Algoritmo de assinatura e parâmetros seguros do BCrypt, fundamentados pela pesquisa da fase e com segredos externalizados.
- Claims técnicas adicionais estritamente necessárias para validação segura, como audiência ou identificador do token, desde que não transformem o JWT em fonte de estado atual do usuário.
- Representação exata de `type` e da coleção `errors` em Problem Details, preservando os códigos e comportamentos definidos acima.

### Deferred Ideas (OUT OF SCOPE)
- Refresh token, rotação e revogação persistente de sessões — capacidade futura, se a experiência exigir sessões longas.
- Armazenamento do token no navegador e restauração da sessão no React — Fase 6.
- Recuperação de senha — requisito v2 `ACCT-01`.
- Confirmação por e-mail e autenticação multifator — fora do escopo atual.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AUTH-01 | Uma pessoa pode se cadastrar publicamente com nome, e-mail e senha e recebe exclusivamente o perfil SOLICITANTE. | DTO de entrada sem perfil, normalização centralizada e atribuição server-side do enum. |
| AUTH-02 | Um usuário ativo pode entrar com e-mail e senha válidos e receber um JWT válido e limitado no tempo. | `AuthenticationManager`/BCrypt e `JwtService` com `iat`, `exp`, `iss`, `aud` e HS256. |
| AUTH-03 | Um usuário autenticado pode consultar seus próprios dados em `GET /api/auth/me`. | Principal carregado do banco em cada requisição e DTO público explícito. |
| AUTH-04 | Senhas são armazenadas somente como hash BCrypt e nunca aparecem em respostas ou logs. | `PasswordEncoder`, DTOs separados, principal que apaga credenciais e testes negativos de serialização/log. |
| AUTH-05 | Requisições sem token, com token inválido/expirado ou de usuário inativo são rejeitadas nas rotas protegidas. | Filtro por requisição, `AuthenticationEntryPoint` e códigos distintos após uma credencial assinada identificar a conta. |
| AUTH-06 | Cada operação aplica no servidor tanto o perfil exigido quanto a relação do usuário com o recurso acessado. | Defesa em duas camadas: regra HTTP/método para perfil e guarda no serviço para relação com a entidade. |
</phase_requirements>

## Summary

A fase deve construir uma cadeia stateless em torno do Spring Security, não um sistema de autenticação paralelo: cadastro e login ficam em um `AuthService`; o login delega a conferência de senha a `AuthenticationManager`/`DaoAuthenticationProvider`; um filtro JWT autentica cada nova requisição e sempre recarrega `Usuario` pelo `sub`. `SessionCreationPolicy.STATELESS` usa `NullSecurityContextRepository`, e `anyRequest().authenticated()` fornece a política segura por padrão. [CITED: https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html] [CITED: https://docs.spring.io/spring-security/reference/6.5/servlet/authorization/authorize-http-requests.html]

Use JJWT 0.13.0 com HS256 e segredo aleatório Base64 de pelo menos 32 bytes decodificados. O monólito é simultaneamente emissor e verificador, portanto HMAC evita complexidade de par de chaves sem cruzar fronteiras de serviço. Além de `sub`, `iat`, `exp` e `iss`, inclua e valide `aud=solveit-api`; a audiência impede reutilização do token em outro serviço e está dentro da discricionariedade da fase. [CITED: https://github.com/jwtk/jjwt/blob/main/README.adoc] [CITED: https://github.com/OWASP/ASVS/blob/master/5.0/en/0x18-V9-Self-contained-Tokens.md]

As falhas de controller devem ser convertidas por `@RestControllerAdvice`, mas `401/403` produzidos na cadeia de segurança precisam de `AuthenticationEntryPoint` e `AccessDeniedHandler` que escrevam o mesmo `ProblemDetail`. O filtro deve diferenciar token ausente, expirado, inválido e conta inativa, jamais registrar o bearer token nem conteúdo de exceções que o contenha. [CITED: https://docs.spring.io/spring-security/reference/6.5/api/java/org/springframework/security/web/access/ExceptionTranslationFilter.html] [CITED: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html]

**Primary recommendation:** planejar a fase em quatro incrementos verificáveis: contratos/validação e BCrypt; login/JWT; cadeia stateless e `/me`; Problem Details mais matriz negativa de segurança.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Cadastro e login | API / Backend | Database / Storage | O servidor valida, normaliza, aplica perfil e compara o hash; o banco garante unicidade. |
| Emissão e validação JWT | API / Backend | — | Segredo, algoritmo e claims são controles do back-end. |
| Estado atual da conta/perfil | Database / Storage | API / Backend | O banco é a fonte atual; o filtro carrega e converte para `Authentication`. |
| Autorização por perfil | API / Backend | — | Regras HTTP/método são impostas pelo Spring Security. |
| Autorização por relação | API / Backend | Database / Storage | O service avalia o recurso persistido e o ID do principal na mesma operação. |
| Respostas Problem Details | API / Backend | — | Advice e handlers de segurança compartilham o contrato de erro. |

## Project Constraints (from AGENTS.md)

- Usar Java 17, Spring Boot 3, MySQL 8/Flyway, JWT/BCrypt e a organização `config`, `controller`, `dto`, `entity`, `enums`, `exception`, `repository`, `security`, `service`. [VERIFIED: AGENTS.md:13-20 — “Java 17”, “Spring Boot 3”, “MySQL 8”, “JWT”, “BCrypt”, “config, controller, dto, entity, enums, exception, repository, security e service”]
- Validar perfil e relação com o chamado no servidor; controles de UI não constituem autorização. [VERIFIED: AGENTS.md:20 — “autorização por perfil e por relação com o chamado deve ser validada no servidor”]
- Planejar, explicar e obter aprovação antes de implementar; executar uma tarefa por vez e ensinar os conceitos no contexto dos arquivos reais. [VERIFIED: AGENTS.md:19,227,234-244 — “planejar e explicar antes de implementar; executar uma tarefa por vez; consultar ambiguidades”]
- Cada ticket usa branch `ticket/NN-descricao-curta`, commits atômicos e Conventional Commits; push, PR e merge exigem aprovações explícitas separadas. [VERIFIED: docs/GIT-WORKFLOW.md:7,14,20-25,33-40 — “ticket/NN-descricao-curta”, “feat:”, “fix:”, “test:”, “docs:”, “refactor:”, “chore:”]
- Nunca versionar senha, token, credencial, `.env` ou configuração pessoal. [VERIFIED: docs/GIT-WORKFLOW.md:24 — “Nunca versionar senhas, tokens, credenciais, `.env` ou configurações pessoais.”]

## Existing Code Contract

- O projeto usa Spring Boot `3.5.16` e Java `17`: `"<version>3.5.16</version>"` e `"<java.version>17</java.version>"`. [VERIFIED: solveit-api/pom.xml:7-21]
- O enum canônico contém exatamente `“SOLICITANTE, ATENDENTE, ADMIN”`; cadastro público deve atribuir `PerfilUsuario.SOLICITANTE` sem receber perfil no DTO. [VERIFIED: solveit-api/src/main/java/com/solveit/enums/PerfilUsuario.java:3-7]
- `Usuario` já possui literalmente `“private String email;”`, `“private String senha;”`, `“private PerfilUsuario perfil;”`, `“private Boolean ativo = true;”` e `“private LocalDateTime criadoEm;”`. [VERIFIED: solveit-api/src/main/java/com/solveit/entity/Usuario.java:29-43]
- O repositório oferece literalmente `“Optional<Usuario> findByEmail(String email);”` e `“boolean existsByEmail(String email);”`. [VERIFIED: solveit-api/src/main/java/com/solveit/repository/UsuarioRepository.java:8-12]
- A migração garante `“CONSTRAINT uk_usuario_email UNIQUE (email)”` e `“perfil IN ('SOLICITANTE', 'ATENDENTE', 'ADMIN')”`; a aplicação ainda precisa normalizar e traduzir corrida de unicidade em 409. [VERIFIED: solveit-api/src/main/resources/db/migration/V1__create_usuario.sql:1-14]
- O perfil dev já usa `“spring.jpa.open-in-view: false”`, `“ddl-auto: validate”` e timezone UTC; a autenticação não deve mudar a propriedade do schema. [VERIFIED: solveit-api/src/main/resources/application-dev.yml:9-18]

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.5.16, 2026-06-25 | BOM e runtime | Fixado no POM; último release OSS da linha 3.5. [CITED: https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now] |
| Spring Security | 6.5.11, BOM-managed | Cadeia, autenticação, BCrypt e autorização | Versão gerenciada pelo Boot 3.5.16. [CITED: https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html] |
| Spring Framework | 6.2.19, BOM-managed | MVC e `ProblemDetail` | Versão gerenciada pelo Boot 3.5.16. [CITED: https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html] |
| JJWT | 0.13.0, 2025-08-20 | Assinar e validar JWT | API oficial atual do projeto; separar API de módulos runtime. [CITED: https://github.com/jwtk/jjwt/releases] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `spring-boot-starter-validation` | 3.5.16 | Bean Validation em DTOs/configuração | Cadastro, login e `JwtProperties`. [CITED: https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html] |
| `spring-security-test` | 6.5.11 | MockMvc com cadeia real | Todos os testes 401/403 e regras de endpoint. [CITED: https://docs.spring.io/spring-security/reference/6.5/servlet/test/mockmvc/setup.html] |
| JUnit Jupiter, AssertJ, Mockito | Boot-managed | Testes unitários | Validação, normalização, serviço e classificação de erros. [VERIFIED: solveit-api/pom.xml:47-66 — “spring-boot-starter-test”] |

### Alternatives Considered

As escolhas BCrypt, Spring Security e JJWT estão bloqueadas pelo projeto; não pesquisar substitutos evita reabrir decisões fora do escopo. [VERIFIED: AGENTS.md:13-20 — “Autenticação: JWT com senhas BCrypt”]

**Installation (Maven):**

```xml
<!-- versões gerenciadas pelo Spring Boot -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.security</groupId>
  <artifactId>spring-security-test</artifactId>
  <scope>test</scope>
</dependency>
<!-- JJWT 0.13.0: api compile; implementação e Jackson runtime -->
```

As três coordenadas JJWT e seus escopos devem seguir o README oficial: `jjwt-api` compile; `jjwt-impl` e `jjwt-jackson` runtime. [CITED: https://github.com/jwtk/jjwt/blob/main/README.adoc]

## Package Legitimacy Audit

| Package | Registry | Age/Publish | Source Repo | Verdict | Disposition |
|---------|----------|-------------|-------------|---------|-------------|
| `spring-boot-starter-security` | Maven Central | 2026-06-25 | github.com/spring-projects/spring-boot | N/A: gate não suporta Maven | Approved from official Boot BOM |
| `spring-boot-starter-validation` | Maven Central | 2026-06-25 | github.com/spring-projects/spring-boot | N/A: gate não suporta Maven | Approved from official Boot BOM |
| `spring-security-test` | Maven Central | Boot-managed 6.5.11 | github.com/spring-projects/spring-security | N/A: gate não suporta Maven | Approved from official Boot BOM |
| `jjwt-api`, `jjwt-impl`, `jjwt-jackson` | Maven Central | 2025-08-20 | github.com/jwtk/jjwt | N/A: gate não suporta Maven | Approved from official project docs and Central |

O comando de legitimidade respondeu literalmente `“--ecosystem <npm|pypi|crates>”`; Maven não é suportado pelo gate. [VERIFIED: gsd-tools package-legitimacy check output, 2026-09-05] As coordenadas JJWT aparecem no README do mantenedor e no Maven Central. [CITED: https://github.com/jwtk/jjwt/blob/main/README.adoc] [CITED: https://central.sonatype.com/artifact/io.jsonwebtoken/jjwt-api/0.13.0]

**Packages removed due to SLOP:** none.
**Packages flagged as SUS:** none; o gate não emitiu veredito Maven.

## Architecture Patterns

### System Architecture Diagram

```text
POST register ──> DTO validation ──> normalize email ──> BCrypt encode ──> UsuarioRepository ──> 201 public DTO
                                      │ duplicate/precondition failure ───────────────> ProblemDetail 400/409

POST login ─────> normalize email ──> AuthenticationManager + BCrypt ──┬─ invalid/inactive ─> generic 401
                                                                       └─ active ─> JwtService ─> token response

Protected request + Authorization header
  └─> JwtAuthenticationFilter
       ├─ missing ─> continue anonymous ─> authorization rule ─> AuthenticationEntryPoint 401
       ├─ malformed/signature/issuer/audience invalid ─> AuthenticationEntryPoint 401
       ├─ expired ─> AuthenticationEntryPoint 401 expired
       └─ valid sub ─> UsuarioRepository.findById
            ├─ absent/inactive ─> AuthenticationEntryPoint 401
            └─ active ─> fresh principal/current perfil ─> SecurityContext
                 └─> HTTP/method role gate ─> service relationship guard ─> controller/DTO
                                      └─ denied ─> AccessDeniedHandler 403
```

### Recommended Project Structure

Estrutura proposta, ainda inexistente e portanto sujeita ao plano: [ASSUMED]

```text
solveit-api/src/main/java/com/solveit/
├── config/       # SecurityConfig, JwtProperties, Clock/PasswordEncoder beans
├── controller/   # AuthController
├── dto/auth/     # RegisterRequest, LoginRequest, TokenResponse, UsuarioResponse
├── exception/    # domain exceptions, advice, ProblemDetail writer
├── security/     # JwtService, JwtAuthenticationFilter, UsuarioPrincipal, handlers
└── service/      # AuthService and future relationship authorization guards
```

### Pattern 1: Stateless SecurityFilterChain

Use `requestMatchers(HttpMethod.POST, ...)` para as duas rotas públicas, depois `anyRequest().authenticated()`. Configure `STATELESS`, desabilite form login e HTTP Basic e use um `NullRequestCache`; desabilite CSRF apenas porque o contrato atual usa bearer header explícito e nenhuma credencial automática por cookie. Uma futura migração para cookie exige reavaliar CSRF. [CITED: https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html] [CITED: https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html]

### Pattern 2: Login via AuthenticationManager

Use `DaoAuthenticationProvider` com `UserDetailsService` e `BCryptPasswordEncoder`; capture qualquer `AuthenticationException` no limite do serviço e traduza para o único erro de credenciais. O principal deve ser uma classe explícita, não a entidade JPA, implementar `CredentialsContainer` e apagar o hash após autenticação. [CITED: https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/architecture.html]

### Pattern 3: Estado atual por requisição

O filtro aceita somente `Bearer `, valida criptografia/claims, converte `sub` para `Long`, consulta `findById`, rejeita ausência/inatividade e só então cria um novo `SecurityContext` com authorities derivadas do perfil atual. Criar um contexto vazio antes de preenchê-lo evita condições de corrida. [CITED: https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/architecture.html]

### Pattern 4: Autorização em duas camadas

Use regras HTTP e `@EnableMethodSecurity`/`@PreAuthorize` para o perfil; o starter não habilita Method Security sozinho. Para propriedade/atribuição, o método transacional do service deve comparar o ID do principal com as relações carregadas do recurso e lançar `AccessDeniedException`. Não coloque a decisão final no controller nem no React. [CITED: https://docs.spring.io/spring-security/reference/6.5/servlet/authorization/method-security.html]

AUTH-06 nesta fase significa instalar e provar essa fundação; as regras concretas de proprietário/atendente pertencem às fases que introduzem os endpoints de chamados. [VERIFIED: .planning/phases/02-autentica-o/02-CONTEXT.md:9-11 — “A autorização concreta por proprietário e atendente será aplicada aos recursos introduzidos nas fases seguintes”]

### Pattern 5: Um contrato de erro, duas integrações

Centralize a construção JSON em um componente sem estado. `@RestControllerAdvice` trata validação/conflitos; `AuthenticationEntryPoint` trata identidade ausente/inválida e `AccessDeniedHandler` trata identidade válida sem permissão. Não dependa do advice para exceções do filtro. [CITED: https://docs.spring.io/spring-security/reference/6.5/api/java/org/springframework/security/web/access/ExceptionTranslationFilter.html] [CITED: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html]

### Anti-Patterns to Avoid

- Não colocar perfil no JWT nem autorizar por claim de perfil; mudanças precisam valer na requisição seguinte. [VERIFIED: .planning/phases/02-autentica-o/02-CONTEXT.md:33-34 — “claims de perfil não são autoridade de autorização”]
- Não expor `Usuario` diretamente, implementar `UserDetails` na entidade ou usar `toString()` que inclua `senha`; use DTO/principal explícitos. [VERIFIED: .planning/phases/02-autentica-o/02-CONTEXT.md:21,82-84 — “Senha e hash nunca aparecem” e “Entidades JPA não são contratos REST”]
- Não usar `password.trim()`, `@NotBlank` ou regex de composição na senha; validar mínimo em code points e máximo em bytes UTF-8, preservando o texto original. [VERIFIED: .planning/phases/02-autentica-o/02-CONTEXT.md:23 — “a senha não é aparada nem normalizada”]
- Não confiar só no `existsByEmail`; mantenha a constraint única e converta `DataIntegrityViolationException` para 409, cobrindo cadastros concorrentes. [VERIFIED: solveit-api/src/main/resources/db/migration/V1__create_usuario.sql:10 — “CONSTRAINT uk_usuario_email UNIQUE (email)”]
- Não capturar `ExpiredJwtException` dentro de um catch amplo antes de classificá-la; expirado e inválido têm códigos diferentes. [CITED: https://github.com/jwtk/jjwt/blob/main/api/src/main/java/io/jsonwebtoken/JwtParser.java]
- Não logar header Authorization, corpo de login/cadastro, senha, hash, segredo ou JWT completo.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Hash/salt de senha | SHA caseiro, salt manual | `BCryptPasswordEncoder` | Geração de salt, formato e comparação já são tratados. [CITED: https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/password-encoder.html] |
| Parser/assinatura JWT | Base64/HMAC manual | JJWT 0.13.0 | Valida estrutura, assinatura, claims e chaves fracas. [CITED: https://github.com/jwtk/jjwt/blob/main/README.adoc] |
| Pipeline de autenticação | Filtro que compara senha | `AuthenticationManager` + `DaoAuthenticationProvider` | Mantém responsabilidades e apagamento de credenciais. [CITED: https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/architecture.html] |
| Formato de erro | DTO proprietário de erro | Spring `ProblemDetail` | Serializa RFC 9457 e extensions top-level. [CITED: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html] |
| Autorização HTTP | `if` em todo controller | `authorizeHttpRequests` e Method Security | Negação central e segura por padrão. [CITED: https://docs.spring.io/spring-security/reference/6.5/servlet/authorization/authorize-http-requests.html] |

**Key insight:** código próprio deve expressar regras do SolveIT; primitives criptográficas, pipeline de segurança e formato HTTP ficam com bibliotecas maduras.

## Common Pitfalls

### Pitfall 1: contar 72 caracteres em vez de 72 bytes
**What goes wrong:** Unicode pode passar em `@Size(max=72)` e falhar no BCrypt.
**Why:** BCrypt limita a entrada UTF-8 a 72 bytes; a implementação atual rejeita novos hashes acima disso. [CITED: https://github.com/spring-projects/spring-security/blob/main/crypto/src/main/java/org/springframework/security/crypto/bcrypt/BCrypt.java]
**Avoid:** validator dedicado: mínimo de 15 code points, máximo de 72 bytes UTF-8, sem trim/normalização.

### Pitfall 2: erro genérico só na mensagem
**What goes wrong:** status, corpo ou tempo diferentes revelam usuário inexistente, senha errada ou conta inativa.
**Avoid:** mapear todas as falhas de login para o mesmo 401/código/mensagem; não consultar `ativo` no controller para responder diferente. A OWASP também alerta para discrepância temporal. [CITED: https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html]

### Pitfall 3: filtro lança exceção esperando ControllerAdvice
**What goes wrong:** a exceção ocorre antes do DispatcherServlet e pode virar resposta inconsistente.
**Avoid:** o filtro classifica a falha e chama o entry point/response writer; ausência de token apenas continua como anônimo para a regra protegida produzir 401.

### Pitfall 4: usuário desativado continua autenticado
**What goes wrong:** validar só assinatura/expiração mantém token utilizável até uma hora.
**Avoid:** carregar usuário por `sub` em toda requisição e construir authorities do perfil atual. [VERIFIED: .planning/phases/02-autentica-o/02-CONTEXT.md:33-35 — “Em toda requisição protegida”]

### Pitfall 5: cadastro concorrente vira 500
**What goes wrong:** duas requisições passam no `existsByEmail` e uma viola unicidade.
**Avoid:** pré-checagem para UX mais DB constraint como autoridade; capturar violação específica da constraint e retornar 409 sem SQL.

### Pitfall 6: `401` e `403` invertidos
**What goes wrong:** o front-end não sabe se deve pedir novo login ou esconder/recusar uma ação.
**Avoid:** sem identidade válida => 401; identidade válida sem permissão => 403. [VERIFIED: .planning/phases/02-autentica-o/02-CONTEXT.md:41-43 — “401 Unauthorized” e “403 Forbidden”]

## Code Examples

Padrões adaptados das APIs oficiais; nomes de classes são recomendações, não implementação existente.

### Emissão e parsing JJWT 0.13.0

```java
SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
Instant now = clock.instant();

String token = Jwts.builder()
        .subject(usuarioId.toString())
        .issuer(properties.issuer())
        .audience().add(properties.audience()).and()
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(properties.accessTokenTtl())))
        .signWith(key, Jwts.SIG.HS256)
        .compact();

Claims claims = Jwts.parser()
        .verifyWith(key)
        .requireIssuer(properties.issuer())
        .requireAudience(properties.audience())
        .clock(() -> Date.from(clock.instant()))
        .build()
        .parseSignedClaims(token)
        .getPayload();
```

Source: [CITED: https://github.com/jwtk/jjwt/blob/main/README.adoc] e [CITED: https://github.com/jwtk/jjwt/blob/main/api/src/main/java/io/jsonwebtoken/JwtParserBuilder.java].

### ProblemDetail com extensions

```java
ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
problem.setTitle(title);
problem.setType(typeUri);
problem.setInstance(URI.create(request.getRequestURI()));
problem.setProperty("code", code);
problem.setProperty("errors", fieldErrors);
```

`code` e `errors` são extensions serializadas no nível superior pelo Jackson mixin do Spring. [CITED: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html]

### Cadastro força o enum do servidor

O valor canônico é literalmente `“SOLICITANTE”`. [VERIFIED: solveit-api/src/main/java/com/solveit/enums/PerfilUsuario.java:3-7]

```java
Usuario usuario = new Usuario(
        request.nome(),
        normalizarEmail(request.email()),
        passwordEncoder.encode(request.senha()),
        PerfilUsuario.SOLICITANTE);
```

## Testing Strategy

Use testes unitários para regras puras e `@SpringBootTest` + MockMvc para o contrato de segurança atravessando `FilterChainProxy`; aplicar `springSecurity()` é o mecanismo oficial de integração do filtro com MockMvc. [CITED: https://docs.spring.io/spring-security/reference/6.5/servlet/test/mockmvc/setup.html]

| Requirement | Essential automated evidence |
|-------------|------------------------------|
| AUTH-01 | register ignora/rejeita perfil enviado, normaliza email, retorna 201 e somente DTO público; duplicidade sequencial e concorrente => 409. |
| AUTH-02 | credencial válida ativa => JWT assinado com TTL; desconhecido/senha errada/inativo => resposta idêntica `AUTH_CREDENTIALS_INVALID`. |
| AUTH-03 | token válido => `/api/auth/me` com dados atuais e sem `senha`; alteração de perfil aparece na próxima requisição. |
| AUTH-04 | hash começa em formato BCrypt, `matches` funciona, plaintext não fica persistido e senha/hash não aparecem no JSON nem logs capturados. |
| AUTH-05 | matriz: sem header, esquema errado, token malformado, assinatura adulterada, issuer/audience incorretos, expirado, subject não numérico, usuário ausente, usuário inativo, reativado antes do `exp`. |
| AUTH-06 | endpoint protegido: anônimo 401, perfil incorreto 403; teste de serviço prova relação permitida/negada usando ID do principal, sem confiar em parâmetro de usuário do cliente. |

Use `Clock` injetável para gerar/validar expiração deterministicamente; JJWT expõe `JwtParserBuilder.clock(...)` para esse fim. [CITED: https://github.com/jwtk/jjwt/blob/main/api/src/main/java/io/jsonwebtoken/JwtParserBuilder.java]

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| `WebSecurityConfigurerAdapter` | `SecurityFilterChain` bean | Evitar tutoriais antigos; adapter foi substituído pela DSL baseada em beans. [CITED: https://docs.spring.io/spring-security/reference/6.5/servlet/configuration/java.html] |
| `authorizeRequests` | `authorizeHttpRequests` | Usa `AuthorizationManager` e lookup tardio de autenticação. [CITED: https://docs.spring.io/spring-security/reference/6.5/servlet/authorization/authorize-http-requests.html] |
| `Jwts.parserBuilder().setSigningKey(...).parseClaimsJws(...)` | `Jwts.parser().verifyWith(...).build().parseSignedClaims(...)` | APIs tipadas 0.13.0 e sem métodos legados. [CITED: https://github.com/jwtk/jjwt/blob/main/README.adoc] |
| RFC 7807 | RFC 9457 | Spring Framework 6.2 documenta Problem Details conforme RFC 9457. [CITED: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html] |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | A estrutura e os nomes internos propostos serão adotados pelo planejador. | Recommended Project Structure | Baixo; contratos HTTP e limites de camada permanecem iguais. |

## Open Questions (RESOLVED)

1. **Custo BCrypt definitivo**
   - What we know: Spring usa 10 por padrão; OWASP recomenda pelo menos 10 e o Spring recomenda calibrar para aproximadamente um segundo. [CITED: https://docs.spring.io/spring-security/reference/7.0/features/authentication/password-storage.html] [CITED: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html]
   - **Resolution adopted:** antes de fixar o parâmetro, a execução deve medir os strengths 10, 11 e 12 no hardware-alvo e selecionar o maior custo cujo tempo de verificação fique próximo de ~1 s. O strength 12 é somente a hipótese inicial do benchmark, não uma decisão fixa.

2. **Rate limiting de login/cadastro**
   - What we know: ASVS 5.0 L1 exige controles documentados contra credential stuffing/brute force. [CITED: https://github.com/OWASP/ASVS/blob/master/5.0/en/0x15-V6-Authentication.md]
   - **Resolution adopted:** rate limiting fica explicitamente fora da implementação da Fase 2. O risco de credential stuffing/brute force e o requisito de controle devem ser retomados e resolvidos antes de produção; esta fase não adicionará silenciosamente biblioteca nem estado distribuído para isso.

3. **Nível ASVS alvo**
   - What we know: MFA está explicitamente fora do escopo, enquanto ASVS 5.0 L2 exige MFA. [VERIFIED: .planning/phases/02-autentica-o/02-CONTEXT.md:107-110 — “autenticação multifator — fora do escopo atual”] [CITED: https://github.com/OWASP/ASVS/blob/master/5.0/en/0x15-V6-Authentication.md]
   - **Resolution adopted:** o alvo de segurança desta fase é o subconjunto aplicável do OWASP ASVS 5.0 L1. A entrega não declarará conformidade L2 nem conformidade integral com o ASVS.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| Sistema operacional | toolchain Windows | ✓ | Windows 11 | — |
| Java | compilação/runtime | ✓ | Temurin 17.0.20.1 | — |
| Maven Wrapper | build/test | ✓ | wrapper 3.3.4 / Apache Maven 3.9.16 | — |
| Docker | Testcontainers MySQL | ✓ | Client 29.7.2 / Server 29.7.2 | — |
| MySQL de teste | integração | Provisionável pelo Docker/Testcontainers | imagem `mysql:8.4.11` já declarada no teste | banco local não é requisito para a suíte integrada |

O wrapper declara literalmente `“wrapperVersion=3.3.4”`, `“distributionUrl=...apache-maven-3.9.16-bin.zip”` e checksum SHA-256. [VERIFIED: solveit-api/.mvn/wrapper/maven-wrapper.properties:1-4]

As falhas observadas anteriormente ao invocar `mvnw.cmd`, localizar Docker e sondar a porta 3306 ocorreram dentro do sandbox restrito desta sessão. A verificação fora do sandbox confirmou que Maven Wrapper e Docker funcionam na máquina; portanto aquelas falhas não evidenciam ausência, quebra ou necessidade de reinstalação das ferramentas. [VERIFIED: verificação externa fornecida pelo orquestrador, 2026-09-05]

**Missing dependencies with no fallback:** nenhuma identificada.

**Missing dependencies with fallback:** nenhuma identificada; o MySQL de integração deve ser provisionado pelo Testcontainers usando o Docker disponível.

## Security Domain

### Applicable ASVS 5.0.0 Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Validation and Business Logic | yes | DTO validation no servidor; senha por code points/bytes; perfil nunca vem do cliente. |
| V6 Authentication | yes | BCrypt, erro genérico de login e matriz de credenciais. |
| V7 Session Management | yes | JWT com expiração absoluta; desativação bloqueia tokens pela consulta ao banco. |
| V8 Authorization | yes | menor privilégio, perfil + atributo/relação do recurso no servidor. |
| V9 Self-contained Tokens | yes | assinatura/MAC, allowlist HS256, chave confiável, `iss`, `aud`, `exp`. |
| V11 Cryptography | yes | JJWT/JCA e BCrypt; nenhum algoritmo próprio. |

ASVS 5.0.0 é a versão estável publicada em maio de 2025; seus capítulos atuais são diferentes da numeração ASVS 4. [CITED: https://github.com/OWASP/ASVS]

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Enumeração de conta | Information Disclosure | resposta de login idêntica e cuidado com timing. |
| Credential stuffing/brute force | Spoofing | custo BCrypt, monitoramento e decisão explícita de rate limit. |
| JWT adulterado/`alg:none`/confusão de algoritmo | Tampering/Spoofing | `parseSignedClaims`, `verifyWith`, algoritmo fixo HS256, nunca habilitar unsecured JWT. |
| Reuso cross-service | Spoofing | validar `iss` e `aud`. |
| Conta desativada com token ainda válido | Elevation of Privilege | lookup do usuário/estado atual em toda requisição. |
| IDOR em recursos futuros | Elevation of Privilege | guarda de relação no service, usando principal e recurso persistido. |
| Vazamento de segredo/token/hash | Information Disclosure | DTOs explícitos, credenciais apagadas, log sanitizado, segredo só em ambiente. |

## Sources

### Primary repository evidence
- `solveit-api/pom.xml` — Java/Boot e dependências atuais.
- `Usuario.java`, `PerfilUsuario.java`, `UsuarioRepository.java`, `V1__create_usuario.sql` — contrato persistente atual.
- `.planning/phases/02-autentica-o/02-CONTEXT.md` — decisões bloqueadas e escopo.
- `AGENTS.md` e `docs/GIT-WORKFLOW.md` — restrições pedagógicas, arquiteturais e de processo.

### Official documentation (MEDIUM under GSD websearch classification)
- https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/architecture.html
- https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html
- https://docs.spring.io/spring-security/reference/6.5/servlet/authorization/authorize-http-requests.html
- https://docs.spring.io/spring-security/reference/6.5/servlet/authorization/method-security.html
- https://docs.spring.io/spring-security/reference/6.5/servlet/test/mockmvc/setup.html
- https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
- https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html
- https://github.com/jwtk/jjwt/blob/main/README.adoc
- https://www.rfc-editor.org/rfc/rfc9457.html
- https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html
- https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
- https://github.com/OWASP/ASVS/blob/master/5.0/en/0x18-V9-Self-contained-Tokens.md

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH for in-repo pins; MEDIUM for current managed dependency metadata.
- Architecture: MEDIUM — grounded in Spring/JJWT official documentation and project decisions.
- Pitfalls: MEDIUM — cross-checked with Spring, OWASP and RFC 9457.

**Research date:** 2026-09-05
**Valid until:** 2026-10-05; revalidar versões e advisories antes da instalação.
