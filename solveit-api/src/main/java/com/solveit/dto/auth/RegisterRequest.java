package com.solveit.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterRequest(
        @NotBlank String nome,
        @NotBlank String email,
        @NotNull String senha) {
}
