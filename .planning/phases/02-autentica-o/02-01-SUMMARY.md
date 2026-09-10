---
phase: 02-autentica-o
plan: 01
subsystem: auth
tags: [spring-security, bcrypt, mockmvc, testcontainers, mysql]
requires:
  - phase: 01-base-do-projeto
    provides: Entidade Usuario, UsuarioRepository, MySQL 8.4, Flyway e testes de persistência
provides:
  - Cadastro público de contas SOLICITANTE em POST /api/auth/register
  - Baseline Spring Security stateless e fail-closed
  - Senhas codificadas com BCrypt antes da persistência
  - Tracer integrado MockMvc, FilterChainProxy e MySQL 8.4
affects: [02-autentica-o, login, jwt, api-errors, authorization]
actuals:
  tokens: 4522
  tasks: 1
  commits: 2
tech-stack:
  added: [spring-boot-starter-security, spring-boot-starter-validation, spring-security-test]
  patterns: [DTOs REST explícitos, service transacional, segurança stateless, configuração externalizada]
key-files:
  created:
    - solveit-api/src/main/java/com/solveit/config/SecurityConfig.java
    - solveit-api/src/main/java/com/solveit/controller/AuthController.java
    - solveit-api/src/main/java/com/solveit/dto/auth/RegisterRequest.java
    - solveit-api/src/main/java/com/solveit/dto/auth/UsuarioResponse.java
    - solveit-api/src/main/java/com/solveit/service/AuthService.java
    - solveit-api/src/test/java/com/solveit/controller/AuthRegistrationIntegrationTest.java
    - solveit-api/src/test/java/com/solveit/security/BcryptCostCalibrationTest.java
  modified:
    - solveit-api/pom.xml
    - solveit-api/src/main/resources/application.yml
key-decisions:
  - "BCrypt strength 12 é o padrão externalizável após medir 10, 11 e 12 no hardware alvo."
  - "Somente POST /api/auth/register é público; qualquer outra requisição permanece protegida por padrão."
  - "E-mail é normalizado no service e senha é entregue intacta ao PasswordEncoder."
patterns-established:
  - "Fronteira REST explícita: requests e responses usam records, nunca entidades JPA."
  - "Segurança stateless: NullSecurityContextRepository, NullRequestCache e nenhuma HttpSession."
requirements-completed: [AUTH-01, AUTH-04]
coverage:
  - id: D1
    description: Cadastro público cria conta ativa SOLICITANTE e retorna somente seis campos públicos
    requirement: AUTH-01
    verification:
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthRegistrationIntegrationTest.java#registersAnActiveApplicantWithNormalizedEmailAndOnlyPublicFields
        status: pass
    human_judgment: false
  - id: D2
    description: Senha original é preservada, codificada com BCrypt e nunca exposta na resposta
    requirement: AUTH-04
    verification:
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthRegistrationIntegrationTest.java#registersAnActiveApplicantWithNormalizedEmailAndOnlyPublicFields
        status: pass
      - kind: unit
        ref: solveit-api/src/test/java/com/solveit/security/BcryptCostCalibrationTest.java#measuresSupportedStrengthsWithoutAFragileTimingLimit
        status: pass
    human_judgment: false
  - id: D3
    description: Cadeia HTTP não exige CSRF para o bearer sem cookie e não persiste sessão ou contexto
    verification:
      - kind: integration
        ref: solveit-api/src/test/java/com/solveit/controller/AuthRegistrationIntegrationTest.java#keepsOnlyPostRegistrationPublicAndDoesNotCreateAnHttpSession
        status: pass
    human_judgment: false
duration: 12m active (5h24m elapsed including user pause)
completed: 2026-09-05
status: complete
---

# Phase 2 Plan 1: Cadastro público stateless Summary

**Cadastro público atravessando Spring Security, service transacional, BCrypt e MySQL, com perfil SOLICITANTE imposto pelo servidor e nenhuma sessão HTTP**

## Performance

- **Duration:** 12 min ativos; 5h24m decorridos incluindo a pausa entre mensagens do usuário
- **Started:** 2026-09-05T13:40:44Z
- **Completed:** 2026-09-05T19:05:02Z
- **Tasks:** 1
- **Files modified:** 9
- **Realized diff estimate:** 4,522 tokens na escala caracteres/4

## Accomplishments

- `POST /api/auth/register` cria uma conta ativa com perfil `SOLICITANTE`, mesmo quando o JSON tenta enviar `ADMIN`.
- E-mail é aparado e convertido com `Locale.ROOT`; a senha original é codificada antes de chegar ao MySQL.
- A resposta `201` contém somente `id`, `nome`, `email`, `perfil`, `ativo` e `criadoEm`, sem senha ou token.
- A cadeia do Spring Security usa política stateless, repositórios nulos de contexto/cache e protege qualquer rota não liberada explicitamente.
- MockMvc atravessa a `FilterChainProxy` real e o Testcontainers executa as cinco migrations no MySQL 8.4.11.

