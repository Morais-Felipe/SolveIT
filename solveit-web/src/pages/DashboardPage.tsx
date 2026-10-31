import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { SeloPrioridade } from '@/components/Selos'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { buscarResumo } from '@/lib/admin'
import { toApiError } from '@/lib/api'
import { PRIORIDADES } from '@/lib/chamados'
import type { ResumoDashboard } from '@/lib/types'
import { cn } from '@/lib/utils'

type Resultado =
  | { tipo: 'carregando' }
  | { tipo: 'erro'; mensagem: string }
  | { tipo: 'pronto'; resumo: ResumoDashboard }

/**
 * Painel do ADMIN e do atendente (D-2): cards e tabelas, sem gráficos. Os números vêm
 * prontos da API, já no escopo do perfil; cada card leva à lista com o mesmo filtro,
 * para que a pessoa confira de onde o número saiu.
 */
export function DashboardPage() {
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

  return (
    <section className="grid gap-6">
      <h1 className="text-2xl font-semibold">Dashboard</h1>

      {estado.tipo === 'carregando' && (
        <p role="status" className="text-muted-foreground">
          Carregando indicadores…
        </p>
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

function Indicadores({ resumo }: { resumo: ResumoDashboard }) {
  const { totais } = resumo
  const cards = [
    { rotulo: 'Abertos', valor: totais.aberto, para: '/chamados?status=ABERTO' },
    { rotulo: 'Em andamento', valor: totais.emAndamento, para: '/chamados?status=EM_ANDAMENTO' },
    { rotulo: 'Resolvidos', valor: totais.resolvido, para: '/chamados?status=RESOLVIDO' },
    {
      rotulo: 'Críticos abertos',
      valor: totais.criticaAberta,
      para: '/chamados?status=ABERTO&prioridade=CRITICA',
      alerta: totais.criticaAberta > 0,
    },
  ]

  return (
    <>
      <ul className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {cards.map((c) => (
          <li key={c.rotulo}>
            <Link
              to={c.para}
              className={cn(
                'grid gap-1 rounded-lg border border-border p-4 transition-colors hover:border-primary hover:bg-muted/50',
                c.alerta && 'border-destructive/50',
              )}
            >
              <span className="text-sm text-muted-foreground">{c.rotulo}</span>
              <span className={cn('text-3xl font-semibold tabular-nums', c.alerta && 'text-destructive')}>
                {c.valor}
              </span>
            </Link>
          </li>
        ))}
      </ul>

      <div className="grid gap-6 lg:grid-cols-2">
        <Tabela titulo="Abertos por prioridade" colunas={['Prioridade', 'Abertos']}>
          {PRIORIDADES.map((p) => (
            <tr key={p} className="border-t border-border">
              <td className="py-2">
                <SeloPrioridade prioridade={p} />
              </td>
              <td className="py-2 text-right tabular-nums">{resumo.abertosPorPrioridade[p] ?? 0}</td>
            </tr>
          ))}
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
              <tr key={c.categoriaId} className="border-t border-border">
                <td className="py-2">
                  <Link to={`/chamados?categoriaId=${c.categoriaId}`} className="hover:text-primary hover:underline">
                    {c.nome}
                  </Link>
                </td>
                <td className="py-2 text-right tabular-nums">{c.quantidade}</td>
              </tr>
            ))
          )}
        </Tabela>

        {resumo.cargaPorAtendente.length > 0 && (
          <Tabela titulo="Carga por atendente" colunas={['Atendente', 'Em andamento']}>
            {resumo.cargaPorAtendente.map((a) => (
              <tr key={a.atendenteId} className="border-t border-border">
                <td className="py-2">{a.nome}</td>
                <td className="py-2 text-right tabular-nums">{a.emAndamento}</td>
              </tr>
            ))}
          </Tabela>
        )}
      </div>
    </>
  )
}

function Tabela({ titulo, colunas, children }: { titulo: string; colunas: [string, string]; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-border p-4">
      <table className="w-full text-sm">
        <caption className="mb-2 text-left font-medium">{titulo}</caption>
        <thead>
          <tr className="text-muted-foreground">
            <th scope="col" className="pb-2 text-left font-normal">
              {colunas[0]}
            </th>
            <th scope="col" className="pb-2 text-right font-normal">
              {colunas[1]}
            </th>
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  )
}
