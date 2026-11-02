import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

export function Bloco({ className }: { className?: string }) {
  return <div className={cn('animate-pulse rounded-md bg-muted', className)} />
}

/**
 * Formato do conteúdo enquanto ele carrega. O texto continua disponível para leitores de tela,
 * e o CSS só mostra os blocos se a resposta demorar: em respostas rápidas, nada pisca.
 */
export function Esqueleto({ rotulo, children }: { rotulo: string; children: ReactNode }) {
  return (
    <div role="status">
      <span className="sr-only">{rotulo}</span>
      <div aria-hidden="true" className="esqueleto-atrasado">
        {children}
      </div>
    </div>
  )
}

export function EsqueletoLista({ rotulo, linhas = 5 }: { rotulo: string; linhas?: number }) {
  return (
    <Esqueleto rotulo={rotulo}>
      <div className="grid gap-3">
        {Array.from({ length: linhas }, (_, indice) => (
          <div key={indice} className="grid gap-2 rounded-lg border border-border p-4">
            <Bloco className="h-4 w-2/3" />
            <Bloco className="h-3 w-1/3" />
          </div>
        ))}
      </div>
    </Esqueleto>
  )
}

export function EsqueletoDashboard({ rotulo }: { rotulo: string }) {
  return (
    <Esqueleto rotulo={rotulo}>
      <div className="grid gap-6">
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }, (_, indice) => (
            <Bloco key={indice} className="h-28 rounded-xl" />
          ))}
        </div>
        <Bloco className="h-80 rounded-xl" />
        <div className="grid gap-6 lg:grid-cols-2">
          <Bloco className="h-64 rounded-xl" />
          <Bloco className="h-64 rounded-xl" />
        </div>
      </div>
    </Esqueleto>
  )
}

export function EsqueletoDetalhe({ rotulo }: { rotulo: string }) {
  return (
    <Esqueleto rotulo={rotulo}>
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <div className="grid content-start gap-3">
          <Bloco className="h-4 w-24" />
          <Bloco className="h-7 w-2/3" />
          <Bloco className="h-4 w-full" />
          <Bloco className="h-4 w-5/6" />
        </div>
        <Bloco className="h-48 rounded-xl" />
      </div>
    </Esqueleto>
  )
}