## BCrypt Calibration

Medições do primeiro ciclo executado no hardware alvo:

| Strength | Encode | Match |
|---|---:|---:|
| 10 | 79 ms | 56 ms |
| 11 | 112 ms | 119 ms |
| 12 | 221 ms | 221 ms |

O strength 12 foi escolhido por ser o maior e o mais próximo de aproximadamente um segundo entre os valores aprovados para medição. Ele é apenas o padrão: `SOLVEIT_BCRYPT_STRENGTH` permite ajuste por ambiente sem recompilar. O teste deliberadamente não contém limite temporal frágil.

## Task Commits

1. **RED — teste de cadastro antes da implementação:** `57b3f4e` (`test`)
2. **GREEN — cadastro público e baseline stateless:** `cb7a088` (`feat`)

## Files Created/Modified

- `solveit-api/pom.xml` — adiciona Security, Validation e suporte de teste de segurança gerenciados pelo Spring Boot.
- `solveit-api/src/main/resources/application.yml` — externaliza o custo BCrypt.
- `solveit-api/src/main/java/com/solveit/config/SecurityConfig.java` — define `PasswordEncoder` e a cadeia stateless/fail-closed.
- `solveit-api/src/main/java/com/solveit/controller/AuthController.java` — expõe somente o cadastro público desta entrega.
- `solveit-api/src/main/java/com/solveit/dto/auth/RegisterRequest.java` — contrato de entrada sem campo de perfil.
- `solveit-api/src/main/java/com/solveit/dto/auth/UsuarioResponse.java` — allowlist de campos públicos.
- `solveit-api/src/main/java/com/solveit/service/AuthService.java` — concentra normalização, hash, perfil imposto e persistência transacional.
- `solveit-api/src/test/java/com/solveit/controller/AuthRegistrationIntegrationTest.java` — prova o tracer completo e a ausência de sessão.
- `solveit-api/src/test/java/com/solveit/security/BcryptCostCalibrationTest.java` — mede strengths 10, 11 e 12 sem assertion temporal.

## Decisions Made

- CSRF está desabilitado porque o contrato aprovado usa bearer explícito no header e nenhuma credencial automática por cookie. Uma futura autenticação por cookie exige reabrir essa análise.
- Form login, HTTP Basic e logout foram desabilitados; somente o matcher exato `POST /api/auth/register` recebe `permitAll`.
- `PasswordEncoder` permanece disponível em contextos sem servidor web; apenas a `SecurityFilterChain` depende de aplicação servlet, preservando os testes existentes da Fase 1.
- A validação detalhada de formato de e-mail e do limite BCrypt de senha permanece no próximo plano, sem alterar a normalização deste tracer.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

- Na primeira tentativa GREEN, `@Email` avaliou o e-mail antes do `trim` e rejeitou espaços externos com `400`. A anotação prematura foi removida e o contrato aprovado de normalização passou sem modificar o teste; as validações completas pertencem ao Plano 02-02.
- O Spring informa uma senha de desenvolvimento gerada enquanto ainda não existe o `UserDetailsService` de login. Form login e HTTP Basic estão desabilitados, portanto ela não cria uma entrada HTTP utilizável; o aviso será eliminado pelo plano de login.
- O Flyway emite um aviso de que esta versão foi testada oficialmente até MySQL 8.1. Todas as migrations e verificações do projeto passaram no MySQL 8.4.11 real.

## TDD Gate Compliance

- RED: `57b3f4e` comprovou `403` em vez de `201` e criação de sessão CSRF antes da implementação.
- GREEN: `cb7a088` fez os mesmos testes passarem sem afrouxar expectativas.
- REFACTOR: não necessário; a implementação final permaneceu mínima e legível.

## Verification

- `mvnw.cmd -q '-Dtest=BcryptCostCalibrationTest,AuthRegistrationIntegrationTest' test` — passou, 3 testes.
- `mvnw.cmd -q test` — passou, incluindo toda a suíte de persistência da Fase 1.
- Nenhuma migration foi criada ou alterada.

## User Setup Required

None. `SOLVEIT_BCRYPT_STRENGTH` é opcional; sem ela, o padrão calibrado é 12.

## Next Phase Readiness

O caminho feliz do cadastro e a baseline stateless estão prontos. O Plano 02-02 pode adicionar validação completa, duplicidade e Problem Details sem misturar essas regras com login ou JWT.

## Self-Check: PASSED

- 8 artefatos obrigatórios encontrados no disco.
- Commits RED `57b3f4e` e GREEN `cb7a088` encontrados no histórico.

---
*Phase: 02-autentica-o*
*Completed: 2026-09-05*
