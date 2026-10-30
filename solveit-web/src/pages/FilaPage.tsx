import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { CampoSelecao } from '@/components/CampoSelecao'
import { Paginacao } from '@/components/Paginacao'
import { SeloPrioridade, SeloStatus } from '@/components/Selos'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { toApiError } from '@/lib/api'
import { atribuir, formatarData, listarChamados, ROTULO_STATUS, STATUS, type FiltrosChamados, type Ordem } from '@/lib/chamados'
import type { Chamado, Pagina, StatusChamado } from '@/lib/types'
import { cn } from '@/lib/utils'

type Aba = 'fila' | 'meus'
type Resultado =
  | { tipo: 'carregando' }
  | { tipo: 'erro'; mensagem: string }
  | { tipo: 'pronto'; pagina: Pagina<Chamado> }

const CORRIDA = 'Outro atendente assumiu este chamado primeiro.'
const MUDOU = 'O chamado mudou enquanto você olhava a fila. A lista foi atualizada.'

/** Na aba "meus", sem status na URL mostra o trabalho em curso; "TODOS" tira o filtro. */
function statusDosMeus(valor: string | null): StatusChamado | undefined {
  if (valor === 'TODOS') return undefined
  return valor && STATUS.includes(valor as StatusChamado) ? (valor as StatusChamado) : 'EM_ANDAMENTO'
}

/**
 * Tela de trabalho do atendente (D-1): a aba Fila mostra os chamados abertos sem
 * responsável, por ordem de chegada (D-2); a aba Meus mostra os atribuídos a ele.
 * Trocar de aba só muda a URL e a lista: a página não recarrega.
 */
