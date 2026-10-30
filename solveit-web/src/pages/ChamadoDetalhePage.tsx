import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { CampoAreaTexto } from '@/components/CampoAreaTexto'
import { CampoTexto } from '@/components/CampoTexto'
import { SeloPrioridade, SeloStatus } from '@/components/Selos'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { acoesDisponiveis, DESTINO, type Acao } from '@/lib/acoes'
import { toApiError, type ApiError } from '@/lib/api'
import {
  alterarStatus,
  buscarChamado,
  comentar,
  editarChamado,
  formatarDataHora,
  listarComentarios,
  listarHistorico,
  ROTULO_STATUS,
} from '@/lib/chamados'
import type { Chamado, Comentario, HistoricoStatus } from '@/lib/types'

// Mesmos limites dos DTOs da API.
const MAX_TITULO = 200
const MAX_DESCRICAO = 10_000
const MAX_COMENTARIO = 10_000
const MAX_OBSERVACAO = 500

const SEM_ACESSO = 'Chamado não encontrado ou você não tem acesso a ele.'
const CONFLITO = 'O chamado foi alterado por outra pessoa. Confira os dados atualizados e tente de novo.'

type Dados = { chamado: Chamado; comentarios: Comentario[]; historico: HistoricoStatus[] }
type Resultado = { tipo: 'pronto'; dados: Dados } | { tipo: 'erro'; mensagem: string }
type Aviso = { tipo: 'sucesso' | 'erro'; texto: string }
type AcaoStatus = 'RESOLVER' | 'FECHAR' | 'CANCELAR'

const ROTULO_ACAO: Record<AcaoStatus, string> = {
  RESOLVER: 'Resolver',
  FECHAR: 'Fechar chamado',
  CANCELAR: 'Cancelar chamado',
}

/** 403 e 404 recebem a mesma mensagem (decisão D-6): quem não tem acesso não descobre se o chamado existe. */
function mensagemDeCarga(erro: ApiError): string {
  return erro.status === 403 || erro.status === 404 ? SEM_ACESSO : erro.message
}

export function ChamadoDetalhePage() {
  const { sessao } = useAuth()
  const id = Number(useParams().id)
  const idValido = Number.isInteger(id) && id > 0
  const [busca, setBusca] = useState<{ id: number; resultado: Resultado } | null>(null)
  const [recarga, setRecarga] = useState(0)
  const [aviso, setAviso] = useState<Aviso | null>(null)

  useEffect(() => {
    if (!idValido) return
    const controle = new AbortController()
    Promise.all([
      buscarChamado(id, controle.signal),
      listarComentarios(id, controle.signal),
      listarHistorico(id, controle.signal),
    ])
      .then(([chamado, comentarios, historico]) =>
        setBusca({ id, resultado: { tipo: 'pronto', dados: { chamado, comentarios, historico } } }),
      )
      .catch((falha: unknown) => {
        if (!controle.signal.aborted) {
          setBusca({ id, resultado: { tipo: 'erro', mensagem: mensagemDeCarga(toApiError(falha)) } })
        }
      })
    return () => controle.abort()
  }, [id, idValido, recarga])

  if (sessao.status !== 'autenticado') {
    return null
  }
  if (!idValido) {
    return <Mensagem texto={SEM_ACESSO} />
  }
  // Durante uma recarga, os dados anteriores continuam na tela em vez de piscar "Carregando…".
  if (busca?.id !== id) {
    return (
      <p role="status" className="text-muted-foreground">
        Carregando chamado…
      </p>
    )
  }
  if (busca.resultado.tipo === 'erro') {
    return <Mensagem texto={busca.resultado.mensagem} />
  }

  const { chamado, comentarios, historico } = busca.resultado.dados
  const acoes = acoesDisponiveis(sessao.usuario, chamado)

  function atualizarDados(mudar: (dados: Dados) => Dados) {
    setBusca((atual) =>
      atual && atual.resultado.tipo === 'pronto'
        ? { ...atual, resultado: { tipo: 'pronto', dados: mudar(atual.resultado.dados) } }
        : atual,
    )
  }

  /** Falha de uma ação: conflito de versão recarrega (D-3); o resto vira aviso de erro. */
  function tratarFalha(falha: unknown) {
    const erro = toApiError(falha)
    if (erro.code === 'STALE_RESOURCE') {
      setAviso({ tipo: 'erro', texto: CONFLITO })
      setRecarga((n) => n + 1)
    } else {
      setAviso({ tipo: 'erro', texto: erro.message })
    }
  }

  return (
    <article className="grid gap-6">
      <Link to="/chamados" className="text-sm text-primary underline-offset-4 hover:underline">
        ← Voltar para chamados
      </Link>

      {aviso && (
        <Alert variant={aviso.tipo === 'erro' ? 'destructive' : 'default'}>
          <AlertDescription>{aviso.texto}</AlertDescription>
        </Alert>
      )}

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <div className="grid content-start gap-6">
          <CabecalhoEDescricao
            chamado={chamado}
            podeEditar={acoes.has('EDITAR')}
            aoSalvar={async (titulo, descricao) => {
              try {
                const atualizado = await editarChamado(chamado, titulo, descricao)
                atualizarDados((d) => ({ ...d, chamado: atualizado }))
                setAviso({ tipo: 'sucesso', texto: 'Chamado atualizado.' })
                return true
              } catch (falha) {
                tratarFalha(falha)
                return false
              }
            }}
          />
          <Conversa
            chamado={chamado}
            comentarios={comentarios}
            podeComentar={acoes.has('COMENTAR')}
            aoComentar={async (texto) => {
              try {
                const novo = await comentar(chamado.id, texto)
                atualizarDados((d) => ({ ...d, comentarios: [...d.comentarios, novo] }))
                return true
              } catch (falha) {
                tratarFalha(falha)
                return false
              }
            }}
          />
        </div>

        <aside className="grid content-start gap-6">
          <FichaChamado chamado={chamado} />
          <Acoes
            acoes={acoes}
            aoConfirmar={async (acao, observacao) => {
              try {
                const atualizado = await alterarStatus(chamado, DESTINO[acao], observacao)
                setAviso({ tipo: 'sucesso', texto: `Status alterado para ${ROTULO_STATUS[atualizado.status]}.` })
                // O histórico ganhou uma linha que só o servidor conhece: recarrega tudo.
                setRecarga((n) => n + 1)
                return true
              } catch (falha) {
                tratarFalha(falha)
                return false
              }
            }}
          />
          <Historico historico={historico} />
        </aside>
      </div>
    </article>
  )
}

