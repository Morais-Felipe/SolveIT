import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { CampoSelecao } from '@/components/CampoSelecao'
import { CampoTexto } from '@/components/CampoTexto'
import { Paginacao } from '@/components/Paginacao'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  alterarOperacao,
  criarUsuario,
  editarDadosUsuario,
  listarUsuarios,
  PERFIS,
  REGISTRO_DESATUALIZADO,
  ROTULO_PERFIL,
  SENHA_MINIMA,
} from '@/lib/admin'
import { toApiError, type ApiError } from '@/lib/api'
import type { Pagina, Perfil, UsuarioAdmin } from '@/lib/types'
import { cn } from '@/lib/utils'

type Resultado =
  | { tipo: 'carregando' }
  | { tipo: 'erro'; mensagem: string }
  | { tipo: 'pronto'; pagina: Pagina<UsuarioAdmin> }

type Aviso = { tipo: 'sucesso' | 'erro'; texto: string }

/** Um painel aberto por vez: criar, editar dados ou perfil e situação de um usuário. */
type Painel = { modo: 'criar' } | { modo: 'dados' | 'operacao'; usuario: UsuarioAdmin } | null

type ErrosCampos = Partial<Record<'nome' | 'email' | 'senha', string>>

/**
 * Administração de usuários (D-3). Dados pessoais e perfil/situação ficam em painéis
 * separados porque a API os separa em duas rotas. Toda resposta 409 de versão
 * recarrega a lista (D-6): o formulário nunca reenvia sozinho.
 */
export function UsuariosPage() {
  const { sessao } = useAuth()
  const meuId = sessao.status === 'autenticado' ? sessao.usuario.id : 0
  const [params, setParams] = useSearchParams()
  const page = Math.max(0, Math.trunc(Number(params.get('page'))) || 0)
  const [busca, setBusca] = useState<{ chave: string; resultado: Resultado } | null>(null)
  const [recarga, setRecarga] = useState(0)
  const [painel, setPainel] = useState<Painel>(null)
  const [aviso, setAviso] = useState<Aviso | null>(null)

  const chaveBusca = JSON.stringify([page, recarga])
  const estado: Resultado = busca?.chave === chaveBusca ? busca.resultado : { tipo: 'carregando' }

  useEffect(() => {
    const controle = new AbortController()
    listarUsuarios(page, controle.signal)
      .then((pagina) => setBusca({ chave: chaveBusca, resultado: { tipo: 'pronto', pagina } }))
      .catch((falha: unknown) => {
        if (!controle.signal.aborted) {
          setBusca({ chave: chaveBusca, resultado: { tipo: 'erro', mensagem: toApiError(falha).message } })
        }
      })
    return () => controle.abort()
  }, [chaveBusca, page])

  function abrir(novo: Painel) {
    setAviso(null)
    setPainel(novo)
  }

  /** Fecha o painel, mostra o resultado e busca a lista de novo. */
  function concluir(texto: string) {
    setPainel(null)
    setAviso({ tipo: 'sucesso', texto })
    setRecarga((n) => n + 1)
  }

  /** Versão desatualizada: fecha o painel e recarrega. Outros erros ficam no painel. */
  function tratarDesatualizado(erro: ApiError): boolean {
    if (erro.code !== 'STALE_RESOURCE') return false
    setPainel(null)
    setAviso({ tipo: 'erro', texto: REGISTRO_DESATUALIZADO })
    setRecarga((n) => n + 1)
    return true
  }

  function irParaPagina(numero: number) {
    setPainel(null)
    setParams(numero > 0 ? { page: String(numero) } : {})
  }

  return (
    <section className="grid gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Usuários</h1>
        {painel?.modo !== 'criar' && <Button onClick={() => abrir({ modo: 'criar' })}>Novo usuário</Button>}
      </div>

      {aviso && (
        <Alert variant={aviso.tipo === 'erro' ? 'destructive' : 'default'}>
          <AlertDescription>{aviso.texto}</AlertDescription>
        </Alert>
      )}

      {painel?.modo === 'criar' && (
        <FormDados
          titulo="Novo usuário"
          rotuloEnviar="Criar usuário"
          aoCancelar={() => setPainel(null)}
          aoEnviar={async (dados) => {
            const criado = await criarUsuario({ ...dados, perfil: dados.perfil ?? 'SOLICITANTE' })
            concluir(`Usuário ${criado.nome} criado.`)
          }}
          tratarDesatualizado={tratarDesatualizado}
        />
      )}

      {estado.tipo === 'carregando' && (
        <p role="status" className="text-muted-foreground">
          Carregando usuários…
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
          Nenhum usuário nesta página.
        </p>
      )}

      {estado.tipo === 'pronto' && estado.pagina.items.length > 0 && (
        <>
          <ul className="grid gap-3">
            {estado.pagina.items.map((u) => {
              const aberto = painel && painel.modo !== 'criar' && painel.usuario.id === u.id ? painel.modo : null
              return (
                <li key={u.id} className="grid gap-3 rounded-lg border border-border p-4">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div className="grid min-w-0 gap-1">
                      <p className="font-medium break-words">
                        {u.nome} {u.id === meuId && <span className="text-sm text-muted-foreground">(você)</span>}
                      </p>
                      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
                        <span className="break-all">{u.email}</span>
                        <span>{ROTULO_PERFIL[u.perfil]}</span>
                        <span
                          className={cn(
                            'inline-flex rounded-full border px-2 py-0.5 text-xs',
                            u.ativo
                              ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300'
                              : 'border-border bg-muted text-muted-foreground',
                          )}
                        >
                          {u.ativo ? 'Ativo' : 'Inativo'}
                        </span>
                      </div>
                    </div>
                    <div className="flex flex-col gap-2 sm:flex-row">
                      <Button variant="outline" size="sm" onClick={() => abrir({ modo: 'dados', usuario: u })}>
                        Editar dados
                      </Button>
                      {/* D-5: o ADMIN logado não muda o próprio perfil nem se desativa por aqui. */}
                      {u.id !== meuId && (
                        <Button variant="outline" size="sm" onClick={() => abrir({ modo: 'operacao', usuario: u })}>
                          Perfil e situação
                        </Button>
                      )}
                    </div>
                  </div>

                  {aberto === 'dados' && (
                    <FormDados
                      titulo={`Editar dados de ${u.nome}`}
                      rotuloEnviar="Salvar dados"
                      inicial={u}
                      aoCancelar={() => setPainel(null)}
                      aoEnviar={async (dados) => {
                        const salvo = await editarDadosUsuario(u, dados)
                        concluir(`Dados de ${salvo.nome} atualizados.`)
                      }}
                      tratarDesatualizado={tratarDesatualizado}
                    />
                  )}

                  {aberto === 'operacao' && (
                    <FormOperacao
                      usuario={u}
                      aoCancelar={() => setPainel(null)}
                      aoConcluir={concluir}
                      tratarDesatualizado={tratarDesatualizado}
                    />
                  )}
                </li>
              )
            })}
          </ul>
          <Paginacao pagina={estado.pagina} aoMudar={irParaPagina} />
        </>
      )}
    </section>
  )
}

