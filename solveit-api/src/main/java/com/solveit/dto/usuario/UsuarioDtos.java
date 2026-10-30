package com.solveit.dto.usuario;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.solveit.dto.validation.ValidPassword;
import com.solveit.enums.PerfilUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class UsuarioDtos {

    private UsuarioDtos() {
    }

    public record Criar(
            @NotBlank(message = "O nome é obrigatório")
            @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
            String nome,
            @NotBlank(message = "O e-mail é obrigatório")
            @Email(message = "O e-mail deve ter um formato válido")
            @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres")
            String email,
            @ValidPassword
            String senha,
            @NotNull(message = "O perfil é obrigatório")
            PerfilUsuario perfil) {

        public Criar {
            nome = trim(nome);
            email = normalizarEmail(email);
        }
    }

    /**
     * Somente dados pessoais. {@code perfil} e {@code ativo} são declarados apenas
     * para que, se enviados, a requisição seja rejeitada com 400 em vez de o Jackson
     * ignorá-los em silêncio; o service nunca lê esses componentes.
     */
    public record EditarDados(
            @NotBlank(message = "O nome é obrigatório")
            @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
            String nome,
            @NotBlank(message = "O e-mail é obrigatório")
            @Email(message = "O e-mail deve ter um formato válido")
            @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres")
            String email,
            @ValidPassword(optional = true)
            String senha,
            @NotNull(message = "A versão esperada é obrigatória")
            @PositiveOrZero(message = "A versão esperada não pode ser negativa")
            Long versaoEsperada,
            @Null(message = "O perfil não pode ser alterado por esta rota")
            Object perfil,
            @Null(message = "O estado ativo não pode ser alterado por esta rota")
            Object ativo) {

        public EditarDados {
            nome = trim(nome);
            email = normalizarEmail(email);
        }
    }

    public record Resposta(
            Long id,
            String nome,
            String email,
            PerfilUsuario perfil,
            Boolean ativo,
            LocalDateTime criadoEm,
            Long versao) {
    }

    /**
     * Estado operacional desejado: perfil e atividade juntos, numa única operação
     * atômica. Qualquer campo fora do contrato é rejeitado.
     */
    public record AlterarOperacao(
            @NotNull(message = "O perfil é obrigatório")
            PerfilUsuario perfil,
            @NotNull(message = "O estado ativo é obrigatório")
            Boolean ativo,
            @NotNull(message = "A versão esperada é obrigatória")
            @PositiveOrZero(message = "A versão esperada não pode ser negativa")
            Long versaoEsperada,
            @JsonAnySetter
            @Size(max = 0, message = "Envie somente perfil, ativo e versaoEsperada")
            Map<String, Object> camposNaoPermitidos) {

        public AlterarOperacao {
            camposNaoPermitidos = camposNaoPermitidos == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(camposNaoPermitidos));
        }
    }

    public record ResultadoOperacao(
            Resposta usuario,
            int chamadosDevolvidos) {
    }

    public record Pagina(
            List<Resposta> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
