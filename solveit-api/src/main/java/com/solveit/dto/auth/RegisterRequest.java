package com.solveit.dto.auth;

import com.solveit.dto.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank String nome,
        @NotBlank String email,
        @ValidPassword String senha) {
}
