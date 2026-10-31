// Tipos que espelham os DTOs da API Spring (pacote com.solveit.dto).
// Se um contrato mudar no back-end, este é o lugar a atualizar.

export type Perfil = 'SOLICITANTE' | 'ATENDENTE' | 'ADMIN'

/** Espelha UsuarioResponse (GET /api/auth/me, POST /api/auth/register). */
export type Usuario = {
  id: number
  nome: string
  email: string
  perfil: Perfil
  ativo: boolean
  criadoEm: string
}

/** Espelha TokenResponse (POST /api/auth/login). */
export type TokenResponse = {
  accessToken: string
  tokenType: string
  expiresIn: number
}

/** Corpo de erro da API (RFC 9457 Problem Details + campos do SolveIT). */
export type ProblemDetail = {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  code?: string
  errors?: Record<string, string>
}

/** Espelha o enum StatusChamado (serializado pelo nome). */
export type StatusChamado = 'ABERTO' | 'EM_ANDAMENTO' | 'RESOLVIDO' | 'FECHADO' | 'CANCELADO'

/** Espelha o enum Prioridade. */
export type Prioridade = 'BAIXA' | 'MEDIA' | 'ALTA' | 'CRITICA'

/** Espelha ChamadoDtos.Pessoa. */
export type Pessoa = { id: number; nome: string }

/** Espelha ChamadoDtos.Detalhe. Datas chegam em UTC sem fuso (ex.: 2026-10-30T10:00:00); use lerDataDaApi. */
export type Chamado = {
  id: number
  titulo: string
  descricao: string
  status: StatusChamado
  prioridade: Prioridade
  solicitante: Pessoa
  atendente: Pessoa | null
  categoria: { id: number; nome: string; ativo: boolean }
  criadoEm: string
  atualizadoEm: string
  resolvidoEm: string | null
  versao: number
}

/** Formato comum das listas paginadas da API (ChamadoDtos.Pagina, CategoriaDtos.Pagina...). */
export type Pagina<T> = {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** Espelha CategoriaDtos.Resposta. */
export type Categoria = {
  id: number
  nome: string
  descricao: string | null
  ativo: boolean
  versao: number
}

/** Espelha UsuarioDtos.Resposta (rotas de administração): traz a versão para o bloqueio otimista. */
export type UsuarioAdmin = Usuario & { versao: number }

/** Espelha UsuarioDtos.ResultadoOperacao: quantos chamados em andamento voltaram para a fila. */
export type ResultadoOperacao = {
  usuario: UsuarioAdmin
  chamadosDevolvidos: number
}

/** Espelha DashboardDtos.Resumo. O escopo (tudo para o ADMIN, o próprio para o atendente) é da API. */
export type ResumoDashboard = {
  totais: { aberto: number; emAndamento: number; resolvido: number; criticaAberta: number }
  porCategoria: { categoriaId: number; nome: string; quantidade: number }[]
  cargaPorAtendente: { atendenteId: number; nome: string; emAndamento: number }[]
  abertosPorPrioridade: Partial<Record<Prioridade, number>>
}

/** Espelha ComentarioDtos.Resposta. Comentários são imutáveis: não há edição nem exclusão. */
export type Comentario = {
  id: number
  texto: string
  autor: Pessoa
  criadoEm: string
}

/** Espelha HistoricoStatusDtos.Resposta. O registro de abertura tem statusAnterior nulo. */
export type HistoricoStatus = {
  id: number
  statusAnterior: StatusChamado | null
  statusNovo: StatusChamado
  alteradoPor: Pessoa
  alteradoEm: string
  observacao: string | null
}