function Mensagem({ texto }: { texto: string }) {
  return (
    <section className="grid gap-3">
      <p className="rounded-lg border border-dashed border-border p-8 text-center text-muted-foreground">{texto}</p>
      <Link to="/chamados" className="text-sm text-primary underline-offset-4 hover:underline">
        ← Voltar para chamados
      </Link>
    </section>
  )
}

function CabecalhoEDescricao({
  chamado,
  podeEditar,
  aoSalvar,
}: {
  chamado: Chamado
  podeEditar: boolean
  aoSalvar: (titulo: string, descricao: string) => Promise<boolean>
}) {
  const [editando, setEditando] = useState(false)
  const [titulo, setTitulo] = useState('')
  const [descricao, setDescricao] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)

  function abrirEdicao() {
    setTitulo(chamado.titulo)
    setDescricao(chamado.descricao)
    setErro(null)
    setEditando(true)
  }

  async function salvar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (!titulo.trim() || !descricao.trim()) {
      setErro('Título e descrição são obrigatórios.')
      return
    }
    setSalvando(true)
    const ok = await aoSalvar(titulo, descricao)
    setSalvando(false)
    if (ok) setEditando(false)
  }

  if (editando) {
    return (
      <Card>
        <form onSubmit={salvar} noValidate>
          <CardContent className="grid gap-4">
            <CampoTexto
              id="editar-titulo"
              rotulo="Título"
              maxLength={MAX_TITULO}
              value={titulo}
              onChange={(e) => setTitulo(e.target.value)}
              erro={erro ?? undefined}
            />
            <CampoAreaTexto
              id="editar-descricao"
              rotulo="Descrição"
              rows={6}
              maximo={MAX_DESCRICAO}
              value={descricao}
              onChange={(e) => setDescricao(e.target.value)}
            />
            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <Button type="button" variant="outline" onClick={() => setEditando(false)}>
                Descartar
              </Button>
              <Button type="submit" disabled={salvando}>
                {salvando ? 'Salvando…' : 'Salvar'}
              </Button>
            </div>
          </CardContent>
        </form>
      </Card>
    )
  }

  return (
    <header className="grid gap-3">
      <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
        <span>#{chamado.id}</span>
        <SeloStatus status={chamado.status} />
        <SeloPrioridade prioridade={chamado.prioridade} />
      </div>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h1 className="min-w-0 text-2xl font-semibold break-words">{chamado.titulo}</h1>
        {podeEditar && (
          <Button variant="outline" size="sm" onClick={abrirEdicao}>
            Editar
          </Button>
        )}
      </div>
      <p className="whitespace-pre-wrap break-words">{chamado.descricao}</p>
    </header>
  )
}

