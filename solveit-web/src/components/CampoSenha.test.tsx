import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CampoSenha } from '@/components/CampoSenha'

describe('CampoSenha', () => {
  it('começa oculta e alterna a visibilidade sem enviar o formulário', async () => {
    const usuarioEvento = userEvent.setup()
    const aoEnviar = vi.fn((evento: React.FormEvent) => evento.preventDefault())
    render(
      <form onSubmit={aoEnviar}>
        <CampoSenha id="senha" rotulo="Senha" />
      </form>,
    )
    const campo = screen.getByLabelText('Senha')
    expect(campo).toHaveAttribute('type', 'password')

    await usuarioEvento.click(screen.getByRole('button', { name: 'Mostrar senha' }))
    expect(campo).toHaveAttribute('type', 'text')

    await usuarioEvento.click(screen.getByRole('button', { name: 'Ocultar senha' }))
    expect(campo).toHaveAttribute('type', 'password')
    expect(aoEnviar).not.toHaveBeenCalled()
  })
})
