import { CircleCheck, Headset, Hourglass, TriangleAlert, type LucideIcon } from 'lucide-react'
import { lazy, Suspense, useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { SeloPrioridade, SeloStatus } from '@/components/Selos'
import { Bloco, Esqueleto, EsqueletoDashboard, EsqueletoLista } from '@/components/Esqueleto'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { buscarResumo } from '@/lib/admin'
import { toApiError } from '@/lib/api'
import { formatarData, listarChamados, PRIORIDADES } from '@/lib/chamados'
import { formatarPeriodo } from '@/lib/semanas'
import type { Chamado, Prioridade, ResumoDashboard } from '@/lib/types'
import { cn } from '@/lib/utils'

// Se o download falhar (ex.: logo após um deploy), só o gráfico avisa; o resto da tela continua.
const GraficoSemanal = lazy(() =>
  import('@/components/GraficoSemanal').catch(() => ({ default: GraficoIndisponivel })),
)

const TOTAL_RECENTES = 5

const PAINEL = 'rounded-xl bg-card p-4 ring-1 ring-foreground/10'

const COR_BARRA_PRIORIDADE: Record<Prioridade, string> = {
  BAIXA: 'bg-muted-foreground',
  MEDIA: 'bg-foreground',
  ALTA: 'bg-primary',
  CRITICA: 'bg-destructive',
}

type Resultado =
  | { tipo: 'carregando' }
  | { tipo: 'erro'; mensagem: string }
  | { tipo: 'pronto'; resumo: ResumoDashboard }

/**
 * Painel do ADMIN e do atendente: cards, gráfico por semana, tabelas com barras e chamados recentes. Os números
 * vêm prontos da API, já no escopo do perfil; cada card leva à lista com o mesmo filtro,
 * para que a pessoa confira de onde o número saiu.
 */
export function DashboardPage() {
  const { sessao } = useAuth()
  const [busca, setBusca] = useState<{ tentativa: number; resultado: Resultado } | null>(null)
  const [tentativa, setTentativa] = useState(0)
  const estado: Resultado = busca?.tentativa === tentativa ? busca.resultado : { tipo: 'carregando' }

  useEffect(() => {
    const controle = new AbortController()
    buscarResumo(controle.signal)
      .then((resumo) => setBusca({ tentativa, resultado: { tipo: 'pronto', resumo } }))
      .catch((falha: unknown) => {
        if (!controle.signal.aborted) {
          setBusca({ tentativa, resultado: { tipo: 'erro', mensagem: toApiError(falha).message } })
        }
      })
    return () => controle.abort()
  }, [tentativa])

  const escopo =
    sessao.status === 'autenticado' && sessao.usuario.perfil === 'ATENDENTE'
      ? 'Resumo da fila e dos seus chamados.'
      : 'Resumo de toda a operação.'

  return (
    <section className="grid gap-6">
      <header className="grid gap-1">
        <h1 className="text-2xl font-semibold">Dashboard</h1>
        <p className="text-sm text-muted-foreground">{escopo}</p>
      </header>

      {estado.tipo === 'carregando' && (
        <EsqueletoDashboard rotulo="Carregando indicadores…" />
      )}

      {estado.tipo === 'erro' && (
        <Alert variant="destructive">
          <AlertDescription className="flex flex-wrap items-center justify-between gap-3">
            <span>{estado.mensagem}</span>
            <Button variant="outline" size="sm" onClick={() => setTentativa((n) => n + 1)}>
              Tentar novamente
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {estado.tipo === 'pronto' && <Indicadores resumo={estado.resumo} />}
    </section>
  )
}

type CardIndicador = {
  rotulo: string
  valor: number
  descricao: string
  para: string
  icone: LucideIcon
  alerta?: boolean
}

function Indicadores({ resumo }: { resumo: ResumoDashboard }) {
  const { totais } = resumo
  const cards: CardIndicador[] = [
    {
      rotulo: 'Abertos',
      valor: totais.aberto,
      descricao: 'Aguardando um atendente',
      para: '/chamados?status=ABERTO',
      icone: Hourglass,
    },
    {
      rotulo: 'Em andamento',
      valor: totais.emAndamento,
      descricao: 'Com um atendente responsável',
      para: '/chamados?status=EM_ANDAMENTO',
      icone: Headset,
    },
    {
      rotulo: 'Resolvidos',
      valor: totais.resolvido,
      descricao: 'Aguardando o solicitante fechar',
      para: '/chamados?status=RESOLVIDO',
      icone: CircleCheck,
    },
    {
      rotulo: 'Críticos abertos',
      valor: totais.criticaAberta,
      descricao: 'Prioridade crítica sem atendente',
      para: '/chamados?status=ABERTO&prioridade=CRITICA',
      icone: TriangleAlert,
      alerta: totais.criticaAberta > 0,
    },
  ]

  const maiorPrioridade = Math.max(...PRIORIDADES.map((p) => resumo.abertosPorPrioridade[p] ?? 0))
  const maiorCategoria = Math.max(0, ...resumo.porCategoria.map((c) => c.quantidade))
  const maiorCarga = Math.max(0, ...resumo.cargaPorAtendente.map((a) => a.emAndamento))
  const temCarga = resumo.cargaPorAtendente.length > 0

  return (
    <>
      <ul className="entrada-suave grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {cards.map((c) => (
          <li key={c.rotulo}>
            <Link
              to={c.para}
              className={cn(
                PAINEL,
                'grid h-full gap-1 transition-[transform,background-color,box-shadow] duration-(--duracao-rapida) ease-(--curva-saida) hover:-translate-y-0.5 hover:bg-muted/50 hover:ring-primary active:translate-y-0',
                c.alerta && 'ring-destructive/50',
              )}
            >
              <span className="flex items-center justify-between gap-2 text-sm text-muted-foreground">
                {c.rotulo}
                <c.icone aria-hidden="true" className={cn('size-4 shrink-0', c.alerta && 'text-destructive')} />
              </span>
              <span className={cn('text-3xl font-semibold tabular-nums', c.alerta && 'text-destructive')}>
                {c.valor}
              </span>
              <span className="text-xs text-muted-foreground">{c.descricao}</span>
            </Link>
          </li>
        ))}
      </ul>

      <PainelSemanas semanas={resumo.semanas} />

      <div className="entrada-suave grid gap-6 lg:grid-cols-2">
        <Tabela titulo="Abertos por prioridade" colunas={['Prioridade', 'Abertos']}>
          {PRIORIDADES.map((p) => {
            const quantidade = resumo.abertosPorPrioridade[p] ?? 0
            return (
              <Linha
                key={p}
                rotulo={<SeloPrioridade prioridade={p} />}
                valor={quantidade}
                maximo={maiorPrioridade}
                corBarra={COR_BARRA_PRIORIDADE[p]}
              />
            )
          })}
        </Tabela>

        <Tabela titulo="Chamados por categoria" colunas={['Categoria', 'Chamados']}>
          {resumo.porCategoria.length === 0 ? (
            <tr className="border-t border-border">
              <td colSpan={2} className="py-2 text-muted-foreground">
                Nenhum chamado registrado.
              </td>
            </tr>
          ) : (
            resumo.porCategoria.map((c) => (
              <Linha
                key={c.categoriaId}
                rotulo={
                  <Link to={`/chamados?categoriaId=${c.categoriaId}`} className="hover:text-primary hover:underline">
                    {c.nome}
                  </Link>
                }
                valor={c.quantidade}
                maximo={maiorCategoria}
              />
            ))
          )}
        </Tabela>

        {temCarga && (
          <Tabela titulo="Carga por atendente" colunas={['Atendente', 'Em andamento']}>
            {resumo.cargaPorAtendente.map((a) => (
              <Linha key={a.atendenteId} rotulo={a.nome} valor={a.emAndamento} maximo={maiorCarga} />
            ))}
          </Tabela>
        )}

        <ChamadosRecentes className={cn(!temCarga && 'lg:col-span-2')} />
      </div>
    </>
  )
}

