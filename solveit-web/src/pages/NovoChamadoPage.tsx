import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { CampoSelecao } from '@/components/CampoSelecao'
import { CampoTexto } from '@/components/CampoTexto'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardFooter } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { toApiError } from '@/lib/api'
import { abrirChamado, listarCategoriasAtivas, PRIORIDADES, ROTULO_PRIORIDADE } from '@/lib/chamados'
import type { Categoria, Prioridade } from '@/lib/types'
import { cn } from '@/lib/utils'

// Mesmos limites de ChamadoDtos.Criar; a validação que vale é a do servidor.
const MAX_TITULO = 200
const MAX_DESCRICAO = 10_000

type Erros = Partial<Record<'titulo' | 'descricao' | 'prioridade' | 'categoriaId', string>>

export function NovoChamadoPage() {
  const navigate = useNavigate()
  const [titulo, setTitulo] = useState('')
  const [descricao, setDescricao] = useState('')
  const [prioridade, setPrioridade] = useState<Prioridade>('MEDIA')
  const [categoriaId, setCategoriaId] = useState('')
  const [categorias, setCategorias] = useState<Categoria[] | null>(null)
  const [erros, setErros] = useState<Erros>({})
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    const controle = new AbortController()
    listarCategoriasAtivas(controle.signal)
      .then(setCategorias)
      .catch((falha: unknown) => {
        if (!controle.signal.aborted) {
          setCategorias([])
          setErroGeral(toApiError(falha).message)
        }
      })
    return () => controle.abort()
  }, [])

  /** Atualiza um campo e apaga só o erro dele: a mensagem some assim que a pessoa corrige. */
  function alterar(campo: keyof Erros, definir: () => void) {
    definir()
    setErros((atuais) => {
      if (!atuais[campo]) return atuais
      const restantes = { ...atuais }
      delete restantes[campo]
      return restantes
    })
  }

  function validar(): Erros {
    const encontrados: Erros = {}
    if (!titulo.trim()) encontrados.titulo = 'Informe o título.'
    if (!descricao.trim()) encontrados.descricao = 'Descreva o problema.'
    if (!categoriaId) encontrados.categoriaId = 'Escolha a categoria.'
    return encontrados
  }

  async function aoEnviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    const encontrados = validar()
    setErros(encontrados)
    setErroGeral(null)
    if (Object.keys(encontrados).length > 0) {
      return
    }
    setEnviando(true)
    try {
      const criado = await abrirChamado({
        titulo: titulo.trim(),
        descricao,
        prioridade,
        categoriaId: Number(categoriaId),
      })
      navigate('/chamados', { replace: true, state: { aviso: `Chamado #${criado.id} aberto com sucesso.` } })
    } catch (falha) {
      const erro = toApiError(falha)
      setErros(erro.fieldErrors)
      // Sem erro por campo, a mensagem geral explica o problema.
      setErroGeral(Object.keys(erro.fieldErrors).length > 0 ? null : erro.message)
      setEnviando(false)
    }
  }

  return (
    <section className="grid max-w-2xl gap-6">
      <header className="grid gap-1">
        <h1 className="text-2xl font-semibold">Abrir chamado</h1>
        <p className="text-sm text-muted-foreground">Descreva o problema; um atendente assume em seguida.</p>
      </header>

      <Card>
        <form onSubmit={aoEnviar} noValidate>
          <CardContent className="grid gap-4">
            {erroGeral && (
              <Alert variant="destructive">
                <AlertDescription>{erroGeral}</AlertDescription>
              </Alert>
            )}

            <CampoTexto
              id="titulo"
              rotulo="Título"
              maxLength={MAX_TITULO}
              value={titulo}
              onChange={(e) => alterar('titulo', () => setTitulo(e.target.value))}
              erro={erros.titulo}
              ajuda="Resumo em uma frase, até 200 caracteres."
            />

            <div className="grid gap-1.5">
              <Label htmlFor="descricao">Descrição</Label>
              <textarea
                id="descricao"
                rows={6}
                maxLength={MAX_DESCRICAO}
                value={descricao}
                onChange={(e) => alterar('descricao', () => setDescricao(e.target.value))}
                aria-invalid={erros.descricao ? true : undefined}
                aria-describedby={erros.descricao ? 'descricao-erro' : 'descricao-contador'}
                className={cn(
                  'w-full min-w-0 rounded-lg border border-input bg-transparent px-2.5 py-2 text-base outline-none transition-colors placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 md:text-sm dark:bg-input/30',
                )}
              />
              {erros.descricao ? (
                <p id="descricao-erro" className="text-xs text-destructive">
                  {erros.descricao}
                </p>
              ) : (
                <p id="descricao-contador" className="text-right text-xs text-muted-foreground">
                  {descricao.length.toLocaleString('pt-BR')} / {MAX_DESCRICAO.toLocaleString('pt-BR')}
                </p>
              )}
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <CampoSelecao
                id="prioridade"
                rotulo="Prioridade"
                value={prioridade}
                onChange={(e) => alterar('prioridade', () => setPrioridade(e.target.value as Prioridade))}
                erro={erros.prioridade}
              >
                {PRIORIDADES.map((p) => (
                  <option key={p} value={p}>
                    {ROTULO_PRIORIDADE[p]}
                  </option>
                ))}
              </CampoSelecao>
              <CampoSelecao
                id="categoria"
                rotulo="Categoria"
                value={categoriaId}
                onChange={(e) => alterar('categoriaId', () => setCategoriaId(e.target.value))}
                erro={erros.categoriaId}
                disabled={categorias === null}
              >
                <option value="">{categorias === null ? 'Carregando…' : 'Selecione'}</option>
                {categorias?.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.nome}
                  </option>
                ))}
              </CampoSelecao>
            </div>
          </CardContent>
          <CardFooter className="mt-4 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
            <Link to="/chamados" className={cn(buttonVariants({ variant: 'outline' }), 'w-full sm:w-auto')}>
              Cancelar
            </Link>
            <Button type="submit" className="w-full sm:w-auto" disabled={enviando}>
              {enviando ? 'Enviando…' : 'Abrir chamado'}
            </Button>
          </CardFooter>
        </form>
      </Card>
    </section>
  )
}
