import { useEffect, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { CampoSelecao } from '@/components/CampoSelecao'
import { SeloPrioridade, SeloStatus } from '@/components/Selos'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button, buttonVariants } from '@/components/ui/button'
import { toApiError } from '@/lib/api'
import {
  formatarData,
  listarCategoriasAtivas,
  listarChamados,
  PRIORIDADES,
  ROTULO_PRIORIDADE,
  ROTULO_STATUS,
  STATUS,
  type FiltrosChamados,
} from '@/lib/chamados'
import type { Categoria, Chamado, Pagina, Prioridade, StatusChamado } from '@/lib/types'

type Resultado =
  | { tipo: 'carregando' }
  | { tipo: 'erro'; mensagem: string }
  | { tipo: 'pronto'; pagina: Pagina<Chamado> }

/** Resultado de uma busca, marcado com a chave dos filtros que o produziram. */
type Busca = { chave: string; resultado: Resultado }

/** Lê os filtros da URL (decisão D-1), descartando valores que a API não reconheceria. */
function lerFiltros(params: URLSearchParams): FiltrosChamados {
  const status = params.get('status') as StatusChamado | null
  const prioridade = params.get('prioridade') as Prioridade | null
  const categoriaId = Number(params.get('categoriaId'))
  const page = Number(params.get('page'))
  return {
    status: status && STATUS.includes(status) ? status : undefined,
    prioridade: prioridade && PRIORIDADES.includes(prioridade) ? prioridade : undefined,
    categoriaId: Number.isInteger(categoriaId) && categoriaId > 0 ? categoriaId : undefined,
    page: Number.isInteger(page) && page > 0 ? page : 0,
  }
}

/**
 * Lista de chamados. Serve a todos os perfis: a API decide o que cada um enxerga
 * (o solicitante recebe só os próprios chamados).
 */
