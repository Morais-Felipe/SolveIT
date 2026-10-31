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
