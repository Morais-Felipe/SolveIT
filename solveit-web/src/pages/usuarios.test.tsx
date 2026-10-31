import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '@/App'
import { AuthProvider } from '@/auth/AuthProvider'
import { api, ApiError, tokenStorage } from '@/lib/api'
import type { Pagina, UsuarioAdmin } from '@/lib/types'

function usuario(id: number, nome: string, extra: Partial<UsuarioAdmin> = {}): UsuarioAdmin {
  return {
    id,
    nome,
    email: `${nome.split(' ')[0].toLowerCase()}@example.com`,
    perfil: 'SOLICITANTE',
    ativo: true,
    criadoEm: '2026-10-30T10:00:00',
    versao: 2,
    ...extra,
  }
}

const ADMIN = usuario(1, 'Ada Admin', { perfil: 'ADMIN' })
const ARI = usuario(5, 'Ari Atendente', { perfil: 'ATENDENTE' })
const SOL = usuario(7, 'Sol Solicitante')

function pagina(items: UsuarioAdmin[]): Pagina<UsuarioAdmin> {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1 }
}

function simularLista(items: UsuarioAdmin[] = [ADMIN, ARI, SOL]) {
  return vi.spyOn(api, 'get').mockImplementation(async (url: string) => {
    if (url === '/auth/me') return { data: ADMIN }
    if (url === '/usuarios') return { data: pagina(items) }
    throw new Error(`GET inesperado: ${url}`)
  })
}

function renderApp(rota: string): void {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={[rota]}>
      <AuthProvider>{children}</AuthProvider>
    </MemoryRouter>
  )
  render(<App />, { wrapper })
}

/** Linha da lista com esse nome (o nome do ADMIN logado também aparece no cabeçalho). */
async function linhaDe(nome: string) {
  return waitFor(() => {
    const linha = screen.queryAllByRole('listitem').find((li) => li.textContent?.includes(nome))
    if (!linha) throw new Error(`Linha de ${nome} não encontrada`)
    return linha
  })
}

