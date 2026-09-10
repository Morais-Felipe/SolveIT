package com.solveit.dto.auth;

import com.solveit.dto.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record RegisterRequest(
        @NotBlank
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
        String nome,
        @NotBlank
        @Email(message = "O e-mail deve ter um formato válido")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres")
        String email,
        @ValidPassword String senha) {

    public RegisterRequest {
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
    }
}
