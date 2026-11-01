# Evidências de qualidade do SolveIT v1

Mapa entre os requisitos de qualidade (QUAL-01 a QUAL-03, QUAL-05, WEB-07) e os testes que os provam. Gerado no plano 07-02 (2026-10-31). Caminhos relativos a `solveit-api/src/test/java/com/solveit/` quando não indicado.

Como ler: cada linha aponta a **classe** e o **método** que prova a afirmação. Para conferir, rode a classe isoladamente (`./mvnw test -Dtest=Classe`) ou leia o método; os nomes descrevem o comportamento esperado.

## Visão geral

| Camada | O que roda de verdade | Onde |
|---|---|---|
| Unitário | Uma classe isolada, sem Spring nem banco | `service/ChamadoTransitionPolicyTest`, `security/JwtServiceTest`, `config/JwtPropertiesContextTest` |
| Integração (back-end) | Spring completo, cadeia de segurança, MockMvc e **MySQL 8.4 real** (Testcontainers) com as migrations Flyway | `controller/*IntegrationTest`, `service/*ConcorrenciaTest`, `service/notificacao/*`, `PersistenceIntegrationTest` |
| Componente (front-end) | React no jsdom, com a API simulada | `solveit-web/src/**/*.test.tsx` |
| Ponta a ponta | Navegador real → nginx → API → MySQL → Mailpit, via `docker compose` | `solveit-web/e2e/jornadas.e2e.ts` |

Todas rodam no GitHub Actions a cada PR (`.github/workflows/`).

## QUAL-01 — Regras críticas testadas

> Serviços críticos possuem testes unitários para autorização, transições, edição, comentários e destinatários de notificação.

**Escolha deliberada:** a revisão de escopo de 2026-10-30 preferiu provar a maior parte dessas regras com **testes de integração** contra MySQL real, e não com testes unitários com repositórios simulados. Um teste de integração prova de uma vez a regra, a autorização, a transação e o SQL; um teste unitário com mocks provaria só a regra, e o projeto já teve falhas (como locks e ordem de escrita) que só aparecem no banco de verdade. Onde a regra é pura (sem banco), há teste unitário.

| Regra | Tipo | Evidência |
|---|---|---|
| Transições de status por origem, destino e ator | Unitário | `ChamadoTransitionPolicyTest.matchesTheContractForEveryOriginDestinationAndActor`, `finalStatesNeverLeaveAndNothingReopens`, `adminDoesNotInheritRequesterDecisions` |
| Transições pela API, com histórico no mesmo instante | Integração | `ChamadoTransicaoIntegrationTest` (11 testes, ex.: `responsibleAttendantResolvesWithOneInstantAcrossTicketAndHistory`, `rejectsTransitionsOutsideTheTable`) |
| Autorização por perfil e relação | Integração | Matriz da seção QUAL-03 |
| Edição de chamado (só ABERTO; dono ou ADMIN; versão) | Integração | `ChamadoEdicaoIntegrationTest` (`ownerEditsOnlyTitleAndDescriptionOfOpenTicket`, `rejectsEditionOutsideOpenStatus`, `editionStartedBeforeAssignmentDoesNotOverwriteTheService`) |
| Comentários (relação atual, conversa fechada, imutabilidade) | Integração | `ComentarioCriacaoIntegrationTest`, `ComentarioImutabilidadeIntegrationTest` (`noAuthenticatedHttpAttemptChangesOrRemovesAComment`, `comentarioEntityHasNoPublicSetters`) |
| Destinatários de notificação | Integração (SMTP falso GreenMail) | `NotificacaoEmailIntegrationTest` (`openingNotifiesEveryActiveAdmin`, `selfAssignmentDoesNotNotifyTheActor`, `statusChangesNotifyTheRequesterButNotTheActor`, `commentRecipientsAreNotDuplicated`, `rolledBackOpeningSendsNothing`) |
| Falha de SMTP não desfaz a operação | Integração | `NotificacaoSmtpIndisponivelIntegrationTest.smtpFailureDoesNotUndoNorFailTheOperation` |
| Token JWT (assinatura, emissor, audiência, expiração) | Unitário | `JwtServiceTest` (14 testes) |

## QUAL-02 — Integração com MySQL, JWT, escopo, atomicidade e filtros

