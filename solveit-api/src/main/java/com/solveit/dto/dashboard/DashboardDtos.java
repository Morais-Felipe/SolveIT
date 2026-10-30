package com.solveit.dto.dashboard;

import com.solveit.enums.Prioridade;
import java.util.List;
import java.util.Map;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record Resumo(
            Totais totais,
            List<PorCategoria> porCategoria,
            List<CargaAtendente> cargaPorAtendente,
            Map<Prioridade, Long> abertosPorPrioridade) {
    }

    /** {@code criticaAberta}: chamados ABERTO com prioridade CRITICA (decisão D-3). */
    public record Totais(long aberto, long emAndamento, long resolvido, long criticaAberta) {
    }

    public record PorCategoria(Long categoriaId, String nome, long quantidade) {
    }

    /** {@code emAndamento}: carga ativa do atendente (decisão D-2). */
    public record CargaAtendente(Long atendenteId, String nome, long emAndamento) {
    }
}
