package com.solveit.dto.usuario;

import com.solveit.enums.PerfilUsuario;
import java.time.LocalDateTime;
import java.util.List;

public final class UsuarioDtos {

    private UsuarioDtos() {
    }

    public record Resposta(
            Long id,
            String nome,
            String email,
            PerfilUsuario perfil,
            Boolean ativo,
            LocalDateTime criadoEm) {
    }

    public record Pagina(
            List<Resposta> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
