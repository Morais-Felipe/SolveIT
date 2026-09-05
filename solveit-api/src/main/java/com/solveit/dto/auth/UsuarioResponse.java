package com.solveit.dto.auth;

import com.solveit.enums.PerfilUsuario;
import java.time.LocalDateTime;

public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        PerfilUsuario perfil,
        Boolean ativo,
        LocalDateTime criadoEm) {
}
