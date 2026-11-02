# Estudo — Ticket 34: novo visual do dashboard

Notas do plano aprovado em 2026-11-02. A inspiração foi o template "App Dashboard Layout" do 21st.dev. O gráfico por semana ficou para o ticket 35, porque exige mudanças no back-end.

## 1. Um painel só vale pelos dados reais

O template mostra "projetos com 75% de progresso", "+2 desde o mês passado" e atividade da equipe. O SolveIT não tem esses dados. Inventá-los deixaria a tela parecida com o print, mas um painel com números falsos é pior do que nenhum: leva a decisões erradas.

Por isso, cada elemento do template foi comparado com o que a API entrega:

| No template | No SolveIT |
|---|---|
| Comparação com o período anterior | Descrição do que o número significa |
| Projetos com percentual | Barras por prioridade, categoria e carga |
| Atividade da equipe | Chamados recentes |
| Busca, sino e "Your team" | Removidos, porque não existem ou só o ADMIN tem acesso |

## 2. Autorização continua no servidor

"Chamados recentes" usa a mesma `listarChamados` da tela de chamados, e `GET /api/chamados` aplica a regra de visibilidade de cada perfil. O front-end não decide nada sobre permissão: só exibe o que recebeu. É a regra do projeto de que restrições de interface não bastam.

Reaproveitar a consulta também evita um segundo caminho que poderia divergir do primeiro.

## 3. Falhas isoladas

Os indicadores e os chamados recentes vêm de duas requisições diferentes. Se os recentes falharem, os indicadores continuam na tela e só esse bloco mostra "Tentar novamente". Um teste em `dashboard.test.tsx` protege esse comportamento.

## 4. Barra não substitui número

As barras comparam as linhas de uma mesma tabela: o maior valor ocupa a largura toda. Elas não são um percentual, e por isso o número continua ao lado.

As barras levam `aria-hidden` e os dados continuam numa `<table>`. Um leitor de tela ouve "Média, 2", sem depender de enxergar a barra.

## 5. Um só bloco, duas posições

O nome do usuário fica no topo no celular e no rodapé do menu no desktop. Em vez de duplicar o elemento e esconder um deles, o HTML tem um único bloco e o CSS o reposiciona: `grid` no celular e `flex` com `mt-auto` no desktop.

Duplicar faria o leitor de tela e os testes encontrarem dois elementos iguais. Em `jsdom`, o CSS do Tailwind não é aplicado, então os dois apareceriam para os testes.

## 6. O que as ferramentas pegaram

| Ferramenta | O que pegou |
|---|---|
| `tsc` | `listarChamados({})` sem o campo `page`. O Vitest passou, porque roda o código sem conferir tipos |
| Revisão visual | Cabeçalho "Em andamento" quebrando e selos maiores que o resto da linha |
| Script de captura | Falhou ao assumir de novo um chamado, e o ambiente ainda rodava o build antigo. A imagem antiga não valia como prova do ajuste |

**Para levar:** cada verificação enxerga coisas diferentes. E, antes de usar um resultado como prova, confirme que ele veio da versão que você quer testar.

## Para aprofundar (opcional)

- `position: sticky`: como o menu acompanha a rolagem sem sair do fluxo da página.
- `min-w-0` em itens flex: por que eles às vezes se recusam a encolher.
- Semântica de tabelas (`caption`, `scope`) e como leitores de tela as anunciam.
