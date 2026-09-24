package com.solveit.controller;

import com.solveit.dto.categoria.CategoriaDtos;
import com.solveit.security.UsuarioPrincipal;
import com.solveit.service.CategoriaService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/categorias")
@PreAuthorize("isAuthenticated()")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public CategoriaDtos.Pagina listar(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return categoriaService.listarAtivas(principal.getId(), page, size);
    }

    @GetMapping("/{id}")
    public CategoriaDtos.Resposta buscarPorId(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return categoriaService.buscarPorId(principal.getId(), id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoriaDtos.Resposta> criar(
            @AuthenticationPrincipal UsuarioPrincipal principal,
            @Valid @RequestBody CategoriaDtos.Criar request) {
        CategoriaDtos.Resposta response = categoriaService.criar(principal.getId(), request);
        return ResponseEntity
                .created(URI.create("/api/categorias/" + response.id()))
                .body(response);
    }
}