beforeEach(() => {
  localStorage.clear()
  tokenStorage.set('token-valido')
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('administração de usuários', () => {
  it('lista os usuários com perfil e situação', async () => {
    const get = simularLista()
    renderApp('/usuarios')

    const ari = await linhaDe('Ari Atendente')
    expect(within(ari).getByText('Atendente')).toBeInTheDocument()
    expect(within(ari).getByText('Ativo')).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith('/usuarios', expect.objectContaining({ params: { page: 0, size: 20 } }))
  })

  it('cria um usuário com o perfil escolhido', async () => {
    simularLista()
    const post = vi.spyOn(api, 'post').mockResolvedValue({ data: usuario(9, 'Novo Atendente', { perfil: 'ATENDENTE' }) })
    renderApp('/usuarios')

    await userEvent.click(await screen.findByRole('button', { name: 'Novo usuário' }))
    await userEvent.type(screen.getByLabelText('Nome'), 'Novo Atendente')
    await userEvent.type(screen.getByLabelText('E-mail'), 'novo@example.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'uma senha bem longa')
    await userEvent.selectOptions(screen.getByLabelText('Perfil'), 'ATENDENTE')
    await userEvent.click(screen.getByRole('button', { name: 'Criar usuário' }))

    expect(post).toHaveBeenCalledWith('/usuarios', {
      nome: 'Novo Atendente',
      email: 'novo@example.com',
      senha: 'uma senha bem longa',
      perfil: 'ATENDENTE',
    })
    expect(await screen.findByText('Usuário Novo Atendente criado.')).toBeInTheDocument()
  })

  it('mostra e-mail repetido no próprio campo', async () => {
    simularLista()
    vi.spyOn(api, 'post').mockRejectedValue(
      new ApiError(409, 'EMAIL_ALREADY_REGISTERED', 'Este e-mail já está cadastrado.', {}),
    )
    renderApp('/usuarios')

    await userEvent.click(await screen.findByRole('button', { name: 'Novo usuário' }))
    await userEvent.type(screen.getByLabelText('Nome'), 'Repetido')
    await userEvent.type(screen.getByLabelText('E-mail'), 'sol@example.com')
    await userEvent.type(screen.getByLabelText('Senha'), 'uma senha bem longa')
    await userEvent.click(screen.getByRole('button', { name: 'Criar usuário' }))

    expect(await screen.findByText('Este e-mail já está cadastrado.')).toBeInTheDocument()
    expect(screen.getByLabelText('E-mail')).toHaveAttribute('aria-invalid', 'true')
  })

  it('edita só os dados pessoais, com a versão lida', async () => {
    simularLista()
    const put = vi.spyOn(api, 'put').mockResolvedValue({ data: { ...SOL, nome: 'Sol Souza', versao: 3 } })
    renderApp('/usuarios')

    await userEvent.click(within(await linhaDe('Sol Solicitante')).getByRole('button', { name: 'Editar dados' }))
    const nome = screen.getByLabelText('Nome')
    await userEvent.clear(nome)
    await userEvent.type(nome, 'Sol Souza')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar dados' }))

    expect(put).toHaveBeenCalledWith('/usuarios/7', {
      nome: 'Sol Souza',
      email: 'sol@example.com',
      senha: undefined,
      versaoEsperada: 2,
    })
    expect(await screen.findByText('Dados de Sol Souza atualizados.')).toBeInTheDocument()
  })

  it('avisa que desativar o atendente devolve os chamados e mostra quantos voltaram', async () => {
    simularLista()
    const patch = vi.spyOn(api, 'patch').mockResolvedValue({
      data: { usuario: { ...ARI, ativo: false, versao: 3 }, chamadosDevolvidos: 2 },
    })
    renderApp('/usuarios')

    await userEvent.click(within(await linhaDe('Ari Atendente')).getByRole('button', { name: 'Perfil e situação' }))
    const salvar = screen.getByRole('button', { name: 'Salvar perfil e situação' })
    expect(salvar).toBeDisabled()
    await userEvent.selectOptions(screen.getByLabelText('Situação'), 'inativo')
    expect(screen.getByText(/chamados em andamento deste atendente voltarão para a fila/)).toBeInTheDocument()
    await userEvent.click(salvar)

    expect(patch).toHaveBeenCalledWith('/usuarios/5/operacao', { perfil: 'ATENDENTE', ativo: false, versaoEsperada: 2 })
    expect(await screen.findByText(/2 chamados voltaram para a fila/)).toBeInTheDocument()
  })

  it('explica o bloqueio do último administrador', async () => {
    const outroAdmin = usuario(3, 'Bia Admin', { perfil: 'ADMIN' })
    simularLista([ADMIN, outroAdmin])
    vi.spyOn(api, 'patch').mockRejectedValue(
      new ApiError(409, 'LAST_ACTIVE_ADMIN', 'O sistema precisa manter ao menos um administrador ativo.', {}),
    )
    renderApp('/usuarios')

    await userEvent.click(within(await linhaDe('Bia Admin')).getByRole('button', { name: 'Perfil e situação' }))
    await userEvent.selectOptions(screen.getByLabelText('Perfil'), 'ATENDENTE')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar perfil e situação' }))

    expect(await screen.findByText('O sistema precisa manter ao menos um administrador ativo.')).toBeInTheDocument()
  })

  it('recarrega a lista quando outra pessoa alterou o usuário antes', async () => {
    const get = simularLista()
    vi.spyOn(api, 'put').mockRejectedValue(new ApiError(409, 'STALE_RESOURCE', 'Versão desatualizada.', {}))
    renderApp('/usuarios')

    await userEvent.click(within(await linhaDe('Sol Solicitante')).getByRole('button', { name: 'Editar dados' }))
    await userEvent.click(screen.getByRole('button', { name: 'Salvar dados' }))

    expect(await screen.findByText(/Outra pessoa alterou este registro/)).toBeInTheDocument()
    expect(get.mock.calls.filter(([url]) => url === '/usuarios')).toHaveLength(2)
  })

  it('não oferece perfil e situação para o próprio administrador logado', async () => {
    simularLista()
    renderApp('/usuarios')

    const propria = await linhaDe('Ada Admin')
    expect(within(propria).getByRole('button', { name: 'Editar dados' })).toBeInTheDocument()
    expect(within(propria).queryByRole('button', { name: 'Perfil e situação' })).not.toBeInTheDocument()
    expect(within(propria).getByText('(você)')).toBeInTheDocument()
  })
})
