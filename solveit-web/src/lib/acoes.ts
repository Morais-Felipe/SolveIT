import type { Chamado, StatusChamado, Usuario } from '@/lib/types'

/**
 * O que a tela de detalhe pode oferecer.
 */
export type Acao = 'RESOLVER' | 'FECHAR' | 'CANCELAR' | 'EDITAR' | 'COMENTAR' | 'ASSUMIR' | 'ATRIBUIR'

/** Ações que mudam o status e para qual status levam. */
export const DESTINO: Record<'RESOLVER' | 'FECHAR' | 'CANCELAR', StatusChamado> = {
  RESOLVER: 'RESOLVIDO',
  FECHAR: 'FECHADO',
  CANCELAR: 'CANCELADO',
}

/**
 * Espelha as regras do back-end para decidir quais botões desenhar. É só conforto:
 * o servidor é a autoridade e responde 403/409 se algo aqui ficar desatualizado.
 */
export function acoesDisponiveis(usuario: Usuario, chamado: Chamado): Set<Acao> {
  const acoes = new Set<Acao>()
  const admin = usuario.perfil === 'ADMIN'
  // Exige o perfil SOLICITANTE: um atendente que abriu o chamado não herda esse papel.
  const solicitanteDono = usuario.perfil === 'SOLICITANTE' && chamado.solicitante.id === usuario.id
  const atendenteResponsavel = usuario.perfil === 'ATENDENTE' && chamado.atendente?.id === usuario.id

  // ChamadoTransitionPolicy
  if (chamado.status === 'EM_ANDAMENTO' && (admin || atendenteResponsavel)) acoes.add('RESOLVER')
  if (chamado.status === 'RESOLVIDO' && solicitanteDono) acoes.add('FECHAR')
  if (chamado.status === 'ABERTO' && solicitanteDono) acoes.add('CANCELAR')
  if (chamado.status === 'EM_ANDAMENTO' && admin) acoes.add('CANCELAR')

  // ChamadoService.atribuir: só tira da fila (ABERTO sem responsável); atendente para si, ADMIN para qualquer um
  const naFila = chamado.status === 'ABERTO' && chamado.atendente === null
  if (naFila && usuario.perfil === 'ATENDENTE') acoes.add('ASSUMIR')
  if (naFila && admin) acoes.add('ATRIBUIR')

  // ChamadoService.editar: só ABERTO, pelo solicitante dono ou ADMIN
  if (chamado.status === 'ABERTO' && (admin || solicitanteDono)) acoes.add('EDITAR')

  // ComentarioService: conversa encerrada em FECHADO e CANCELADO (o acesso já foi checado ao abrir)
  if (chamado.status !== 'FECHADO' && chamado.status !== 'CANCELADO') acoes.add('COMENTAR')

  return acoes
}
