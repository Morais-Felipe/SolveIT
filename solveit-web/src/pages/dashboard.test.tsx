import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '@/App'
import { AuthProvider } from '@/auth/AuthProvider'
import { api, ApiError, tokenStorage } from '@/lib/api'
import type { ResumoDashboard, Usuario } from '@/lib/types'

const ADMIN: Usuario = {
  id: 1,
  nome: 'Ada Admin',
  email: 'ada@example.com',
  perfil: 'ADMIN',
  ativo: true,
  criadoEm: '2026-10-30T10:00:00',
}

const RESUMO: ResumoDashboard = {
  totais: { aberto: 4, emAndamento: 3, resolvido: 9, criticaAberta: 1 },
  porCategoria: [
    { categoriaId: 10, nome: 'Rede', quantidade: 5 },
    { categoriaId: 11, nome: 'Impressoras', quantidade: 2 },
  ],
  cargaPorAtendente: [{ atendenteId: 5, nome: 'Ari Atendente', emAndamento: 3 }],
  abertosPorPrioridade: { BAIXA: 1, MEDIA: 2, ALTA: 0, CRITICA: 1 },
}

function simularApi(usuario: Usuario, resumo: () => Promise<unknown>) {
  return vi.spyOn(api, 'get').mockImplementation(async (url: string) => {
    if (url === '/auth/me') return { data: usuario }
    if (url === '/dashboard/resumo') return resumo()
    if (url === '/chamados') return { data: { items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 } }
    if (url === '/categorias') return { data: { items: [], page: 0, size: 100, totalElements: 0, totalPages: 0 } }
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

beforeEach(() => {
  localStorage.clear()
  tokenStorage.set('token-valido')
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('dashboard', () => {
  it('mostra os totais, as prioridades, as categorias e a carga por atendente', async () => {
    simularApi(ADMIN, async () => ({ data: RESUMO }))
    renderApp('/dashboard')

    const abertos = await screen.findByRole('link', { name: /Abertos\s*4/ })
    expect(abertos).toHaveAttribute('href', '/chamados?status=ABERTO')
    expect(screen.getByRole('link', { name: /Em andamento\s*3/ })).toHaveAttribute('href', '/chamados?status=EM_ANDAMENTO')
    expect(screen.getByRole('link', { name: /Críticos abertos\s*1/ })).toHaveAttribute(
      'href',
      '/chamados?status=ABERTO&prioridade=CRITICA',
    )

    const prioridades = screen.getByRole('table', { name: 'Abertos por prioridade' })
    expect(within(prioridades).getByRole('row', { name: /Média\s*2/ })).toBeInTheDocument()

    const categorias = screen.getByRole('table', { name: 'Chamados por categoria' })
    expect(within(categorias).getByRole('link', { name: 'Rede' })).toHaveAttribute('href', '/chamados?categoriaId=10')

    const carga = screen.getByRole('table', { name: 'Carga por atendente' })
    expect(within(carga).getByRole('row', { name: /Ari Atendente\s*3/ })).toBeInTheDocument()
  })

  it('esconde a carga por atendente quando a API não devolve itens', async () => {
    simularApi(ADMIN, async () => ({ data: { ...RESUMO, cargaPorAtendente: [] } }))
    renderApp('/dashboard')

    await screen.findByRole('table', { name: 'Abertos por prioridade' })
    expect(screen.queryByRole('table', { name: 'Carga por atendente' })).not.toBeInTheDocument()
  })

  it('mostra o erro e tenta de novo', async () => {
    const respostas = [
      () => Promise.reject(new ApiError(0, 'NETWORK_ERROR', 'Não foi possível conectar ao servidor.', {})),
      () => Promise.resolve({ data: RESUMO }),
    ]
    simularApi(ADMIN, () => respostas.shift()!())
    renderApp('/dashboard')

    expect(await screen.findByText('Não foi possível conectar ao servidor.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }))
    expect(await screen.findByRole('link', { name: /Abertos\s*4/ })).toBeInTheDocument()
  })
})