| Critério | Evidência |
|---|---|
| Migrações em MySQL real | `PersistenceIntegrationTest.schemaMetadataMatchesTheApprovedPhysicalContract`, `versionSixDatabaseUpgradesToCurrentWithoutLosingOrFabricatingData`, `concurrentSpringContextsMigrateAnEmptyDatabaseExactlyOnce`, `repeatedMigrationAndHibernateValidationDoNotChangeSchemaOrHistory` |
| Restrições do banco | `PersistenceIntegrationTest.databaseRejectsDuplicatesRequiredNullsInvalidEnumsAndOrphanReferences` |
| Autenticação JWT na cadeia real | `AuthSecurityIntegrationTest` (`authenticatesBearerPerRequestWithCurrentActiveUserAndNoSession`, `reloadsProfileAndActiveStateOnEveryRequest`, `classifiesEveryInvalidBearerShapeWithoutAuthenticatingTheRequest`, `returnsExpiredProblemDetailsForExpiredBearer`) |
| Escopo por usuário | `ChamadoConsultaIntegrationTest.listShowsExactlyTheTicketsTheDetailPolicyAllows`, `filtersNarrowButNeverWidenTheScope`; `DashboardIntegrationTest.attendantSeesOnlyTheQueueAndTheirOwnTickets`, `everyIndicatorReconcilesWithTheEquivalentListQuery` |
| Atomicidade do histórico | `ChamadoAberturaIntegrationTest.historyFailureRollsBackTheTicket`; `ChamadoAtribuicaoConcorrenciaTest.historyFailureRollsBackAttendantStatusAndVersion`; `ChamadoTransicaoConcorrenciaTest.historyFailureRollsBackStatusDatesAndVersion`; `CoreWriteCoordinatorIntegrationTest.rollsBackJdbcAndJpaWorkTogetherAndReleasesTheGuard` |
| Concorrência | `*ConcorrenciaTest` (primeiro confirmado vence; o perdedor recebe 409 sem efeito parcial); `UsuarioOperacaoConcorrenciaTest.mutualDeactivationLeavesTheFirstWinnerAsTheOnlyAdmin` |
| Filtros e paginação | `ChamadoConsultaIntegrationTest.paginatesInTheDatabaseWithIdAsTieBreakerInTheSameDirection`, `rejectsInvalidListParameters`; `CategoriaIntegrationTest.listsOnlyActiveCategoriesWithStablePagination`; `UsuarioIntegrationTest.listsAllUsersOrderedByIdWithStablePaginationAndNoCredentials` |

## QUAL-03 — Matriz de autorização

Regra geral (`config/SecurityConfig`): toda rota exige autenticação, exceto cadastro e login. Sem token → **401**; perfil ou relação sem permissão → **403** `ACCESS_DENIED`; recurso inexistente → **404**. As rotas são protegidas em duas camadas: a anotação `@PreAuthorize` no controller e a checagem no service, que relê o usuário do banco a cada requisição (perfil trocado ou conta desativada valem na hora: `AuthSecurityIntegrationTest.reloadsProfileAndActiveStateOnEveryRequest`).

Legenda: ✅ permitido · ⛔ 403 · 🔗 depende da relação com o chamado · — não se aplica.

