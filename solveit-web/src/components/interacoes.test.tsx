import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { LogIn } from 'lucide-react'
import type { FormEvent } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AvisoPagina } from '@/components/AvisoPagina'
import { EsqueletoLista } from '@/components/Esqueleto'
import { Button } from '@/components/ui/button'
import { ShinyButton } from '@/components/ui/shiny-button'

describe('feedback de interação', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('aviso rola até ficar visível, sem mexer na página se já estiver', () => {
    const rolar = vi.spyOn(Element.prototype, 'scrollIntoView')
    render(<AvisoPagina texto="Usuário salvo." />)

    expect(screen.getByRole('alert')).toHaveTextContent('Usuário salvo.')
    expect(rolar).toHaveBeenCalledWith({ block: 'nearest', behavior: 'instant' })
  })

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

  it('botão brilhante envia o formulário e o nome acessível é só o texto', async () => {
    const aoEnviar = vi.fn((evento: FormEvent) => evento.preventDefault())
    render(
      <form onSubmit={aoEnviar}>
        <ShinyButton type="submit" icone={LogIn}>
          Entrar
        </ShinyButton>
      </form>,
    )

    const botao = screen.getByRole('button', { name: 'Entrar' })
    expect(botao.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
    await userEvent.click(botao)
    expect(aoEnviar).toHaveBeenCalledOnce()
  })

  it('botão brilhante em andamento não envia de novo e troca o ícone pelo indicador', async () => {
    const aoEnviar = vi.fn((evento: FormEvent) => evento.preventDefault())
    render(
      <form onSubmit={aoEnviar}>
        <ShinyButton type="submit" icone={LogIn} carregando disabled>
          Entrando…
        </ShinyButton>
      </form>,
    )

    const botao = screen.getByRole('button', { name: 'Entrando…' })
    expect(botao).toBeDisabled()
    expect(botao.querySelectorAll('svg')).toHaveLength(1)
    expect(botao.querySelector('svg')).toHaveClass('animate-spin')
    await userEvent.click(botao)
    expect(aoEnviar).not.toHaveBeenCalled()
  })
})