function GraficoIndisponivel() {
  return <p className="text-sm text-muted-foreground">Não foi possível carregar o gráfico. Recarregue a página.</p>
}

function PainelSemanas({ semanas }: { semanas: ResumoDashboard['semanas'] }) {
  const semDados = semanas.every((s) => s.novos === 0 && s.resolvidos === 0)

  return (
    <div className={cn(PAINEL, 'entrada-suave grid min-w-0 gap-3')}>
      <div className="flex flex-wrap items-start justify-between gap-x-6 gap-y-2">
        <div className="grid gap-1">
          <h2 className="text-sm font-medium">Chamados por semana</h2>
          <p className="text-xs text-muted-foreground">Novos e resolvidos em janelas de 7 dias, terminando agora.</p>
        </div>
        {!semDados && (
          <ul aria-hidden="true" className="flex gap-4 text-xs text-muted-foreground">
            <li className="flex items-center gap-1.5">
              <span className="w-4 border-t-2 border-dashed border-foreground" />
              Novos
            </li>
            <li className="flex items-center gap-1.5">
              <span className="w-4 border-t-2 border-primary" />
              Resolvidos
            </li>
          </ul>
        )}
      </div>

      {semDados ? (
        <p className="text-sm text-muted-foreground">Nenhum chamado nas últimas 8 semanas.</p>
      ) : (
        <>
          <Suspense
            fallback={
              <Esqueleto rotulo="Carregando gráfico…">
                <Bloco className="h-64 w-full" />
              </Esqueleto>
            }
          >
            <GraficoSemanal semanas={semanas} />
          </Suspense>
          <table className="sr-only">
            <caption>Chamados novos e resolvidos por semana</caption>
            <thead>
              <tr>
                <th scope="col">Período</th>
                <th scope="col">Novos</th>
                <th scope="col">Resolvidos</th>
              </tr>
            </thead>
            <tbody>
              {semanas.map((s) => (
                <tr key={s.inicio}>
                  <td>{formatarPeriodo(s)}</td>
                  <td>{s.novos}</td>
                  <td>{s.resolvidos}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  )
}

/**
 * Usa a mesma listagem da tela de chamados, então respeita o que o perfil pode ver.
 * Tem carregamento e erro próprios: uma falha aqui não derruba os indicadores.
 */
function ChamadosRecentes({ className }: { className?: string }) {
  const [busca, setBusca] = useState<{ tentativa: number; chamados?: Chamado[]; erro?: string } | null>(null)
  const [tentativa, setTentativa] = useState(0)
  const atual = busca?.tentativa === tentativa ? busca : null

  useEffect(() => {
    const controle = new AbortController()
    listarChamados({ page: 0 }, controle.signal)
      .then((pagina) => setBusca({ tentativa, chamados: pagina.items.slice(0, TOTAL_RECENTES) }))
      .catch((falha: unknown) => {
        if (!controle.signal.aborted) {
          setBusca({ tentativa, erro: toApiError(falha).message })
        }
      })
    return () => controle.abort()
  }, [tentativa])

  return (
    <div className={cn(PAINEL, 'grid content-start gap-3', className)}>
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-sm font-medium">Chamados recentes</h2>
        <Link to="/chamados" className="text-xs text-primary underline-offset-4 hover:underline">
          Ver todos
        </Link>
      </div>

      {!atual && (
        <EsqueletoLista rotulo="Carregando chamados…" linhas={3} />
      )}

      {atual?.erro && (
        <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-destructive">
          <span>{atual.erro}</span>
          <Button variant="outline" size="sm" onClick={() => setTentativa((n) => n + 1)}>
            Tentar novamente
          </Button>
        </div>
      )}

      {atual?.chamados?.length === 0 && <p className="text-sm text-muted-foreground">Nenhum chamado registrado.</p>}

      {atual?.chamados && atual.chamados.length > 0 && (
        <ol aria-label="Chamados recentes" className="grid gap-3">
          {atual.chamados.map((c) => (
            <li key={c.id} className="grid gap-1 border-t border-border pt-3 first:border-t-0 first:pt-0">
              <div className="flex items-start justify-between gap-2">
                <Link to={`/chamados/${c.id}`} className="min-w-0 text-sm font-medium break-words hover:text-primary hover:underline">
                  {c.titulo}
                </Link>
                <SeloStatus status={c.status} />
              </div>
              <div className="flex flex-wrap items-center gap-x-3 text-sm text-muted-foreground">
                <span>#{c.id}</span>
                <span>{c.categoria.nome}</span>
                <SeloPrioridade prioridade={c.prioridade} />
                <span>{formatarData(c.criadoEm)}</span>
              </div>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}

function Tabela({ titulo, colunas, children }: { titulo: string; colunas: [string, string]; children: ReactNode }) {
  return (
    <div className={PAINEL}>
      <table className="w-full text-sm">
        <caption className="mb-2 text-left font-medium">{titulo}</caption>
        <thead>
          <tr className="text-muted-foreground">
            <th scope="col" className="pb-2 text-left font-normal">
              {colunas[0]}
            </th>
            <th scope="col" className="pb-2 text-right font-normal whitespace-nowrap">
              {colunas[1]}
            </th>
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  )
}

/** A barra só compara as linhas da mesma tabela: o maior valor ocupa a largura toda. O número é a informação. */
function Linha({
  rotulo,
  valor,
  maximo,
  corBarra = 'bg-primary',
}: {
  rotulo: ReactNode
  valor: number
  maximo: number
  corBarra?: string
}) {
  return (
    <tr className="border-t border-border">
      <td className="w-full py-2 pr-4">
        {rotulo}
        <div aria-hidden="true" className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-muted">
          <div
            className={cn('h-full rounded-full', corBarra)}
            style={{ width: `${maximo > 0 ? (valor / maximo) * 100 : 0}%` }}
          />
        </div>
      </td>
      <td className="py-2 text-right tabular-nums">{valor}</td>
    </tr>
  )
}