| Rota | Anônimo | SOLICITANTE | ATENDENTE | ADMIN | Evidência |
|---|---|---|---|---|---|
| `POST /api/auth/register` | ✅ | — | — | — | `AuthRegistrationIntegrationTest` |
| `POST /api/auth/login` | ✅ | — | — | — | `AuthLoginIntegrationTest` |
| `GET /api/auth/me` | 401 | ✅ | ✅ | ✅ | `AuthSecurityIntegrationTest.returnsOnlyTheAuthenticatedUsersCurrentPublicDataFromMe`, `returnsProblemDetailsWhenBearerIsMissing` |
| `GET /api/categorias` | 401 | ✅ ativas | ✅ ativas | ✅ + inativas | `CategoriaIntegrationTest.requiresBearerToListCategories`, `listsInactiveCategoriesOnlyForAdminWhenRequested` |
| `GET /api/categorias/{id}` | 401 | ✅ ativa · 🔗 inativa | ✅ ativa · 🔗 inativa | ✅ | `CategoriaIntegrationTest.forbidsInactiveCategoryForSolicitanteButAllowsAdmin`; `ChamadoConsultaIntegrationTest.inactiveCategoryIsReadableByAdminOrWhoeverSeesALinkedTicket` |
| `POST /api/categorias` | 401 | ⛔ | ⛔ | ✅ | `CategoriaIntegrationTest.createsAnActiveCategoryAsAdmin`, `forbidsSolicitanteFromCreatingCategoryWithoutWriting`; `CategoriaEdicaoIntegrationTest.forbidsAtendenteFromEveryCategoryWrite` |
| `PUT /api/categorias/{id}` | 401 | ⛔ | ⛔ | ✅ | `CategoriaEdicaoIntegrationTest.editsCategoryAsAdminAndRejectsRepeatedStalePayload`, `forbidsSolicitanteFromEditingWithoutWriting`, `forbidsAtendenteFromEveryCategoryWrite` |
| `PATCH /api/categorias/{id}/ativo` | 401 | ⛔ | ⛔ | ✅ | `CategoriaEdicaoIntegrationTest.deactivatesReferencedCategoryWithoutDeletingTicketRelationship`, `forbidsSolicitanteFromChangingCategoryActivation`, `forbidsAtendenteFromEveryCategoryWrite` |
| `POST /api/chamados` | 401 | ✅ | ⛔ | ⛔ | `ChamadoAberturaIntegrationTest.solicitanteOpensTicketWithInitialHistoryInTheSameInstant`, `forbidsAdminAndAtendenteFromOpeningTickets`, `requiresBearerToOpenTicket` |
| `GET /api/chamados` | 401 | próprios | fila + atribuídos a ele | todos | `ChamadoConsultaIntegrationTest.listShowsExactlyTheTicketsTheDetailPolicyAllows`, `filtersNarrowButNeverWidenTheScope` |
| `GET /api/chamados/{id}` | 401 | 🔗 dono | 🔗 fila ou responsável | ✅ | `ChamadoConsultaIntegrationTest.appliesTheRelationalAccessMatrixToTicketDetail`, `currentRelationIsTheOnlyAuthority`, `commentAuthorshipDoesNotGrantAccess` |
| `PUT /api/chamados/{id}` | 401 | 🔗 dono, se ABERTO | ⛔ | ✅ se ABERTO | `ChamadoEdicaoIntegrationTest.ownerEditsOnlyTitleAndDescriptionOfOpenTicket`, `adminEditsAnyOpenTicket`, `forbidsAttendantsAndOtherRequesters` |
| `PATCH /api/chamados/{id}/atendente` | 401 | ⛔ | 🔗 assume da fila, só para si | ✅ atribui a atendente ativo | `ChamadoAtribuicaoIntegrationTest.attendantAssumesQueueTicketWithOneHistoryAtTheSameInstant`, `adminAssignsAnotherAttendantAndIsRecordedAsTheActor`, `forbidsRequestersAndAttendantsAssigningSomeoneElse` |
| `PATCH /api/chamados/{id}/status` | 401 | 🔗 dono: fechar resolvido, cancelar aberto | 🔗 responsável: resolver | resolver; cancelar em andamento | `ChamadoTransicaoIntegrationTest.forbidsActorsOutsideTheResolutionRule`, `ownerClosesResolvedTicketKeepingResolutionDate`, `ownerCancelsOpenTicketAndAdminCancelsOngoingOne`, `adminAndAttendantsDoNotInheritRequesterDecisions`; `ChamadoTransitionPolicyTest` |
| `GET /api/chamados/{id}/comentarios` | 401 | 🔗 | 🔗 | ✅ | `ComentarioHistoricoConsultaIntegrationTest.forbidsUsersWithoutRelationAndReturnsNotFoundForMissingTicket`, `commentAuthorshipDoesNotKeepReadAccess` |
| `POST /api/chamados/{id}/comentarios` | 401 | 🔗 conversa aberta | 🔗 conversa aberta | ✅ conversa aberta | `ComentarioCriacaoIntegrationTest.everyoneWithCurrentAccessCommentsWhileTheConversationIsOpen`, `forbidsUsersWithoutCurrentRelation`, `accessIsCheckedBeforeTheClosedState`, `attendantWhoLostTheQueueCannotCommentAfterwards` |
| `GET /api/chamados/{id}/historico` | 401 | 🔗 | 🔗 | ✅ | `ComentarioHistoricoConsultaIntegrationTest.historyFollowsTheSameAccessAsTheTicket` |
| `GET /api/dashboard/resumo` | 401 | ⛔ | ✅ escopo próprio | ✅ tudo | `DashboardIntegrationTest.requesterIsForbiddenAndAnonymousIsUnauthorized`, `attendantSeesOnlyTheQueueAndTheirOwnTickets`, `adminSeesTheWholeOperation` |
| `GET /api/usuarios` | 401 | ⛔ | ⛔ | ✅ | `UsuarioIntegrationTest.requiresBearerToListUsers`, `forbidsNonAdminProfilesFromListingAndReadingUsers` |
| `GET /api/usuarios/{id}` | 401 | ⛔ | ⛔ | ✅ | `UsuarioIntegrationTest.forbidsNonAdminProfilesFromListingAndReadingUsers`, `returnsUserDetailsForAdminWithoutCredentials` |
| `POST /api/usuarios` | 401 | ⛔ | ⛔ | ✅ | `UsuarioIntegrationTest.adminCreatesActiveUsersForEveryProfileWithBcryptHash`, `forbidsNonAdminFromCreatingAndEditingUsers` |
| `PUT /api/usuarios/{id}` | 401 | ⛔ | ⛔ | ✅ | `UsuarioIntegrationTest.editsOnlyPersonalDataWithCurrentVersionAndPreservesPasswordWhenAbsent`, `forbidsNonAdminFromCreatingAndEditingUsers` |
| `PATCH /api/usuarios/{id}/operacao` | 401 | ⛔ | ⛔ | ✅ exceto o último ADMIN ativo | `UsuarioOperacaoIntegrationTest.rejectsNonAdminsStaleVersionsNoOpsAndInvalidBodies`, `protectsTheLastActiveAdmin` |

