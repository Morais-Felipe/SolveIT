import { describe, expect, it } from 'vitest'
import { acoesDisponiveis, type Acao } from '@/lib/acoes'
import type { Chamado, Perfil, StatusChamado, Usuario } from '@/lib/types'

const DONO = 10
const RESPONSAVEL = 20
const OUTRO = 30

function usuario(id: number, perfil: Perfil): Usuario {
  return { id, nome: `U${id}`, email: `u${id}@example.com`, perfil, ativo: true, criadoEm: '2026-10-30T10:00:00' }
}

function chamado(status: StatusChamado, comResponsavel = status !== 'ABERTO'): Chamado {
  return {
    id: 1,
    titulo: 'T',
    descricao: 'D',
    status,
    prioridade: 'MEDIA',
    solicitante: { id: DONO, nome: 'Dono' },
    atendente: comResponsavel ? { id: RESPONSAVEL, nome: 'Resp' } : null,
    categoria: { id: 1, nome: 'Rede', ativo: true },
    criadoEm: '2026-10-30T10:00:00',
    atualizadoEm: '2026-10-30T10:00:00',
    resolvidoEm: null,
    versao: 0,
  }
}

// Espelho de ChamadoTransitionPolicy, ChamadoService.editar e ComentarioService.
// Cada linha: quem, status do chamado, ações esperadas (em ordem alfabética).
const CENARIOS: [string, Usuario, StatusChamado, Acao[]][] = [
  ['solicitante dono, aberto', usuario(DONO, 'SOLICITANTE'), 'ABERTO', ['CANCELAR', 'COMENTAR', 'EDITAR']],
  ['solicitante dono, em andamento', usuario(DONO, 'SOLICITANTE'), 'EM_ANDAMENTO', ['COMENTAR']],
  ['solicitante dono, resolvido', usuario(DONO, 'SOLICITANTE'), 'RESOLVIDO', ['COMENTAR', 'FECHAR']],
  ['solicitante dono, fechado', usuario(DONO, 'SOLICITANTE'), 'FECHADO', []],
  ['solicitante dono, cancelado', usuario(DONO, 'SOLICITANTE'), 'CANCELADO', []],
  ['atendente responsável, em andamento', usuario(RESPONSAVEL, 'ATENDENTE'), 'EM_ANDAMENTO', ['COMENTAR', 'RESOLVER']],
  ['atendente responsável, resolvido', usuario(RESPONSAVEL, 'ATENDENTE'), 'RESOLVIDO', ['COMENTAR']],
  ['atendente na fila (aberto, sem responsável)', usuario(OUTRO, 'ATENDENTE'), 'ABERTO', ['COMENTAR']],
  ['outro atendente, em andamento', usuario(OUTRO, 'ATENDENTE'), 'EM_ANDAMENTO', ['COMENTAR']],
  ['admin, aberto', usuario(OUTRO, 'ADMIN'), 'ABERTO', ['COMENTAR', 'EDITAR']],
  ['admin, em andamento', usuario(OUTRO, 'ADMIN'), 'EM_ANDAMENTO', ['CANCELAR', 'COMENTAR', 'RESOLVER']],
  ['admin, resolvido', usuario(OUTRO, 'ADMIN'), 'RESOLVIDO', ['COMENTAR']],
  ['admin, fechado', usuario(OUTRO, 'ADMIN'), 'FECHADO', []],
]

describe('acoesDisponiveis', () => {
  it.each(CENARIOS)('%s', (_nome, quem, status, esperadas) => {
    expect([...acoesDisponiveis(quem, chamado(status))].sort()).toEqual(esperadas)
  })

  it('um atendente que é dono do chamado não herda as ações de solicitante', () => {
    const atendenteDono = usuario(DONO, 'ATENDENTE')
    expect(acoesDisponiveis(atendenteDono, chamado('RESOLVIDO')).has('FECHAR')).toBe(false)
    expect(acoesDisponiveis(atendenteDono, chamado('ABERTO')).has('CANCELAR')).toBe(false)
  })
})
