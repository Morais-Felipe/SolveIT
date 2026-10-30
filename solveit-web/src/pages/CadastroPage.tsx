import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { rotaInicial, useAuth } from '@/auth/useAuth'
import { CampoTexto } from '@/components/CampoTexto'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
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
    <div className="flex min-h-svh items-center justify-center bg-background p-4 text-foreground">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>
            <h1 className="text-xl">Criar conta</h1>
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
              autoComplete="name"
              value={nome}
              erro={erros.nome}
              onChange={(e) => setNome(e.target.value)}
            />
            <CampoTexto
              id="email"
              rotulo="E-mail"
              type="email"
              autoComplete="email"
              value={email}
              erro={erros.email}
              onChange={(e) => setEmail(e.target.value)}
            />
            <CampoTexto
              id="senha"
              rotulo="Senha"
              type="password"
              autoComplete="new-password"
              value={senha}
              erro={erros.senha}
              ajuda={`Mínimo de ${SENHA_MINIMA} caracteres. Uma frase longa é mais fácil de lembrar.`}
              onChange={(e) => setSenha(e.target.value)}
            />
          </CardContent>
          <CardFooter className="mt-4 flex flex-col gap-3">
            <Button type="submit" className="w-full" disabled={enviando}>
              {enviando ? 'Criando conta…' : 'Criar conta'}
            </Button>
            <p className="text-sm text-muted-foreground">
              Já tem conta?{' '}
              <Link to="/login" className="text-primary underline-offset-4 hover:underline">
                Entrar
              </Link>
            </p>
          </CardFooter>
        </form>
      </Card>
    </div>
  )
}
