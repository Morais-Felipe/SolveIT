# Phase 3: Core de Chamados - Research

**Researched:** 2026-09-11
**Domain:** Spring MVC/JPA, autorização relacional, auditoria e concorrência MySQL
**Confidence:** LOW pela classificação automática do provider webfetch; fontes oficiais citadas e propostas de desenho distinguidas abaixo.

<user_constraints>
## User Constraints (from CONTEXT.md)

Cópia literal das decisões aprovadas; a seção de diretrizes contém as áreas de discrição técnica.

## Implementation Decisions

### Visibilidade e fila
- **D-01:** SOLICITANTE acessa seus próprios chamados; ADMIN mantém visão geral. ATENDENTE acessa ABERTOS sem responsável e chamados atribuídos a ele, inclusive encerrados.
- **D-02:** Antes de assumir, atendente pode ler detalhe completo, comentários e histórico, e adicionar comentários nos chamados disponíveis.
- **D-03:** Comentário não reserva o chamado, não altera status/responsável e não concede preferência nem acesso permanente. Após outro atendente assumir, o comentarista perde acesso; seus comentários permanecem.
- **D-04:** Vence a primeira atribuição confirmada pelo servidor. A tentativa concorrente não sobrescreve o responsável.

### Administração
- **D-05:** Desativar um atendente com chamados EM_ANDAMENTO exige confirmação explícita mostrando a quantidade a devolver. A conta é desativada e todos esses chamados voltam a ABERTO, sem responsável, numa única transação.
- **D-06:** Cada retorno gera histórico com o administrador como ator e motivo automático obrigatório: retorno à fila por desativação do atendente. Preservar comentários e históricos anteriores.
- **D-07:** Qualquer mudança que retire o perfil ATENDENTE usa o mesmo comportamento para chamados EM_ANDAMENTO, inclusive confirmação e motivo automático específico de mudança de perfil.
- **D-08:** EM_ANDAMENTO → ABERTO é exceção exclusiva dessas operações administrativas. Ao retornar, título/descrição voltam a ser editáveis conforme TICK-05. FECHADO e CANCELADO continuam sem reabertura.
- **D-09:** Bloquear desativação ou retirada do perfil do último ADMIN ativo. Com outro ADMIN ativo, essas ações são permitidas.
- **D-10:** Chamados RESOLVIDOS/FECHADOS/CANCELADOS preservam o vínculo histórico ao antigo atendente e não impedem desativação. Acesso obedece ao perfil atual, sem conservar permissões antigas; ao virar SOLICITANTE, vê apenas os chamados que abriu.
- **D-11:** Categoria inativa não aceita novos chamados. Chamados existentes preservam vínculo/nome visível e seguem o atendimento normalmente.

### Comentários e histórico
- **D-12:** Comentários novos apenas em ABERTO, EM_ANDAMENTO e RESOLVIDO, por identidade autorizada. FECHADO/CANCELADO permitem apenas leitura de mensagens e histórico, respeitando o escopo de acesso.
- **D-13:** Registrar a abertura no histórico com autor, data e estado inicial ABERTO. Pesquisar a representação do estado anterior inexistente no esquema atual; não inventar transição anterior fictícia.
- **D-14:** Observação opcional nas transições manuais; motivo obrigatório gerado pelo sistema nas devoluções administrativas.
- **D-15:** Comentários e histórico em ordem cronológica crescente, com ID como desempate determinístico. Comentários permanecem imutáveis.

### Concorrência e atomicidade
- **D-16:** Rejeitar alterações desatualizadas e orientar atualização antes de nova tentativa. Exemplo: edição iniciada em ABERTO não pode sobrescrever uma atribuição confirmada depois.
- **D-17:** Conflito em qualquer chamado durante desativação/mudança de perfil desfaz toda essa tentativa administrativa, mantendo a conta como estava e sem devoluções parciais. Mudanças confirmadas por outras transações permanecem. Exigir nova revisão/confirmação.
- **D-18:** Validar estado atual ao adicionar comentário. Se o encerramento já ocorreu, recusar: “Este chamado foi encerrado e não aceita novos comentários.”
- **D-19:** Tentativas rejeitadas não geram HistoricoStatus. Abertura e mudanças confirmadas geram exatamente seus registros na mesma transação.

