---
phase: 02-autentica-o
plan: 04
subsystem: auth
tags: [spring-security, authentication-manager, bcrypt, jwt, stateless-login]
requires:
  - phase: 02-autentica-o
    plan: 03
    provides: Emissão e validação JWT HS256 com TTL e claims mínimas
provides:
  - Autenticação de e-mail e senha pelo AuthenticationManager e DaoAuthenticationProvider
  - Principal separado da entidade, com ID/perfil atuais e credenciais apagáveis
  - POST /api/auth/login público com accessToken, tokenType Bearer e expiresIn configurado
  - Prova de login stateless sem cookie, sessão ou dados sensíveis nos logs
affects: [02-autentica-o, login-errors, bearer-filter, me-endpoint, api-authorization]
actuals:
  tasks: 2
  commits: 4
tech-stack:
  added: []
  patterns: [DaoAuthenticationProvider, principal explícito, CredentialsContainer, TDD RED-GREEN, contrato HTTP stateless]
key-files:
  created:
    - solveit-api/src/main/java/com/solveit/security/UsuarioPrincipal.java
    - solveit-api/src/main/java/com/solveit/security/UsuarioDetailsService.java
    - solveit-api/src/main/java/com/solveit/dto/auth/LoginRequest.java
    - solveit-api/src/main/java/com/solveit/dto/auth/TokenResponse.java
    - solveit-api/src/test/java/com/solveit/controller/AuthLoginIntegrationTest.java
  modified:
    - solveit-api/src/main/java/com/solveit/config/SecurityConfig.java
    - solveit-api/src/main/java/com/solveit/controller/AuthController.java
    - solveit-api/src/main/java/com/solveit/service/AuthService.java
key-decisions:
  - "O DaoAuthenticationProvider, e não AuthService, compara o BCrypt e exige a conta ativa."
  - "UsuarioPrincipal copia somente identidade, perfil, estado e hash necessários ao provider e apaga a credencial após autenticar."
  - "O login normaliza somente o e-mail; a senha é entregue intacta ao Spring Security."
  - "expiresIn é derivado do mesmo access-token-ttl usado pelo JwtService, sem uma segunda duração literal."
patterns-established:
  - "Credenciais não confiáveis cruzam o AuthenticationManager antes que qualquer token possa ser emitido."
  - "Cadastro e login mantêm contratos separados; apenas o login devolve JWT."
requirements-completed: [AUTH-02, AUTH-04]
coverage:
  - id: D1
    description: E-mail normalizado e BCrypt correto autenticam usuário ativo pelo provider real; senha incorreta e conta inativa são recusadas
    requirement: AUTH-02
    verification:
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthLoginIntegrationTest.java
        status: pass
    human_judgment: false
  - id: D2
    description: Login HTTP retorna contrato mínimo, JWT com ID no sub e duração configurada, sem sessão ou cookie
    requirement: AUTH-02
    verification:
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthLoginIntegrationTest.java#returnsBearerTokenWithoutSessionCookieOrSensitiveLogs
        status: pass
    human_judgment: false
  - id: D3
    description: O caminho feliz não registra senha, hash BCrypt, segredo, bearer, JWT, SQL, stack trace ou classe interna
    requirement: AUTH-04
    verification:
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthLoginIntegrationTest.java#returnsBearerTokenWithoutSessionCookieOrSensitiveLogs
        status: pass
      - kind: integration
        ref: .\solveit-api\mvnw.cmd -f .\solveit-api\pom.xml -q test
        status: pass
    human_judgment: false
duration: 31m
completed: 2026-09-09
status: complete
---

# Phase 2 Plan 4: Login pelo Spring Security e emissão JWT Summary

**Login stateless que comprova credenciais pelo provider do Spring antes de emitir um JWT mínimo e temporário.**

## Performance

- **Duration:** 31 min ativos
- **Started:** 2026-09-09T11:15:00-03:00
- **Completed:** 2026-09-09T11:46:00-03:00
- **Tasks:** 2
- **Files modified:** 8
- **Tests:** 35 na suíte completa, zero falhas, erros ou skips

## Accomplishments

- `UsuarioDetailsService` normaliza o e-mail e carrega o estado atual do usuário no MySQL.
- `DaoAuthenticationProvider` compara o BCrypt e bloqueia conta inativa sem comparação manual no serviço.
- `UsuarioPrincipal` mantém a entidade JPA fora do Spring Security e apaga o hash após autenticar.
- `POST /api/auth/login` retorna somente `accessToken`, `tokenType: "Bearer"` e `expiresIn`, sem dados pessoais.
- O JWT usa o ID autenticado em `sub`; cadastro continua separado e não emite token.
- O teste HTTP confirma ausência de sessão, cookie e valores sensíveis nos logs do login bem-sucedido.

