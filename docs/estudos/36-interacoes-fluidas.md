# Estudo — Ticket 36: interações mais fluidas

Notas do plano aprovado em 2026-11-02. O objetivo foi tirar a sensação de interface "dura" sem deixá-la lenta e sem adicionar dependências.

## 1. Movimento também tem um lugar central

Como as cores, os tempos ficam em variáveis no `src/index.css`:

```css
--duracao-rapida: 150ms;  /* resposta a um gesto: botão, foco, hover */
--duracao-media: 220ms;   /* algo que entra na tela */
--curva-saida: cubic-bezier(0.22, 0.61, 0.36, 1);
```

Para ajustar a sensação do sistema inteiro, basta mudar esses valores. A curva começa rápida e desacelera, por isso a interface responde na hora e chega suavemente.

## 2. Por que `transform` e `opacity`

Mudar `height` ou `margin` obriga o navegador a recalcular a posição de tudo ao redor. `transform` e `opacity` costumam ser animados sem esse recálculo, o que evita travadas.

O projeto também troca `transition-all` por uma lista explícita. Assim, nenhuma propriedade é animada por acidente.

## 3. Skeleton com atraso

O skeleton mostra o formato do conteúdo durante o carregamento, mas fica invisível nos primeiros 150 ms. Com resposta rápida, ele nunca aparece. Sem esse atraso, cada carregamento rápido viraria uma piscada, que é pior do que não mostrar nada.

Mesmo invisível, ele ocupa espaço, então o conteúdo abaixo não pula. Medições num Chromium real: com resposta lenta, a opacidade passou de 0 aos 50 ms para 1 aos 700 ms; com resposta rápida, os blocos nunca ficaram visíveis.

## 4. Atualização otimista: por que não aplicamos

Atualização otimista mostra o resultado antes da resposta do servidor. No SolveIT, status e atribuição dependem de permissão, versão esperada e auditoria, e o servidor pode recusar. Mostrar "resolvido" e desfazer depois seria enganar o usuário, por isso essas ações mostram um indicador de progresso.

**Para levar:** a técnica é boa quando a recusa é rara e barata de desfazer. Aqui, a verdade é do servidor.

## 5. A regressão que os skeletons criaram

Essa é a lição principal deste ticket. Antes, o texto "Carregando…" encolhia a página, e o navegador voltava para o topo **por acidente**. Com o skeleton mantendo a altura, a página ficou parada, e o aviso de sucesso aparecia no topo, fora da tela de quem salvou algo no fim de uma lista no celular.

O problema só apareceu porque o E2E falhou por outro motivo, ao tentar usar o menu. O teste do aviso passava, porque `toBeVisible` não exige que o elemento esteja na área visível.

A correção atacou a causa:

- `AvisoPagina` rola até o aviso ficar visível.
- Tela nova começa no topo.
- O E2E agora exige `toBeInViewport` para o aviso.

No CI, esse teste novo falhou: a página ficou parada no fim da lista. Em 14 rodadas locais, com e sem limite de CPU, a falha não se repetiu. Por isso, a rolagem suave foi trocada pela instantânea, por suspeita de que fosse interrompida no caminho. O CI falhou de novo, e a suspeita estava errada.

A causa real apareceu ao registrar cada rolagem, com a mesma lista do CI e sem depender do instante da verificação (celular, 740 px de altura):

| Momento | Rolagem (`y`) | Altura da página |
|---|---|---|
| Aviso aparece e a lista vira skeleton | 13 | 753 px |
| Lista volta | 351 | 1091 px |

A página cresceu 338 px e a rolagem andou os mesmos 338 px. Esse é o **scroll anchoring**: quando o conteúdo muda, o navegador move a rolagem para manter no lugar o que está na tela. Normalmente isso ajuda, mas aqui empurrou o aviso para fora.

O teste passava localmente porque o aviso fica visível por cerca de 17 ms antes do salto, e às vezes a verificação via esse instante. O defeito, porém, era real para quem usava a tela.

A correção tem duas partes:

- `html { overflow-anchor: none }` no `index.css`. Com a ancoragem desligada, o aviso continuou visível em todas as 20 tentativas; com ela ligada, ficou fora da tela em todas as 20.
- O E2E agora espera a lista recarregar antes de verificar o aviso. Assim, ele falha sempre que o defeito existe, em vez de depender do momento.

**O custo:** se um conteúdo acima da área visível crescer, a tela vai se mover. Hoje as telas trocam a lista inteira ao recarregar, então a ancoragem atrapalhava mais do que ajudava.

**Para levar:** um comportamento bom que existe por acidente some quando você muda algo sem relação aparente. Ao ver um teste falhar, pergunte o que ele revela antes de ajustá-lo. E se uma falha não se repete, procure a diferença entre os dois ambientes: um teste que verifica um estado passageiro pode passar sem que o estado final esteja certo.

## 6. Acessibilidade do movimento

`prefers-reduced-motion` é uma preferência do sistema operacional, usada por quem passa mal ou se distrai com movimento. Uma regra global reduz durações e atrasos a quase zero. O texto do botão, como "Enviando…", continua informando o andamento, e o skeleton continua aparecendo, sem pulsar.

## 7. Limitações conhecidas

- **Voltar não devolve a posição exata:** na medição, a página estava rolada em 757 px e voltou para 135 px. Quando o navegador tenta restaurar, a lista ainda está carregando e a página é mais baixa. Resolver exigiria guardar os dados da lista entre as telas.
- **Sem animação de saída:** o React remove o elemento na hora. Animar a saída exigiria mais código ou uma biblioteca.
- **Sensação não é testável:** os testes provam que as animações existem e que nada quebrou. Se ficaram agradáveis, só usando para saber.

## 8. Uma falha de processo

Num comando, o typecheck apontou um erro, mas as etapas seguintes rodaram mesmo assim, e o commit saiu com o problema. O build da imagem falhou em seguida, e o erro foi corrigido antes de publicar.

**Para levar:** encadeie verificações de modo que a primeira falha interrompa o resto (`set -e` ou `&&`).

## Para aprofundar (opcional)

- `will-change` e camadas de composição: como o navegador acelera animações.
- `scrollIntoView` e `scroll-margin`.
- Bibliotecas de animação com saída, como Motion, e quando compensam.
