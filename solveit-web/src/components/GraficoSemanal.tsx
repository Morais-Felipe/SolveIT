import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { formatarDiaEMes, formatarPeriodo, type LinhaSemana } from '@/lib/semanas'

type DadosDica = { active?: boolean; payload?: readonly { payload?: LinhaSemana }[] }

function Dica({ active, payload }: DadosDica) {
  const semana = payload?.[0]?.payload
  if (!active || !semana) {
    return null
  }
  return (
    <div className="grid gap-1 rounded-lg bg-popover px-3 py-2 text-xs text-popover-foreground shadow-lg ring-1 ring-foreground/10">
      <p className="font-medium">{formatarPeriodo(semana)}</p>
      <p className="text-muted-foreground">
        Novos: <span className="text-foreground tabular-nums">{semana.novos}</span>
      </p>
      <p className="text-muted-foreground">
        Resolvidos: <span className="text-primary tabular-nums">{semana.resolvidos}</span>
      </p>
    </div>
  )
}

/**
 * Só o desenho: fica em arquivo próprio para o Recharts ser baixado apenas ao abrir o dashboard.
 * As séries se distinguem pela forma (tracejada e sólida com área), não só pela cor. A animação
 * fica desligada porque os dados não mudam na tela. Os números acessíveis estão na tabela do painel.
 */
export default function GraficoSemanal({ semanas }: { semanas: LinhaSemana[] }) {
  return (
    <div aria-hidden="true" className="h-64 w-full min-w-0">
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={semanas} margin={{ left: 0, right: 8, top: 8 }} accessibilityLayer={false}>
          <defs>
            <linearGradient id="grafico-semanal-resolvidos" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--primary)" stopOpacity={0.3} />
              <stop offset="100%" stopColor="var(--primary)" stopOpacity={0.02} />
            </linearGradient>
          </defs>
          <CartesianGrid vertical={false} stroke="var(--border)" strokeDasharray="3 3" />
          <XAxis
            dataKey="inicio"
            tickFormatter={formatarDiaEMes}
            tick={{ fill: 'var(--muted-foreground)', fontSize: 12 }}
            tickLine={false}
            axisLine={false}
            tickMargin={8}
            interval="preserveStartEnd"
            minTickGap={16}
          />
          <YAxis
            allowDecimals={false}
            tick={{ fill: 'var(--muted-foreground)', fontSize: 12 }}
            tickLine={false}
            axisLine={false}
            width={32}
          />
          <Tooltip content={Dica} cursor={{ stroke: 'var(--border)' }} />
          <Area
            dataKey="novos"
            type="monotone"
            stroke="var(--foreground)"
            strokeDasharray="4 4"
            strokeWidth={2}
            fill="none"
            isAnimationActive={false}
          />
          <Area
            dataKey="resolvidos"
            type="monotone"
            stroke="var(--primary)"
            strokeWidth={2}
            fill="url(#grafico-semanal-resolvidos)"
            isAnimationActive={false}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  )
}