### Diretrizes para pesquisa e planejamento
- Preservar JWT Bearer sem cookies/refresh, identidade atual do banco, DTOs explícitos, Problem Details, Flyway e modo pedagógico.
- Usar uma branch/ticket/PR por plano de implementação aprovado, integrando o anterior antes da execução seguinte. Esta branch é documental, não corresponde ao plano 03-01.
- Pesquisar confirmação de operações em lote na API e proteção contra mudanças entre prévia e confirmação; implementação exata ainda não escolhida.
- Verificar proteção concorrente do último ADMIN, mudanças de perfil versus atribuição e fechamento versus comentário. @Version em Chamado é base existente, não prova suficiente de todos esses casos.
- Avaliar migração aditiva para registro inicial e semântica de resolvidoEm no retorno à fila. Não editar migrações já aplicadas.
- Nomes internos, códigos adicionais de erro, paginação e desenho transacional ficam para pesquisa/planejamento, preservando as decisões acima.

## Deferred Ideas
Interface de confirmação será concretizada na Fase 6; contrato seguro da operação pertence à Fase 3. E-mail permanece na Fase 4. Nenhuma autorização para implementar reabertura genérica ou transferência livre.

</user_constraints>

## Summary

A fase deve reutilizar a API existente e implementar os serviços transacionais, controllers e DTOs do domínio. O principal risco é preservar conjuntamente autorização atual, confirmação administrativa e histórico quando requisições concorrem. A presença de uma versão no chamado não resolve, sozinha, a proteção do último administrador nem a inserção concorrente de comentário. [VERIFIED: solveit-api/src/main/java/com/solveit/entity/Chamado.java:67-69] Trecho: `@Version`, `@Column(name = "version", nullable = false)`, `private Long version;`.

**Recomendação de desenho para aprovação no plano:** usar inicialmente uma linha persistente de coordenação, bloqueada antes de qualquer escrita da Fase 3, mais versões esperadas enviadas pelo cliente. Trata-se de uma proposta deliberadamente conservadora: serializa escritas curtas e simplifica a prova de correção; não é uma exigência de Spring nem uma decisão de produto já aprovada. [ASSUMED]

## Architectural Responsibility Map

Proposta de divisão que atende às decisões acima. [ASSUMED]

| Capacidade | Primária | Secundária | Responsabilidade |
|---|---|---|---|
| Identidade e autorização atual | API/service | Banco | Revalidar ator antes de escrever |
| Fila, filtros e paginação | Banco | API | Escopo obrigatório combinado com filtros |
| Confirmação administrativa | API | Cliente futuro | Comparar prévia completa; cliente exibe quantidade |
| Transições e histórico | Service transacional | Banco | Uma confirmação, uma mudança, um registro |
| Imutabilidade de comentários | API/service | Banco | Apenas inserção e consulta |
| Concorrência | Banco | API | Lock, versões esperadas e resposta de conflito |

## Project Constraints (from AGENTS.md)

Diretivas de projeto lidas nesta sessão: Java 17/Spring Boot 3; MySQL 8/Flyway; JWT/BCrypt; arquitetura em camadas estabelecida; autorização por perfil e relação no servidor; comentários imutáveis; histórico obrigatório; SQL e JPA coerentes; migrações aplicadas imutáveis; testes MySQL reais, sem H2 como evidência; DTOs explícitos; não introduzir Lombok, MapStruct, WebFlux, brokers ou segredos versionados. SMTP pertence à fase posterior. [VERIFIED: AGENTS.md]

Execução exige plano apresentado/aprovado, uma tarefa por vez, explicação antes e interpretação depois de comandos, ambiguidades consultadas, workflow GSD, branch própria por plano, integração antes do plano seguinte e aprovações remotas específicas. Registrar correspondência plano/branch/PR e checkpoint para troca de máquina. [VERIFIED: AGENTS.md; docs/GIT-WORKFLOW.md]

## Standard Stack