export function ChamadosPage() {
  const { sessao } = useAuth()
  const location = useLocation()
  const [params, setParams] = useSearchParams()
  const { status, prioridade, categoriaId, page } = lerFiltros(params)
  const [busca, setBusca] = useState<Busca | null>(null)
  const [categorias, setCategorias] = useState<Categoria[]>([])
  const [tentativa, setTentativa] = useState(0)
  const aviso = (location.state as { aviso?: string } | null)?.aviso
  const ehSolicitante = sessao.status === 'autenticado' && sessao.usuario.perfil === 'SOLICITANTE'
  const filtrando = Boolean(status || prioridade || categoriaId)
  // "Carregando" é derivado: se o último resultado é de outros filtros (ou de outra
  // tentativa), ainda estamos esperando a resposta atual.
  const chaveBusca = JSON.stringify([status, prioridade, categoriaId, page, tentativa])
  const estado: Resultado = busca?.chave === chaveBusca ? busca.resultado : { tipo: 'carregando' }

  useEffect(() => {
    const controle = new AbortController()
    listarCategoriasAtivas(controle.signal)
      .then(setCategorias)
      .catch(() => {
        // Sem categorias o filtro fica só com "Todas"; a lista continua utilizável.
      })
    return () => controle.abort()
  }, [])

  useEffect(() => {
    // Cada mudança de filtro cancela a busca anterior: uma resposta atrasada
    // nunca sobrescreve a lista mais recente.
    const controle = new AbortController()
    listarChamados({ status, prioridade, categoriaId, page }, controle.signal)
      .then((pagina) => setBusca({ chave: chaveBusca, resultado: { tipo: 'pronto', pagina } }))
      .catch((falha: unknown) => {
        if (!controle.signal.aborted) {
          setBusca({ chave: chaveBusca, resultado: { tipo: 'erro', mensagem: toApiError(falha).message } })
        }
      })
    return () => controle.abort()
  }, [chaveBusca, status, prioridade, categoriaId, page])

  /** Muda um filtro e volta à primeira página; valor vazio remove o parâmetro. */
  function filtrar(nome: 'status' | 'prioridade' | 'categoriaId', valor: string) {
    const novos = new URLSearchParams(params)
    if (valor) novos.set(nome, valor)
    else novos.delete(nome)
    novos.delete('page')
    setParams(novos)
  }

  function irParaPagina(numero: number) {
    const novos = new URLSearchParams(params)
    if (numero > 0) novos.set('page', String(numero))
    else novos.delete('page')
    setParams(novos)
  }

  return (
    <section className="grid gap-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">{ehSolicitante ? 'Meus chamados' : 'Chamados'}</h1>
        {ehSolicitante && (
          <Link to="/chamados/novo" className={buttonVariants()}>
            Novo chamado
          </Link>
        )}
      </header>

      {aviso && (
        <Alert>
          <AlertDescription>{aviso}</AlertDescription>
        </Alert>
      )}

      <div className="grid gap-3 sm:grid-cols-3">
        <CampoSelecao id="filtro-status" rotulo="Status" value={status ?? ''} onChange={(e) => filtrar('status', e.target.value)}>
          <option value="">Todos</option>
          {STATUS.map((s) => (
            <option key={s} value={s}>
              {ROTULO_STATUS[s]}
            </option>
          ))}
        </CampoSelecao>
        <CampoSelecao
          id="filtro-prioridade"
          rotulo="Prioridade"
          value={prioridade ?? ''}
          onChange={(e) => filtrar('prioridade', e.target.value)}
        >
          <option value="">Todas</option>
          {PRIORIDADES.map((p) => (
            <option key={p} value={p}>
              {ROTULO_PRIORIDADE[p]}
            </option>
          ))}
        </CampoSelecao>
        <CampoSelecao
          id="filtro-categoria"
          rotulo="Categoria"
          value={categoriaId ? String(categoriaId) : ''}
          onChange={(e) => filtrar('categoriaId', e.target.value)}
        >
          <option value="">Todas</option>
          {categorias.map((c) => (
            <option key={c.id} value={c.id}>
              {c.nome}
            </option>
          ))}
        </CampoSelecao>
      </div>

      {estado.tipo === 'carregando' && (
        <p role="status" className="text-muted-foreground">
          Carregando chamados…
        </p>
      )}

      {estado.tipo === 'erro' && (
        <Alert variant="destructive">
          <AlertDescription className="flex flex-wrap items-center justify-between gap-3">
            <span>{estado.mensagem}</span>
            <Button variant="outline" size="sm" onClick={() => setTentativa((t) => t + 1)}>
              Tentar novamente
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {estado.tipo === 'pronto' && estado.pagina.items.length === 0 && (
        <p className="rounded-lg border border-dashed border-border p-8 text-center text-muted-foreground">
          {filtrando
            ? 'Nenhum chamado encontrado para estes filtros.'
            : ehSolicitante
              ? 'Você ainda não abriu nenhum chamado.'
              : 'Nenhum chamado por aqui.'}
        </p>
      )}

      {estado.tipo === 'pronto' && estado.pagina.items.length > 0 && (
        <>
          <ListaChamados chamados={estado.pagina.items} />
          <Paginacao pagina={estado.pagina} aoMudar={irParaPagina} />
        </>
      )}
    </section>
  )
}

/** Tabela em telas largas; cartões empilhados no celular. */
function ListaChamados({ chamados }: { chamados: Chamado[] }) {
  return (
    <>
      <div className="hidden overflow-hidden rounded-lg border border-border md:block">
        <table className="w-full text-sm">
          <thead className="bg-muted/50 text-left text-muted-foreground">
            <tr>
              <th className="px-4 py-2 font-medium">#</th>
              <th className="px-4 py-2 font-medium">Título</th>
              <th className="px-4 py-2 font-medium">Categoria</th>
              <th className="px-4 py-2 font-medium">Prioridade</th>
              <th className="px-4 py-2 font-medium">Status</th>
              <th className="px-4 py-2 font-medium">Aberto em</th>
            </tr>
          </thead>
          <tbody>
            {chamados.map((c) => (
              <tr key={c.id} className="border-t border-border">
                <td className="px-4 py-3 text-muted-foreground">{c.id}</td>
                <td className="px-4 py-3 font-medium">{c.titulo}</td>
                <td className="px-4 py-3">{c.categoria.nome}</td>
                <td className="px-4 py-3">
                  <SeloPrioridade prioridade={c.prioridade} />
                </td>
                <td className="px-4 py-3">
                  <SeloStatus status={c.status} />
                </td>
                <td className="px-4 py-3 text-muted-foreground">{formatarData(c.criadoEm)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <ul className="grid gap-3 md:hidden">
        {chamados.map((c) => (
          <li key={c.id} className="grid gap-2 rounded-lg border border-border p-4">
            <div className="flex items-start justify-between gap-2">
              <span className="font-medium">{c.titulo}</span>
              <SeloStatus status={c.status} />
            </div>
            <div className="flex flex-wrap gap-x-3 gap-y-1 text-sm text-muted-foreground">
              <span>#{c.id}</span>
              <span>{c.categoria.nome}</span>
              <SeloPrioridade prioridade={c.prioridade} />
              <span>{formatarData(c.criadoEm)}</span>
            </div>
          </li>
        ))}
      </ul>
    </>
  )
}

function Paginacao({ pagina, aoMudar }: { pagina: Pagina<Chamado>; aoMudar: (numero: number) => void }) {
  if (pagina.totalPages <= 1) {
    return null
  }
  return (
    <nav aria-label="Paginação" className="flex items-center justify-between gap-3 text-sm">
      <Button variant="outline" size="sm" disabled={pagina.page === 0} onClick={() => aoMudar(pagina.page - 1)}>
        Anterior
      </Button>
      <span className="text-muted-foreground">
        Página {pagina.page + 1} de {pagina.totalPages}
      </span>
      <Button
        variant="outline"
        size="sm"
        disabled={pagina.page + 1 >= pagina.totalPages}
        onClick={() => aoMudar(pagina.page + 1)}
      >
        Próxima
      </Button>
    </nav>
  )
}
