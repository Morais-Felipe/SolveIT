package com.solveit.dto.chamado;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.solveit.enums.Prioridade;
import com.solveit.enums.StatusChamado;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChamadoDtos {

    private ChamadoDtos() {
    }

    /**
     * Somente o que o solicitante decide. Solicitante, atendente, status, versão e datas
     * pertencem ao servidor: qualquer campo fora deste contrato cai em
     * {@code camposNaoPermitidos} e a requisição é rejeitada em vez de ignorada.
     */
    public record Criar(
            @NotBlank(message = "O título é obrigatório")
            @Size(max = 200, message = "O título deve ter no máximo 200 caracteres")
            String titulo,
            @NotBlank(message = "A descrição é obrigatória")
            @Size(max = 10_000, message = "A descrição deve ter no máximo 10000 caracteres")
            String descricao,
            @NotNull(message = "A prioridade é obrigatória")
            Prioridade prioridade,
            @NotNull(message = "A categoria é obrigatória")
            @Positive(message = "A categoria deve ser um identificador positivo")
            Long categoriaId,
            @JsonAnySetter
            @Size(max = 0, message = "Envie somente titulo, descricao, prioridade e categoriaId")
            Map<String, Object> camposNaoPermitidos) {

        public Criar {
            if (titulo != null) {
                titulo = titulo.trim();
            }
            camposNaoPermitidos = camposNaoPermitidos == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(camposNaoPermitidos));
        }
    }

    /**
     * Edição limitada ao texto do chamado. Status, prioridade, categoria, responsável e
     * datas têm rotas próprias ou pertencem ao servidor; se enviados, viram 400.
     */
    public record Editar(
            @NotBlank(message = "O título é obrigatório")
            @Size(max = 200, message = "O título deve ter no máximo 200 caracteres")
            String titulo,
            @NotBlank(message = "A descrição é obrigatória")
            @Size(max = 10_000, message = "A descrição deve ter no máximo 10000 caracteres")
            String descricao,
            @NotNull(message = "A versão esperada é obrigatória")
            @PositiveOrZero(message = "A versão esperada não pode ser negativa")
            Long versaoEsperada,
            @JsonAnySetter
            @Size(max = 0, message = "Envie somente titulo, descricao e versaoEsperada")
            Map<String, Object> camposNaoPermitidos) {

        public Editar {
            if (titulo != null) {
                titulo = titulo.trim();
            }
            camposNaoPermitidos = camposNaoPermitidos == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(camposNaoPermitidos));
        }
    }

    public record Atribuir(
            @NotNull(message = "O atendente é obrigatório")
            @Positive(message = "O atendente deve ser um identificador positivo")
            Long atendenteId,
            @NotNull(message = "A versão esperada é obrigatória")
            @PositiveOrZero(message = "A versão esperada não pode ser negativa")
            Long versaoEsperada,
            @Size(max = 500, message = "A observação deve ter no máximo 500 caracteres")
            String observacao,
            @JsonAnySetter
            @Size(max = 0, message = "Envie somente atendenteId, versaoEsperada e observacao")
            Map<String, Object> camposNaoPermitidos) {

        public Atribuir {
            if (observacao != null) {
                observacao = observacao.trim();
                if (observacao.isEmpty()) {
                    observacao = null;
                }
            }
            camposNaoPermitidos = camposNaoPermitidos == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(camposNaoPermitidos));
        }
    }

    public record Detalhe(
            Long id,
            String titulo,
            String descricao,
            StatusChamado status,
            Prioridade prioridade,
            Pessoa solicitante,
            Pessoa atendente,
            CategoriaResumo categoria,
            LocalDateTime criadoEm,
            LocalDateTime atualizadoEm,
            LocalDateTime resolvidoEm,
            Long versao) {
    }

    public record Filtros(
            StatusChamado status,
            Prioridade prioridade,
            Long categoriaId,
            Long atendenteId,
            Long solicitanteId) {
    }

    public record Pagina(
            List<Detalhe> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record Pessoa(Long id, String nome) {
    }

    public record CategoriaResumo(Long id, String nome, Boolean ativo) {
    }
}
