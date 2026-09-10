---
phase: 02-autentica-o
reviewed: 2026-09-10
depth: deep
audited_commit: ee8c6b9
files_reviewed: 31
scope_manifest: audit/artifact-checks.json
findings:
  critical: 0
  warning: 5
  info: 0
  total: 5
status: issues_found
---

# Auditoria global da Fase 2

Os oito planos foram executados e a suíte existente passa, mas a fase tem lacunas de validação e de contrato HTTP. Recomenda-se corrigir R1–R3 e sincronizar a documentação antes de concluir a verificação e avançar para a Fase 3. Não houve alteração de código de produção, push, PR ou merge nesta auditoria.

**Escopo:** os 31 arquivos de aplicação/configuração/testes alterados entre `e465840d9d98f0f42608e8ce1f56c72259b8f5d9` e `ee8c6b9`, listados em [artifact-checks.json](audit/artifact-checks.json); adicionalmente, requisitos, contexto/decisões, oito planos e resumos, STATE, ROADMAP, README, documento de origem e contratos de Usuario/repositório/migração. Revisão inline pelo fluxo GSD code-review, profundidade deep.

## Evidência executada

- `mvnw.cmd clean verify`: **55 testes, zero falhas, erros ou ignorados**, empacotamento Spring Boot concluído. Integração com MySQL 8.4.11/Testcontainers e migrações reais. [Resultado](audit/regression-results.txt).
- Sete cenários adicionais através de **HTTP real**, Tomcat e MySQL: seis reproduzem problemas; um confirma cadastro → login → me e ausência de cookie. [Fonte diagnóstica](audit/Phase2AuditProbeTest.java), [resultados](audit/observed-results.txt).
- Os sete testes diagnósticos afirmam o comportamento observado, inclusive o incorreto. Portanto, seu resultado verde confirma a reprodução, não a aprovação do produto. O arquivo temporário foi removido de `src/test` antes do build limpo.
- Oito resumos passam no verificador GSD; requisitos de cada plano/resumo coincidem; não há arquivos ou métodos de teste referenciados ausentes. Os 24 itens classificados como cobertos nos metadados não significam cobertura exaustiva.
- JAR final sem classes de teste/probe em `BOOT-INF/classes`. Sem medição de cobertura de linhas/branches: não se afirma 100% de cobertura.

## Achados

### R1 — WARNING / bloqueia aceite: JWT assinado sem expiração é aceito

**Arquivo:** `solveit-api/src/main/java/com/solveit/security/JwtService.java:55`.

`parse()` verifica assinatura, algoritmo, emissor e audiência, mas não exige a presença de `exp`. Um token assinado com a chave sintética do teste, usuário ativo existente e `iat` do dia anterior, sem `exp`, recebeu **200** em `/api/auth/me`. O emissor atual inclui expiração corretamente; a lacuna está no verificador. `iat` também não tem presença explicitamente exigida, observação estática a complementar com teste.

**Limite do risco:** não foi demonstrada falsificação sem a chave. Remover `exp` de um token existente invalida sua assinatura. Trata-se de assegurar o contrato de tokens limitados no tempo, especialmente contra emissão incorreta, não de um bypass disponível a qualquer cliente.

