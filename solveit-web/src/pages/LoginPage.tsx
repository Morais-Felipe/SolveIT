import { LogIn, Mail } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { rotaInicial, useAuth } from '@/auth/useAuth'
import { CampoSenha } from '@/components/CampoSenha'
import { CampoTexto } from '@/components/CampoTexto'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { ShinyButton } from '@/components/ui/shiny-button'
import { AutenticacaoLayout } from '@/layout/AutenticacaoLayout'
import { toApiError } from '@/lib/api'

export function LoginPage() {
  const { entrar } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  async function aoEnviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    if (!email.trim() || !senha) {
      setErro('Informe e-mail e senha.')
      return
    }
    setErro(null)
    setEnviando(true)
    try {
      const usuario = await entrar(email, senha)
      const destino = (location.state as { de?: string } | null)?.de
      navigate(destino ?? rotaInicial(usuario.perfil), { replace: true })
    } catch (falha) {
      setErro(toApiError(falha).message)
      setEnviando(false)
    }
  }

  return (
    <AutenticacaoLayout>
        <CardHeader className="text-center">
          <CardTitle>
            <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">
              Entrar no Solve<span className="text-primary">IT</span>
            </h1>
          </CardTitle>
          <CardDescription>Acompanhe e resolva chamados de suporte.</CardDescription>
        </CardHeader>
        <form onSubmit={aoEnviar} noValidate>
          <CardContent className="grid gap-4">
            {erro && (
              <Alert variant="destructive">
                <AlertDescription>{erro}</AlertDescription>
              </Alert>
            )}
            <CampoTexto
              id="email"
              rotulo="E-mail"
              type="email"
              icone={Mail}
              className="h-12 rounded-xl px-4"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
            <CampoSenha
              id="senha"
              rotulo="Senha"
              className="h-12 rounded-xl px-4"
              autoComplete="current-password"
              value={senha}
              onChange={(e) => setSenha(e.target.value)}
            />
          </CardContent>
          <CardFooter className="mt-5 flex flex-col gap-4 border-0 bg-transparent pt-0">
            <ShinyButton icone={LogIn} carregando={enviando} type="submit" className="w-full" disabled={enviando}>
              {enviando ? 'Entrando…' : 'Entrar'}
            </ShinyButton>
            <div className="flex w-full items-center gap-3 text-sm text-muted-foreground">
              <span aria-hidden="true" className="h-px flex-1 bg-border" />
              <p>
                Não tem conta?{' '}
                <Link to="/cadastro" className="text-primary underline-offset-4 hover:underline">
                  Cadastre-se
                </Link>
              </p>
              <span aria-hidden="true" className="h-px flex-1 bg-border" />
            </div>
          </CardFooter>
        </form>
    </AutenticacaoLayout>
  )
}
