package com.solveit.controller;

import com.solveit.dto.usuario.UsuarioDtos;
import com.solveit.security.UsuarioPrincipal;
import com.solveit.service.UsuarioService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public UsuarioDtos.Pagina listar(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "A página não pode ser negativa")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "O tamanho da página deve ser entre 1 e 100")
            @Max(value = 100, message = "O tamanho da página deve ser entre 1 e 100")
            int size) {
        return usuarioService.listar(principal.getId(), page, size);
    }

    @GetMapping("/{id}")
    public UsuarioDtos.Resposta buscarPorId(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return usuarioService.buscarPorId(principal.getId(), id);
    }
}
