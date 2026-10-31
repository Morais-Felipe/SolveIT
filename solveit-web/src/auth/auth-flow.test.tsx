import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '@/App'
import { AuthProvider } from '@/auth/AuthProvider'
import { api, ApiError, tokenStorage } from '@/lib/api'
import type { Usuario } from '@/lib/types'

const ADMIN: Usuario = {
  id: 1,
  nome: 'Ana Admin',
  email: 'ana@example.com',
  perfil: 'ADMIN',
  ativo: true,
  criadoEm: '2026-10-30T10:00:00',
}

const SOLICITANTE: Usuario = { ...ADMIN, id: 2, nome: 'Sol', email: 'sol@example.com', perfil: 'SOLICITANTE' }

function renderApp(rota: string): void {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={[rota]}>
      <AuthProvider>{children}</AuthProvider>
    </MemoryRouter>
  )
  render(<App />, { wrapper })
}

const RESUMO_VAZIO = {
  totais: { aberto: 0, emAndamento: 0, resolvido: 0, criticaAberta: 0 },
  porCategoria: [],
  cargaPorAtendente: [],
  abertosPorPrioridade: {},
}

/** /auth/me devolve o usuário; o dashboard e as listas das telas iniciais vêm vazios. */
function respostaMe(usuario: Usuario) {
  return vi.spyOn(api, 'get').mockImplementation(async (url: string) => {
    if (url === '/auth/me') return { data: usuario }
    if (url === '/dashboard/resumo') return { data: RESUMO_VAZIO }
    return { data: { items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 } }
  })
}

beforeEach(() => {
  localStorage.clear()
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('autenticação', () => {
  it('redireciona para o login quem tenta abrir uma tela protegida sem sessão', async () => {
    renderApp('/dashboard')

    expect(await screen.findByRole('heading', { name: /entrar no solveit/i })).toBeInTheDocument()
  })

  it('entra e vai para a tela inicial do perfil', async () => {
    const usuarioEvento = userEvent.setup()
    const post = vi.spyOn(api, 'post').mockResolvedValue({
      data: { accessToken: 'token-123', tokenType: 'Bearer', expiresIn: 3600 },
    })
    respostaMe(ADMIN)
    renderApp('/login')

    await usuarioEvento.type(screen.getByLabelText('E-mail'), 'ana@example.com')
    await usuarioEvento.type(screen.getByLabelText('Senha'), 'senha muito segura 123')
    await usuarioEvento.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(screen.getByText('Ana Admin')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Usuários' })).toBeInTheDocument()
    expect(post).toHaveBeenCalledWith('/auth/login', {
      email: 'ana@example.com',
      senha: 'senha muito segura 123',
    })
    expect(tokenStorage.get()).toBe('token-123')
  })

  it('mostra a mensagem da API quando as credenciais são inválidas', async () => {
    const usuarioEvento = userEvent.setup()
    vi.spyOn(api, 'post').mockRejectedValue(
      new ApiError(401, 'AUTH_CREDENTIALS_INVALID', 'E-mail ou senha inválidos.', {}),
    )
    renderApp('/login')

    await usuarioEvento.type(screen.getByLabelText('E-mail'), 'ana@example.com')
    await usuarioEvento.type(screen.getByLabelText('Senha'), 'senha errada')
    await usuarioEvento.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('E-mail ou senha inválidos.')
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeEnabled()
    expect(tokenStorage.get()).toBeNull()
  })

  it('valida a senha curta no cadastro sem chamar a API', async () => {
    const usuarioEvento = userEvent.setup()
    const post = vi.spyOn(api, 'post')
    renderApp('/cadastro')

    await usuarioEvento.type(screen.getByLabelText('Nome'), 'Sol')
    await usuarioEvento.type(screen.getByLabelText('E-mail'), 'sol@example.com')
    await usuarioEvento.type(screen.getByLabelText('Senha'), 'curta')
    await usuarioEvento.click(screen.getByRole('button', { name: 'Criar conta' }))

    expect(screen.getByText(/pelo menos 15 caracteres/)).toBeInTheDocument()
    expect(screen.getByLabelText('Senha')).toHaveAttribute('aria-invalid', 'true')
    expect(post).not.toHaveBeenCalled()
  })

  it('mostra o erro de e-mail duplicado vindo da API', async () => {
    const usuarioEvento = userEvent.setup()
    vi.spyOn(api, 'post').mockRejectedValue(
      new ApiError(409, 'EMAIL_ALREADY_REGISTERED', 'E-mail já cadastrado', {}),
    )
    renderApp('/cadastro')

    await usuarioEvento.type(screen.getByLabelText('Nome'), 'Sol')
    await usuarioEvento.type(screen.getByLabelText('E-mail'), 'sol@example.com')
    await usuarioEvento.type(screen.getByLabelText('Senha'), 'senha muito segura 123')
    await usuarioEvento.click(screen.getByRole('button', { name: 'Criar conta' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('E-mail já cadastrado')
  })

  it('restaura a sessão a partir do token salvo', async () => {
    tokenStorage.set('token-salvo')
    const get = respostaMe(SOLICITANTE)
    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Meus chamados' })).toBeInTheDocument()
    expect(screen.getByText('Sol')).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith('/auth/me')
  })

  it('descarta um token que a API não aceita mais', async () => {
    tokenStorage.set('token-expirado')
    vi.spyOn(api, 'get').mockRejectedValue(
      new ApiError(401, 'AUTH_TOKEN_EXPIRED', 'Sessão expirada.', {}),
    )
    renderApp('/chamados')

    expect(await screen.findByRole('heading', { name: /entrar no solveit/i })).toBeInTheDocument()
    expect(tokenStorage.get()).toBeNull()
  })

  it('sai e limpa a sessão', async () => {
    const usuarioEvento = userEvent.setup()
    tokenStorage.set('token-salvo')
    respostaMe(ADMIN)
    renderApp('/dashboard')

    const [botaoSair] = await screen.findAllByRole('button', { name: 'Sair' })
    await usuarioEvento.click(botaoSair)

    expect(await screen.findByRole('heading', { name: /entrar no solveit/i })).toBeInTheDocument()
    expect(tokenStorage.get()).toBeNull()
  })

  it('não mostra ao solicitante uma tela de administrador', async () => {
    tokenStorage.set('token-salvo')
    respostaMe(SOLICITANTE)
    renderApp('/usuarios')

    expect(await screen.findByRole('heading', { name: 'Meus chamados' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Usuários' })).not.toBeInTheDocument()
  })
})
