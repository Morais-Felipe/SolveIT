package com.solveit.dto.categoria;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class CategoriaDtos {

    private CategoriaDtos() {
    }

    public record Criar(
            @NotBlank(message = "O nome é obrigatório")
            @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
            String nome,
            @Size(max = 10_000, message = "A descrição deve ter no máximo 10000 caracteres")
            String descricao) {
    }

    public record Resposta(
            Long id,
            String nome,
            String descricao,
            Boolean ativo) {
    }

    public record Pagina(
            List<Resposta> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