function Conversa({
  chamado,
  comentarios,
  podeComentar,
  aoComentar,
}: {
  chamado: Chamado
  comentarios: Comentario[]
  podeComentar: boolean
  aoComentar: (texto: string) => Promise<boolean>
}) {
  const [texto, setTexto] = useState('')
  const [enviando, setEnviando] = useState(false)

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (!texto.trim()) return
    setEnviando(true)
    const ok = await aoComentar(texto)
    setEnviando(false)
    if (ok) setTexto('')
  }

  return (
    <section className="grid gap-4">
      <h2 className="text-lg font-semibold">Comentários</h2>
      {comentarios.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum comentário ainda.</p>
      ) : (
        <ol aria-label="Comentários" className="grid gap-3">
          {comentarios.map((c) => (
            <li key={c.id} className="grid gap-1 rounded-lg border border-border p-3">
              <div className="flex flex-wrap justify-between gap-2 text-xs text-muted-foreground">
                <span className="font-medium text-foreground">{c.autor.nome}</span>
                <time dateTime={c.criadoEm}>{formatarDataHora(c.criadoEm)}</time>
              </div>
              <p className="text-sm whitespace-pre-wrap break-words">{c.texto}</p>
            </li>
          ))}
        </ol>
      )}

      {podeComentar ? (
        <form onSubmit={enviar} className="grid gap-2">
          <CampoAreaTexto
            id="novo-comentario"
            rotulo="Novo comentário"
            rows={3}
            maximo={MAX_COMENTARIO}
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
          />
          <Button type="submit" className="justify-self-end" disabled={enviando || !texto.trim()}>
            {enviando ? 'Enviando…' : 'Comentar'}
          </Button>
        </form>
      ) : (
        <p className="text-sm text-muted-foreground">
          A conversa foi encerrada porque o chamado está {chamado.status === 'FECHADO' ? 'fechado' : 'cancelado'}.
        </p>
      )}
    </section>
  )
}

function FichaChamado({ chamado }: { chamado: Chamado }) {
  return (
    <Card>
      <CardContent>
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
          <dt className="text-muted-foreground">Categoria</dt>
          <dd>{chamado.categoria.nome}</dd>
          <dt className="text-muted-foreground">Solicitante</dt>
          <dd>{chamado.solicitante.nome}</dd>
          <dt className="text-muted-foreground">Responsável</dt>
          <dd>{chamado.atendente?.nome ?? 'Ninguém assumiu ainda'}</dd>
          <dt className="text-muted-foreground">Aberto em</dt>
          <dd>{formatarDataHora(chamado.criadoEm)}</dd>
          {chamado.resolvidoEm && (
            <>
              <dt className="text-muted-foreground">Resolvido em</dt>
              <dd>{formatarDataHora(chamado.resolvidoEm)}</dd>
            </>
          )}
        </dl>
      </CardContent>
    </Card>
  )
}

/** Botões de mudança de status, cada um com confirmação e observação opcional (D-4). */
function Acoes({
  acoes,
  aoConfirmar,
}: {
  acoes: Set<Acao>
  aoConfirmar: (acao: AcaoStatus, observacao: string) => Promise<boolean>
}) {
  const [escolhida, setEscolhida] = useState<AcaoStatus | null>(null)
  const [observacao, setObservacao] = useState('')
  const [enviando, setEnviando] = useState(false)
  const disponiveis = (['RESOLVER', 'FECHAR', 'CANCELAR'] as const).filter((a) => acoes.has(a))

  if (disponiveis.length === 0) {
    return null
  }

  async function confirmar() {
    if (!escolhida) return
    setEnviando(true)
    const ok = await aoConfirmar(escolhida, observacao)
    setEnviando(false)
    if (ok) {
      setEscolhida(null)
      setObservacao('')
    }
  }

  if (escolhida) {
    return (
      <Card>
        <CardContent className="grid gap-3">
          <p className="font-medium">{ROTULO_ACAO[escolhida]}?</p>
          <CampoAreaTexto
            id="observacao"
            rotulo="Observação (opcional)"
            rows={3}
            maximo={MAX_OBSERVACAO}
            value={observacao}
            onChange={(e) => setObservacao(e.target.value)}
          />
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button variant="outline" onClick={() => setEscolhida(null)} disabled={enviando}>
              Voltar
            </Button>
            <Button
              variant={escolhida === 'CANCELAR' ? 'destructive' : 'default'}
              onClick={confirmar}
              disabled={enviando}
            >
              {enviando ? 'Enviando…' : 'Confirmar'}
            </Button>
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <section aria-label="Ações" className="grid gap-2">
      {disponiveis.map((acao) => (
        <Button
          key={acao}
          variant={acao === 'CANCELAR' ? 'destructive' : 'default'}
          className="w-full"
          onClick={() => setEscolhida(acao)}
        >
          {ROTULO_ACAO[acao]}
        </Button>
      ))}
    </section>
  )
}

function Historico({ historico }: { historico: HistoricoStatus[] }) {
  return (
    <section className="grid gap-3">
      <h2 className="text-lg font-semibold">Histórico</h2>
      <ol aria-label="Histórico de status" className="grid gap-3 border-l border-border pl-4">
        {historico.map((h) => (
          <li key={h.id} className="grid gap-0.5 text-sm">
            <span className="font-medium">
              {h.statusAnterior
                ? `${ROTULO_STATUS[h.statusAnterior]} → ${ROTULO_STATUS[h.statusNovo]}`
                : 'Chamado aberto'}
            </span>
            <span className="text-xs text-muted-foreground">
              {h.alteradoPor.nome} · <time dateTime={h.alteradoEm}>{formatarDataHora(h.alteradoEm)}</time>
            </span>
            {h.observacao && <p className="text-muted-foreground whitespace-pre-wrap break-words">{h.observacao}</p>}
          </li>
        ))}
      </ol>
    </section>
  )
}
