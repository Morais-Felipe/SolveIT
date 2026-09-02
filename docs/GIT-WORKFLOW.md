# Fluxo Git por ticket

Este documento define como o SolveIT usa Git e GitHub a partir do Ticket 1. O objetivo é manter cada incremento recuperável, revisável e disponível para continuar em outra máquina sem colocar trabalho incompleto na `main`.

## Regra central

Cada ticket usa uma branch própria. Podem existir vários commits atômicos dentro do ticket, mas o push é obrigatório ao concluir o ticket e a integração na `main` só acontece depois de testes, resumo e aprovação do usuário.

## Início do ticket

1. Confirmar que não há alterações locais esquecidas.
2. Atualizar a `main` somente por avanço direto: `git pull --ff-only origin main`.
3. Criar a branch `ticket/NN-descricao-curta`, por exemplo `ticket/01-setup-spring-boot`.
4. Atualizar a seção **Tarefa Atual** do documento de origem.
5. Apresentar o plano do ticket e aguardar aprovação antes de escrever código.

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
5. Aguardar aprovação do usuário para concluir.
6. Fazer `git push -u origin ticket/NN-descricao-curta`.
7. Abrir ou atualizar o pull request para `main`.
8. Integrar na `main` somente após aprovação; depois confirmar que `origin/main` contém o resultado.

## Continuidade entre computadores

Se for necessário trocar de máquina antes de concluir um ticket:

1. Registrar o ponto de retomada com `$gsd-pause-work` quando houver trabalho de fase em andamento.
2. Criar um commit de checkpoint claramente identificado, sem misturá-lo à `main`.
3. Fazer push da branch do ticket.
4. Na outra máquina, atualizar o repositório e trocar para a mesma branch.

Um checkpoint permite continuidade, mas não significa que o ticket está aprovado ou pronto para merge.

## Exceções

- Correções urgentes ou tarefas administrativas ainda usam branch própria, salvo autorização explícita para atuar diretamente na `main`.
- Falha de testes, dúvida de escopo ou conflito de merge interrompe o fluxo; não avançar nem publicar uma integração parcial sem consultar o usuário.
