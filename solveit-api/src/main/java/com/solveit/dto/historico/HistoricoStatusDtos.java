package com.solveit.dto.historico;

import com.solveit.enums.StatusChamado;
import java.time.LocalDateTime;

public final class HistoricoStatusDtos {

    private HistoricoStatusDtos() {
    }

    /** {@code statusAnterior} é nulo somente no registro de abertura. */
    public record Resposta(
            Long id,
            StatusChamado statusAnterior,
            StatusChamado statusNovo,
            Autor alteradoPor,
            LocalDateTime alteradoEm,
            String observacao) {
    }

    public record Autor(Long id, String nome) {
    }
}