Manter dependências já declaradas; esta pesquisa não recomenda instalação nem atualização de pacotes. [VERIFIED: solveit-api/pom.xml:7-23,25-97] Valores literais: `spring-boot-starter-parent` / `3.5.16`; `<java.version>17</java.version>`; `<jjwt.version>0.13.0</jjwt.version>`.

| Elemento existente | Uso proposto |
|---|---|
| Spring MVC, Validation e Security | DTOs, validação e proteção de rotas/services |
| Spring Data JPA / Hibernate | Repositórios, transações e versões |
| Flyway + MySQL | Migrações aditivas e locks persistentes |
| JUnit, MockMvc e Testcontainers | Regras, contratos reais e concorrência |

Bibliotecas confirmadas no POM: `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `flyway-core`, `flyway-mysql`, `mysql-connector-j`, `spring-boot-starter-test`, `spring-security-test`, `spring-boot-testcontainers`, `junit-jupiter`, `mysql`. [VERIFIED: solveit-api/pom.xml:25-97]

## Architecture Patterns

### Protocolo transacional recomendado

Todo item deste protocolo é proposta técnica para os planos, não descrição de código existente. [ASSUMED]

1. Controller valida forma do DTO e obtém somente o ID do principal autenticado.
2. Service público chamado via proxy inicia transação; sua primeira leitura de estado mutável deve ser a aquisição exclusiva da linha de coordenação por chave primária.
3. Carregar novamente ator e alvo dentro dessa transação; rejeitar ator inativo/perfil atual inadequado. Não confiar em entidades carregadas anteriormente pelo filtro nem no perfil do token.
4. Depois da linha de coordenação, ordem uniforme: usuários por ID, categoria quando necessária, chamados por ID. Usar leitura atual bloqueante para estado decisório, evitando snapshot criado antes do lock.
5. Comparar versão esperada com versão atual, validar relação e regra. Só então alterar entidades e inserir histórico/comentário.
6. Flush e commit na mesma unidade; qualquer falha de regra, versão ou persistência desfaz a tentativa completa. Converter conflito no handler externo; não capturar para continuar usando transação marcada para rollback.
7. Nenhum SMTP, espera por confirmação humana ou outra chamada externa dentro da transação.

Spring Data permite configurar locks nos métodos de consulta com `@Lock`. MySQL mantém locks de leituras bloqueantes até conclusão da transação. [CITED: https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/locking.html] [CITED: https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html]

A transação declarativa deve ser atravessada pelo proxy Spring; chamada interna entre métodos da mesma instância não aplica uma nova fronteira anotada. [CITED: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html]

**Custo:** uma única escrita coordenada por vez, inclusive entre instâncias da API. A proposta favorece auditabilidade pedagógica; particionar locks só futuramente, com medições e nova prova de ordem. Todos os caminhos de escrita precisam aderir, inclusive criação/edição de usuários/categorias, atribuição, transição, abertura e comentário. Cadastro público pode permanecer fora: cria exclusivamente solicitante, não altera atores ou conjunto de administradores; unicidade continua garantida pelo banco. [ASSUMED]

### Prévia administrativa completa e confirmação

Contrato recomendado, nomes ainda propostos: endpoint de prévia somente ADMIN recebe ação pretendida e alvo; devolve versão do usuário, ação/perfil/ativo desejado, quantidade e lista **completa** ordenada de pares ID/versão dos chamados afetados. A confirmação reenvia esse conteúdo e um booleano explícito. Não paginar silenciosamente esse conjunto nem aceitar somente quantidade. [ASSUMED]

Sob o lock global, recarregar alvo e conjunto atual, comparar exatamente alvo, versão, ação, cardinalidade, IDs e versões. Rejeitar conjunto com duplicações, omissões, inclusões, versão ausente ou item alterado. Se um chamado foi resolvido, retirado ou adicionado desde a prévia, rejeitar tudo e exigir outra revisão; nunca recalcular silenciosamente e devolver o novo conjunto. Comparar inclusive prévia vazia: atribuição ocorrida depois precisa invalidá-la. [ASSUMED]

Um DTO de prévia não comprova que uma pessoa leu a tela; garante que a API não aplica estado diferente do explicitamente confirmado. Não é necessário criar JWT de confirmação nem guardar estado de sessão. A operação deve sempre reautorizar o administrador. Hash assinado ou registro persistente de prévia seria alternativa se surgisse requisito de comprovar emissão prévia pelo servidor. [ASSUMED]

Adicionar versão ao usuário em migração aditiva e expô-la em DTO administrativo; exigir versão em edição/status mesmo sem chamados afetados. Isso evita sobrescrever nome/e-mail/perfil/ativo com formulário antigo. Propor versão também para categoria para cumprir rejeição de edição desatualizada nesse recurso. Não aceitar setters de versão do cliente. [ASSUMED]

### Proteção do último administrador e corridas

Com a coordenação adquirida, recontar administradores ativos e avaliar o resultado da operação. Duas demissões/desativações concorrentes não podem ambas usar a contagem anterior. Revalidar também o ator: se um administrador foi desativado enquanto aguardava, não pode concluir uma operação com autoridade antiga. [ASSUMED]

Atribuição valida atendente ativo/perfil atual sob o mesmo protocolo; se desativação vence, atribuição falha. Se atribuição vence depois da prévia, a confirmação administrativa falha por conjunto diferente. Comentário valida acesso/status após o lock: fechamento vencedor impede inserção; comentário vencedor permanece e pode ser seguido por fechamento. A criação de chamado valida categoria ativa no mesmo protocolo usado na desativação de categoria. [ASSUMED]

### Histórico inicial e datas

O esquema atual exige estado anterior: `status_anterior VARCHAR(20) NOT NULL`; os estados são `'ABERTO', 'EM_ANDAMENTO', 'RESOLVIDO', 'FECHADO', 'CANCELADO'`. [VERIFIED: solveit-api/src/main/resources/db/migration/V5__create_historico_status.sql:4-14]

Criar nova migração tornando anterior anulável e ajustar JPA; abertura será anterior nulo/novo aberto. Acrescentar CHECK que permita anterior nulo somente para abertura. Preservar registros antigos; não inventar retroativamente autoria/data de abertura que não foi auditada. Gerar o primeiro histórico junto ao chamado. [ASSUMED]

Retorno administrativo afeta somente chamados ainda em andamento: limpar responsável e manter data de resolução nula; não apagar a data de chamados resolvidos/encerrados preservados. No retorno, definir explicitamente resolução nula para resguardar a invariante. Ao resolver preencher UTC; ao fechar preservar resolução. [ASSUMED]

O mapeamento existente contém `private LocalDateTime resolvidoEm;` e `@Column(name = "resolvido_em", nullable = true, columnDefinition = "DATETIME(6)")`. [VERIFIED: solveit-api/src/main/java/com/solveit/entity/Chamado.java:64-65]

### Visibilidade e paginação

Implementar um predicado obrigatório de visibilidade e combiná-lo por AND com TODOS os filtros opcionais; reutilizar a mesma política no detalhe, comentários e histórico. Para atendente, agrupar corretamente a alternativa fila disponível OU atribuído ao próprio. Parâmetros de atendente/solicitante nunca substituem o escopo do principal. Propor página limitada e ordenação com ID como desempate; lista e total precisam usar o mesmo predicado. Mapear DTOs em transação; não fazer fetch de coleções de comentários dentro da paginação de chamados. [ASSUMED]

Usar `Specification` e `JpaSpecificationExecutor` da linha 3.5; não copiar a API nova de predicados da linha 4 encontrada na documentação latest. A composição de specifications permite combinar critérios reutilizáveis. [CITED: https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/specifications.html]

### Primeiro administrador

A busca nos fontes, docs e artefatos de autenticação não encontrou bootstrap operacional; o serviço de cadastro lido cria apenas solicitante. Isso é lacuna observada, não prova de inexistência em sistemas externos. [VERIFIED: inspeção AuthService.java e busca bootstrap/primeiro admin/CommandLineRunner/ApplicationRunner/INSERT INTO usuario, 2026-09-11]

Proposta mínima: documentar provisionamento local controlado, após cadastro normal, promovendo um ID/e-mail exatos com acesso administrativo ao banco e conferência de uma única linha. Não criar endpoint público, senha padrão ou promoção automática a cada startup. Para teste, fixtures criam administrador; para operação real o procedimento exige aprovação antes de executar. Runner one-shot seria escopo adicional opcional, não necessário ao CRUD administrativo. [ASSUMED]

## Don't Hand-Roll

| Problema | Usar | Evitar |
|---|---|---|
| Concorrência entre instâncias | Lock de linha MySQL/JPA | synchronized/lock em memória |
| Stale update | Versão JPA + versão esperada no DTO | Apenas reler último valor e sobrescrever |
| Atomicidade | Transação Spring | Compensações manuais parciais |
| Filtros | Criteria/Specification parametrizado | Concatenação SQL e filtro em memória |
| Senhas e token | Componentes herdados | Novo hash, token de autorização caseiro |

Recomendações de desenho [ASSUMED], apoiadas nos contratos de locks/versionamento JPA [CITED: https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1].

## Common Pitfalls

- Guard adquirido depois da leitura do usuário: autoridade/snapshot já podem estar antigos.
- Lock somente no chamado: não protege redução simultânea do conjunto de administradores.
- Versão do chamado não incrementada ao inserir comentário: sozinha não protege comentário versus fechamento.
- Prévia só com quantidade: dois conjuntos diferentes podem ter mesmo tamanho.
- Atualização em lote JPQL/SQL: pode contornar callbacks/versões e omitir histórico individual.
- Repetição automática após conflito: aplicaria operação sem nova revisão humana.
- Teste concorrente que espera dois workers chegarem depois do mesmo lock exclusivo: deadlock do próprio teste.
- Ajustar migrações antigas para passar teste: viola upgrade de banco existente.

Riscos identificados por análise do protocolo proposto [ASSUMED].

## Code Examples

Esqueleto ilustrativo proposto; nomes são novos, não símbolos existentes. [ASSUMED]

```java
@Transactional
public void executar(Comando comando, Long atorId) {
    coordenacao.adquirir(); // primeira leitura de estado da operação
    var ator = usuarios.carregarAtual(atorId);
    autorizacao.validarAtivoEPerfil(ator);
    var alvo = usuarios.carregarAtual(comando.alvoId());
    validarVersao(alvo, comando.versaoEsperada());
    var atuais = chamados.carregarAfetadosOrdenados(alvo);
    validarConfirmacaoCompleta(comando, alvo, atuais);
    aplicarAlteracaoEHistoricos(ator, alvo, atuais);
    entityManager.flush();
}
```

O lock deve pertencer à mesma transação da escrita; versão otimista detecta estado concorrente, não substitui autorização. [CITED: https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1]

## Test Strategy and Requirement Coverage

Nyquist está explicitamente desabilitado; os testes funcionais abaixo continuam necessários. [VERIFIED: .planning/config.json]

Toda linha abaixo é cobertura proposta, ainda não implementada. [ASSUMED]

| Requisitos | Evidência obrigatória |
|---|---|
| ADMN-01/02 | Paginação, perfis, email normalizado/duplicado, resposta sem senha, edição stale |
| ADMN-03 | Último ADMIN concorrente; prévia exata; vazio→não vazio; mesma quantidade/outros IDs; rollback integral; ator desativado aguardando lock |
| ADMN-04/05/06 | Leitura permitida, escrita só admin, nome duplicado, categoria inativa e abertura concorrente |
| TICK-01/11 | Abertura e histórico inicial atômicos; falha de histórico desfaz chamado |
| TICK-02/03/04 | Matriz de visibilidade, filtros combinados, total correto, paginação estável |
| TICK-05/12 | Formulário stale após atribuição; versão ausente/incorreta; nenhuma sobrescrita |
| TICK-06 | Duas atribuições: um vencedor; alvo ativo/perfil; disputa com retirada de perfil |
| TICK-07/08/09/10 | Matriz completa de transições por ator/estado; exceção administrativa exclusiva |
| COLL-01/02/03/04 | Comentário sem reserva; perda de acesso; encerramento concorrente; ordem e imutabilidade |

Usar testes unitários para matriz de política e MockMvc com filtro real para IDs falsificados/401/403/erros controlados. Usar MySQL Testcontainers com duas transações reais, conexões separadas, latches e timeout finito para corridas. Coordenar worker A após adquirir lock, iniciar B antes de liberar A, então verificar estado FINAL e quantidade de históricos. Não substituir esses testes por Mockito ou sleeps. [ASSUMED]

Manter suite anterior; ajustar testes que fixam quantidade de tabelas/migrações/constraints quando novas migrações forem introduzidas. Testar banco vazio e upgrade desde versão anterior. Comando proposto na pasta backend: `./mvnw.cmd clean verify`; seleção unitária durante tarefa não exige Docker. [ASSUMED]

## Environment Availability

| Dependência | Observação nesta sessão |
|---|---|
| Java | Temurin 17.0.20.1 retornado pelo CLI |
| Docker daemon | Versão servidor 29.7.2 retornada |
| MySQL de integração | Teste existente usa imagem "mysql:8.4.11"; container não iniciado nesta pesquisa |

[VERIFIED: java -version; docker version, 2026-09-11] Imagem literal `new MySQLContainer<>("mysql:8.4.11")`. [VERIFIED: solveit-api/src/test/java/com/solveit/PersistenceIntegrationTest.java:82-88]

## Security Domain

Mapa de controles proposto; não declarar certificação ASVS. A numeração de categorias varia por edição: fixar a edição no relatório antes de atribuir IDs normativos. [ASSUMED]

| Área aplicável | Ameaça | Controle proposto |
|---|---|---|
| Autenticação/sessão | Ator com estado revogado | Bearer herdado + recarga transacional |
| Controle de acesso | IDOR/perfil errado | Escopo SQL e autorização de cada ação |
| Validação | Overposting/identidade falsa | DTO permitido, autor derivado do principal |
| Negócio/concorrência | Último admin/atribuição indevida | Coordenação persistente + comparação stale |
| Auditoria | História ausente/falsa | Inserts na transação e rollback |
| Criptografia | Senha/token expostos | BCrypt/JWT existentes; DTO sem credencial |
| Disponibilidade | Lote/lock excessivo | Limites explícitos, transações curtas, timeout; nunca truncar prévia |

## Assumptions Log

As marcações ASSUMED acima são propostas de implementação que devem constar da apresentação do plano, não novas decisões de produto silenciosas.

| ID | Proposta | Risco/validação |
|---|---|---|
| A1 | Guard global e ordem única | Contenção; exigir aderência de todas as escritas |
| A2 | Prévia completa ecoada + versões usuário/categoria | DTO e payload devem validar integralmente |
| A3 | Migração inicial/datas | Testar upgrade e invariantes |
| A4 | Specifications/DTOs/paginação | Testar igualdade de escopo de lista e detalhe |
| A5 | Bootstrap operacional manual | Apresentar procedimento antes da primeira execução |
| A6 | Matriz/testes/limites/erros | Fixar nomes/limites no plano e verificar em MySQL |
| A7 | Controle ASVS por área | Fixar edição antes de usar IDs normativos |

## Open Questions

Nenhuma decisão D-01 a D-19 precisa ser reaberta. Apresentar custo da coordenação global e procedimento de primeiro ADMIN no plano antes de implementar. A interpretação de categoria inativa consultável por ID deve ser explícita: proposta é permitir ao ADMIN e a quem possui chamado visível vinculado, preservando nome no DTO do chamado; confirmar no plano por ser detalhe ainda não especificado. [ASSUMED]

## Sources

- https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/locking.html — locks na linha compatível.
- https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/specifications.html — composição de filtros.
- https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html — leituras bloqueantes.
- https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1 — versões e locks.
- https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html — fronteira proxy.
- Fontes locais abertas: contexto, requisitos, projeto, AGENTS, política Git, documento de origem, POM, entidades, repositórios, migrações, filtro JWT, AuthService e teste de persistência.

## Metadata

Não houve instalação nem benchmark nesta pesquisa. Consulta automática classify-confidence retornou LOW para webfetch mesmo com verified; conservar essa limitação em vez de inventar HIGH. Lookup direto de fontes oficiais usado; consulta OWASP falhou e não é evidência normativa. Esta pesquisa não comprova runtime concorrente: essa prova é tarefa dos testes planejados.

