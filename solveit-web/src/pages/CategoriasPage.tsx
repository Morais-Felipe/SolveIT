import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { CampoAreaTexto } from '@/components/CampoAreaTexto'
import { CampoTexto } from '@/components/CampoTexto'
import { Paginacao } from '@/components/Paginacao'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  alterarAtivoCategoria,
  criarCategoria,
  editarCategoria,
  listarCategorias,
  REGISTRO_DESATUALIZADO,
} from '@/lib/admin'
import { toApiError, type ApiError } from '@/lib/api'
import type { Categoria, Pagina } from '@/lib/types'
import { cn } from '@/lib/utils'

type Visao = 'ativas' | 'todas'
type Resultado =
  | { tipo: 'carregando' }
  | { tipo: 'erro'; mensagem: string }
  | { tipo: 'pronto'; pagina: Pagina<Categoria> }

type Aviso = { tipo: 'sucesso' | 'erro'; texto: string }
type Painel = { modo: 'criar' } | { modo: 'editar'; categoria: Categoria } | null

const MAX_DESCRICAO = 10_000

/**
 * Catálogo de categorias (D-7). A aba Todas usa ?incluirInativas, aceito só para o
 * ADMIN (D-1), para que uma categoria desativada possa ser reativada. Ativar e
 * desativar são um clique: a ação é reversível e não apaga chamados.
 */
