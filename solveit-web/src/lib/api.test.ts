import { describe, expect, it } from 'vitest'
import { ApiError, toApiError } from '@/lib/api'

describe('toApiError', () => {
  it('usa code, detail e erros por campo do Problem Details', () => {
    const erro = toApiError({
      response: {
        status: 400,
        data: {
          code: 'VALIDATION_ERROR',
          detail: 'Um ou mais campos estão inválidos.',
          errors: { email: 'O e-mail deve ter um formato válido' },
        },
      },
    })

    expect(erro).toBeInstanceOf(ApiError)
    expect(erro.status).toBe(400)
    expect(erro.code).toBe('VALIDATION_ERROR')
    expect(erro.message).toBe('Um ou mais campos estão inválidos.')
    expect(erro.fieldErrors.email).toBe('O e-mail deve ter um formato válido')
  })

  it('trata falta de resposta como erro de rede', () => {
    const erro = toApiError({ message: 'Network Error' })

    expect(erro.status).toBe(0)
    expect(erro.code).toBe('NETWORK_ERROR')
    expect(erro.message).toMatch(/conectar ao servidor/)
  })

  it('usa uma mensagem genérica quando a resposta não é Problem Details', () => {
    const erro = toApiError({ response: { status: 502, data: '<html>Bad gateway</html>' } })

    expect(erro.status).toBe(502)
    expect(erro.code).toBe('UNEXPECTED_ERROR')
    expect(erro.message).not.toContain('<html>')
  })
})