export function FilaPage() {
  const { sessao } = useAuth()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const aba: Aba = params.get('aba') === 'meus' ? 'meus' : 'fila'
  const page = Math.max(0, Math.trunc(Number(params.get('page'))) || 0)
  const statusMeus = statusDosMeus(params.get('status'))
  const meuId = sessao.status === 'autenticado' ? sessao.usuario.id : 0
  const [busca, setBusca] = useState<{ chave: string; resultado: Resultado } | null>(null)
  const [recarga, setRecarga] = useState(0)
  const [aviso, setAviso] = useState<string | null>(null)
  const [assumindo, setAssumindo] = useState<number | null>(null)

  const chaveBusca = JSON.stringify([aba, page, statusMeus, meuId, recarga])
  const estado: Resultado = busca?.chave === chaveBusca ? busca.resultado : { tipo: 'carregando' }

  useEffect(() => {
    const controle = new AbortController()
    const [filtros, ordem]: [FiltrosChamados, Ordem] =
      aba === 'fila'
        ? [{ status: 'ABERTO', page }, 'criadoEm,asc']
        : [{ atendenteId: meuId, status: statusMeus, page }, 'atualizadoEm,desc']
    listarChamados(filtros, controle.signal, ordem)
      .then((pagina) => setBusca({ chave: chaveBusca, resultado: { tipo: 'pronto', pagina } }))
      .catch((falha: unknown) => {
        if (!controle.signal.aborted) {
          setBusca({ chave: chaveBusca, resultado: { tipo: 'erro', mensagem: toApiError(falha).message } })
        }
      })
    return () => controle.abort()
  }, [chaveBusca, aba, page, statusMeus, meuId])

  function trocarAba(nova: Aba) {
    setAviso(null)
    setParams(nova === 'meus' ? { aba: 'meus' } : {})
  }

  function irParaPagina(numero: number) {
    const novos = new URLSearchParams(params)
    if (numero > 0) novos.set('page', String(numero))
    else novos.delete('page')
    setParams(novos)
  }

  function filtrarStatus(valor: string) {
    const novos = new URLSearchParams(params)
    novos.set('status', valor)
    novos.delete('page')
    setParams(novos)
  }

  async function assumir(chamado: Chamado) {
    setAviso(null)
    setAssumindo(chamado.id)
    try {
      await atribuir(chamado, meuId)
      navigate(`/chamados/${chamado.id}`, { state: { aviso: `Você assumiu o chamado #${chamado.id}.` } })
    } catch (falha) {
      const erro = toApiError(falha)
      // D-4: perdeu a corrida (ou o chamado mudou): avisa e recarrega, sem reenviar nada.
      setAviso(erro.code === 'INVALID_STATE' ? CORRIDA : erro.code === 'STALE_RESOURCE' ? MUDOU : erro.message)
      if (erro.status === 409) setRecarga((n) => n + 1)
      setAssumindo(null)
    }
  }

  return (
    <section className="grid gap-6">
      <h1 className="text-2xl font-semibold">Fila de atendimento</h1>

      <div role="tablist" aria-label="Visões da fila" className="flex gap-1 border-b border-border">
        {(
          [
            ['fila', 'Fila'],
            ['meus', 'Meus chamados'],
          ] as const
        ).map(([valor, rotulo]) => (
          <button
            key={valor}
            type="button"
            role="tab"
            aria-selected={aba === valor}
            onClick={() => trocarAba(valor)}
            className={cn(
              '-mb-px border-b-2 px-3 py-2 text-sm transition-colors',
              aba === valor
                ? 'border-primary font-medium text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground',
            )}
          >
            {rotulo}
          </button>
        ))}
      </div>

      {aba === 'meus' && (
        <div className="max-w-xs">
          <CampoSelecao
            id="filtro-status-meus"
            rotulo="Status"
            value={statusMeus ?? 'TODOS'}
            onChange={(e) => filtrarStatus(e.target.value)}
          >
            <option value="TODOS">Todos</option>
            {STATUS.filter((s) => s !== 'ABERTO').map((s) => (
              <option key={s} value={s}>
                {ROTULO_STATUS[s]}
              </option>
            ))}
          </CampoSelecao>
        </div>
      )}

      {aviso && (
        <Alert variant="destructive">
          <AlertDescription>{aviso}</AlertDescription>
        </Alert>
      )}

      {estado.tipo === 'carregando' && (
        <p role="status" className="text-muted-foreground">
          Carregando chamados…
        </p>
      )}

      {estado.tipo === 'erro' && (
        <Alert variant="destructive">
          <AlertDescription className="flex flex-wrap items-center justify-between gap-3">
            <span>{estado.mensagem}</span>
            <Button variant="outline" size="sm" onClick={() => setRecarga((n) => n + 1)}>
              Tentar novamente
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {estado.tipo === 'pronto' && estado.pagina.items.length === 0 && (
        <p className="rounded-lg border border-dashed border-border p-8 text-center text-muted-foreground">
          {aba === 'fila'
            ? 'A fila está vazia: nenhum chamado aguardando atendimento.'
            : 'Você não tem chamados nesta situação.'}
        </p>
      )}

      {estado.tipo === 'pronto' && estado.pagina.items.length > 0 && (
        <>
          <ul className="grid gap-3">
            {estado.pagina.items.map((c) => (
              <li
                key={c.id}
                className="flex flex-col gap-3 rounded-lg border border-border p-4 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="grid min-w-0 gap-1">
                  <Link
                    to={`/chamados/${c.id}`}
                    className="font-medium break-words hover:text-primary hover:underline"
                  >
                    {c.titulo}
                  </Link>
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
                    <span>#{c.id}</span>
                    {aba === 'meus' && <SeloStatus status={c.status} />}
                    <SeloPrioridade prioridade={c.prioridade} />
                    <span>{c.categoria.nome}</span>
                    <span>{c.solicitante.nome}</span>
                    <span>{formatarData(c.criadoEm)}</span>
                  </div>
                </div>
                {aba === 'fila' && (
                  <Button
                    aria-label={`Assumir ${c.titulo}`}
                    className="w-full sm:w-auto"
                    disabled={assumindo !== null}
                    onClick={() => assumir(c)}
                  >
                    {assumindo === c.id ? 'Assumindo…' : 'Assumir'}
                  </Button>
                )}
              </li>
            ))}
          </ul>
          <Paginacao pagina={estado.pagina} aoMudar={irParaPagina} />
        </>
      )}
    </section>
  )
}
