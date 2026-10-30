# Fluxo Git por ticket

Este documento define como o SolveIT usa Git e GitHub a partir do Ticket 1. O objetivo é manter cada incremento recuperável, revisável e disponível para continuar em outra máquina sem colocar trabalho incompleto na `main`.

## Regra central

Cada ticket usa uma branch própria. Podem existir vários commits atômicos dentro do ticket. Execução local e publicação (push, pull request e merge) são decisões diferentes: aprovar a execução não autoriza publicar. Nenhuma ação remota acontece antes de testes, resumo e da aprovação explícita de publicação.

## Uma branch por plano aprovado — decisão de 2026-09-10

A partir da Fase 3, cada plano de implementação aprovado corresponde a um ticket, uma branch própria e um PR. Não reunir todos os planos de uma fase na mesma branch.

- A fase reúne vários planos; cada plano pode conter várias tarefas e commits atômicos.
- O número do ticket é sequencial no projeto e não reinicia a cada fase. O identificador `03-01` significa Fase 3, Plano 1; não determina o número `NN` da branch.
- Registrar no resumo do plano (`NN-PP-SUMMARY.md`) a correspondência entre plano, branch `ticket/NN-descricao-curta` e PR.
- Apresentar e obter aprovação do plano antes de implementar. Concluir testes, resumo e publicação do ticket antes de iniciar a implementação do próximo plano.
- Criar a próxima branch a partir da `main` atualizada após o merge anterior. Não acumular planos seguintes na branch anterior.
- Discussão, planejamento e tarefas administrativas têm ticket documental próprio quando alteram arquivos; não contam como execução de um plano ainda não aprovado.
- Na troca de máquina durante um ticket, retomar a mesma branch e o checkpoint. Push intermediário autorizado permite continuidade, sem representar conclusão ou merge.

Histórico: os tickets 01–05 implementaram os cinco planos da Fase 1; 06–07 trataram verificação e ambiente. O ticket 08 concentrou os oito planos da Fase 2 e foi integrado pelo PR #10. Essa concentração é histórica e não deve ser repetida nas próximas fases.

## Início do ticket

1. Confirmar que não há alterações locais esquecidas.
2. Confirmar `git config --local user.name` e `git config --local user.email`; o e-mail deve estar verificado na conta GitHub se os commits precisarem aparecer vinculados ao perfil.
3. Atualizar a `main` somente por avanço direto: `git pull --ff-only origin main`.
4. Criar a branch `ticket/NN-descricao-curta`, por exemplo `ticket/01-setup-spring-boot`.
5. Apresentar o plano do ticket e aguardar aprovação antes de escrever código.

## Durante o desenvolvimento

- Trabalhar em apenas um ticket por vez.
- Criar commits pequenos e atômicos por tarefa verificável.
- Usar Conventional Commits: `feat:`, `fix:`, `test:`, `docs:`, `refactor:` ou `chore:`.
- Separar mudanças sem relação em commits diferentes.
- Nunca versionar senhas, tokens, credenciais, `.env` ou configurações pessoais.
- Não usar `git push --force` nem reescrever commits já compartilhados sem autorização explícita.
- Testes proporcionais ao risco (revisão de escopo de 2026-10-30): caminho feliz, permissões e erros; prova de concorrência somente quando a funcionalidade introduz uma disputa nova.

## Conclusão do ticket

1. Executar testes e verificações proporcionais ao ticket, incluindo a suíte completa quando houver código.
2. Confirmar que o diff contém somente arquivos do escopo aprovado.
3. Atualizar o resumo do plano e `.planning/STATE.md`, `ROADMAP.md` e `REQUIREMENTS.md` quando aplicável.
4. Apresentar um resumo do que foi feito, como foi verificado e eventuais pendências.
5. Aguardar **uma** aprovação explícita de publicação do ticket.
6. Fazer `git push -u origin ticket/NN-descricao-curta`, abrir o pull request para `main` e integrá-lo; depois confirmar que `origin/main` contém o resultado e atualizar a `main` local.

### Autorização única de publicação — decisão de 2026-10-30

Uma única aprovação final cobre push, pull request e merge **do ticket em questão**. Ela não vale para tickets seguintes e não substitui a aprovação do plano antes da implementação. Se qualquer etapa falhar (teste, conflito, PR não integrável), o fluxo para antes da etapa seguinte e o responsável é consultado.

Até 2026-10-29 eram exigidas três aprovações separadas (push, PR e merge); os tickets anteriores seguiram essa regra.

## Continuidade entre computadores

Se for necessário trocar de máquina antes de concluir um ticket:

1. Registrar o ponto de retomada em `.planning/STATE.md`.
2. Criar um commit de checkpoint claramente identificado, sem misturá-lo à `main`.
3. Após autorização específica para o checkpoint, fazer push da branch do ticket.
4. Na outra máquina, atualizar o repositório e trocar para a mesma branch.

Um checkpoint permite continuidade, mas não significa que o ticket está aprovado ou pronto para merge.

## Exceções

- Correções urgentes ou tarefas administrativas ainda usam branch própria, salvo autorização explícita para atuar diretamente na `main`.
- Falha de testes, dúvida de escopo ou conflito de merge interrompe o fluxo; não avançar nem publicar uma integração parcial sem consultar o usuário.
