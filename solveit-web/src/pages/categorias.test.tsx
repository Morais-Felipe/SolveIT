import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '@/App'
import { AuthProvider } from '@/auth/AuthProvider'
import { api, ApiError, tokenStorage } from '@/lib/api'
import type { Categoria, Pagina, Usuario } from '@/lib/types'

const ADMIN: Usuario = {
  id: 1,
  nome: 'Ada Admin',
  email: 'ada@example.com',
  perfil: 'ADMIN',
  ativo: true,
  criadoEm: '2026-10-30T10:00:00',
}

const REDE: Categoria = { id: 10, nome: 'Rede', descricao: 'Wi-Fi e cabos', ativo: true, versao: 1 }
const IMPRESSORAS: Categoria = { id: 11, nome: 'Impressoras', descricao: null, ativo: false, versao: 4 }

function pagina(items: Categoria[]): Pagina<Categoria> {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1 }
}

function simularLista() {
  return vi.spyOn(api, 'get').mockImplementation(async (url, config) => {
    if (url === '/auth/me') return { data: ADMIN }
    if (url === '/categorias') {
      const params = config?.params as { incluirInativas?: boolean } | undefined
      return { data: pagina(params?.incluirInativas ? [IMPRESSORAS, REDE] : [REDE]) }
    }
    throw new Error(`GET inesperado: ${url}`)
  })
}

function parametrosDasListas(get: ReturnType<typeof simularLista>) {
  return get.mock.calls
    .filter(([url]) => url === '/categorias')
    .map(([, config]) => (config as { params: Record<string, unknown> }).params)
}

function renderApp(rota: string): void {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={[rota]}>
      <AuthProvider>{children}</AuthProvider>
    </MemoryRouter>
  )
  render(<App />, { wrapper })
}

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

describe('administração de categorias', () => {
  it('mostra só as ativas e, na aba Todas, pede também as inativas', async () => {
    const get = simularLista()
    renderApp('/categorias')

    await linhaDe('Rede')
    expect(screen.queryByText('Impressoras')).not.toBeInTheDocument()
    expect(parametrosDasListas(get).at(-1)?.incluirInativas).toBeUndefined()

    await userEvent.click(screen.getByRole('tab', { name: 'Todas' }))
    const impressoras = await linhaDe('Impressoras')
    expect(within(impressoras).getByText('Inativa')).toBeInTheDocument()
    expect(parametrosDasListas(get).at(-1)).toMatchObject({ incluirInativas: true })
  })

  it('cria uma categoria', async () => {
    simularLista()
    const post = vi.spyOn(api, 'post').mockResolvedValue({ data: { id: 12, nome: 'Telefonia', descricao: null, ativo: true, versao: 0 } })
    renderApp('/categorias')

    await userEvent.click(await screen.findByRole('button', { name: 'Nova categoria' }))
    await userEvent.type(screen.getByLabelText('Nome'), '  Telefonia ')
    await userEvent.click(screen.getByRole('button', { name: 'Criar categoria' }))

    expect(post).toHaveBeenCalledWith('/categorias', { nome: 'Telefonia', descricao: undefined })
    expect(await screen.findByText('Categoria Telefonia criada.')).toBeInTheDocument()
  })

  it('mostra nome repetido no próprio campo', async () => {
    simularLista()
    vi.spyOn(api, 'put').mockRejectedValue(
      new ApiError(409, 'CATEGORY_NAME_ALREADY_EXISTS', 'Já existe uma categoria com esse nome.', {}),
    )
    renderApp('/categorias')

    await userEvent.click(within(await linhaDe('Rede')).getByRole('button', { name: 'Editar' }))
    await userEvent.clear(screen.getByLabelText('Nome'))
    await userEvent.type(screen.getByLabelText('Nome'), 'Impressoras')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar categoria' }))

    expect(await screen.findByText('Já existe uma categoria com esse nome.')).toBeInTheDocument()
    expect(screen.getByLabelText('Nome')).toHaveAttribute('aria-invalid', 'true')
  })

  it('reativa uma categoria inativa enviando a versão lida', async () => {
    simularLista()
    const patch = vi.spyOn(api, 'patch').mockResolvedValue({ data: { ...IMPRESSORAS, ativo: true, versao: 5 } })
    renderApp('/categorias?visao=todas')

    await userEvent.click(within(await linhaDe('Impressoras')).getByRole('button', { name: 'Ativar' }))

    expect(patch).toHaveBeenCalledWith('/categorias/11/ativo', { ativo: true, versaoEsperada: 4 })
    expect(await screen.findByText('Categoria Impressoras ativada.')).toBeInTheDocument()
  })

  it('recarrega quando a categoria mudou antes', async () => {
    const get = simularLista()
    vi.spyOn(api, 'patch').mockRejectedValue(new ApiError(409, 'STALE_RESOURCE', 'Versão desatualizada.', {}))
    renderApp('/categorias')

    await userEvent.click(within(await linhaDe('Rede')).getByRole('button', { name: 'Desativar' }))

    expect(await screen.findByText(/Outra pessoa alterou este registro/)).toBeInTheDocument()
    expect(parametrosDasListas(get)).toHaveLength(2)
  })
})