**Base:** AUTH-05, decisões D-06/D-10 e contrato do Plano 03. O [parser oficial JJWT 0.13.0](https://raw.githubusercontent.com/jwtk/jjwt/0.13.0/impl/src/main/java/io/jsonwebtoken/impl/DefaultJwtParser.java) verifica expiração condicionalmente à presença da claim.

**Correção proposta:** exigir claims temporais obrigatórias e coerentes; traduzir ausência/formato inválido para `AUTH_TOKEN_INVALID`; preservar `AUTH_TOKEN_EXPIRED` para expiração. Criar testes de ausência de `exp`/`iat`, valores inválidos e coerência temporal. Preservar a política de duração externalizada sem impor retroativamente uma nova duração a tokens já emitidos.

### R2 — WARNING / bloqueia aceite: cadastro aceita e-mail inválido e estoura limites do banco

**Arquivo:** `solveit-api/src/main/java/com/solveit/dto/auth/RegisterRequest.java:7`; normalização em `AuthService.register` e limites na migração de Usuario.

Nome/e-mail têm apenas `@NotBlank`. `not-an-email` foi persistido com **201**; nome de 121 caracteres e e-mail de 256 caracteres retornaram **500**, em vez de erro de entrada **400**. Os limites relacionais são 120/255. O banco continua protegendo seu esquema, mas a API não orienta corretamente o cliente.

**Origem documental:** `02-01-SUMMARY.md` registra a retirada de `@Email` para permitir espaços externos e o adiamento de validação para o Plano 02. Essa validação não foi retomada. Uma anotação aplicada antes da normalização pode reintroduzir o problema que motivou a retirada.

**Correção proposta:** validar formato e tamanho dos valores que serão persistidos, respeitando a normalização de e-mail aprovada. Retornar Problem Details 400 com erros de campo. Testar limites válidos/inválidos e e-mail com espaços externos. Não aparar nem normalizar a senha. Relaciona-se a AUTH-01 e D-04/D-16/D-17.

### R3 — WARNING / bloqueia aceite: erros de protocolo viram erro de autenticação

**Arquivos:** `GlobalExceptionHandler.java`; `SecurityConfig.java:79`.

Via HTTP real, JSON truncado em `POST /api/auth/login` retornou **401 AUTH_TOKEN_MISSING**, `instance: /error`, em vez de **400**. Cadastro com `Content-Type: text/plain` retornou **401**, em vez de **415**. As exceções não tratadas pelo advice seguem o mecanismo de erro do servlet; o despacho interno para `/error` é barrado pela regra de autenticação.

**Consequência:** um problema de corpo/formato passa a parecer sessão ausente. Os testes MockMvc atuais não exercitam esse ciclo completo do contêiner, por isso permanecem verdes. Viola a distinção de erros de D-16/D-17/D-18.

**Correção proposta:** tratar erros MVC de leitura/formato com Problem Details controlado e decidir explicitamente a política de despacho ERROR, preservando a proteção de requisições normais. Não liberar indiscriminadamente a URL `/error`. Manter testes com servidor real para JSON inválido, mídia não suportada e os caminhos normais de autenticação.

**Referências:** [erros REST no Spring MVC](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-rest-exceptions.html) e [autorização por tipo de despacho no Spring Security](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html).

### R4 — WARNING: documentação de entrada descreve um estágio anterior

`README.md` ainda informa 2/8 planos e trata JWT/login como trabalho futuro. `SolveIT_GSD_SystemPrompt.md`, seção Tarefa Atual, ainda aponta para discussão da Fase 2. STATE/ROADMAP já registravam 8/8, pendentes de verificação. Isso pode provocar uma retomada incorreta em outra máquina.

**Correção proposta:** sincronizar README, Tarefa Atual e o resultado de verificação após as correções. Preservar resumos históricos e distinguir planos implementados de fase verificada. O checklist AUTH-06 representa a fundação aprovada, não certifica automaticamente operações futuras.

### R5 — WARNING / melhoria de robustez: filtro pode ser registrado em duas cadeias

`JwtAuthenticationFilter` é `@Component` e também entra por `addFilterBefore`; não há desativação explícita do registro servlet do bean. O [Spring Security documenta esse registro automático](https://docs.spring.io/spring-security/reference/servlet/architecture.html) e recomenda `FilterRegistrationBean` desabilitado quando a execução deve ocorrer somente na cadeia de segurança.

Observação estática: **não foi reproduzido bypass nem consulta duplicada**. `OncePerRequestFilter` evita repetição no fluxo normal observado. Recomenda-se explicitar um único registro e verificar seu comportamento com HTTP real, em tarefa pequena, sem atribuir a esse ponto a causa dos outros achados.

## O que está coerente com as decisões

- Cadastro força SOLICITANTE ativo; concorrência de e-mail é protegida pela constraint e classificada de forma controlada.
- BCrypt, senha preservada, mínimo de 15 code points e limite de 72 bytes; segredo JWT externo e validação de configuração.
- Login de conta ativa emite Bearer; emissor inclui expiração configurável; dados pessoais e perfil não são usados como claims de autoridade.
- Sem cookies de autenticação, refresh token ou sessão HTTP, conforme aprovado. A ausência desses mecanismos não é uma pendência desta fase.
- `/me` deriva identidade do principal; perfil e atividade são recarregados do banco. Testes cobrem desativação, mudança de perfil, reativação e isolamento entre requisições.
- Plano 08 prova autorização por perfil e relação persistida com componentes exclusivos de teste. As políticas concretas de Chamado pertencem à Fase 3.
- `ApiErrorCode` existe e participa do contrato; sua omissão anterior foi tratada na implementação/documentação dos planos.

## Cobertura e limitações

| Grupo | Testes existentes |
|---|---:|
| Configuração JWT | 4 |
| JWT | 11 |
| Calibração BCrypt | 1 |
| Cadastro | 10 |
| Login | 7 |
| Segurança/autorização | 15 |
| Persistência (fundação) | 7 |
| **Total** | **55** |

Os testes existentes oferecem boa evidência dos fluxos aprovados. As lacunas são casos de entrada/claims e integração do mecanismo de erro real, não ausência geral de testes. Acrescentar casos de TTL zero/negativo e issuer/audience vazios pode fortalecer a configuração; a implementação já valida essas restrições.

Avisos de encerramento de pools Hikari e compatibilidade declarada do Flyway com MySQL apareceram nos testes; migrações e validação passaram no MySQL 8.4.11. Não são justificativa, por si, para atualizar dependências nesta correção. Rate limiting continua deliberadamente adiado, com decisão obrigatória antes de exposição à internet. Não houve auditoria de infraestrutura de produção nem teste de SMTP/frontend, fora do escopo implementado.

## Sequência proposta para correção

1. Fechar o contrato temporal JWT e acrescentar testes de regressão.
2. Completar validação do cadastro, preservando normalização e senha.
3. Corrigir erros HTTP e verificar a execução do filtro no servidor real.
4. Executar a suíte completa, rever os achados e sincronizar documentos/estado.

Apresentar a implementação detalhada de cada passo antes de executar, conforme o modo pedagógico do projeto. Push permanece sujeito à aprovação explícita do usuário depois do resumo e dos testes; não é necessário esperar uma fase inteira para criar commits locais ou realizar um checkpoint autorizado.
