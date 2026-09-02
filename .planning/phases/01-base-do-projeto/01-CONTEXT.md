# Phase 1: Base do Projeto - Context

**Gathered:** 2026-09-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase entrega a fundação executável do back-end: projeto Spring Boot, configuração local MySQL, enums, entidades JPA, repositórios e migrações Flyway capazes de criar e validar o esquema. Autenticação, endpoints de negócio, notificações e interface pertencem às fases seguintes.

</domain>

<decisions>
## Implementation Decisions

### Bootstrap e dependências
- **D-01:** Usar Maven e Java 17 com a linha estável atual do Spring Boot 3, deixando o BOM do Boot controlar versões do ecossistema.
- **D-02:** Incluir somente dependências necessárias à fundação e aos testes desta fase; segurança, JWT e e-mail entram quando suas fases começarem.
- **D-03:** Organizar o back-end em `solveit-api/` com package base `com.solveit` e os diretórios prescritos no documento de origem.

### Esquema e migrações
- **D-04:** Flyway é a única autoridade de criação/evolução do esquema; Hibernate usa `ddl-auto=validate` — **Reversibility:** costly — mudar depois exigiria reconciliar histórico de migrações e ambientes existentes.
- **D-05:** Criar migrações numeradas e imutáveis para as cinco tabelas, com chaves estrangeiras, unicidade, nulabilidade e índices alinhados às consultas previstas.
- **D-06:** Persistir enums como texto, nunca ordinal — **Reversibility:** costly — alterar representação após dados existentes exige migração de dados.
- **D-07:** Adotar controle de versão otimista no agregado Chamado desde a criação do modelo — **Reversibility:** costly — introduzi-lo depois mudaria esquema e contratos de atualização concorrente.

### Modelo JPA
- **D-08:** Entidades refletem o domínio, mas não serão contratos REST; DTOs serão introduzidos com os endpoints.
- **D-09:** Datas de criação e atualização são controladas consistentemente pela aplicação/JPA, com o banco preservando restrições essenciais.
- **D-10:** Comentario e HistoricoStatus são modelados como registros append-only; nenhuma operação de atualização ou exclusão será planejada para eles.

### Ambiente e validação
- **D-11:** `application.yml` ativa o perfil de desenvolvimento, enquanto credenciais e segredos ficam externalizáveis por variáveis de ambiente; nenhum segredo real será versionado.
- **D-12:** Validar a fundação com um teste de integração sobre MySQL via Testcontainers, provando migrações limpas e compatibilidade JPA; não usar H2 como substituto de MySQL.
- **D-13:** Fornecer configuração local simples e explicações pedagógicas sobre PK, FK, índices, nulabilidade e migrações.

### the agent's Discretion
- Nomes exatos de artefatos Maven auxiliares, plugins de qualidade e detalhes internos de builders/construtores, desde que mantenham Java 17, simplicidade pedagógica e compatibilidade com JPA.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Escopo e domínio
- `SolveIT_GSD_SystemPrompt.md` — fonte da stack, entidades, estrutura de pastas, regras e ordem de desenvolvimento.
- `.planning/PROJECT.md` — valor central, restrições e decisões do projeto.
- `.planning/REQUIREMENTS.md` — requisitos DATA-01 a DATA-04 atribuídos a esta fase.
- `.planning/ROADMAP.md` — objetivo e critérios de sucesso da Fase 1.

### Pesquisa
- `.planning/research/STACK.md` — versões e dependências recomendadas.
- `.planning/research/ARCHITECTURE.md` — limites de camada, persistência e ordem de construção.
- `.planning/research/PITFALLS.md` — riscos de drift Flyway/JPA, enums e concorrência.
- `.planning/research/SUMMARY.md` — síntese e implicações para o roadmap.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- Não há código de aplicação existente; o projeto é greenfield.

### Established Patterns
- A estrutura de pacotes e a stack estão prescritas no documento de origem.
- Planejamento GSD e commits atômicos já estão configurados no repositório.

### Integration Points
- O novo módulo `solveit-api/` será a base para autenticação e demais endpoints nas fases posteriores.
- O esquema Flyway e as entidades serão consumidos pelos serviços e repositórios das próximas fases.

</code_context>

<specifics>
## Specific Ideas

- O material produzido deve ensinar o “porquê”, especialmente conceitos de banco de dados e Spring Boot.
- A fase deve terminar com a aplicação iniciando e o esquema sendo recriado do zero, não apenas com classes que compilam.

</specifics>

<deferred>
## Deferred Ideas

- DTOs HTTP, tratamento global de erros e endpoints serão aprofundados nas fases 2 e 3.
- Autenticação, e-mail, dashboard e frontend permanecem nas fases designadas pelo roadmap.

</deferred>

---

*Phase: 1-Base do Projeto*
*Context gathered: 2026-09-02*
