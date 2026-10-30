package com.solveit.controller;

import com.solveit.dto.usuario.UsuarioDtos;
import com.solveit.security.UsuarioPrincipal;
import com.solveit.service.UsuarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping
    public ResponseEntity<UsuarioDtos.Resposta> criar(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody UsuarioDtos.Criar request) {
        UsuarioDtos.Resposta response = usuarioService.criar(principal.getId(), request);
        return ResponseEntity
                .created(URI.create("/api/usuarios/" + response.id()))
                .body(response);
    }

    @PutMapping("/{id}")
    public UsuarioDtos.Resposta editarDados(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody UsuarioDtos.EditarDados request) {
        return usuarioService.editarDados(principal.getId(), id, request);
    }

    @PatchMapping("/{id}/operacao")
    public UsuarioDtos.ResultadoOperacao alterarOperacao(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody UsuarioDtos.AlterarOperacao request) {
        return usuarioService.alterarOperacao(principal.getId(), id, request);
    }
}
