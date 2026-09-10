# Phase 2: Autenticação - Context

**Gathered:** 2026-09-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase entrega a identidade segura do back-end: cadastro público de solicitantes, login com e-mail e senha, emissão e validação de JWT temporário, consulta do usuário autenticado em `GET /api/auth/me` e uma base de autorização que usa o estado atual do usuário no banco. Inclui respostas de erro consistentes para autenticação e acesso.

Não inclui interface React, armazenamento do token no navegador, refresh token, recuperação de senha, confirmação por e-mail, MFA nem as regras específicas dos chamados. A autorização concreta por proprietário e atendente será aplicada aos recursos introduzidos nas fases seguintes, sempre no servidor.

</domain>

<decisions>
## Implementation Decisions

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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Escopo e contratos
- `SolveIT_GSD_SystemPrompt.md` — rotas de autenticação, estrutura do back-end e sequência original da Fase 2.
- `.planning/PROJECT.md` — valor central, restrições de JWT/BCrypt e servidor como autoridade de autorização.
- `.planning/REQUIREMENTS.md` — requisitos AUTH-01 a AUTH-06 atribuídos à fase.
- `.planning/ROADMAP.md` — objetivo e critérios de sucesso da Fase 2.
- `.planning/phases/01-base-do-projeto/01-CONTEXT.md` — decisões herdadas sobre DTOs, esquema, entidades e configuração externalizada.

### Pesquisa existente
- `.planning/research/STACK.md` — dependências JJWT e Spring Security, testes MockMvc e diretrizes de configuração segura.
- `.planning/research/ARCHITECTURE.md` — limites de camada e integração da autenticação com o monólito modular.
- `.planning/research/PITFALLS.md` — riscos de segurança e de implementação que o planejamento deve mitigar.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `solveit-api/src/main/java/com/solveit/entity/Usuario.java` — entidade existente com e-mail, senha, perfil, estado ativo e criação em UTC.
- `solveit-api/src/main/java/com/solveit/repository/UsuarioRepository.java` — já fornece busca e verificação de existência por e-mail.
- `solveit-api/src/main/resources/db/migration/V1__create_usuario.sql` — garante e-mail único, perfis permitidos e conta ativa por padrão.
- `solveit-api/pom.xml` — ponto de entrada para as dependências de Security, Validation e JJWT desta fase.

### Established Patterns
- Entidades JPA não são contratos REST; esta fase introduz DTOs explícitos.
- Flyway permanece a única autoridade DDL e Hibernate continua em `ddl-auto=validate`.
- Classes Java permanecem explícitas, sem Lombok, para preservar a leitura pedagógica.
- E-mails já usam unicidade no banco com collation sem distinção de maiúsculas e minúsculas; a aplicação acrescenta normalização determinística.

### Integration Points
- O filtro JWT integra-se à cadeia do Spring Security antes do acesso aos controllers protegidos.
- O serviço de autenticação usa `UsuarioRepository`, `PasswordEncoder` e o componente JWT sem expor entidades diretamente.
- Um tratamento global de exceções estabelece Problem Details para esta fase e para os endpoints futuros.
- Testes de segurança devem atravessar a cadeia real do Spring Security com MockMvc e cobrir cenários permitidos e negados.

</code_context>

<specifics>
## Specific Ideas

- Ensinar separadamente cadastro, comprovação de credenciais, emissão de token, autenticação por requisição e autorização.
- Usar a mensagem “Sua sessão expirou. Entre novamente para continuar.” para tornar a expiração compreensível ao usuário.
- Explicar nos planos e na implementação por que `401` significa ausência de identidade válida e `403` significa identidade conhecida sem permissão.

</specifics>

<deferred>
## Deferred Ideas

- Refresh token, rotação e revogação persistente de sessões — capacidade futura, se a experiência exigir sessões longas.
- Armazenamento do token no navegador e restauração da sessão no React — Fase 6.
- Recuperação de senha — requisito v2 `ACCT-01`.
- Confirmação por e-mail e autenticação multifator — fora do escopo atual.

</deferred>

---

*Phase: 2-Autenticação*
*Context gathered: 2026-09-05*