Garantias transversais:

- **Identidade só pelo token:** IDs enviados pelo cliente não dão acesso (`AuthSecurityIntegrationTest.deniesAdminWhenPersistedRelationshipDoesNotMatchEvenWithForgedUserId`; `ComentarioCriacaoIntegrationTest.rejectsInvalidTextAndClientChosenAuthor`).
- **404 igual para todos os perfis** em chamado inexistente (`ChamadoConsultaIntegrationTest.returnsNotFoundForMissingTicketToEveryProfile`).
- **Erros sem vazamento:** `exception/GlobalExceptionHandlerIntegrationTest` (500 com `errorId`, 401/403/404/405 preservados).

### Revisão feita no 07-02

Cada célula da matriz foi conferida contra o teste citado. Lacuna encontrada e corrigida: as três escritas de categoria só eram testadas contra SOLICITANTE. O novo teste `CategoriaEdicaoIntegrationTest.forbidsAtendenteFromEveryCategoryWrite` cobre o ATENDENTE. Para ter certeza de que ele detecta uma regressão, a regra foi aberta de propósito ao ATENDENTE:

- **Só no controller:** o teste continuou verde, porque o service também checa o perfil (defesa em profundidade).
- **No controller e no service:** o teste falhou com `expected 403 but was 201`.

Nenhuma outra lacuna de autorização foi encontrada.

## QUAL-05 e WEB-07 — Jornadas de ponta a ponta

`solveit-web/e2e/jornadas.e2e.ts`, executado por `npm run e2e` e pelo workflow `E2E` em cada PR, em **desktop (1280 px) e celular (360 px)**:

1. **ADMIN** transforma um usuário em atendente e cria uma categoria.
2. **Solicitante** se cadastra pela tela, abre um chamado na categoria nova e comenta.
3. **Atendente** assume pela fila e resolve; o **solicitante** recebe o e-mail de atribuição no Mailpit e fecha o chamado; o histórico mostra `Resolvido → Fechado`.

Além disso, as jornadas provam:

- **Autorização sem depender da tela:** o solicitante chama `GET /api/usuarios` direto na API e recebe 403.
- **Menu inteiro no celular:** todos os itens do menu aparecem inteiros na tela, sem rolagem.
- **Telas sem rolagem horizontal:** nenhuma tela visitada é mais larga que o aparelho.

## Limites conhecidos

- O E2E cobre uma jornada por perfil, não todas as variações; as variações estão nos testes de integração.
- Os testes de componente do front-end usam a API simulada; o contrato real é coberto pelo E2E.
- Não há teste de carga nem de desempenho; fora do escopo do v1.
