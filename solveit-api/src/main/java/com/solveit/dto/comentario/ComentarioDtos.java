package com.solveit.dto.comentario;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ComentarioDtos {

    private ComentarioDtos() {
    }

    /**
     * Somente o texto. O autor vem do usuário autenticado e nunca do corpo; qualquer
     * outro campo, como autorId, é rejeitado em vez de ignorado.
     */
    public record Criar(
            @NotBlank(message = "O texto é obrigatório")
            @Size(max = 10_000, message = "O texto deve ter no máximo 10000 caracteres")
            String texto,
            @JsonAnySetter
            @Size(max = 0, message = "Envie somente texto")
            Map<String, Object> camposNaoPermitidos) {

        public Criar {
            if (texto != null) {
                texto = texto.trim();
            }
            camposNaoPermitidos = camposNaoPermitidos == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(camposNaoPermitidos));
        }
    }

    public record Resposta(
            Long id,
            String texto,
            Autor autor,
            LocalDateTime criadoEm) {
    }

    public record Autor(Long id, String nome) {
    }
}
