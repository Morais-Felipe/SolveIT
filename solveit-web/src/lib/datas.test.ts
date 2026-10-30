import { describe, expect, it } from 'vitest'
import { formatarData, formatarDataHora, lerDataDaApi } from '@/lib/chamados'

// A API grava LocalDateTime.now(ZoneOffset.UTC) e o serializa sem fuso.
// O esperado é calculado a partir do mesmo instante UTC, então o teste passa em qualquer fuso.
const INSTANTE_UTC = new Date(Date.UTC(2026, 9, 30, 18, 16, 0))

describe('datas da API', () => {
  it('lê LocalDateTime sem fuso como UTC', () => {
    expect(lerDataDaApi('2026-10-30T18:16:00').getTime()).toBe(INSTANTE_UTC.getTime())
  })

  it('respeita um fuso explícito quando a API o envia', () => {
    expect(lerDataDaApi('2026-10-30T15:16:00-03:00').getTime()).toBe(INSTANTE_UTC.getTime())
    expect(lerDataDaApi('2026-10-30T18:16:00Z').getTime()).toBe(INSTANTE_UTC.getTime())
  })

  it('formata no fuso de quem está vendo', () => {
    expect(formatarDataHora('2026-10-30T18:16:00')).toBe(
      INSTANTE_UTC.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }),
    )
    expect(formatarData('2026-10-30T18:16:00')).toBe(INSTANTE_UTC.toLocaleDateString('pt-BR'))
  })
})
