/** Página provisória para as telas das próximas etapas da Fase 6. */
export function EmBreve({ titulo, plano }: { titulo: string; plano: string }) {
  return (
    <section className="grid gap-2">
      <h1 className="text-2xl font-semibold">{titulo}</h1>
      <p className="text-muted-foreground">Esta tela chega no plano {plano}.</p>
    </section>
  )
}

export function NaoEncontrado() {
  return (
    <section className="flex min-h-svh flex-col items-center justify-center gap-2 bg-background p-4 text-foreground">
      <h1 className="text-2xl font-semibold">Página não encontrada</h1>
      <a href="/" className="text-primary underline-offset-4 hover:underline">
        Voltar ao início
      </a>
    </section>
  )
}
