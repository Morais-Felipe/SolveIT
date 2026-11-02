package com.solveit.dto.dashboard;

import com.solveit.enums.Prioridade;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record Resumo(
            Totais totais,
            List<PorCategoria> porCategoria,
            List<CargaAtendente> cargaPorAtendente,
            Map<Prioridade, Long> abertosPorPrioridade,
            List<Semana> semanas) {
    }

    /** {@code criticaAberta}: chamados ABERTO com prioridade CRITICA (decisão D-3). */
    public record Totais(long aberto, long emAndamento, long resolvido, long criticaAberta) {
    }

    public record PorCategoria(Long categoriaId, String nome, long quantidade) {
    }

    /** {@code emAndamento}: carga ativa do atendente (decisão D-2). */
    public record CargaAtendente(Long atendenteId, String nome, long emAndamento) {
    }

    /**
     * Janela de 7 dias em UTC: {@code inicio} entra e {@code fim} não, para nenhum chamado
     * contar em duas janelas. {@code novos} usa criadoEm e {@code resolvidos} usa resolvidoEm.
     */
    public record Semana(LocalDateTime inicio, LocalDateTime fim, long novos, long resolvidos) {
    }
}