export function CategoriasPage() {
  const [params, setParams] = useSearchParams()
  const visao: Visao = params.get('visao') === 'todas' ? 'todas' : 'ativas'
  const page = Math.max(0, Math.trunc(Number(params.get('page'))) || 0)
  const [busca, setBusca] = useState<{ chave: string; resultado: Resultado } | null>(null)
  const [recarga, setRecarga] = useState(0)
  const [painel, setPainel] = useState<Painel>(null)
  const [aviso, setAviso] = useState<Aviso | null>(null)
  const [alternando, setAlternando] = useState<number | null>(null)

  const chaveBusca = JSON.stringify([visao, page, recarga])
  const estado: Resultado = busca?.chave === chaveBusca ? busca.resultado : { tipo: 'carregando' }

  useEffect(() => {
    const controle = new AbortController()
    listarCategorias(page, visao === 'todas', controle.signal)
      .then((pagina) => setBusca({ chave: chaveBusca, resultado: { tipo: 'pronto', pagina } }))
      .catch((falha: unknown) => {
        if (!controle.signal.aborted) {
          setBusca({ chave: chaveBusca, resultado: { tipo: 'erro', mensagem: toApiError(falha).message } })
        }
      })
    return () => controle.abort()
  }, [chaveBusca, visao, page])

  function trocarVisao(nova: Visao) {
    setAviso(null)
    setPainel(null)
    setParams(nova === 'todas' ? { visao: 'todas' } : {})
  }

  function irParaPagina(numero: number) {
    const novos = new URLSearchParams(params)
    if (numero > 0) novos.set('page', String(numero))
    else novos.delete('page')
    setPainel(null)
    setParams(novos)
  }

  function abrir(novo: Painel) {
    setAviso(null)
    setPainel(novo)
  }

  function concluir(texto: string) {
    setPainel(null)
    setAviso({ tipo: 'sucesso', texto })
    setRecarga((n) => n + 1)
  }

  function tratarDesatualizado(erro: ApiError): boolean {
    if (erro.code !== 'STALE_RESOURCE') return false
    setPainel(null)
    setAviso({ tipo: 'erro', texto: REGISTRO_DESATUALIZADO })
    setRecarga((n) => n + 1)
    return true
  }

  async function alternarAtivo(categoria: Categoria) {
    setAviso(null)
    setAlternando(categoria.id)
    try {
      const salva = await alterarAtivoCategoria(categoria, !categoria.ativo)
      concluir(`Categoria ${salva.nome} ${salva.ativo ? 'ativada' : 'desativada'}.`)
    } catch (falha) {
      const erro = toApiError(falha)
      if (!tratarDesatualizado(erro)) setAviso({ tipo: 'erro', texto: erro.message })
    } finally {
      setAlternando(null)
    }
  }

  return (
    <section className="grid gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Categorias</h1>
        {painel?.modo !== 'criar' && <Button onClick={() => abrir({ modo: 'criar' })}>Nova categoria</Button>}
      </div>

      <div role="tablist" aria-label="Situação das categorias" className="flex gap-1 border-b border-border">
        {(
          [
            ['ativas', 'Ativas'],
            ['todas', 'Todas'],
          ] as const
        ).map(([valor, rotulo]) => (
          <button
            key={valor}
            type="button"
            role="tab"
            aria-selected={visao === valor}
            onClick={() => trocarVisao(valor)}
            className={cn(
              '-mb-px border-b-2 px-3 py-2 text-sm transition-colors',
              visao === valor
                ? 'border-primary font-medium text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground',
            )}
          >
            {rotulo}
          </button>
        ))}
      </div>

      {aviso && (
        <Alert variant={aviso.tipo === 'erro' ? 'destructive' : 'default'}>
          <AlertDescription>{aviso.texto}</AlertDescription>
        </Alert>
      )}

      {painel?.modo === 'criar' && (
        <FormCategoria
          titulo="Nova categoria"
          rotuloEnviar="Criar categoria"
          aoCancelar={() => setPainel(null)}
          aoEnviar={async (dados) => {
            const criada = await criarCategoria(dados)
            concluir(`Categoria ${criada.nome} criada.`)
          }}
          tratarDesatualizado={tratarDesatualizado}
        />
      )}

      {estado.tipo === 'carregando' && (
        <p role="status" className="text-muted-foreground">
          Carregando categorias…
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
          {visao === 'ativas'
            ? 'Nenhuma categoria ativa. Sem categoria ativa, ninguém consegue abrir chamados.'
            : 'Nenhuma categoria cadastrada.'}
        </p>
      )}

      {estado.tipo === 'pronto' && estado.pagina.items.length > 0 && (
        <>
          <ul className="grid gap-3">
            {estado.pagina.items.map((c) => (
              <li key={c.id} className="grid gap-3 rounded-lg border border-border p-4">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                  <div className="grid min-w-0 gap-1">
                    <p className="flex flex-wrap items-center gap-2 font-medium break-words">
                      {c.nome}
                      {!c.ativo && (
                        <span className="inline-flex rounded-full border border-border bg-muted px-2 py-0.5 text-xs font-normal text-muted-foreground">
                          Inativa
                        </span>
                      )}
                    </p>
                    {c.descricao && <p className="text-sm break-words text-muted-foreground">{c.descricao}</p>}
                  </div>
                  <div className="flex flex-col gap-2 sm:flex-row">
                    <Button variant="outline" size="sm" onClick={() => abrir({ modo: 'editar', categoria: c })}>
                      Editar
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={alternando !== null}
                      onClick={() => alternarAtivo(c)}
                    >
                      {alternando === c.id ? 'Salvando…' : c.ativo ? 'Desativar' : 'Ativar'}
                    </Button>
                  </div>
                </div>

                {painel?.modo === 'editar' && painel.categoria.id === c.id && (
                  <FormCategoria
                    titulo={`Editar ${c.nome}`}
                    rotuloEnviar="Salvar categoria"
                    inicial={c}
                    aoCancelar={() => setPainel(null)}
                    aoEnviar={async (dados) => {
                      const salva = await editarCategoria(c, dados)
                      concluir(`Categoria ${salva.nome} atualizada.`)
                    }}
                    tratarDesatualizado={tratarDesatualizado}
                  />
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

function FormCategoria({
  titulo,
  rotuloEnviar,
  inicial,
  aoCancelar,
  aoEnviar,
  tratarDesatualizado,
}: {
  titulo: string
  rotuloEnviar: string
  inicial?: Categoria
  aoCancelar: () => void
  aoEnviar: (dados: { nome: string; descricao: string }) => Promise<void>
  tratarDesatualizado: (erro: ApiError) => boolean
}) {
  const [nome, setNome] = useState(inicial?.nome ?? '')
  const [descricao, setDescricao] = useState(inicial?.descricao ?? '')
  const [erroNome, setErroNome] = useState<string | null>(null)
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    setErroGeral(null)
    if (!nome.trim()) {
      setErroNome('Informe o nome.')
      return
    }
    setErroNome(null)
    setEnviando(true)
    try {
      await aoEnviar({ nome, descricao })
    } catch (falha) {
      const erro = toApiError(falha)
      if (tratarDesatualizado(erro)) return
      if (erro.code === 'CATEGORY_NAME_ALREADY_EXISTS') setErroNome(erro.message)
      else if (erro.fieldErrors.nome) setErroNome(erro.fieldErrors.nome)
      else setErroGeral(erro.message)
      setEnviando(false)
    }
  }

  return (
    <Card>
      <CardContent>
        <form noValidate onSubmit={enviar} className="grid gap-3">
          <p className="font-medium">{titulo}</p>
          {erroGeral && (
            <Alert variant="destructive">
              <AlertDescription>{erroGeral}</AlertDescription>
            </Alert>
          )}
          <CampoTexto
            id="categoria-nome"
            rotulo="Nome"
            maxLength={120}
            value={nome}
            erro={erroNome ?? undefined}
            onChange={(e) => setNome(e.target.value)}
          />
          <CampoAreaTexto
            id="categoria-descricao"
            rotulo="Descrição (opcional)"
            rows={3}
            maximo={MAX_DESCRICAO}
            value={descricao}
            onChange={(e) => setDescricao(e.target.value)}
          />
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={aoCancelar} disabled={enviando}>
              Cancelar
            </Button>
            <Button type="submit" disabled={enviando}>
              {enviando ? 'Salvando…' : rotuloEnviar}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}
