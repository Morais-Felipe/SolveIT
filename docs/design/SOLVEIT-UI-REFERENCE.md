# Referência visual do SolveIT

Esta referência registra a direção visual fornecida para o front-end do SolveIT. Ela orienta o design da Fase 6, mas não altera requisitos, regras de negócio ou o fluxo de status definido no projeto.

![Composição com visão geral, chamados, detalhe e relatórios](./solveit-ui-reference.png)

## Direção visual aprovada

- Aplicação administrativa em tema escuro, com alto contraste e acabamento sóbrio.
- Cor laranja como destaque principal para navegação ativa, ações e séries de dados.
- Navegação lateral persistente, barra superior com pesquisa e identidade do usuário.
- Conteúdo organizado em cards, tabelas densas, indicadores, gráficos e painéis laterais.
- Estados e prioridades representados por chips compactos e cores semânticas.
- Hierarquia tipográfica clara: título da página, contexto secundário, métricas e detalhes.
- Bordas discretas, cantos moderadamente arredondados e pouco ruído visual.

## Telas de referência

1. **Visão geral** — cards de métricas, evolução temporal, distribuição por prioridade e chamados recentes.
2. **Lista de chamados** — filtros, pesquisa, ordenação, paginação e tabela operacional.
3. **Detalhe do chamado** — cabeçalho de estado, conversa cronológica, formulário de resposta e painel de metadados.
4. **Relatórios** — indicadores e gráficos agregados para leitura operacional.

## Tradução para o escopo do SolveIT

- A aparência e a composição são referências; textos, dados e nomenclaturas serão adaptados às entidades e perfis do SolveIT.
- Somente os status `ABERTO`, `EM_ANDAMENTO`, `RESOLVIDO`, `FECHADO` e `CANCELADO` podem aparecer na implementação v1.
- As prioridades válidas são `BAIXA`, `MEDIA`, `ALTA` e `CRITICA`.
- A interface deve respeitar SOLICITANTE, ATENDENTE e ADMIN, mantendo a API como autoridade de autorização.
- A tela de relatórios será limitada aos quatro endpoints de dashboard definidos para o v1.
- O layout responsivo será especificado posteriormente; a imagem fornecida representa apenas desktop.

## Elementos que não ampliam o escopo

Os seguintes itens aparecem na composição, mas continuam fora do v1 ou dependem de decisão futura:

- Base de conhecimento.
- Gestão de SLA além dos indicadores já definidos.
- Anexos e notas internas.
- CSAT/satisfação do cliente.
- Cadastro separado de clientes.
- Novos estados como “Aguardando cliente” ou “Novo”.
- Exportação de relatórios.

## Proveniência

- **Arquivo original:** `C:\Users\felipe.morais\.codex\generated_images\01a06216-3488-7990-b4a6-e9b8532d5c48\exec-2b68f201-2f2f-40cc-9822-57088261a621.png`
- **Cópia versionada:** `docs/design/solveit-ui-reference.png`
- **Dimensões:** 1536 × 1024 px
- **SHA-256:** `00518B62371A5BACB50CB7605EE7CB5F2051BD764674ABF3152FA27B44FE48DF`
- **Registrada em:** 2026-09-02
