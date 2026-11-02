import { Mail, User, UserPlus } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { rotaInicial, useAuth } from '@/auth/useAuth'
import { CampoSenha } from '@/components/CampoSenha'
import { CampoTexto } from '@/components/CampoTexto'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { ShinyButton } from '@/components/ui/shiny-button'
import { AutenticacaoLayout } from '@/layout/AutenticacaoLayout'
import { toApiError } from '@/lib/api'

/** Mesma regra do back-end (@ValidPassword); a API continua sendo quem decide. */
const SENHA_MINIMA = 15

type Erros = Partial<Record<'nome' | 'email' | 'senha', string>>

export function CadastroPage() {
  const { cadastrar } = useAuth()
  const navigate = useNavigate()
  const [nome, setNome] = useState('')
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erros, setErros] = useState<Erros>({})
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  function validar(): Erros {
    const encontrados: Erros = {}
    if (!nome.trim()) encontrados.nome = 'Informe seu nome.'
    if (!email.trim()) encontrados.email = 'Informe seu e-mail.'
    if ([...senha].length < SENHA_MINIMA) {
      encontrados.senha = `A senha precisa de pelo menos ${SENHA_MINIMA} caracteres.`
    }
    return encontrados
  }

  async function aoEnviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    const locais = validar()
    setErros(locais)
    setErroGeral(null)
    if (Object.keys(locais).length > 0) {
      return
    }
    setEnviando(true)
    try {
      const usuario = await cadastrar({ nome, email, senha })
      navigate(rotaInicial(usuario.perfil), { replace: true })
    } catch (falha) {
      const apiError = toApiError(falha)
      setErros(apiError.fieldErrors)
      if (Object.keys(apiError.fieldErrors).length === 0) {
        setErroGeral(apiError.message)
      }
      setEnviando(false)
    }
  }

  return (
    <AutenticacaoLayout>
        <CardHeader className="text-center">
          <CardTitle>
            <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">Criar conta</h1>
          </CardTitle>
          <CardDescription>Contas novas entram como solicitante.</CardDescription>
        </CardHeader>
        <form onSubmit={aoEnviar} noValidate>
          <CardContent className="grid gap-4">
            {erroGeral && (
              <Alert variant="destructive">
                <AlertDescription>{erroGeral}</AlertDescription>
              </Alert>
            )}
            <CampoTexto
              id="nome"
              rotulo="Nome"
              icone={User}
              className="h-12 rounded-xl px-4"
              autoComplete="name"
              value={nome}
              erro={erros.nome}
              onChange={(e) => setNome(e.target.value)}
            />
            <CampoTexto
              id="email"
              rotulo="E-mail"
              type="email"
              icone={Mail}
              className="h-12 rounded-xl px-4"
              autoComplete="email"
              value={email}
              erro={erros.email}
              onChange={(e) => setEmail(e.target.value)}
            />
            <CampoSenha
              id="senha"
              rotulo="Senha"
              className="h-12 rounded-xl px-4"
              autoComplete="new-password"
              value={senha}
              erro={erros.senha}
              ajuda={`Mínimo de ${SENHA_MINIMA} caracteres. Uma frase longa é mais fácil de lembrar.`}
              onChange={(e) => setSenha(e.target.value)}
            />
          </CardContent>
          <CardFooter className="mt-5 flex flex-col gap-4 border-0 bg-transparent pt-0">
            <ShinyButton icone={UserPlus} carregando={enviando} type="submit" className="w-full" disabled={enviando}>
              {enviando ? 'Criando conta…' : 'Criar conta'}
            </ShinyButton>
            <div className="flex w-full items-center gap-3 text-sm text-muted-foreground">
              <span aria-hidden="true" className="h-px flex-1 bg-border" />
              <p>
                Já tem conta?{' '}
                <Link to="/login" className="text-primary underline-offset-4 hover:underline">
                  Entrar
                </Link>
              </p>
              <span aria-hidden="true" className="h-px flex-1 bg-border" />
            </div>
          </CardFooter>
        </form>
    </AutenticacaoLayout>
  )
}
