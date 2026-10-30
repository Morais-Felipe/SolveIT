import { api } from '@/lib/api'
import type { Categoria, Chamado, Pagina, Prioridade, StatusChamado } from '@/lib/types'

export const ROTULO_STATUS: Record<StatusChamado, string> = {
  ABERTO: 'Aberto',
  EM_ANDAMENTO: 'Em andamento',
  RESOLVIDO: 'Resolvido',
  FECHADO: 'Fechado',
  CANCELADO: 'Cancelado',
}

export const ROTULO_PRIORIDADE: Record<Prioridade, string> = {
  BAIXA: 'Baixa',
  MEDIA: 'Média',
  ALTA: 'Alta',
  CRITICA: 'Crítica',
}

export const STATUS = Object.keys(ROTULO_STATUS) as StatusChamado[]
export const PRIORIDADES = Object.keys(ROTULO_PRIORIDADE) as Prioridade[]

/** Filtros que a lista envia. O escopo por perfil (só os meus chamados) é decidido pela API. */
export type FiltrosChamados = {
  status?: StatusChamado
  prioridade?: Prioridade
  categoriaId?: number
  page: number
}

export const TAMANHO_PAGINA = 10

export async function listarChamados(filtros: FiltrosChamados, signal?: AbortSignal): Promise<Pagina<Chamado>> {
  const resposta = await api.get<Pagina<Chamado>>('/chamados', {
    params: { ...filtros, size: TAMANHO_PAGINA, sort: 'criadoEm,desc' },
    signal,
  })
  return resposta.data
}

/** Exatamente os quatro campos aceitos por ChamadoDtos.Criar; qualquer outro vira 400. */
export type NovoChamado = {
  titulo: string
  descricao: string
  prioridade: Prioridade
  categoriaId: number
}

export async function abrirChamado(dados: NovoChamado): Promise<Chamado> {
  const resposta = await api.post<Chamado>('/chamados', dados)
  return resposta.data
}

/** Categorias ativas para filtros e formulários. 100 é o limite de página da API. */
export async function listarCategoriasAtivas(signal?: AbortSignal): Promise<Categoria[]> {
  const resposta = await api.get<Pagina<Categoria>>('/categorias', { params: { size: 100 }, signal })
  return resposta.data.items
}

/** Formata LocalDateTime (sem fuso) como data local curta, ex.: 30/10/2026. */
export function formatarData(valor: string): string {
  const data = new Date(valor)
  return Number.isNaN(data.getTime()) ? valor : data.toLocaleDateString('pt-BR')
}
