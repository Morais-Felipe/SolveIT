import { useEffect, useRef } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'

/**
 * Aviso de resultado no topo da tela. Como a página não muda de altura ao recarregar, quem agiu
 * no fim de uma lista longa não veria o aviso: por isso ele rola até ficar visível. Se já está
 * visível, nada se move.
 */
export function AvisoPagina({ texto, erro = false }: { texto: string; erro?: boolean }) {
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    ref.current?.scrollIntoView({ block: 'nearest' })
  }, [texto])

  return (
    <Alert ref={ref} variant={erro ? 'destructive' : 'default'} className="scroll-mt-4">
      <AlertDescription>{texto}</AlertDescription>
    </Alert>
  )
}
