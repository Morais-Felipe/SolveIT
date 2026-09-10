# Fluxo Git por ticket

Este documento define como o SolveIT usa Git e GitHub a partir do Ticket 1. O objetivo é manter cada incremento recuperável, revisável e disponível para continuar em outra máquina sem colocar trabalho incompleto na `main`.

## Regra central

Cada ticket usa uma branch própria. Podem existir vários commits atômicos dentro do ticket. Execução local, push, criação ou atualização do pull request e merge são ações diferentes: a autorização de uma não autoriza a seguinte. Nenhuma ação remota acontece antes de testes, resumo e sua aprovação explícita correspondente.

## Uma branch por plano aprovado — decisão de 2026-09-10

A partir da Fase 3, cada plano de implementação GSD aprovado corresponde a um ticket, uma branch própria e um PR. Não reunir todos os planos de uma fase na mesma branch.

- A fase reúne vários planos; cada plano pode conter várias tarefas e commits atômicos.
- O número do ticket é sequencial no projeto e não reinicia a cada fase. O identificador GSD `03-01` significa Fase 3, Plano 1; não determina o número `NN` da branch.
- Registrar no plano/resumo a correspondência entre plano GSD, branch `ticket/NN-descricao-curta` e PR.
- Apresentar e obter aprovação do plano antes de implementar. Concluir testes, resumo, push, PR e merge do ticket, com as aprovações correspondentes, antes de iniciar a implementação do próximo plano.
- Criar a próxima branch a partir da `main` atualizada após o merge anterior. Não acumular planos seguintes na branch anterior.
- Discussão/pesquisa/planejamento da fase e tarefas administrativas têm ticket documental próprio quando alteram arquivos; não contam como execução de um plano ainda não aprovado.
- Na troca de máquina durante um ticket, retomar a mesma branch e o checkpoint. Push intermediário autorizado permite continuidade, sem representar conclusão ou merge.

Histórico: os tickets 01–05 implementaram os cinco planos da Fase 1; 06–07 trataram verificação e ambiente. O ticket 08 concentrou os oito planos da Fase 2 e foi integrado pelo PR #10. Essa concentração é histórica e não deve ser repetida nas próximas fases.

## Início do ticket

1. Confirmar que não há alterações locais esquecidas.
2. Confirmar `git config --local user.name` e `git config --local user.email`; o e-mail deve estar verificado na conta GitHub se os commits precisarem aparecer vinculados ao perfil.
3. Atualizar a `main` somente por avanço direto: `git pull --ff-only origin main`.
4. Criar a branch `ticket/NN-descricao-curta`, por exemplo `ticket/01-setup-spring-boot`.
5. Atualizar a seção **Tarefa Atual** do documento de origem.
6. Apresentar o plano do ticket e aguardar aprovação antes de escrever código.

## Durante o desenvolvimento

- Trabalhar em apenas um ticket por vez.
- Criar commits pequenos e atômicos por tarefa verificável.
- Usar Conventional Commits: `feat:`, `fix:`, `test:`, `docs:`, `refactor:` ou `chore:`.
- Separar mudanças sem relação em commits diferentes.
- Nunca versionar senhas, tokens, credenciais, `.env` ou configurações pessoais.
- Não usar `git push --force` nem reescrever commits já compartilhados sem autorização explícita.

## Conclusão do ticket

1. Executar testes e verificações proporcionais ao ticket.
2. Confirmar que o diff contém somente arquivos do escopo aprovado.
3. Atualizar documentação e estado do GSD quando aplicável.
4. Apresentar um resumo do que foi feito, como foi verificado e eventuais pendências.
5. Aguardar aprovação explícita apenas para o push.
6. Fazer `git push -u origin ticket/NN-descricao-curta` e apresentar a prova da branch remota.
7. Aguardar uma nova aprovação explícita apenas para abrir ou atualizar o pull request.
8. Abrir ou atualizar o pull request para `main` e apresentar seu estado.
9. Aguardar uma terceira aprovação explícita apenas para o merge.
10. Integrar na `main`; depois confirmar que `origin/main` contém o resultado.

Uma resposta que autoriza execução local não autoriza push. A autorização de push não autoriza PR, e a autorização de PR não autoriza merge. Uma autorização conjunta só vale quando o usuário nomeia explicitamente cada uma das ações na mesma mensagem.

## Continuidade entre computadores

Se for necessário trocar de máquina antes de concluir um ticket:

1. Registrar o ponto de retomada com `$gsd-pause-work` quando houver trabalho de fase em andamento.
2. Criar um commit de checkpoint claramente identificado, sem misturá-lo à `main`.
3. Após autorização específica para o checkpoint, fazer push da branch do ticket.
4. Na outra máquina, atualizar o repositório e trocar para a mesma branch.

Um checkpoint permite continuidade, mas não significa que o ticket está aprovado ou pronto para merge.

## Exceções

- Correções urgentes ou tarefas administrativas ainda usam branch própria, salvo autorização explícita para atuar diretamente na `main`.
- Falha de testes, dúvida de escopo ou conflito de merge interrompe o fluxo; não avançar nem publicar uma integração parcial sem consultar o usuário.