type DadosFormulario = { nome: string; email: string; senha: string; perfil?: Perfil }

/**
 * Formulário de dados pessoais. Sem "inicial" é criação: senha obrigatória e perfil
 * escolhido aqui. Com "inicial" é edição: senha vazia mantém a atual e não há perfil.
 */
function FormDados({
  titulo,
  rotuloEnviar,
  inicial,
  aoCancelar,
  aoEnviar,
  tratarDesatualizado,
}: {
  titulo: string
  rotuloEnviar: string
  inicial?: UsuarioAdmin
  aoCancelar: () => void
  aoEnviar: (dados: DadosFormulario) => Promise<void>
  tratarDesatualizado: (erro: ApiError) => boolean
}) {
  const criando = !inicial
  const [nome, setNome] = useState(inicial?.nome ?? '')
  const [email, setEmail] = useState(inicial?.email ?? '')
  const [senha, setSenha] = useState('')
  const [perfil, setPerfil] = useState<Perfil>('SOLICITANTE')
  const [erros, setErros] = useState<ErrosCampos>({})
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  function validar(): ErrosCampos {
    const encontrados: ErrosCampos = {}
    if (!nome.trim()) encontrados.nome = 'Informe o nome.'
    if (!email.trim()) encontrados.email = 'Informe o e-mail.'
    if ((criando || senha) && [...senha].length < SENHA_MINIMA) {
      encontrados.senha = `A senha precisa de pelo menos ${SENHA_MINIMA} caracteres.`
    }
    return encontrados
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    setErroGeral(null)
    const encontrados = validar()
    setErros(encontrados)
    if (Object.keys(encontrados).length > 0) return

    setEnviando(true)
    try {
      await aoEnviar({ nome: nome.trim(), email: email.trim(), senha, perfil: criando ? perfil : undefined })
    } catch (falha) {
      const erro = toApiError(falha)
      if (tratarDesatualizado(erro)) return
      if (erro.code === 'EMAIL_ALREADY_REGISTERED') {
        setErros({ email: erro.message })
      } else if (Object.keys(erro.fieldErrors).length > 0) {
        setErros(erro.fieldErrors)
      } else {
        setErroGeral(erro.message)
      }
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
          <CampoTexto id="usuario-nome" rotulo="Nome" maxLength={120} value={nome} erro={erros.nome}
            onChange={(e) => setNome(e.target.value)} />
          <CampoTexto id="usuario-email" rotulo="E-mail" type="email" maxLength={255} value={email} erro={erros.email}
            onChange={(e) => setEmail(e.target.value)} />
          <CampoTexto
            id="usuario-senha"
            rotulo="Senha"
            type="password"
            autoComplete="new-password"
            value={senha}
            erro={erros.senha}
            ajuda={criando ? `Pelo menos ${SENHA_MINIMA} caracteres.` : 'Deixe em branco para manter a senha atual.'}
            onChange={(e) => setSenha(e.target.value)}
          />
          {criando && (
            <CampoSelecao id="usuario-perfil" rotulo="Perfil" value={perfil} onChange={(e) => setPerfil(e.target.value as Perfil)}>
              {PERFIS.map((p) => (
                <option key={p} value={p}>
                  {ROTULO_PERFIL[p]}
                </option>
              ))}
            </CampoSelecao>
          )}
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

/**
 * Perfil e situação numa única operação (D-4). Quando o atendente deixa de atender, a
 * API devolve os chamados em andamento dele à fila; a tela avisa antes e conta depois.
 */
function FormOperacao({
  usuario,
  aoCancelar,
  aoConcluir,
  tratarDesatualizado,
}: {
  usuario: UsuarioAdmin
  aoCancelar: () => void
  aoConcluir: (texto: string) => void
  tratarDesatualizado: (erro: ApiError) => boolean
}) {
  const [perfil, setPerfil] = useState<Perfil>(usuario.perfil)
  const [ativo, setAtivo] = useState(usuario.ativo)
  const [erro, setErro] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  const mudou = perfil !== usuario.perfil || ativo !== usuario.ativo
  const deixaDeAtender = usuario.perfil === 'ATENDENTE' && !(perfil === 'ATENDENTE' && ativo)

  async function salvar() {
    setErro(null)
    setEnviando(true)
    try {
      const { usuario: salvo, chamadosDevolvidos } = await alterarOperacao(usuario, perfil, ativo)
      const devolvidos =
        chamadosDevolvidos === 0
          ? ''
          : chamadosDevolvidos === 1
            ? ' 1 chamado voltou para a fila.'
            : ` ${chamadosDevolvidos} chamados voltaram para a fila.`
      aoConcluir(`Perfil e situação de ${salvo.nome} atualizados.${devolvidos}`)
    } catch (falha) {
      const apiErro = toApiError(falha)
      if (tratarDesatualizado(apiErro)) return
      setErro(apiErro.message)
      setEnviando(false)
    }
  }

  return (
    <Card>
      <CardContent className="grid gap-3">
        <p className="font-medium">Perfil e situação de {usuario.nome}</p>
        {erro && (
          <Alert variant="destructive">
            <AlertDescription>{erro}</AlertDescription>
          </Alert>
        )}
        <div className="grid gap-3 sm:grid-cols-2">
          <CampoSelecao id="operacao-perfil" rotulo="Perfil" value={perfil} onChange={(e) => setPerfil(e.target.value as Perfil)}>
            {PERFIS.map((p) => (
              <option key={p} value={p}>
                {ROTULO_PERFIL[p]}
              </option>
            ))}
          </CampoSelecao>
          <CampoSelecao
            id="operacao-situacao"
            rotulo="Situação"
            value={ativo ? 'ativo' : 'inativo'}
            onChange={(e) => setAtivo(e.target.value === 'ativo')}
          >
            <option value="ativo">Ativo</option>
            <option value="inativo">Inativo</option>
          </CampoSelecao>
        </div>
        {deixaDeAtender && (
          <p className="rounded-md border border-amber-500/40 bg-amber-500/10 p-3 text-sm text-amber-800 dark:text-amber-200">
            Os chamados em andamento deste atendente voltarão para a fila, sem responsável. O histórico de cada um
            registra a devolução.
          </p>
        )}
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variant="outline" onClick={aoCancelar} disabled={enviando}>
            Cancelar
          </Button>
          <Button onClick={salvar} disabled={!mudou || enviando}>
            {enviando ? 'Salvando…' : 'Salvar perfil e situação'}
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
