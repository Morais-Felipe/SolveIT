# Phase 3: Core de Chamados - Discussion Log

> Registro para consulta humana. Pesquisa e planejamento devem consumir 03-CONTEXT.md.

**Date:** 2026-09-10 a 2026-09-11
**Consolidação autorizada:** “podemos consolidar”.

| Área / pergunta | Proposta ou alternativa apresentada | Decisão do usuário |
|---|---|---|
| Visibilidade | Fila ABERTO sem atendente e próprios atribuídos, inclusive encerrados | “Perfeito” |
| Leitura antes de assumir | Detalhe completo, comentários e histórico | “Sim” |
| Comentar antes de assumir | Permitir sem atribuir | “Sim” |
| Acesso após outro assumir | Perder acesso, preservar comentário | Confirmou; comentou que preferência é questão social/moral, sem impedir outro de assumir |
| Desativação com trabalho em andamento | Inicialmente sugerido bloquear desativação | Usuário propôs devolver os chamados à fila |
| Exceção administrativa | Devolver a ABERTO sem responsável, confirmação da quantidade, histórico e transação única; volta a permitir edição | “Sim” |
| Último ADMIN | Bloquear desativação e perda de perfil do último ativo | “Sim” |
| Mudança de perfil do atendente | Mesma devolução confirmada com motivo próprio | “Cerot” (confirmação) |
| Categoria inativa | Bloquear novos chamados, preservar existentes | “perfeito” |
| Vínculos antigos | Preservar autoria do atendimento, acesso pelo perfil atual | “ok” |
| Conversa encerrada | Somente leitura após FECHADO/CANCELADO versus permitir novos comentários | “encerramento da conversa, mas disponivel leitura do historico de msgs” |
| Abertura no histórico | Autor, data e ABERTO inicial | “ok” |
| Observação | Opcional manual; obrigatória automática nos retornos | “ok” |
| Ordenação | Antigo para recente, ID como desempate | “ok” |
| Edição desatualizada | Rejeitar e pedir atualização | “certp” (confirmação) |
| Conflito no lote administrativo | Desfazer tentativa inteira, preservar transações alheias, pedir nova revisão | “ok” |
| Comentário após encerramento concorrente | Recusar conforme estado atual | “ok” |
| Histórico de rejeições | Não gerar mudança de status fictícia | “ok” |

## Discrição técnica
Implementação e pesquisa dos mecanismos permanecem para o planejamento; nenhuma biblioteca ou estratégia completa de locking foi selecionada pelo usuário nesta discussão.

## Ajustes aprovados
Exceção administrativa EM_ANDAMENTO → ABERTO; restrição de COLL-01 por status; registro inicial de abertura e proteções administrativas detalhadas em CONTEXT.md e REQUIREMENTS.md.
