import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { EsqueletoLista } from '@/components/Esqueleto'
import { Button } from '@/components/ui/button'

describe('feedback de interação', () => {
  it('esqueleto anuncia o carregamento e esconde os blocos dos leitores de tela', () => {
    render(<EsqueletoLista rotulo="Carregando chamados…" linhas={2} />)

    const status = screen.getByRole('status')
    expect(status).toHaveTextContent('Carregando chamados…')
    expect(status.querySelector('[aria-hidden="true"]')).not.toBeNull()
  })

  it('botão em andamento mostra o indicador sem mudar o nome acessível', () => {
    render(
      <Button carregando disabled>
        Enviando…
      </Button>,
    )

    const botao = screen.getByRole('button', { name: 'Enviando…' })
    expect(botao.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
  })

  it('botão parado não mostra o indicador', () => {
    render(<Button>Salvar</Button>)

    expect(screen.getByRole('button', { name: 'Salvar' }).querySelector('svg')).toBeNull()
  })
})
