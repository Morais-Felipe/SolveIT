import type { ReactNode } from 'react'
import { FundoAnimado } from '@/components/FundoAnimado'
import { Card } from '@/components/ui/card'

/**
 * Moldura de Login e Cadastro. A altura mínima (em vez de altura fixa) deixa a página rolar
 * quando o cartão não cabe, como em celular deitado ou com o teclado aberto.
 */
export function AutenticacaoLayout({ children }: { children: ReactNode }) {
  return (
    <div className="isolate flex min-h-svh flex-col bg-background text-foreground">
      <FundoAnimado />
      <header className="border-b border-border px-6 py-4">
        <span className="text-sm font-semibold tracking-wide">
          Solve<span className="text-primary">IT</span>
        </span>
      </header>
      <main className="flex flex-1 items-center justify-center p-4">
        <Card className="w-full max-w-sm bg-card/70 backdrop-blur animate-in fade-in slide-in-from-bottom-4 duration-700 motion-reduce:animate-none">
          {children}
        </Card>
      </main>
    </div>
  )
}
