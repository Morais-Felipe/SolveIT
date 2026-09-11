# Protocolo de execução da Fase 3

## Estado e autorizações

Discussão e planejamento autorizados; implementação ainda depende da apresentação e aprovação de cada plano. A branch `ticket/10-discussao-fase-3` contém somente documentação e deve ser publicada/integrada com as aprovações remotas correspondentes antes do primeiro ticket de implementação.

## Sequência obrigatória

1. Apresentar objetivo, arquivos, comportamento, conceitos e validação do plano individual.
2. Obter aprovação de implementação; confirmar main atualizada e criar uma nova `ticket/NN-descricao-curta` a partir dela. Verificar numeração livre no momento, sem confundir `03-NN` com número de ticket.
3. Executar uma tarefa por vez com explicação, commits atômicos e testes do incremento completo.
4. Produzir SUMMARY do plano com a branch real e evidências. Pedir aprovações separadas de push, PR e merge, salvo autorização conjunta explícita.
5. Integrar o plano anterior antes de implementar o próximo, mesmo que uma ferramenta sugira ondas paralelas ou autoexecução.
6. Registrar plano, branch e PR no SUMMARY. Ao trocar de máquina no meio do ticket, retomar a mesma branch após checkpoint/push autorizado.

## Limites de validação

Verificação dos PLAN.md comprova coerência do planejamento, não funcionamento do código futuro. Concorrência e migrações exigem evidência real em MySQL durante a implementação. Não declarar testes novos aprovados antes de existirem e serem executados.

## Decisões técnicas a explicar nos planos

Coordenação persistente das escritas e seu custo de contenção; versões esperadas para rejeitar dados antigos; comparação completa da prévia administrativa; migração do histórico inicial; provisionamento controlado do primeiro ADMIN. São propostas técnicas a apresentar, não operações já executadas.