## Task Commits

1. **Task 1 RED — contrato do provider:** `13a847f` (`test`)
2. **Task 1 GREEN — autenticação pelo Spring:** `5690b4d` (`feat`)
3. **Task 2 RED — contrato HTTP do login:** `4d917b8` (`test`)
4. **Task 2 GREEN — endpoint e emissão JWT:** `78dcaa7` (`feat`)

## Files Created/Modified

- `SecurityConfig.java` — configura provider/manager e libera somente os POSTs de cadastro e login.
- `UsuarioPrincipal.java` — representa a identidade autenticada fora da entidade JPA e implementa apagamento de credencial.
- `UsuarioDetailsService.java` — normaliza e-mail e carrega usuário pelo repository.
- `AuthController.java` — expõe o contrato HTTP do login.
- `LoginRequest.java` — recebe e-mail e senha sem normalizar a senha.
- `TokenResponse.java` — limita a resposta aos três campos definidos em D-08.
- `AuthService.java` — autentica antes de emitir e deriva `expiresIn` da configuração JWT.
- `AuthLoginIntegrationTest.java` — atravessa provider, MockMvc, MySQL e captura de logs.

## Decisions Made

- O `AuthenticationManager` é o limite de confiança: somente o principal devolvido após sucesso fornece o ID usado no JWT.
- O hash existe no principal apenas durante a verificação do provider e é apagado por `CredentialsContainer`.
- O login é `@Transactional(readOnly = true)` para manter a leitura do usuário consistente sem criar estado de sessão.
- A senha de login exige presença, mas não recebe trim, normalização Unicode nem a política de criação; o provider verifica o valor exato contra o hash existente.

## Deviations from Plan

Nenhuma. Os oito arquivos e os dois ciclos TDD previstos foram implementados no escopo aprovado.

## Issues Encountered

- A primeira tentativa de commit usou por engano a pasta `auth` para o teste que fica em `controller`; o Git interrompeu o staging inteiro, e o commit foi repetido com o caminho correto.
- No comando focado, o PowerShell interpretou a vírgula de `-Dtest` como sintaxe própria; colocar a propriedade inteira entre aspas entregou a lista corretamente ao Surefire.
- O console PowerShell exibiu inicialmente um caractere acentuado da senha com codificação divergente. O teste passou a serializar um `LoginPayload` com `ObjectMapper`, reutilizando a mesma constante da senha.
- O Flyway mantém o aviso conhecido de suporte testado oficialmente até MySQL 8.1; todas as cinco migrations passaram no MySQL 8.4.11.
- A suíte completa exibiu avisos do Hikari ao encerrar contêineres entre contextos; os 35 testes e os bancos seguintes concluíram normalmente.

## TDD Gate Compliance

- O commit RED `13a847f` executou três cenários pelo `AuthenticationManager`: o ativo falhou por ausência de carregamento e o inativo ainda era classificado como credencial inválida.
- O commit GREEN `5690b4d` fez os três cenários passarem pelo `DaoAuthenticationProvider` sem reduzir expectativas.
- O commit RED `4d917b8` obteve `401` e `Handler: null` para o endpoint ainda inexistente, enquanto esperava o contrato `200`.
- O commit GREEN `78dcaa7` fez o mesmo teste HTTP passar com todas as verificações de token, transporte e logs.

## Verification

- `mvnw.cmd -Dtest=AuthLoginIntegrationTest test` — RED HTTP confirmado: 4 testes, 1 falha esperada por `401` no lugar de `200`.
- `mvnw.cmd -q "-Dtest=JwtServiceTest,AuthLoginIntegrationTest" test` — passou, 13 testes.
- `mvnw.cmd -q test` — passou, 35 testes em 6 relatórios Surefire.
- MySQL 8.4.11, cinco migrations Flyway, cadeia Spring Security e MockMvc foram exercitados na suíte.

## User Setup Required

- Fora do perfil `test`, continuar definindo `SOLVEIT_JWT_SECRET` com Base64 que decodifique para pelo menos 32 bytes.
- O cliente deve enviar futuramente `Authorization: Bearer <token>`; esta fase ainda não implementa armazenamento no React.

## Next Phase Readiness

O caminho feliz está pronto para o Plano 02-05 uniformizar `401` de e-mail inexistente, senha incorreta e conta inativa sem revelar a causa. Refresh token, cookies e sessão HTTP continuam fora do escopo.

## Self-Check: PASSED

- 8 arquivos da entrega encontrados entre os commits do plano.
- 4 commits RED/GREEN encontrados no histórico.
- 35 testes passaram com zero falhas, erros ou skips.
- Cadastro continua sem token; login não cria sessão/cookie nem registra valores sensíveis.

---
*Phase: 02-autentica-o*
*Completed: 2026-09-09*
