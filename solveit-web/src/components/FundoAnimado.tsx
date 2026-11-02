import { useEffect, useRef } from 'react'

const LINHAS_HORIZONTAIS = [18, 50, 82]
const LINHAS_VERTICAIS = [22, 50, 78]

/** Área de tela (em px²) por partícula: telas menores recebem menos partículas. */
const AREA_POR_PARTICULA = 9000

type Particula = { x: number; y: number; velocidade: number; opacidade: number }

function novaParticula(largura: number, altura: number): Particula {
  return {
    x: Math.random() * largura,
    y: Math.random() * altura,
    velocidade: Math.random() * 0.25 + 0.05,
    opacidade: Math.random() * 0.35 + 0.15,
  }
}

/**
 * Fundo decorativo das telas de entrada: vinheta, grade e partículas laranja subindo.
 * Quem pede menos movimento ao sistema vê a grade pronta e nenhuma partícula. O navegador
 * já suspende o requestAnimationFrame em abas ocultas, então nada é desenhado sem necessidade.
 */
export function FundoAnimado() {
  const canvasRef = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
      return
    }
    const canvas = canvasRef.current
    const contexto = canvas?.getContext('2d')
    if (!canvas || !contexto) {
      return
    }

    let particulas: Particula[] = []
    let quadro = 0

    function ajustarTamanho(tela: HTMLCanvasElement) {
      tela.width = window.innerWidth
      tela.height = window.innerHeight
      const total = Math.floor((tela.width * tela.height) / AREA_POR_PARTICULA)
      particulas = Array.from({ length: total }, () => novaParticula(tela.width, tela.height))
    }

    function desenhar(tela: HTMLCanvasElement, pincel: CanvasRenderingContext2D) {
      pincel.clearRect(0, 0, tela.width, tela.height)
      for (const particula of particulas) {
        particula.y -= particula.velocidade
        if (particula.y < 0) {
          Object.assign(particula, novaParticula(tela.width, tela.height), {
            y: tela.height + Math.random() * 40,
          })
        }
        pincel.fillStyle = `rgba(249, 115, 22, ${particula.opacidade})`
        pincel.fillRect(particula.x, particula.y, 0.7, 2.2)
      }
      quadro = requestAnimationFrame(() => desenhar(tela, pincel))
    }

    const aoRedimensionar = () => ajustarTamanho(canvas)
    window.addEventListener('resize', aoRedimensionar)
    ajustarTamanho(canvas)
    quadro = requestAnimationFrame(() => desenhar(canvas, contexto))

    return () => {
      window.removeEventListener('resize', aoRedimensionar)
      cancelAnimationFrame(quadro)
    }
  }, [])

  return (
    <div aria-hidden="true" className="pointer-events-none fixed inset-0 -z-10 overflow-hidden">
      <div className="absolute inset-0 [background:radial-gradient(80%_60%_at_50%_30%,color-mix(in_oklch,var(--primary)_12%,transparent),transparent_60%)]" />
      {LINHAS_HORIZONTAIS.map((posicao, indice) => (
        <div
          key={`h${posicao}`}
          className="fundo-linha-h"
          style={{ top: `${posicao}%`, animationDelay: `${0.12 + indice * 0.1}s` }}
        />
      ))}
      {LINHAS_VERTICAIS.map((posicao, indice) => (
        <div
          key={`v${posicao}`}
          className="fundo-linha-v"
          style={{ left: `${posicao}%`, animationDelay: `${0.42 + indice * 0.12}s` }}
        />
      ))}
      <canvas ref={canvasRef} className="absolute inset-0 size-full opacity-60 mix-blend-screen" />
    </div>
  )
}
