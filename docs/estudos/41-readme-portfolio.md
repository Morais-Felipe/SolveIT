# Estudo — Ticket 41: README para portfólio

Notas do plano aprovado em 2026-11-02. O objetivo foi trocar um README escrito para quem acompanha o desenvolvimento por um escrito para quem avalia o projeto.

## 1. Quem lê muda o que importa

| Quem | O que procura |
| --- | --- |
| Recrutador | O que é, se funciona, qual a stack |
| Pessoa técnica | Decisões difíceis e como foram resolvidas |
| Quem vai modificar | Como rodar, rotas e regras detalhadas |

O README atende aos dois primeiros, e o terceiro fica a um clique, em `docs/DOCUMENTACAO-TECNICA.md`. Por isso, a ordem é: mostrar, deixar testar, explicar.

## 2. Mover em vez de reescrever

As seções técnicas foram movidas por script, copiando o texto como estava. Reescrever à mão arriscaria perder uma regra ou mudar um detalhe sem perceber. Os títulos e os links relativos foram ajustados, porque o arquivo mudou de pasta.

## 3. Afirmações precisam de prova

Cada destaque técnico aponta para o código ou para o mapa de evidências. "Autorização real" é só uma frase; a matriz de 23 rotas é uma prova. Pelo mesmo motivo, os números de testes foram conferidos na execução real antes de entrarem no texto.

## 4. Links também quebram

Foi usado um script que confere arquivos, imagens e âncoras (`#secao`). Um detalhe: o GitHub gera a âncora a partir do título, mantendo acentos e tirando pontuação. "QUAL-03 — Matriz de autorização" vira `qual-03--matriz-de-autorização`, com dois hifens no lugar do travessão.

## 5. Licença

Sem licença, a lei de direitos autorais vale por padrão: as pessoas podem ver o código num repositório público, mas não têm permissão formal para reutilizá-lo. Uma licença como a MIT dá essa permissão, desde que o aviso de autoria seja mantido, e isenta o autor de garantias.

## Para aprofundar (opcional)

- Diagramas Mermaid no GitHub.
- Alertas do GitHub (`> [!WARNING]`).
- Comparação de licenças em [choosealicense.com](https://choosealicense.com/).
